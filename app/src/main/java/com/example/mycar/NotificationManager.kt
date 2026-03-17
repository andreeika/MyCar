package com.example.mycar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class NotificationManager {

    companion object {
        private const val PREF_CURRENT_CAR_ID = "current_car_id"
    }

    fun getCurrentCarId(context: Context): Int {
        val prefs = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
        return prefs.getInt(PREF_CURRENT_CAR_ID, -1)
    }

    fun setCurrentCarId(context: Context, carId: Int) {
        val prefs = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt(PREF_CURRENT_CAR_ID, carId).apply()
    }

    data class AllNotifications(
        val urgent: List<MainActivityNotifications.Notification>,
        val recommendations: List<MainActivityNotifications.Notification>,
        val info: List<MainActivityNotifications.Notification>
    )

    suspend fun getAllNotifications(context: Context): AllNotifications {
        return withContext(Dispatchers.IO) {
            try {
                val sessionManager = SessionManager(context)
                val userId = sessionManager.getUserId()
                val currentCarId = getCurrentCarId(context)

                val carsArr = ApiClient.getCars(userId)
                val urgent = mutableListOf<MainActivityNotifications.Notification>()
                val recommendations = mutableListOf<MainActivityNotifications.Notification>()
                val info = mutableListOf<MainActivityNotifications.Notification>()

                for (i in 0 until carsArr.length()) {
                    val carObj = carsArr.getJSONObject(i)
                    val carId = carObj.getInt("car_id")
                    if (currentCarId > 0 && carId != currentCarId) continue

                    val currentMileage = carObj.optInt("mileage", 0)
                    val brand = carObj.optString("brand", "Неизвестно")
                    val model = carObj.optString("model", "Неизвестно")
                    val carName = "$brand $model"

                    // Один запрос на обслуживание для всех типов уведомлений
                    val maintenanceArr = ApiClient.getMaintenance(carId)

                    for (j in 0 until maintenanceArr.length()) {
                        val m = maintenanceArr.getJSONObject(j)
                        val maintenanceId = m.getInt("maintenance_id")
                        val serviceTypeId = m.optInt("service_type_id", 0)
                        val serviceName = m.optString("service_type", "Обслуживание")
                        val lastMileage = m.optInt("mileage", 0)
                        val nextMileage = if (m.isNull("next_service_mileage")) 0 else m.optInt("next_service_mileage", 0)

                        if (nextMileage <= 0) continue
                        val remaining = nextMileage - currentMileage

                        when {
                            remaining < 0 -> urgent.add(
                                MainActivityNotifications.Notification(
                                    id = maintenanceId * 1000 + serviceTypeId,
                                    type = MainActivityNotifications.NotificationType.URGENT,
                                    title = "ПРОСРОЧЕНО: $serviceName",
                                    message = "$carName: Обслуживание '$serviceName' просрочено на ${-remaining} км. " +
                                            "Последнее на $lastMileage км. Следующее было на $nextMileage км. Текущий: $currentMileage км.",
                                    carId = carId, carName = carName, date = Date(), actionRequired = true
                                )
                            )
                            remaining in 0..500 -> urgent.add(
                                MainActivityNotifications.Notification(
                                    id = maintenanceId * 1000 + serviceTypeId + 10000,
                                    type = MainActivityNotifications.NotificationType.URGENT,
                                    title = "СРОЧНО: $serviceName",
                                    message = "$carName: Обслуживание '$serviceName' требуется через $remaining км. " +
                                            "Последнее на $lastMileage км. Следующее на $nextMileage км. Текущий: $currentMileage км.",
                                    carId = carId, carName = carName, date = Date(), actionRequired = true
                                )
                            )
                            remaining in 501..2000 -> recommendations.add(
                                MainActivityNotifications.Notification(
                                    id = carId * 1000 + serviceTypeId + 20000,
                                    type = MainActivityNotifications.NotificationType.MAINTENANCE_RECOMMENDATION,
                                    title = "Рекомендация: $serviceName",
                                    message = "$carName: Обслуживание '$serviceName' рекомендуется через $remaining км. Текущий пробег: $currentMileage км.",
                                    carId = carId, carName = carName, date = Date()
                                )
                            )
                        }
                    }

                    // Информационные уведомления на основе реальных данных
                    info.add(
                        MainActivityNotifications.Notification(
                            id = carId * 1000 + 1,
                            type = MainActivityNotifications.NotificationType.INFO,
                            title = "Регулярная проверка",
                            message = "$carName: Рекомендуется проверить давление в шинах и уровень жидкостей. Текущий пробег: $currentMileage км.",
                            carId = carId, carName = carName, date = Date()
                        )
                    )

                    val month = Calendar.getInstance().get(Calendar.MONTH)
                    val seasonMessage = when {
                        month in 10..11 || month == 0 -> "Подготовка к зиме: проверьте антифриз и зимнюю резину"
                        month in 3..5 -> "Весеннее обслуживание: замена резины, проверка кондиционера"
                        month in 6..8 -> "Летнее обслуживание: проверка системы охлаждения"
                        else -> "Осеннее обслуживание: подготовка к зиме"
                    }
                    info.add(
                        MainActivityNotifications.Notification(
                            id = carId * 1000 + 2,
                            type = MainActivityNotifications.NotificationType.INFO,
                            title = "Сезонное обслуживание",
                            message = "$carName: $seasonMessage",
                            carId = carId, carName = carName, date = Date()
                        )
                    )

                    if (urgent.none { it.carId == carId }) {
                        info.add(
                            MainActivityNotifications.Notification(
                                id = carId * 1000 + 999,
                                type = MainActivityNotifications.NotificationType.INFO,
                                title = "Все в порядке",
                                message = "$carName: Все обслуживания выполнены своевременно. Текущий пробег: $currentMileage км.",
                                carId = carId, carName = carName, date = Date()
                            )
                        )
                    }
                }

                AllNotifications(urgent, recommendations, info)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AllNotifications(emptyList(), emptyList(), emptyList())
            }
        }
    }

    fun markAsRead(context: Context, notificationId: Int) {
        context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("notification_$notificationId", true).apply()
    }

    data class CarInfo(
        val carId: Int,
        val mileage: Int,
        val brandName: String,
        val modelName: String
    ) {
        val carName: String get() = "$brandName $modelName"
    }
}
