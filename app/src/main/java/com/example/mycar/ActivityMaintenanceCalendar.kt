package com.example.mycar

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ActivityMaintenanceCalendar : BaseActivity() {

    private lateinit var carSpinner: Spinner
    private lateinit var timelineContainer: LinearLayout
    private lateinit var textEmpty: TextView
    private lateinit var progressOverlay: FrameLayout

    private data class CarItem(val id: Int, val name: String, val mileage: Int)
    private val cars = mutableListOf<CarItem>()
    private var selectedCarId = -1
    private var selectedMileage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maintenance_calendar)

        carSpinner = findViewById(R.id.carSpinner)
        timelineContainer = findViewById(R.id.timelineContainer)
        textEmpty = findViewById(R.id.textEmpty)
        progressOverlay = findViewById(R.id.progressOverlay)

        findViewById<ImageView>(R.id.imageViewClose).setOnClickListener { finish() }

        loadCars()
    }

    private fun loadCars() {
        val userId = SessionManager(this).getUserId()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val arr = ApiClient.getCars(userId)
                cars.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val brand = obj.optString("brand", "")
                    val model = obj.optString("model", "")
                    val name = if (brand.isNotEmpty() && model.isNotEmpty()) "$brand $model" else "Авто ${i+1}"
                    val mileage = obj.optInt("mileage", 0)
                    cars.add(CarItem(obj.getInt("car_id"), name, mileage))
                }
                withContext(Dispatchers.Main) {
                    if (cars.isEmpty()) return@withContext
                    val adapter = ArrayAdapter(this@ActivityMaintenanceCalendar,
                        android.R.layout.simple_spinner_item, cars.map { it.name })
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    carSpinner.adapter = adapter

                    val intentCarId = intent.getIntExtra("car_id", -1)
                    val idx = if (intentCarId != -1) cars.indexOfFirst { it.id == intentCarId }.takeIf { it != -1 } ?: 0 else 0
                    selectedCarId = cars[idx].id
                    selectedMileage = cars[idx].mileage
                    carSpinner.setSelection(idx, false)
                    loadTimeline(selectedCarId, selectedMileage)

                    carSpinner.post {
                        carSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                                val car = cars[position]
                                if (car.id != selectedCarId) {
                                    selectedCarId = car.id
                                    selectedMileage = car.mileage
                                    loadTimeline(selectedCarId, selectedMileage)
                                }
                            }
                            override fun onNothingSelected(parent: AdapterView<*>) {}
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ActivityMaintenanceCalendar, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadTimeline(carId: Int, currentMileage: Int) {
        progressOverlay.visibility = View.VISIBLE
        timelineContainer.removeAllViews()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val arr = ApiClient.getMaintenance(carId)
                data class TimelineItem(
                    val name: String,
                    val nextMileage: Int,
                    val nextDate: String,
                    val remaining: Int,
                    val status: Int  // -1 просрочено, 0 срочно, 1 скоро, 2 норм
                )

                val items = mutableListOf<TimelineItem>()
                val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dispFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val today = Calendar.getInstance().time

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("service_type", "Обслуживание")
                    val nextMileage = if (obj.isNull("next_service_mileage")) 0 else obj.optInt("next_service_mileage", 0)
                    val nextDateStr = if (obj.isNull("next_service_date")) "" else obj.optString("next_service_date", "")

                    val nextDateDisp = if (nextDateStr.isNotEmpty()) {
                        try { dispFmt.format(isoFmt.parse(nextDateStr)!!) } catch (_: Exception) { nextDateStr }
                    } else ""

                    if (nextMileage <= 0 && nextDateStr.isEmpty()) continue

                    val remaining = if (nextMileage > 0) nextMileage - currentMileage else Int.MAX_VALUE

                    // Проверяем дату
                    val dateOverdue = if (nextDateStr.isNotEmpty()) {
                        try { isoFmt.parse(nextDateStr)!!.before(today) } catch (_: Exception) { false }
                    } else false

                    val status = when {
                        remaining < 0 || dateOverdue -> -1
                        remaining in 0..500 -> 0
                        remaining in 501..2000 -> 1
                        else -> 2
                    }

                    items.add(TimelineItem(name, nextMileage, nextDateDisp, remaining, status))
                }

                // Сортируем: сначала просроченные, потом по остатку пробега
                items.sortWith(compareBy({ it.status }, { it.remaining }))

                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    timelineContainer.removeAllViews()

                    if (items.isEmpty()) {
                        textEmpty.visibility = View.VISIBLE
                        return@withContext
                    }
                    textEmpty.visibility = View.GONE

                    items.forEach { item ->
                        addTimelineItem(item.name, item.nextMileage, item.nextDate,
                            item.remaining, item.status, currentMileage)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@ActivityMaintenanceCalendar, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addTimelineItem(
        name: String, nextMileage: Int, nextDate: String,
        remaining: Int, status: Int, currentMileage: Int
    ) {
        val accentColor = when (status) {
            -1 -> Color.parseColor("#FA5252")
            0  -> Color.parseColor("#FF9800")
            1  -> Color.parseColor("#FCC419")
            else -> Color.parseColor("#40C057")
        }

        val statusText = when (status) {
            -1 -> if (remaining < 0) "Просрочено на ${-remaining} км" else "Просрочено по дате"
            0  -> "Срочно — через $remaining км"
            1  -> "Скоро — через $remaining км"
            else -> if (nextMileage > 0) "Через $remaining км" else "По дате"
        }

        // Контейнер строки таймлайна
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 0 }
        }

        // Левая колонка: линия + точка
        val lineCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(32, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                topMargin = 16
                gravity = Gravity.CENTER_HORIZONTAL
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(accentColor)
            }
        }

        val line = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 4
            }
            setBackgroundColor(Color.parseColor("#DDDDDD"))
        }

        lineCol.addView(dot)
        lineCol.addView(line)

        // Правая колонка: карточка
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12
                bottomMargin = 16
            }
            radius = 10f
            cardElevation = 4f
            strokeColor = accentColor
            strokeWidth = 2
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }

        val titleView = TextView(this).apply {
            text = name
            textSize = 15f
            setTextColor(Color.parseColor("#1A1A1A"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val statusView = TextView(this).apply {
            text = statusText
            textSize = 13f
            setTextColor(accentColor)
        }

        val detailsText = buildString {
            if (nextMileage > 0) append("Пробег: $nextMileage км (сейчас $currentMileage км)")
            if (nextDate.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Дата: $nextDate")
            }
        }

        if (detailsText.isNotEmpty()) {
            val detailView = TextView(this).apply {
                text = detailsText
                textSize = 12f
                setTextColor(Color.parseColor("#666666"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4 }
            }
            cardContent.addView(titleView)
            cardContent.addView(statusView)
            cardContent.addView(detailView)
        } else {
            cardContent.addView(titleView)
            cardContent.addView(statusView)
        }

        card.addView(cardContent)
        row.addView(lineCol)
        row.addView(card)
        timelineContainer.addView(row)
    }
}
