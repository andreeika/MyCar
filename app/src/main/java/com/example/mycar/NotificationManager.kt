package com.example.mycar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.sql.Statement
import java.util.*

class NotificationManager {

    companion object {
        private const val PREF_CURRENT_CAR_ID = "current_car_id"
    }

    fun getCurrentCarId(context: Context): Int {
        val sharedPreferences = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt(PREF_CURRENT_CAR_ID, -1)
    }

    fun setCurrentCarId(context: Context, carId: Int) {
        val sharedPreferences = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt(PREF_CURRENT_CAR_ID, carId).apply()
    }

    suspend fun getUrgentNotifications(context: Context): List<MainActivityNotifications.Notification> {
        return withContext(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()
                val sharedPreferences = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
                val userId = sharedPreferences.getInt("user_id", 1)
                val currentCarId = getCurrentCarId(context)

                if (connect != null) {
                    val carsQuery = if (currentCarId > 0) {
                        """
                        SELECT c.car_id, c.mileage as current_mileage, 
                               cb.name as brand_name, cm.name as model_name
                        FROM Cars c
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
                        WHERE c.user_id = $userId AND c.car_id = $currentCarId
                        """
                    } else {
                        """
                        SELECT c.car_id, c.mileage as current_mileage, 
                               cb.name as brand_name, cm.name as model_name
                        FROM Cars c
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
                        WHERE c.user_id = $userId
                        ORDER BY c.car_id
                        """
                    }

                    val carsStatement: Statement = connect.createStatement()
                    val carsResult: ResultSet = carsStatement.executeQuery(carsQuery)

                    val notifications = mutableListOf<MainActivityNotifications.Notification>()
                    val processedCars = mutableSetOf<Int>()

                    while (carsResult.next()) {
                        val carId = carsResult.getInt("car_id")
                        val currentMileage = carsResult.getInt("current_mileage")
                        val brandName = carsResult.getString("brand_name") ?: "Неизвестно"
                        val modelName = carsResult.getString("model_name") ?: "Неизвестно"
                        val carName = "$brandName $modelName"

                        processedCars.add(carId)

                        val overdueQuery = """
                            SELECT st.service_type_id, st.name as service_name, st.internal_km,
                                   COALESCE(MAX(m.mileage), 0) as last_service_mileage
                            FROM ServiceTypes st
                            LEFT JOIN Maintenance m ON st.service_type_id = m.service_type_id AND m.car_id = $carId
                            WHERE st.internal_km > 0
                            GROUP BY st.service_type_id, st.name, st.internal_km
                            HAVING COALESCE(MAX(m.mileage), 0) + st.internal_km < $currentMileage
                        """

                        val overdueStatement: Statement = connect.createStatement()
                        val overdueResult: ResultSet = overdueStatement.executeQuery(overdueQuery)

                        while (overdueResult.next()) {
                            val serviceName = overdueResult.getString("service_name")
                            val lastServiceMileage = overdueResult.getInt("last_service_mileage")
                            val interval = overdueResult.getInt("internal_km")
                            val overdueBy = currentMileage - (lastServiceMileage + interval)

                            val notificationId = carId * 1000 + overdueResult.getInt("service_type_id")

                            notifications.add(
                                MainActivityNotifications.Notification(
                                    id = notificationId,
                                    type = MainActivityNotifications.NotificationType.URGENT,
                                    title = "ПРОСРОЧЕНО: $serviceName",
                                    message = "$carName: Обслуживание '$serviceName' просрочено на $overdueBy км. " +
                                            "Последнее обслуживание было на пробеге $lastServiceMileage км. " +
                                            "Текущий пробег: $currentMileage км.",
                                    carId = carId,
                                    carName = carName,
                                    date = Date(),
                                    actionRequired = true
                                )
                            )
                        }
                        overdueResult.close()
                        overdueStatement.close()

                        val urgentQuery = """
                            SELECT st.service_type_id, st.name as service_name, st.internal_km,
                                   COALESCE(MAX(m.mileage), 0) as last_service_mileage
                            FROM ServiceTypes st
                            LEFT JOIN Maintenance m ON st.service_type_id = m.service_type_id AND m.car_id = $carId
                            WHERE st.internal_km > 0
                            GROUP BY st.service_type_id, st.name, st.internal_km
                            HAVING COALESCE(MAX(m.mileage), 0) + st.internal_km - $currentMileage BETWEEN 1 AND 500
                            ORDER BY COALESCE(MAX(m.mileage), 0) + st.internal_km - $currentMileage ASC
                        """

                        val urgentStatement: Statement = connect.createStatement()
                        val urgentResult: ResultSet = urgentStatement.executeQuery(urgentQuery)

                        while (urgentResult.next()) {
                            val serviceName = urgentResult.getString("service_name")
                            val lastServiceMileage = urgentResult.getInt("last_service_mileage")
                            val interval = urgentResult.getInt("internal_km")
                            val remainingKm = (lastServiceMileage + interval) - currentMileage

                            val notificationId = carId * 1000 + urgentResult.getInt("service_type_id") + 10000

                            notifications.add(
                                MainActivityNotifications.Notification(
                                    id = notificationId,
                                    type = MainActivityNotifications.NotificationType.URGENT,
                                    title = "СРОЧНО: $serviceName",
                                    message = "$carName: Обслуживание '$serviceName' требуется через $remainingKm км. " +
                                            "Текущий пробег: $currentMileage км.",
                                    carId = carId,
                                    carName = carName,
                                    date = Date(),
                                    actionRequired = true
                                )
                            )
                        }
                        urgentResult.close()
                        urgentStatement.close()
                    }

                    carsResult.close()
                    carsStatement.close()
                    connect.close()

                    // Если нет уведомлений для текущего автомобиля, показываем общее уведомление
                    if (notifications.isEmpty() && currentCarId > 0) {
                        val carInfo = getCarInfoById(context, currentCarId)
                        if (carInfo != null) {
                            notifications.add(
                                MainActivityNotifications.Notification(
                                    id = currentCarId * 1000 + 999,
                                    type = MainActivityNotifications.NotificationType.INFO,
                                    title = "Все в порядке",
                                    message = "${carInfo.carName}: Все обслуживания выполнены своевременно. Текущий пробег: ${carInfo.mileage} км.",
                                    carId = currentCarId,
                                    carName = carInfo.carName,
                                    date = Date()
                                )
                            )
                        }
                    }

                    notifications
                } else {
                    emptyList()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun getMaintenanceRecommendations(context: Context): List<MainActivityNotifications.Notification> {
        return withContext(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()
                val sharedPreferences = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
                val userId = sharedPreferences.getInt("user_id", 1)
                val currentCarId = getCurrentCarId(context)

                if (connect != null) {
                    val carsQuery = if (currentCarId > 0) {
                        """
                        SELECT c.car_id, c.mileage as current_mileage, 
                               cb.name as brand_name, cm.name as model_name
                        FROM Cars c
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
                        WHERE c.user_id = $userId AND c.car_id = $currentCarId
                        """
                    } else {
                        """
                        SELECT c.car_id, c.mileage as current_mileage, 
                               cb.name as brand_name, cm.name as model_name
                        FROM Cars c
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
                        WHERE c.user_id = $userId
                        ORDER BY c.car_id
                        """
                    }

                    val carsStatement: Statement = connect.createStatement()
                    val carsResult: ResultSet = carsStatement.executeQuery(carsQuery)

                    val recommendations = mutableListOf<MainActivityNotifications.Notification>()

                    while (carsResult.next()) {
                        val carId = carsResult.getInt("car_id")
                        val currentMileage = carsResult.getInt("current_mileage")
                        val brandName = carsResult.getString("brand_name") ?: "Неизвестно"
                        val modelName = carsResult.getString("model_name") ?: "Неизвестно"
                        val carName = "$brandName $modelName"

                        val recommendationQuery = """
                            SELECT st.service_type_id, st.name as service_name, st.internal_km,
                                   COALESCE(MAX(m.mileage), 0) as last_service_mileage
                            FROM ServiceTypes st
                            LEFT JOIN Maintenance m ON st.service_type_id = m.service_type_id AND m.car_id = $carId
                            WHERE st.internal_km > 0
                            GROUP BY st.service_type_id, st.name, st.internal_km
                            HAVING COALESCE(MAX(m.mileage), 0) + st.internal_km - $currentMileage BETWEEN 501 AND 2000
                            ORDER BY COALESCE(MAX(m.mileage), 0) + st.internal_km - $currentMileage ASC
                        """

                        val recommendationStatement: Statement = connect.createStatement()
                        val recommendationResult: ResultSet = recommendationStatement.executeQuery(recommendationQuery)

                        while (recommendationResult.next()) {
                            val serviceName = recommendationResult.getString("service_name")
                            val lastServiceMileage = recommendationResult.getInt("last_service_mileage")
                            val interval = recommendationResult.getInt("internal_km")
                            val remainingKm = (lastServiceMileage + interval) - currentMileage

                            val notificationId = carId * 1000 + recommendationResult.getInt("service_type_id") + 20000

                            recommendations.add(
                                MainActivityNotifications.Notification(
                                    id = notificationId,
                                    type = MainActivityNotifications.NotificationType.MAINTENANCE_RECOMMENDATION,
                                    title = "Рекомендация: $serviceName",
                                    message = "$carName: Обслуживание '$serviceName' рекомендуется через $remainingKm км. " +
                                            "Текущий пробег: $currentMileage км.",
                                    carId = carId,
                                    carName = carName,
                                    date = Date()
                                )
                            )
                        }
                        recommendationResult.close()
                        recommendationStatement.close()
                    }

                    carsResult.close()
                    carsStatement.close()
                    connect.close()

                    recommendations
                } else {
                    emptyList()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun getInfoNotifications(context: Context): List<MainActivityNotifications.Notification> {
        return withContext(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()
                val sharedPreferences = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
                val userId = sharedPreferences.getInt("user_id", 1)
                val currentCarId = getCurrentCarId(context)

                if (connect != null) {
                    val infoNotifications = mutableListOf<MainActivityNotifications.Notification>()

                    val carsQuery = if (currentCarId > 0) {
                        """
                        SELECT c.car_id, cb.name as brand_name, cm.name as model_name, c.mileage
                        FROM Cars c
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
                        WHERE c.user_id = $userId AND c.car_id = $currentCarId
                        """
                    } else {
                        """
                        SELECT c.car_id, cb.name as brand_name, cm.name as model_name, c.mileage
                        FROM Cars c
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
                        WHERE c.user_id = $userId
                        ORDER BY c.car_id
                        """
                    }

                    val carsStatement: Statement = connect.createStatement()
                    val carsResult: ResultSet = carsStatement.executeQuery(carsQuery)

                    while (carsResult.next()) {
                        val carId = carsResult.getInt("car_id")
                        val brandName = carsResult.getString("brand_name") ?: "Неизвестно"
                        val modelName = carsResult.getString("model_name") ?: "Неизвестно"
                        val mileage = carsResult.getInt("mileage")
                        val carName = "$brandName $modelName"

                        infoNotifications.add(
                            MainActivityNotifications.Notification(
                                id = carId * 1000 + 1,
                                type = MainActivityNotifications.NotificationType.INFO,
                                title = "Регулярная проверка",
                                message = "$carName: Рекомендуется проверить давление в шинах и уровень жидкостей. " +
                                        "Текущий пробег: $mileage км.",
                                carId = carId,
                                carName = carName,
                                date = Date()
                            )
                        )

                        val calendar = Calendar.getInstance()
                        val month = calendar.get(Calendar.MONTH)
                        val seasonMessage = when {
                            month in 10..11 || month == 0 -> "Подготовка к зиме: проверьте антифриз и зимнюю резину"
                            month in 3..5 -> "Весеннее обслуживание: замена резины, проверка кондиционера"
                            month in 6..8 -> "Летнее обслуживание: проверка системы охлаждения"
                            else -> "Осеннее обслуживание: подготовка к зиме"
                        }

                        infoNotifications.add(
                            MainActivityNotifications.Notification(
                                id = carId * 1000 + 2,
                                type = MainActivityNotifications.NotificationType.INFO,
                                title = "Сезонное обслуживание",
                                message = "$carName: $seasonMessage",
                                carId = carId,
                                carName = carName,
                                date = Date()
                            )
                        )
                    }

                    carsResult.close()
                    carsStatement.close()
                    connect.close()

                    infoNotifications
                } else {
                    emptyList()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun getCarInfoById(context: Context, carId: Int): CarInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()
                val sharedPreferences = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
                val userId = sharedPreferences.getInt("user_id", 1)

                if (connect != null) {
                    val query = """
                        SELECT c.car_id, c.mileage, 
                               cb.name as brand_name, cm.name as model_name
                        FROM Cars c
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
                        WHERE c.user_id = $userId AND c.car_id = $carId
                    """

                    val statement: Statement = connect.createStatement()
                    val result: ResultSet = statement.executeQuery(query)

                    val carInfo = if (result.next()) {
                        CarInfo(
                            carId = result.getInt("car_id"),
                            mileage = result.getInt("mileage"),
                            brandName = result.getString("brand_name") ?: "Неизвестно",
                            modelName = result.getString("model_name") ?: "Неизвестно"
                        )
                    } else {
                        null
                    }

                    result.close()
                    statement.close()
                    connect.close()

                    carInfo
                } else {
                    null
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
        }
    }

    data class CarInfo(
        val carId: Int,
        val mileage: Int,
        val brandName: String,
        val modelName: String
    ) {
        val carName: String get() = "$brandName $modelName"
    }

    fun markAsRead(notificationId: Int) {
        println("Notification $notificationId marked as read")
    }
}