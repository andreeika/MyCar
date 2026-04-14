package com.example.mycar

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityFuelPrices : BaseActivity() {

    private lateinit var carSpinner: Spinner
    private lateinit var priceChart: LineChart
    private lateinit var textMinPrice: TextView
    private lateinit var textAvgPrice: TextView
    private lateinit var textMaxPrice: TextView
    private lateinit var progressOverlay: FrameLayout

    private data class CarItem(val id: Int, val name: String)
    private val cars = mutableListOf<CarItem>()
    private var selectedCarId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fuel_prices)

        carSpinner = findViewById(R.id.carSpinner)
        priceChart = findViewById(R.id.priceChart)
        textMinPrice = findViewById(R.id.textMinPrice)
        textAvgPrice = findViewById(R.id.textAvgPrice)
        textMaxPrice = findViewById(R.id.textMaxPrice)
        progressOverlay = findViewById(R.id.progressOverlay)

        findViewById<ImageView>(R.id.imageViewClose).setOnClickListener { finish() }

        setupChart()
        loadCars()
    }

    private fun setupChart() {
        priceChart.description.isEnabled = false
        priceChart.setTouchEnabled(true)
        priceChart.isDragEnabled = true
        priceChart.setScaleEnabled(true)
        priceChart.setPinchZoom(true)
        priceChart.setDrawGridBackground(false)
        priceChart.axisRight.isEnabled = false
        priceChart.legend.isEnabled = false

        val xAxis = priceChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = -45f

        priceChart.axisLeft.setDrawGridLines(true)
        priceChart.axisLeft.axisMinimum = 0f
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
                    cars.add(CarItem(obj.getInt("car_id"), name))
                }
                withContext(Dispatchers.Main) {
                    if (cars.isEmpty()) return@withContext
                    val adapter = ArrayAdapter(this@ActivityFuelPrices,
                        android.R.layout.simple_spinner_item, cars.map { it.name })
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    carSpinner.adapter = adapter

                    val intentCarId = intent.getIntExtra("car_id", -1)
                    val idx = if (intentCarId != -1) cars.indexOfFirst { it.id == intentCarId }.takeIf { it != -1 } ?: 0 else 0
                    selectedCarId = cars[idx].id
                    carSpinner.setSelection(idx, false)
                    loadPrices(selectedCarId)

                    carSpinner.post {
                        carSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                                val newId = cars[position].id
                                if (newId != selectedCarId) {
                                    selectedCarId = newId
                                    loadPrices(selectedCarId)
                                }
                            }
                            override fun onNothingSelected(parent: AdapterView<*>) {}
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ActivityFuelPrices, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadPrices(carId: Int) {
        progressOverlay.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val arr = ApiClient.getRefueling(carId)
                data class PricePoint(val date: String, val price: Double, val fuelName: String)
                val points = mutableListOf<PricePoint>()

                val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dispFmt = SimpleDateFormat("dd.MM", Locale.getDefault())

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val price = obj.optDouble("price_per_liter", 0.0)
                    val dateStr = obj.optString("date", "")
                    val fuel = obj.optString("fuel", "")
                    if (price > 0 && dateStr.isNotEmpty()) {
                        try {
                            val parsed = isoFmt.parse(dateStr)
                            val label = if (parsed != null) dispFmt.format(parsed) else dateStr
                            points.add(PricePoint(label, price, fuel))
                        } catch (_: Exception) {
                            points.add(PricePoint(dateStr, price, fuel))
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    if (points.isEmpty()) {
                        Toast.makeText(this@ActivityFuelPrices, "Нет данных о заправках", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    // Группируем по видам топлива
                    val byFuel = points.groupBy { it.fuelName }
                    val colors = listOf(
                        Color.parseColor("#228BE6"),
                        Color.parseColor("#40C057"),
                        Color.parseColor("#FA5252"),
                        Color.parseColor("#FCC419")
                    )

                    val allLabels = points.map { it.date }
                    val dataSets = mutableListOf<LineDataSet>()

                    byFuel.entries.forEachIndexed { idx, (fuelName, fuelPoints) ->
                        val entries = fuelPoints.mapIndexed { i, p ->
                            Entry(allLabels.indexOf(p.date).toFloat(), p.price.toFloat())
                        }
                        val ds = LineDataSet(entries, fuelName.ifEmpty { "Топливо" })
                        ds.color = colors[idx % colors.size]
                        ds.valueTextColor = colors[idx % colors.size]
                        ds.lineWidth = 2f
                        ds.setCircleColor(colors[idx % colors.size])
                        ds.circleRadius = 4f
                        ds.setDrawCircleHole(false)
                        ds.valueTextSize = 9f
                        ds.setDrawValues(true)
                        ds.valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float) = "%.1f".format(value)
                        }
                        dataSets.add(ds)
                    }

                    priceChart.xAxis.valueFormatter = IndexAxisValueFormatter(allLabels)
                    priceChart.xAxis.labelCount = minOf(6, allLabels.size)
                    priceChart.data = LineData(dataSets.toList())
                    priceChart.legend.isEnabled = byFuel.size > 1
                    priceChart.animateX(800)
                    priceChart.invalidate()

                    // Статистика
                    val allPrices = points.map { it.price }
                    textMinPrice.text = "%.1f руб".format(allPrices.min())
                    textAvgPrice.text = "%.1f руб".format(allPrices.average())
                    textMaxPrice.text = "%.1f руб".format(allPrices.max())
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@ActivityFuelPrices, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
