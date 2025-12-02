package com.example.mycar

import SessionManager
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.*

class MainActivityStatistics : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var carSpinner: Spinner
    private lateinit var periodTypeSpinner: Spinner
    private lateinit var customPeriodLayout: LinearLayout
    private lateinit var editTextDateFrom: EditText
    private lateinit var editTextDateTo: EditText
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
        closeImageView.setOnClickListener {
            finish()
        }

        buttonApply.setOnClickListener {
            loadStatistics()
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
            pieChart.setExtraOffsets(5f, 10f, 5f, 5f)
            pieChart.dragDecelerationFrictionCoef = 0.95f
            pieChart.isDrawHoleEnabled = false
            pieChart.setHoleColor(Color.WHITE)
            pieChart.setTransparentCircleColor(Color.WHITE)
            pieChart.setTransparentCircleAlpha(110)
            pieChart.holeRadius = 58f
            pieChart.transparentCircleRadius = 61f
            pieChart.setDrawCenterText(false)
            pieChart.rotationAngle = 0f
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()
                val sessionManager = SessionManager(this@MainActivityStatistics)
                val userId = sessionManager.getUserId()

                if (connect != null) {
                    val query = """
                        SELECT c.car_id, cb.name as brand_name, cm.name as model_name 
                        FROM Cars c 
                        LEFT JOIN CarModels cm ON c.model_id = cm.model_id 
                        LEFT JOIN CarBrands cb ON cm.brand_id = cb.brand_id 
                        WHERE c.user_id = $userId
                    """

                    val statement: Statement = connect.createStatement()
                    val resultSet: ResultSet = statement.executeQuery(query)

                    userCars.clear()
                    while (resultSet.next()) {
                        val carId = resultSet.getInt("car_id")
                        val brand = resultSet.getString("brand_name") ?: ""
                        val model = resultSet.getString("model_name") ?: ""
                        val displayName = if (brand.isNotEmpty() && model.isNotEmpty()) {
                            "$brand $model"
                        } else {
                            "Автомобиль ${userCars.size + 1}"
                        }

                        userCars.add(Car(carId, brand, model, displayName))
                    }

                    resultSet.close()
                    statement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        setupCarSpinner()
                        if (userCars.isNotEmpty()) {
                            selectedCarId = userCars[0].id
                            loadStatistics()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityStatistics, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e("Statistics", "Error loading cars: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityStatistics, "Ошибка загрузки автомобилей", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupCarSpinner() {
        try {
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, userCars.map { it.displayName })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            carSpinner.adapter = adapter

            val periodAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
                arrayOf("За месяц", "За 3 месяца", "За 6 месяцев", "За год", "Произвольный период"))
            periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            periodTypeSpinner.adapter = periodAdapter

            carSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    if (position in userCars.indices) {
                        selectedCarId = userCars[position].id
                        loadStatistics()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        } catch (e: Exception) {
            Log.e("Statistics", "Error setting up spinner: ${e.message}", e)
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val statistics = loadStatisticsFromDatabase(carId, dateFrom, dateTo)
                val monthlyStats = loadMonthlyStatistics(carId, dateFrom, dateTo)
                val fuelConsumption = calculateFuelConsumption(carId, dateFrom, dateTo)

                withContext(Dispatchers.Main) {
                    updateUI(statistics, monthlyStats, fuelConsumption)
                }
            } catch (ex: Exception) {
                Log.e("Statistics", "Error loading statistics: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityStatistics, "Ошибка загрузки статистики", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun loadStatisticsFromDatabase(carId: Int, dateFrom: String, dateTo: String): Map<String, Double> {
        val connectionHelper = ConnectionHelper()
        val connect = connectionHelper.connectionclass() ?: return emptyMap()

        return try {
            val query = """
                SELECT 
                    COALESCE(SUM(fuel_costs), 0) + COALESCE(SUM(maintenance_costs), 0) as total_expenses,
                    COALESCE(SUM(fuel_costs), 0) as fuel_expenses,
                    COALESCE(SUM(maintenance_costs), 0) as maintenance_expenses,
                    COALESCE(MAX(total_mileage), 0) as total_mileage
                FROM (
                    SELECT 
                        SUM(r.total_amount) as fuel_costs,
                        NULL as maintenance_costs,
                        MAX(r.mileage) - MIN(r.mileage) as total_mileage
                    FROM Refueling r 
                    WHERE r.car_id = $carId 
                    AND CONVERT(date, r.date, 104) BETWEEN CONVERT(date, '$dateFrom', 104) AND CONVERT(date, '$dateTo', 104)
                    
                    UNION ALL
                    
                    SELECT 
                        NULL as fuel_costs,
                        SUM(m.total_amount) as maintenance_costs,
                        NULL as total_mileage
                    FROM Maintenance m 
                    WHERE m.car_id = $carId 
                    AND CONVERT(date, m.date, 104) BETWEEN CONVERT(date, '$dateFrom', 104) AND CONVERT(date, '$dateTo', 104)
                ) combined_data
            """

            val statement: Statement = connect.createStatement()
            val resultSet: ResultSet = statement.executeQuery(query)

            val statistics = mutableMapOf<String, Double>()
            if (resultSet.next()) {
                statistics["total_expenses"] = resultSet.getDouble("total_expenses")
                statistics["fuel_expenses"] = resultSet.getDouble("fuel_expenses")
                statistics["maintenance_expenses"] = resultSet.getDouble("maintenance_expenses")
                statistics["total_mileage"] = resultSet.getDouble("total_mileage")
            }

            resultSet.close()
            statement.close()
            connect.close()

            statistics
        } catch (ex: Exception) {
            Log.e("Statistics", "Error in loadStatisticsFromDatabase: ${ex.message}", ex)
            emptyMap()
        }
    }

    private suspend fun loadMonthlyStatistics(carId: Int, dateFrom: String, dateTo: String): List<MonthlyStat> {
        val connectionHelper = ConnectionHelper()
        val connect = connectionHelper.connectionclass() ?: return emptyList()

        return try {
            val query = """
                SELECT 
                    month,
                    SUM(fuel_amount) + SUM(maintenance_amount) as total_amount,
                    SUM(fuel_amount) as fuel_amount,
                    SUM(maintenance_amount) as maintenance_amount
                FROM (
                    SELECT 
                        FORMAT(CONVERT(date, r.date, 104), 'yyyy-MM') as month,
                        SUM(r.total_amount) as fuel_amount,
                        0 as maintenance_amount
                    FROM Refueling r 
                    WHERE r.car_id = $carId 
                    AND CONVERT(date, r.date, 104) BETWEEN CONVERT(date, '$dateFrom', 104) AND CONVERT(date, '$dateTo', 104)
                    GROUP BY FORMAT(CONVERT(date, r.date, 104), 'yyyy-MM')
                    
                    UNION ALL
                    
                    SELECT 
                        FORMAT(CONVERT(date, m.date, 104), 'yyyy-MM') as month,
                        0 as fuel_amount,
                        SUM(m.total_amount) as maintenance_amount
                    FROM Maintenance m 
                    WHERE m.car_id = $carId 
                    AND CONVERT(date, m.date, 104) BETWEEN CONVERT(date, '$dateFrom', 104) AND CONVERT(date, '$dateTo', 104)
                    GROUP BY FORMAT(CONVERT(date, m.date, 104), 'yyyy-MM')
                ) monthly_data
                GROUP BY month
                ORDER BY month DESC
            """

            val statement: Statement = connect.createStatement()
            val resultSet: ResultSet = statement.executeQuery(query)

            val monthlyStats = mutableListOf<MonthlyStat>()
            while (resultSet.next()) {
                val month = resultSet.getString("month")
                val totalAmount = resultSet.getDouble("total_amount")
                val fuelAmount = resultSet.getDouble("fuel_amount")
                val maintenanceAmount = resultSet.getDouble("maintenance_amount")

                monthlyStats.add(MonthlyStat(month, totalAmount, fuelAmount, maintenanceAmount))
            }

            resultSet.close()
            statement.close()
            connect.close()

            monthlyStats
        } catch (ex: Exception) {
            Log.e("Statistics", "Error in loadMonthlyStatistics: ${ex.message}", ex)
            emptyList()
        }
    }

    private suspend fun calculateFuelConsumption(carId: Int, dateFrom: String, dateTo: String): Map<String, Double> {
        val connectionHelper = ConnectionHelper()
        val connect = connectionHelper.connectionclass() ?: return emptyMap()

        return try {
            val query = """
                SELECT 
                    SUM(r.total_amount) as fuel_cost,
                    COUNT(*) as refuel_count,
                    MAX(r.mileage) - MIN(r.mileage) as distance,
                    SUM(r.volume) as total_volume
                FROM Refueling r 
                WHERE r.car_id = $carId 
                AND CONVERT(date, r.date, 104) BETWEEN CONVERT(date, '$dateFrom', 104) AND CONVERT(date, '$dateTo', 104)
            """

            val statement: Statement = connect.createStatement()
            val resultSet: ResultSet = statement.executeQuery(query)

            val consumptionData = mutableMapOf<String, Double>()
            if (resultSet.next()) {
                val fuelCost = resultSet.getDouble("fuel_cost")
                val distance = resultSet.getDouble("distance")
                val totalVolume = resultSet.getDouble("total_volume")

                val avgConsumption = if (distance > 0) (totalVolume / distance * 100) else 0.0
                val costPerKm = if (distance > 0) fuelCost / distance else 0.0

                consumptionData["avg_consumption"] = avgConsumption
                consumptionData["cost_per_km"] = costPerKm
                consumptionData["distance"] = distance
                consumptionData["total_volume"] = totalVolume
            }

            resultSet.close()
            statement.close()
            connect.close()

            consumptionData
        } catch (ex: Exception) {
            Log.e("Statistics", "Error in calculateFuelConsumption: ${ex.message}", ex)
            emptyMap()
        }
    }

    private fun updateUI(statistics: Map<String, Double>, monthlyStats: List<MonthlyStat>, fuelConsumption: Map<String, Double>) {
        try {
            val totalExpenses = statistics["total_expenses"] ?: 0.0
            val fuelExpenses = statistics["fuel_expenses"] ?: 0.0
            val maintenanceExpenses = statistics["maintenance_expenses"] ?: 0.0
            val totalMileage = statistics["total_mileage"] ?: 0.0

            val avgConsumption = fuelConsumption["avg_consumption"] ?: 0.0
            val costPerKm = fuelConsumption["cost_per_km"] ?: 0.0

            textViewTotalExpenses.text = "%,d".format(totalExpenses.toInt())
            textViewFuelExpenses.text = "%,d".format(fuelExpenses.toInt())
            textViewMaintenanceExpenses.text = "%,d".format(maintenanceExpenses.toInt())
            textViewCostPerKm.text = "%.1f".format(costPerKm)
            textViewMileage.text = "%,d".format(totalMileage.toInt())
            textViewFuelConsumption.text = "%.1f".format(avgConsumption)

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

                override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
                    val label = pieEntry?.label ?: ""
                    val amount = pieEntry?.value ?: 0f
                    return if (pieChart.isUsePercentValuesEnabled) {
                        "$label\n${value.toInt()}%"
                    } else {
                        "$label\n%,d руб.".format(amount.toInt())
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