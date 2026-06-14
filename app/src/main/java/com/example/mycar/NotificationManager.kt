package com.example.mycar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class NotificationManager {

    companion object {
        private const val PREF_CURRENT_CAR_ID = "current_car_id"
        private val MILESTONE_KM = listOf(10000, 25000, 50000, 75000, 100000, 150000, 200000, 250000, 300000)
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
                val prefs = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)

                val carsArr = ApiClient.getCars(userId)
                val urgent = mutableListOf<MainActivityNotifications.Notification>()
                val recommendations = mutableListOf<MainActivityNotifications.Notification>()
                val info = mutableListOf<MainActivityNotifications.Notification>()

                // Приветствие при первом входе
                val isFirstLogin = prefs.getBoolean("first_login_notif_shown", false).not()
                if (isFirstLogin) {
                    info.add(MainActivityNotifications.Notification(
                        id = 999001,
                        type = MainActivityNotifications.NotificationType.INFO,
                        title = "Добро пожаловать в MyCar!",
                        message = "Добавьте свой автомобиль и начните отслеживать расходы, заправки и обслуживание.",
                        carId = -1, carName = "MyCar", date = Date()
                    ))
                    prefs.edit().putBoolean("first_login_notif_shown", true).apply()
                }

                for (i in 0 until carsArr.length()) {
                    val carObj = carsArr.getJSONObject(i)
                    val carId = carObj.getInt("car_id")
                    if (currentCarId > 0 && carId != currentCarId) continue

                    val currentMileage = carObj.optInt("mileage", 0)
                    val brand = carObj.optString("brand", "Неизвестно")
                    val model = carObj.optString("model", "Неизвестно")
                    val carName = "$brand $model"

                    val maintenanceArr = ApiClient.getMaintenance(carId)
                    val refuelingArr = ApiClient.getRefueling(carId)

                    // ── СРОЧНЫЕ и РЕКОМЕНДАЦИИ по ТО ──────────────────────────────
                    val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                    // Летние месяцы: май-август (4..7), зимние: ноябрь-февраль (10..11, 0..1)
                    val isSummer = currentMonth in 4..7
                    val isWinter = currentMonth in 10..11 || currentMonth in 0..1

                    // Ключевые слова для шин — показываем только в нужный сезон
                    val winterTireKeywords = listOf("зимн", "шин", "резин", "колес")
                    val summerTireKeywords = listOf("летн", "шин", "резин", "колес")

                    fun isTireService(name: String): Boolean {
                        val n = name.lowercase()
                        return winterTireKeywords.any { n.contains(it) } || summerTireKeywords.any { n.contains(it) }
                    }

                    fun isWinterTire(name: String) = name.lowercase().contains("зимн")
                    fun isSummerTire(name: String) = name.lowercase().contains("летн")

                    fun shouldShowTireNotification(serviceName: String): Boolean {
                        if (!isTireService(serviceName)) return true // не шины — показывать всегда
                        return when {
                            isWinterTire(serviceName) -> !isSummer  // зимние шины — не летом
                            isSummerTire(serviceName) -> !isWinter  // летние шины — не зимой
                            else -> true // просто "шины/резина" без сезона — показывать
                        }
                    }

                    for (j in 0 until maintenanceArr.length()) {
                        val m = maintenanceArr.getJSONObject(j)
                        val maintenanceId = m.getInt("maintenance_id")
                        val serviceTypeId = m.optInt("service_type_id", 0)
                        val serviceName = m.optString("service_type", "Обслуживание")
                        val lastMileage = m.optInt("mileage", 0)
                        val nextMileage = if (m.isNull("next_service_mileage")) 0 else m.optInt("next_service_mileage", 0)

                        if (nextMileage <= 0) continue
                        if (!shouldShowTireNotification(serviceName)) continue

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

                    // ── КРУГЛЫЙ ПРОБЕГ ─────────────────────────────────────────────
                    val milestone = MILESTONE_KM.lastOrNull { it <= currentMileage }
                    if (milestone != null) {
                        val milestoneKey = "milestone_shown_${carId}_$milestone"
                        if (!prefs.getBoolean(milestoneKey, false)) {
                            info.add(MainActivityNotifications.Notification(
                                id = carId * 10000 + milestone / 1000,
                                type = MainActivityNotifications.NotificationType.INFO,
                                title = "🏆 Круглый пробег: ${milestone / 1000} тыс. км",
                                message = "$carName достиг отметки $milestone км! Рекомендуем провести плановую проверку автомобиля.",
                                carId = carId, carName = carName, date = Date()
                            ))
                            prefs.edit().putBoolean(milestoneKey, true).apply()
                        }
                    }

                    // ── ДАВНО НЕ ЗАПРАВЛЯЛИСЬ (>30 дней) ──────────────────────────
                    if (refuelingArr.length() > 0) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        val sdfAlt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        var lastRefuelDate: Date? = null
                        for (r in 0 until refuelingArr.length()) {
                            val dateStr = refuelingArr.getJSONObject(r).optString("date", "")
                            val parsed = runCatching { sdf.parse(dateStr) }.getOrNull()
                                ?: runCatching { sdfAlt.parse(dateStr) }.getOrNull()
                            if (parsed != null && (lastRefuelDate == null || parsed.after(lastRefuelDate))) {
                                lastRefuelDate = parsed
                            }
                        }
                        lastRefuelDate?.let { last ->
                            val daysSince = ((Date().time - last.time) / (1000 * 60 * 60 * 24)).toInt()
                            if (daysSince >= 30) {
                                info.add(MainActivityNotifications.Notification(
                                    id = carId * 10000 + 101,
                                    type = MainActivityNotifications.NotificationType.INFO,
                                    title = "Давно не заправлялись",
                                    message = "$carName: Последняя заправка была $daysSince дней назад. Не забудьте добавить запись о заправке.",
                                    carId = carId, carName = carName, date = Date()
                                ))
                            }
                        }
                    }

                    // ── РОСТ РАСХОДА ТОПЛИВА ───────────────────────────────────────
                    if (refuelingArr.length() >= 4) {
                        val volumes = mutableListOf<Double>()
                        val mileages = mutableListOf<Int>()
                        for (r in 0 until refuelingArr.length()) {
                            val obj = refuelingArr.getJSONObject(r)
                            if (obj.optBoolean("full_tank", false)) {
                                volumes.add(obj.optDouble("volume", 0.0))
                                mileages.add(obj.optInt("mileage", 0))
                            }
                        }
                        if (volumes.size >= 4) {
                            val half = volumes.size / 2
                            val recentVol = volumes.take(half).sum()
                            val recentDist = (mileages.first() - mileages[half - 1]).toDouble()
                            val oldVol = volumes.drop(half).sum()
                            val oldDist = (mileages[half] - mileages.last()).toDouble()
                            if (recentDist > 0 && oldDist > 0) {
                                val recentConsumption = recentVol / recentDist * 100
                                val oldConsumption = oldVol / oldDist * 100
                                if (oldConsumption > 0 && recentConsumption > oldConsumption * 1.1) {
                                    val increase = ((recentConsumption - oldConsumption) / oldConsumption * 100).toInt()
                                    recommendations.add(MainActivityNotifications.Notification(
                                        id = carId * 10000 + 201,
                                        type = MainActivityNotifications.NotificationType.MAINTENANCE_RECOMMENDATION,
                                        title = "Расход топлива вырос на $increase%",
                                        message = "$carName: Средний расход увеличился с ${"%.1f".format(oldConsumption)} до ${"%.1f".format(recentConsumption)} л/100км. Рекомендуем проверить свечи, воздушный фильтр и давление в шинах.",
                                        carId = carId, carName = carName, date = Date()
                                    ))
                                }
                            }
                        }
                    }

                    // ── ИТОГИ МЕСЯЦА ───────────────────────────────────────────────
                    val cal = Calendar.getInstance()
                    val thisMonth = cal.get(Calendar.MONTH)
                    val thisYear = cal.get(Calendar.YEAR)
                    val lastMonthKey = "monthly_summary_${carId}_${thisYear}_$thisMonth"
                    if (!prefs.getBoolean(lastMonthKey, false)) {
                        var monthFuelCost = 0.0
                        var monthFuelVol = 0.0
                        var monthRefuelCount = 0
                        val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                        val currentMonthStr = sdfMonth.format(cal.time)
                        for (r in 0 until refuelingArr.length()) {
                            val obj = refuelingArr.getJSONObject(r)
                            val dateStr = obj.optString("date", "")
                            if (dateStr.startsWith(currentMonthStr)) {
                                monthFuelCost += obj.optDouble("total_amount", 0.0)
                                monthFuelVol += obj.optDouble("volume", 0.0)
                                monthRefuelCount++
                            }
                        }
                        if (monthRefuelCount > 0) {
                            info.add(MainActivityNotifications.Notification(
                                id = carId * 10000 + 301,
                                type = MainActivityNotifications.NotificationType.INFO,
                                title = "Статистика за месяц",
                                message = "$carName: В этом месяце заправок: $monthRefuelCount, топлива: ${"%.1f".format(monthFuelVol)} л, расходы на топливо: ${"%.0f".format(monthFuelCost)} руб.",
                                carId = carId, carName = carName, date = Date()
                            ))
                        }
                    }

                    // ── СЕЗОННОЕ ОБСЛУЖИВАНИЕ ──────────────────────────────────────
                    // Показываем только в первые 2 недели сезона и только один раз за сезон
                    val month = cal.get(Calendar.MONTH) // 0=Янв, 11=Дек
                    val day = cal.get(Calendar.DAY_OF_MONTH)

                    // Определяем текущий сезон и окно показа (первые 14 дней)
                    data class SeasonInfo(val key: String, val title: String, val msg: String, val active: Boolean)

                    val season = when {
                        // Подготовка к зиме: октябрь 1-14
                        month == 9 && day <= 14 -> SeasonInfo(
                            key = "season_winter_${thisYear}",
                            title = "Подготовка к зиме",
                            msg = "$carName: Проверьте антифриз, зимнюю резину, аккумулятор и систему обогрева.",
                            active = true
                        )
                        // Весеннее: апрель 1-14
                        month == 3 && day <= 14 -> SeasonInfo(
                            key = "season_spring_${thisYear}",
                            title = "Весеннее обслуживание",
                            msg = "$carName: Замените зимнюю резину на летнюю, проверьте тормоза, кондиционер и кузов после зимы.",
                            active = true
                        )
                        // Летнее: июнь 1-14
                        month == 5 && day <= 14 -> SeasonInfo(
                            key = "season_summer_${thisYear}",
                            title = "Летнее обслуживание",
                            msg = "$carName: Проверьте систему охлаждения, кондиционер и давление в шинах.",
                            active = true
                        )
                        else -> SeasonInfo("", "", "", active = false)
                    }

                    if (season.active && !prefs.getBoolean("${season.key}_${carId}", false)) {
                        info.add(MainActivityNotifications.Notification(
                            id = carId * 10000 + 401,
                            type = MainActivityNotifications.NotificationType.INFO,
                            title = season.title,
                            message = season.msg,
                            carId = carId, carName = carName, date = Date()
                        ))
                        prefs.edit().putBoolean("${season.key}_${carId}", true).apply()
                    }

                    // ── АВТОМОБИЛЬ НЕ ИСПОЛЬЗОВАЛСЯ >14 ДНЕЙ ──────────────────────
                    if (refuelingArr.length() > 0 && maintenanceArr.length() > 0) {
                        val sdf2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        val sdf2Alt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        var lastActivity: Date? = null
                        for (r in 0 until refuelingArr.length()) {
                            val d = refuelingArr.getJSONObject(r).optString("date", "")
                            val p = runCatching { sdf2.parse(d) }.getOrNull() ?: runCatching { sdf2Alt.parse(d) }.getOrNull()
                            if (p != null && (lastActivity == null || p.after(lastActivity))) lastActivity = p
                        }
                        lastActivity?.let { last ->
                            val days = ((Date().time - last.time) / (1000 * 60 * 60 * 24)).toInt()
                            if (days in 14..60) {
                                info.add(MainActivityNotifications.Notification(
                                    id = carId * 10000 + 501,
                                    type = MainActivityNotifications.NotificationType.INFO,
                                    title = "Автомобиль не используется $days дней",
                                    message = "$carName: Рекомендуем проверить давление в шинах и уровень заряда аккумулятора.",
                                    carId = carId, carName = carName, date = Date()
                                ))
                            }
                        }
                    }

                    // ── ВСЁ В ПОРЯДКЕ ──────────────────────────────────────────────
                    if (urgent.none { it.carId == carId }) {
                        info.add(MainActivityNotifications.Notification(
                            id = carId * 10000 + 999,
                            type = MainActivityNotifications.NotificationType.INFO,
                            title = "Всё в порядке",
                            message = "$carName: Все обслуживания выполнены своевременно. Текущий пробег: $currentMileage км.",
                            carId = carId, carName = carName, date = Date()
                        ))
                    }

                    // ── ВЫСОКАЯ СТОИМОСТЬ ОДНОЙ ЗАПРАВКИ ──────────────────────────
                    if (refuelingArr.length() >= 3) {
                        var totalCost = 0.0
                        val count = minOf(refuelingArr.length(), 10)
                        for (r in 0 until count) {
                            totalCost += refuelingArr.getJSONObject(r).optDouble("total_amount", 0.0)
                        }
                        val avgCost = totalCost / count
                        val lastCost = refuelingArr.getJSONObject(0).optDouble("total_amount", 0.0)
                        if (lastCost > avgCost * 1.3 && avgCost > 0) {
                            recommendations.add(MainActivityNotifications.Notification(
                                id = carId * 10000 + 601,
                                type = MainActivityNotifications.NotificationType.MAINTENANCE_RECOMMENDATION,
                                title = "Дорогая заправка",
                                message = "$carName: Последняя заправка обошлась в ${"%.0f".format(lastCost)} руб., " +
                                        "что на ${((lastCost / avgCost - 1) * 100).toInt()}% выше среднего (${"%.0f".format(avgCost)} руб.). " +
                                        "Попробуйте другую АЗС.",
                                carId = carId, carName = carName, date = Date()
                            ))
                        }
                    }

                    // ── ВЫСОКИЙ ПРОБЕГ — РЕКОМЕНДАЦИЯ ОБЩЕЙ ДИАГНОСТИКИ ───────────
                    val diagMileages = listOf(50000, 100000, 150000, 200000, 250000)
                    for (diagMileage in diagMileages) {
                        val diagKey = "diag_notif_${carId}_$diagMileage"
                        if (currentMileage >= diagMileage && !prefs.getBoolean(diagKey, false)) {
                            recommendations.add(MainActivityNotifications.Notification(
                                id = carId * 10000 + 700 + diagMileage / 1000,
                                type = MainActivityNotifications.NotificationType.MAINTENANCE_RECOMMENDATION,
                                title = "Рекомендуется диагностика",
                                message = "$carName достиг ${diagMileage / 1000} тыс. км. " +
                                        "Рекомендуем пройти комплексную диагностику: тормоза, подвеска, рулевое управление.",
                                carId = carId, carName = carName, date = Date()
                            ))
                            prefs.edit().putBoolean(diagKey, true).apply()
                            break
                        }
                    }

                    // ── ДАВНО НЕТ ЗАПИСЕЙ ТО (>6 МЕСЯЦЕВ) ────────────────────────
                    if (maintenanceArr.length() > 0) {
                        val sdfM = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        val sdfMAlt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        var lastMaintenanceDate: Date? = null
                        for (j in 0 until maintenanceArr.length()) {
                            val dateStr = maintenanceArr.getJSONObject(j).optString("date", "")
                            val parsed = runCatching { sdfM.parse(dateStr) }.getOrNull()
                                ?: runCatching { sdfMAlt.parse(dateStr) }.getOrNull()
                            if (parsed != null && (lastMaintenanceDate == null || parsed.after(lastMaintenanceDate))) {
                                lastMaintenanceDate = parsed
                            }
                        }
                        lastMaintenanceDate?.let { last ->
                            val daysSince = ((Date().time - last.time) / (1000 * 60 * 60 * 24)).toInt()
                            if (daysSince > 180) {
                                val noMaintKey = "no_maint_notif_${carId}_${thisYear}"
                                if (!prefs.getBoolean(noMaintKey, false)) {
                                    recommendations.add(MainActivityNotifications.Notification(
                                        id = carId * 10000 + 801,
                                        type = MainActivityNotifications.NotificationType.MAINTENANCE_RECOMMENDATION,
                                        title = "Давно не было обслуживания",
                                        message = "$carName: Последняя запись об обслуживании была более ${daysSince / 30} месяцев назад. " +
                                                "Рекомендуем провести плановый осмотр.",
                                        carId = carId, carName = carName, date = Date()
                                    ))
                                    prefs.edit().putBoolean(noMaintKey, true).apply()
                                }
                            }
                        }
                    }

                    // ── СТРАХОВКА / ТО (НАПОМИНАНИЕ ПО ДАТЕ СЛЕДУЮЩЕГО ТО) ────────
                    for (j in 0 until maintenanceArr.length()) {
                        val m = maintenanceArr.getJSONObject(j)
                        val nextDateStr = if (m.isNull("next_service_date")) "" else m.optString("next_service_date", "")
                        if (nextDateStr.isEmpty()) continue
                        val serviceName = m.optString("service_type", "Обслуживание")
                        val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                        val sdfDateAlt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val nextDate = runCatching { sdfDate.parse(nextDateStr) }.getOrNull()
                            ?: runCatching { sdfDateAlt.parse(nextDateStr) }.getOrNull()
                            ?: continue
                        val daysUntil = ((nextDate.time - Date().time) / (1000 * 60 * 60 * 24)).toInt()
                        when {
                            daysUntil < 0 -> urgent.add(MainActivityNotifications.Notification(
                                id = carId * 10000 + 900 + j,
                                type = MainActivityNotifications.NotificationType.URGENT,
                                title = "Просрочено по дате: $serviceName",
                                message = "$carName: Плановая дата обслуживания '$serviceName' истекла ${-daysUntil} дней назад.",
                                carId = carId, carName = carName, date = Date(), actionRequired = true
                            ))
                            daysUntil in 0..7 -> urgent.add(MainActivityNotifications.Notification(
                                id = carId * 10000 + 910 + j,
                                type = MainActivityNotifications.NotificationType.URGENT,
                                title = "Через $daysUntil дней: $serviceName",
                                message = "$carName: Плановое обслуживание '$serviceName' запланировано через $daysUntil дней.",
                                carId = carId, carName = carName, date = Date(), actionRequired = true
                            ))
                            daysUntil in 8..30 -> recommendations.add(MainActivityNotifications.Notification(
                                id = carId * 10000 + 920 + j,
                                type = MainActivityNotifications.NotificationType.MAINTENANCE_RECOMMENDATION,
                                title = "Через $daysUntil дней: $serviceName",
                                message = "$carName: Плановое обслуживание '$serviceName' запланировано через $daysUntil дней.",
                                carId = carId, carName = carName, date = Date()
                            ))
                        }
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
}