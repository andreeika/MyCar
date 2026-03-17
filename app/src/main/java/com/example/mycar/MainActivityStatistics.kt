package com.example.mycar

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.app.DatePickerDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivityStatistics : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var carSpinner: Spinner
    private lateinit var periodTypeSpinner: Spinner
    private lateinit var customPeriodLayout: LinearLayout
    private lateinit var editTextDateFrom: TextView
    private lateinit var editTextDateTo: TextView
    private lateinit var buttonApply: Button
    private lateinit var closeImageView: ImageView

    private lateinit var textViewTotalExpenses: TextView
    private lateinit var textViewFuelExpenses: TextView
    private lateinit var textViewMaintenanceExpenses: TextView
    private lateinit var textViewCostPerKm: TextView
    private lateinit var textViewMileage: TextView
    private lateinit var textViewFuelConsumption: TextView
    private lateinit var pieChart: PieChart
    private lateinit var lineChart: LineChart
    private lateinit var barChart: BarChart
    private lateinit var progressOverlay: android.widget.FrameLayout
    private lateinit var scrollViewContent: android.widget.ScrollView

    private val userCars = mutableListOf<Car>()
    private var selectedCarId: Int = -1

    data class Car(
        val id: Int,
        val brand: String,
        val model: String,
        val displayName: String
    )

    data class StatItem(
        val category: String,
        val amount: Double,
        val percentage: Double
    )

    data class MonthlyStat(
        val month: String,
        val totalAmount: Double,
        val fuelAmount: Double,
        val maintenanceAmount: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_statistics)

        sharedPreferences = getSharedPreferences("my_car_prefs", MODE_PRIVATE)

        try {
            initializeViews()
            setupClickListeners()
            setupCharts()
            loadUserCars()
        } catch (e: Exception) {
            Log.e("Statistics", "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Ошибка инициализации: ${e.message}", Toast.LENGTH_LONG).show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }

    private fun initializeViews() {
        try {
            carSpinner = findViewById(R.id.carSpinner)
            periodTypeSpinner = findViewById(R.id.periodTypeSpinner)
            customPeriodLayout = findViewById(R.id.customPeriodLayout)
            editTextDateFrom = findViewById(R.id.editTextDateFrom)
            editTextDateTo = findViewById(R.id.editTextDateTo)
            buttonApply = findViewById(R.id.buttonApply)
            closeImageView = findViewById(R.id.imageViewClose)
            textViewTotalExpenses = findViewById(R.id.textViewTotalExpenses)
            textViewFuelExpenses = findViewById(R.id.textViewFuelExpenses)
            textViewMaintenanceExpenses = findViewById(R.id.textViewMaintenanceExpenses)
            textViewCostPerKm = findViewById(R.id.textViewCostPerKm)
            textViewMileage = findViewById(R.id.textViewMileageValue)
            textViewFuelConsumption = findViewById(R.id.textViewFuelConsumptionValue)
            pieChart = findViewById(R.id.pieChart)
            lineChart = findViewById(R.id.lineChart)
            barChart = findViewById(R.id.barChart)
            progressOverlay = findViewById(R.id.progressOverlay)
            scrollViewContent = findViewById(R.id.scrollViewContent)

            setDefaultDates()

        } catch (e: Exception) {
            Log.e("Statistics", "Error initializing views: ${e.message}", e)
            throw e
        }
    }

    private fun setDefaultDates() {
        try {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.DAY_OF_MONTH, 1)
            editTextDateFrom.setText(dateFormat.format(calendar.time))

            calendar.time = Date()
            editTextDateTo.setText(dateFormat.format(calendar.time))
        } catch (e: Exception) {
            Log.e("Statistics", "Error setting default dates: ${e.message}", e)
        }
    }

    private fun setupClickListeners() {
        closeImageView.setOnClickListener { finish() }
        buttonApply.setOnClickListener { loadStatistics() }
        editTextDateFrom.setOnClickListener { showDatePicker(editTextDateFrom) }
        editTextDateTo.setOnClickListener { showDatePicker(editTextDateTo) }
    }

    private fun showDatePicker(target: TextView) {
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        val current = target.text.toString()
        if (current.isNotEmpty() && current != target.hint) {
            try { cal.time = fmt.parse(current)!! } catch (_: Exception) {}
        }
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            target.text = fmt.format(cal.time)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDatesForPeriod(periodPosition: Int) {
        try {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val calendar = Calendar.getInstance()

            editTextDateTo.setText(dateFormat.format(calendar.time))

            when (periodPosition) {
                0 -> calendar.add(Calendar.MONTH, -1)
                1 -> calendar.add(Calendar.MONTH, -3)
                2 -> calendar.add(Calendar.MONTH, -6)
                3 -> calendar.add(Calendar.YEAR, -1)
            }

            editTextDateFrom.setText(dateFormat.format(calendar.time))
        } catch (e: Exception) {
            Log.e("Statistics", "Error updating dates: ${e.message}", e)
        }
    }

    private fun setupCharts() {
        try {
            // круговая диаграмма
            pieChart.setUsePercentValues(true)
            pieChart.description.isEnabled = false
            pieChart.setExtraOffsets(0f, 0f, 0f, 0f)
            pieChart.dragDecelerationFrictionCoef = 0.95f
            pieChart.isDrawHoleEnabled = false
            pieChart.setHoleColor(Color.WHITE)
            pieChart.setTransparentCircleColor(Color.WHITE)
            pieChart.setTransparentCircleAlpha(110)
            pieChart.holeRadius = 58f
            pieChart.transparentCircleRadius = 61f
            pieChart.setDrawCenterText(false)
            pieChart.rotationAngle = 15f
            pieChart.isRotationEnabled = true
            pieChart.isHighlightPerTapEnabled = true
            pieChart.legend.isEnabled = true

            // линейная диаграмма
            lineChart.description.isEnabled = false
            lineChart.setTouchEnabled(true)
            lineChart.isDragEnabled = true
            lineChart.setScaleEnabled(true)
            lineChart.setPinchZoom(true)
            lineChart.setDrawGridBackground(false)

            val xAxis: XAxis = lineChart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.isGranularityEnabled = true

            val leftAxis: YAxis = lineChart.axisLeft
            leftAxis.setDrawGridLines(true)
            leftAxis.axisMinimum = 0f

            lineChart.axisRight.isEnabled = false
            lineChart.legend.isEnabled = true

            // столбчатая диаграмма
            barChart.description.isEnabled = false
            barChart.setTouchEnabled(true)
            barChart.isDragEnabled = true
            barChart.setScaleEnabled(true)
            barChart.setPinchZoom(false)
            barChart.setDrawGridBackground(false)
            barChart.setDrawBarShadow(false)
            barChart.setDrawValueAboveBar(true)

            val xAxisBar: XAxis = barChart.xAxis
            xAxisBar.position = XAxis.XAxisPosition.BOTTOM
            xAxisBar.setDrawGridLines(false)
            xAxisBar.granularity = 1f
            xAxisBar.isGranularityEnabled = true

            val leftAxisBar: YAxis = barChart.axisLeft
            leftAxisBar.setDrawGridLines(true)
            leftAxisBar.axisMinimum = 0f

            barChart.axisRight.isEnabled = false
            barChart.legend.isEnabled = true

        } catch (e: Exception) {
            Log.e("Statistics", "Error setting up charts: ${e.message}", e)
        }
    }

    private fun loadUserCars() {
        val sessionManager = SessionManager(this)
        val userId = sessionManager.getUserId()
        val intentCarId = intent.getIntExtra("car_id", -1)

        progressOverlay.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arr = ApiClient.getCars(userId)
                userCars.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val brand = obj.optString("brand", "")
                    val model = obj.optString("model", "")
                    val displayName = if (brand.isNotEmpty() && model.isNotEmpty()) "$brand $model"
                                      else "Автомобиль ${i + 1}"
                    userCars.add(Car(obj.getInt("car_id"), brand, model, displayName))
                }
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    setupCarSpinner()
                    if (userCars.isNotEmpty()) {
                        val targetIndex = if (intentCarId != -1)
                            userCars.indexOfFirst { it.id == intentCarId }.takeIf { it != -1 } ?: 0
                        else 0
                        selectedCarId = userCars[targetIndex].id
                        carSpinner.setSelection(targetIndex, false)
                        loadStatistics()
                        // Listeners вешаем только после первой загрузки — исключает двойной вызов
                        carSpinner.post { attachSpinnerListeners() }
                    }
                }
            } catch (ex: Exception) {
                Log.e("Statistics", "Error loading cars: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@MainActivityStatistics, "Ошибка загрузки автомобилей", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupCarSpinner() {
        try {
            // Ставим адаптеры без listeners — Android может доставить onItemSelected асинхронно
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, userCars.map { it.displayName })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            carSpinner.adapter = adapter

            val periodAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
                arrayOf("За месяц", "За 3 месяца", "За 6 месяцев", "За год", "Произвольный период"))
            periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            periodTypeSpinner.adapter = periodAdapter

        } catch (e: Exception) {
            Log.e("Statistics", "Error setting up spinner: ${e.message}", e)
        }
    }

    private fun attachSpinnerListeners() {
        carSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position in userCars.indices && userCars[position].id != selectedCarId) {
                    selectedCarId = userCars[position].id
                    loadStatistics()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        periodTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val showCustomPeriod = position == 4
                customPeriodLayout.visibility = if (showCustomPeriod) View.VISIBLE else View.GONE
                if (!showCustomPeriod) {
                    updateDatesForPeriod(position)
                    loadStatistics()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun loadStatistics() {
        if (userCars.isEmpty()) {
            Toast.makeText(this, "Нет доступных автомобилей", Toast.LENGTH_SHORT).show()
            return
        }

        val carId = if (selectedCarId == -1) userCars.first().id else selectedCarId
        val dateFrom = editTextDateFrom.text.toString()
        val dateTo = editTextDateTo.text.toString()

        if (dateFrom.isEmpty() || dateTo.isEmpty()) {
            Toast.makeText(this, "Укажите период", Toast.LENGTH_SHORT).show()
            return
        }

        progressOverlay.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val statistics = loadStatisticsFromDatabase(carId, dateFrom, dateTo)
                val monthlyStats = loadMonthlyStatistics(carId, dateFrom, dateTo)
                val fuelConsumption = calculateFuelConsumption(carId, dateFrom, dateTo)

                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    scrollViewContent.visibility = View.VISIBLE
                    updateUI(statistics, monthlyStats, fuelConsumption)
                }
            } catch (ex: Exception) {
                Log.e("Statistics", "Error loading statistics: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@MainActivityStatistics, "Ошибка загрузки статистики", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun loadStatisticsFromDatabase(carId: Int, dateFrom: String, dateTo: String): Map<String, Double> {
        return try {
            val obj = ApiClient.getStatistics(carId, dateFrom, dateTo)
            mapOf(
                "total_expenses"        to obj.optDouble("total_expenses", 0.0),
                "fuel_expenses"         to obj.optDouble("fuel_expenses", 0.0),
                "maintenance_expenses"  to obj.optDouble("maintenance_expenses", 0.0),
                "total_mileage"         to obj.optDouble("total_mileage", 0.0)
            )
        } catch (ex: Exception) {
            Log.e("Statistics", "Error in loadStatisticsFromDatabase: ${ex.message}", ex)
            emptyMap()
        }
    }

    private suspend fun loadMonthlyStatistics(carId: Int, dateFrom: String, dateTo: String): List<MonthlyStat> {
        return try {
            val arr = ApiClient.getMonthlyStatistics(carId, dateFrom, dateTo)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                MonthlyStat(
                    month = obj.optString("month", ""),
                    totalAmount = obj.optDouble("total_amount", 0.0),
                    fuelAmount = obj.optDouble("fuel_amount", 0.0),
                    maintenanceAmount = obj.optDouble("maintenance_amount", 0.0)
                )
            }
        } catch (ex: Exception) {
            Log.e("Statistics", "Error in loadMonthlyStatistics: ${ex.message}", ex)
            emptyList()
        }
    }

    private suspend fun calculateFuelConsumption(carId: Int, dateFrom: String, dateTo: String): Map<String, Double> {
        return try {
            val obj = ApiClient.getFuelConsumption(carId, dateFrom, dateTo)
            mapOf(
                "avg_consumption" to obj.optDouble("avg_consumption", 0.0),
                "cost_per_km"     to obj.optDouble("cost_per_km", 0.0),
                "distance"        to obj.optDouble("distance", 0.0),
                "total_volume"    to obj.optDouble("total_volume", 0.0),
                "total_fuel_cost" to obj.optDouble("total_fuel_cost", 0.0)
            )
        } catch (ex: Exception) {
            Log.e("Statistics", "Error in calculateFuelConsumption: ${ex.message}", ex)
            mapOf("avg_consumption" to 0.0, "cost_per_km" to 0.0, "distance" to 0.0,
                  "total_volume" to 0.0, "total_fuel_cost" to 0.0)
        }
    }

    private fun updateUI(statistics: Map<String, Double>, monthlyStats: List<MonthlyStat>, fuelConsumption: Map<String, Double>) {
        try {
            val totalExpenses = statistics["total_expenses"] ?: 0.0
            val fuelExpenses = statistics["fuel_expenses"] ?: 0.0
            val maintenanceExpenses = statistics["maintenance_expenses"] ?: 0.0
            val totalMileage = statistics["total_mileage"] ?: 0.0

            // Используем distance из calculateFuelConsumption, а не total_mileage
            val distance = fuelConsumption["distance"] ?: 0.0
            val avgConsumption = fuelConsumption["avg_consumption"] ?: 0.0
            val costPerKm = fuelConsumption["cost_per_km"] ?: 0.0

            textViewTotalExpenses.text = "%,d руб".format(totalExpenses.toInt())
            textViewFuelExpenses.text = "%,d руб".format(fuelExpenses.toInt())
            textViewMaintenanceExpenses.text = "%,d руб".format(maintenanceExpenses.toInt())

            // Форматируем с 2 знаками после запятой для точности
            textViewCostPerKm.text = "%.2f руб/км".format(costPerKm)
            textViewMileage.text = "%,d км".format(distance.toInt())
            textViewFuelConsumption.text = "%.1f л/100км".format(avgConsumption)

            updatePieChart(fuelExpenses, maintenanceExpenses)
            updateLineChart(monthlyStats)
            updateBarChart(monthlyStats)

        } catch (e: Exception) {
            Log.e("Statistics", "Error updating UI: ${e.message}", e)
        }
    }

    private fun updatePieChart(fuelExpenses: Double, maintenanceExpenses: Double) {
        try {
            val entries = ArrayList<PieEntry>()

            if (fuelExpenses > 0) {
                entries.add(PieEntry(fuelExpenses.toFloat(), "Топливо"))
            }
            if (maintenanceExpenses > 0) {
                entries.add(PieEntry(maintenanceExpenses.toFloat(), "Обслуживание"))
            }

            val dataSet = PieDataSet(entries, "")
            dataSet.colors = listOf(
                Color.parseColor("#228BE6"),
                Color.parseColor("#40C057"),
                Color.parseColor("#FA5252"),
                Color.parseColor("#FCC419")
            )
            dataSet.sliceSpace = 3f
            dataSet.selectionShift = 5f

            val data = PieData(dataSet)
            data.setValueTextSize(12f)
            data.setValueTextColor(Color.WHITE)

            data.setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (pieChart.isUsePercentValuesEnabled) {
                        "${value.toInt()}%"
                    } else {
                        "%,d руб.".format(value.toInt())
                    }
                }

            })

            pieChart.data = data
            pieChart.animateY(1000, Easing.EaseInOutQuad)
            pieChart.invalidate()

        } catch (e: Exception) {
            Log.e("Statistics", "Error updating pie chart: ${e.message}", e)
        }
    }

    private fun updateLineChart(monthlyStats: List<MonthlyStat>) {
        try {
            val entries = ArrayList<Entry>()
            val months = ArrayList<String>()

            monthlyStats.reversed().forEachIndexed { index, stat ->
                entries.add(Entry(index.toFloat(), stat.totalAmount.toFloat()))
                try {
                    val monthName = SimpleDateFormat("MMM yy", Locale.getDefault()).format(
                        SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(stat.month) ?: Date()
                    )
                    months.add(monthName)
                } catch (e: Exception) {
                    months.add(stat.month)
                }
            }

            if (entries.isEmpty()) {
                entries.add(Entry(0f, 0f))
                months.add("Нет данных")
            }

            val dataSet = LineDataSet(entries, "Общие расходы")
            dataSet.color = Color.parseColor("#228BE6")
            dataSet.valueTextColor = Color.parseColor("#228BE6")
            dataSet.lineWidth = 2.5f
            dataSet.setCircleColor(Color.parseColor("#228BE6"))
            dataSet.circleRadius = 4f
            dataSet.setDrawCircleHole(false)
            dataSet.valueTextSize = 10f
            dataSet.setDrawValues(true)

            val data = LineData(dataSet)
            data.setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "%,d".format(value.toInt())
                }
            })

            lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(months)
            lineChart.data = data
            lineChart.animateX(1000, Easing.EaseInOutQuad)
            lineChart.invalidate()

        } catch (e: Exception) {
            Log.e("Statistics", "Error updating line chart: ${e.message}", e)
        }
    }

    private fun updateBarChart(monthlyStats: List<MonthlyStat>) {
        try {
            val fuelEntries = ArrayList<BarEntry>()
            val maintenanceEntries = ArrayList<BarEntry>()
            val months = ArrayList<String>()

            monthlyStats.reversed().forEachIndexed { index, stat ->
                fuelEntries.add(BarEntry(index.toFloat(), stat.fuelAmount.toFloat()))
                maintenanceEntries.add(BarEntry(index.toFloat(), stat.maintenanceAmount.toFloat()))
                try {
                    val monthName = SimpleDateFormat("MMM yy", Locale.getDefault()).format(
                        SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(stat.month) ?: Date()
                    )
                    months.add(monthName)
                } catch (e: Exception) {
                    months.add(stat.month)
                }
            }

            if (fuelEntries.isEmpty()) {
                fuelEntries.add(BarEntry(0f, 0f))
                maintenanceEntries.add(BarEntry(0f, 0f))
                months.add("Нет данных")
            }

            val fuelSet = BarDataSet(fuelEntries, "Топливо")
            fuelSet.color = Color.parseColor("#228BE6")

            val maintenanceSet = BarDataSet(maintenanceEntries, "Обслуживание")
            maintenanceSet.color = Color.parseColor("#40C057")

            val data = BarData(fuelSet, maintenanceSet)
            data.barWidth = 0.4f
            data.setValueTextSize(10f)
            data.setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0) "%,d".format(value.toInt()) else ""
                }
            })

            barChart.xAxis.valueFormatter = IndexAxisValueFormatter(months)
            barChart.data = data
            barChart.groupBars(0f, 0.4f, 0.1f)
            barChart.animateY(1000, Easing.EaseInOutQuad)
            barChart.invalidate()

        } catch (e: Exception) {
            Log.e("Statistics", "Error updating bar chart: ${e.message}", e)
        }
    }


    class StatsAdapter(private val stats: List<StatItem>) :
        RecyclerView.Adapter<StatsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textCategory: TextView = view.findViewById(R.id.textCategory)
            val textAmount: TextView = view.findViewById(R.id.textAmount)
            val textPercentage: TextView = view.findViewById(R.id.textPercentage)
            val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_stat, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = stats[position]
            holder.textCategory.text = item.category
            holder.textAmount.text = "%,d руб.".format(item.amount.toInt())
            holder.textPercentage.text = "%.1f%%".format(item.percentage)
            holder.progressBar.progress = item.percentage.toInt()
        }

        override fun getItemCount() = stats.size
    }
}