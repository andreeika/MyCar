package com.example.mycar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.sql.Statement
import java.util.*

class NotificationManager {

    suspend fun getUrgentNotifications(context: Context): List<MainActivityNotifications.Notification> {
        return withContext(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()
                val sharedPreferences = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
                val userId = sharedPreferences.getInt("user_id", 1)

                if (connect != null) {
                    val mileageQuery = """
                        SELECT c.car_id, c.mileage as current_mileage, 
                               cb.name as brand_name, cm.name as model_name
                        FROM Cars c
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
                        WHERE c.user_id = $userId
                    """

                    val mileageStatement: Statement = connect.createStatement()
                    val mileageResult: ResultSet = mileageStatement.executeQuery(mileageQuery)

                    val notifications = mutableListOf<MainActivityNotifications.Notification>()

                    while (mileageResult.next()) {
                        val carId = mileageResult.getInt("car_id")
                        val currentMileage = mileageResult.getInt("current_mileage")
                        val carName = "${mileageResult.getString("brand_name")} ${mileageResult.getString("model_name")}"

                        val overdueQuery = """
                            SELECT st.service_type_id, st.name as service_name, st.internal_km,
                                   MAX(m.mileage) as last_service_mileage
                            FROM ServiceTypes st
                            LEFT JOIN Maintenance m ON st.service_type_id = m.service_type_id AND m.car_id = $carId
                            WHERE st.internal_km > 0
                            GROUP BY st.service_type_id, st.name, st.internal_km
                            HAVING MAX(m.mileage) + st.internal_km < $currentMileage
                        """

                        val overdueStatement: Statement = connect.createStatement()
                        val overdueResult: ResultSet = overdueStatement.executeQuery(overdueQuery)

                        while (overdueResult.next()) {
                            val serviceName = overdueResult.getString("service_name")
                            val lastServiceMileage = overdueResult.getInt("last_service_mileage")
                            val interval = overdueResult.getInt("internal_km")
                            val overdueBy = currentMileage - (lastServiceMileage + interval)

                            notifications.add(
                                MainActivityNotifications.Notification(
                                    id = overdueResult.getInt("service_type_id"),
                                    type = MainActivityNotifications.NotificationType.URGENT,
                                    title = "ПРОСРОЧЕНО: $serviceName",
                                    message = "Обслуживание '$serviceName' просрочено на $overdueBy км. Последнее обслуживание было на пробеге $lastServiceMileage км.",
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
                                   MAX(m.mileage) as last_service_mileage
                            FROM ServiceTypes st
                            LEFT JOIN Maintenance m ON st.service_type_id = m.service_type_id AND m.car_id = $carId
                            WHERE st.internal_km > 0
                            GROUP BY st.service_type_id, st.name, st.internal_km
                            HAVING MAX(m.mileage) + st.internal_km - $currentMileage BETWEEN 1 AND 500
                        """

                        val urgentStatement: Statement = connect.createStatement()
                        val urgentResult: ResultSet = urgentStatement.executeQuery(urgentQuery)

                        while (urgentResult.next()) {
                            val serviceName = urgentResult.getString("service_name")
                            val lastServiceMileage = urgentResult.getInt("last_service_mileage")
                            val interval = urgentResult.getInt("internal_km")
                            val remainingKm = (lastServiceMileage + interval) - currentMileage

                            notifications.add(
                                MainActivityNotifications.Notification(
                                    id = urgentResult.getInt("service_type_id"),
                                    type = MainActivityNotifications.NotificationType.URGENT,
                                    title = "СРОЧНО: $serviceName",
                                    message = "Обслуживание '$serviceName' требуется через $remainingKm км.",
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

                    mileageResult.close()
                    mileageStatement.close()
                    connect.close()

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

                if (connect != null) {
                    val mileageQuery = """
                        SELECT c.car_id, c.mileage as current_mileage, 
                               cb.name as brand_name, cm.name as model_name
                        FROM Cars c
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
                        WHERE c.user_id = $userId
                    """

                    val mileageStatement: Statement = connect.createStatement()
                    val mileageResult: ResultSet = mileageStatement.executeQuery(mileageQuery)

                    val recommendations = mutableListOf<MainActivityNotifications.Notification>()

                    while (mileageResult.next()) {
                        val carId = mileageResult.getInt("car_id")
                        val currentMileage = mileageResult.getInt("current_mileage")
                        val carName = "${mileageResult.getString("brand_name")} ${mileageResult.getString("model_name")}"

                        val recommendationQuery = """
                            SELECT st.service_type_id, st.name as service_name, st.internal_km,
                                   MAX(m.mileage) as last_service_mileage
                            FROM ServiceTypes st
                            LEFT JOIN Maintenance m ON st.service_type_id = m.service_type_id AND m.car_id = $carId
                            WHERE st.internal_km > 0
                            GROUP BY st.service_type_id, st.name, st.internal_km
                            HAVING MAX(m.mileage) + st.internal_km - $currentMileage BETWEEN 501 AND 2000
                        """

                        val recommendationStatement: Statement = connect.createStatement()
                        val recommendationResult: ResultSet = recommendationStatement.executeQuery(recommendationQuery)

                        while (recommendationResult.next()) {
                            val serviceName = recommendationResult.getString("service_name")
                            val lastServiceMileage = recommendationResult.getInt("last_service_mileage")
                            val interval = recommendationResult.getInt("internal_km")
                            val remainingKm = (lastServiceMileage + interval) - currentMileage

                            recommendations.add(
                                MainActivityNotifications.Notification(
                                    id = recommendationResult.getInt("service_type_id"),
                                    type = MainActivityNotifications.NotificationType.MAINTENANCE_RECOMMENDATION,
                                    title = "Рекомендация: $serviceName",
                                    message = "Обслуживание '$serviceName' рекомендуется через $remainingKm км.",
                                    carId = carId,
                                    carName = carName,
                                    date = Date()
                                )
                            )
                        }
                        recommendationResult.close()
                        recommendationStatement.close()
                    }

                    mileageResult.close()
                    mileageStatement.close()
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

                if (connect != null) {
                    val infoNotifications = mutableListOf<MainActivityNotifications.Notification>()

                    val carsQuery = """
                        SELECT c.car_id, cb.name as brand_name, cm.name as model_name
                        FROM Cars c
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id
                        WHERE c.user_id = $userId
                    """

                    val carsStatement: Statement = connect.createStatement()
                    val carsResult: ResultSet = carsStatement.executeQuery(carsQuery)

                    while (carsResult.next()) {
                        val carId = carsResult.getInt("car_id")
                        val carName = "${carsResult.getString("brand_name")} ${carsResult.getString("model_name")}"

                        infoNotifications.add(
                            MainActivityNotifications.Notification(
                                id = carId + 1000,
                                type = MainActivityNotifications.NotificationType.INFO,
                                title = "Регулярная проверка",
                                message = "Рекомендуется проверить давление в шинах и уровень жидкостей.",
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

    fun markAsRead(notificationId: Int) {
        println("Notification $notificationId marked as read")
    }
}