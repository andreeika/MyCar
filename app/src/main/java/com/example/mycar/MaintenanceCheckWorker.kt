package com.example.mycar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MaintenanceCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "maintenance_channel"
        const val CHANNEL_NAME = "Обслуживание автомобиля"
        const val WORK_NAME = "maintenance_check"
        private const val PREF_NOTIFICATIONS = "notifications_enabled"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Напоминания о техническом обслуживании"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    enableLights(true)
                    lightColor = android.graphics.Color.parseColor("#228BE6")
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }
    }

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_NOTIFICATIONS, true)) return Result.success()

        return withContext(Dispatchers.IO) {
            try {
                val sessionManager = SessionManager(context)
                if (!sessionManager.isLoggedIn()) return@withContext Result.success()

                val userId = sessionManager.getUserId()
                val carsArr = ApiClient.getCars(userId)

                for (i in 0 until carsArr.length()) {
                    val carObj = carsArr.getJSONObject(i)
                    val carId = carObj.getInt("car_id")
                    val currentMileage = carObj.optInt("mileage", 0)
                    val brand = carObj.optString("brand", "")
                    val model = carObj.optString("model", "")
                    val carName = "$brand $model".trim()

                    val maintenanceArr = ApiClient.getMaintenance(carId)
                    for (j in 0 until maintenanceArr.length()) {
                        val m = maintenanceArr.getJSONObject(j)
                        val serviceName = m.optString("service_type", "Обслуживание")
                        val nextMileage = if (m.isNull("next_service_mileage")) 0 else m.optInt("next_service_mileage", 0)
                        if (nextMileage <= 0) continue

                        val remaining = nextMileage - currentMileage
                        val notifId = carId * 10000 + m.getInt("maintenance_id")

                        when {
                            remaining < 0 -> sendPush(
                                id = notifId,
                                title = "⚠️ Просрочено: $serviceName",
                                text = "$carName — просрочено на ${-remaining} км (пробег $currentMileage км)"
                            )
                            remaining in 0..500 -> sendPush(
                                id = notifId + 1,
                                title = "🔧 Срочно: $serviceName",
                                text = "$carName — через $remaining км (пробег $currentMileage км)"
                            )
                            remaining in 501..2000 -> sendPush(
                                id = notifId + 2,
                                title = "ℹ️ Рекомендация: $serviceName",
                                text = "$carName — через $remaining км (пробег $currentMileage км)"
                            )
                        }
                    }
                }
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }

    private fun sendPush(id: Int, title: String, text: String) {
        createNotificationChannel(context)
        val intent = Intent(context, MainActivityNotifications::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.logo)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }
}
