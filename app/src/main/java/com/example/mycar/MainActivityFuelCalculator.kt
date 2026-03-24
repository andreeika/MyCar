package com.example.mycar

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityFuelCalculator : AppCompatActivity() {

    private lateinit var carSpinner: Spinner
    private lateinit var editTextDistance: EditText
    private lateinit var editTextConsumption: EditText
    private lateinit var editTextPrice: EditText
    private lateinit var buttonCalculate: Button
    private lateinit var resultCard: View
    private lateinit var textViewVolume: TextView
    private lateinit var textViewCost: TextView
    private lateinit var textViewHint: TextView

    private data class CarItem(val id: Int, val displayName: String)
    private val cars = mutableListOf<CarItem>()
    private var selectedCarId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_fuel_calculator)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }

        carSpinner = findViewById(R.id.carSpinner)
        editTextDistance = findViewById(R.id.editTextDistance)
        editTextConsumption = findViewById(R.id.editTextConsumption)
        editTextPrice = findViewById(R.id.editTextPrice)
        buttonCalculate = findViewById(R.id.buttonCalculate)
        resultCard = findViewById(R.id.resultCard)
        textViewVolume = findViewById(R.id.textViewVolume)
        textViewCost = findViewById(R.id.textViewCost)
        textViewHint = findViewById(R.id.textViewHint)

        findViewById<ImageView>(R.id.imageViewClose).setOnClickListener { finish() }
        buttonCalculate.setOnClickListener { calculate() }

        val intentCarId = intent.getIntExtra("car_id", -1)
        loadCars(intentCarId)
    }

    private fun loadCars(preferredCarId: Int) {
        val sessionManager = SessionManager(this)
        val userId = sessionManager.getUserId()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val arr = ApiClient.getCars(userId)
                cars.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val brand = obj.optString("brand", "")
                    val model = obj.optString("model", "")
                    val name = if (brand.isNotEmpty() && model.isNotEmpty()) "$brand $model"
                               else "Автомобиль ${i + 1}"
                    cars.add(CarItem(obj.getInt("car_id"), name))
                }
                withContext(Dispatchers.Main) {
                    if (cars.isEmpty()) return@withContext
                    val adapter = ArrayAdapter(
                        this@MainActivityFuelCalculator,
                        android.R.layout.simple_spinner_item,
                        cars.map { it.displayName }
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    carSpinner.adapter = adapter

                    // Выбираем нужный авто
                    val idx = if (preferredCarId != -1)
                        cars.indexOfFirst { it.id == preferredCarId }.takeIf { it != -1 } ?: 0
                    else 0
                    selectedCarId = cars[idx].id
                    carSpinner.setSelection(idx, false)
                    loadDefaults(selectedCarId)

                    carSpinner.post {
                        carSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                                val newId = cars[position].id
                                if (newId != selectedCarId) {
                                    selectedCarId = newId
                                    editTextConsumption.setText("")
                                    editTextPrice.setText("")
                                    textViewHint.text = ""
                                    resultCard.visibility = View.GONE
                                    loadDefaults(selectedCarId)
                                }
                            }
                            override fun onNothingSelected(parent: AdapterView<*>) {}
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityFuelCalculator, "Ошибка загрузки автомобилей", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadDefaults(carId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cal = java.util.Calendar.getInstance()
                val fmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                val dateTo = fmt.format(cal.time)
                cal.add(java.util.Calendar.MONTH, -3)
                val dateFrom = fmt.format(cal.time)

                val consumption = ApiClient.getFuelConsumption(carId, dateFrom, dateTo)
                val avgConsumption = consumption.optDouble("avg_consumption", 0.0)

                val refueling = ApiClient.getRefueling(carId)
                val lastPrice = if (refueling.length() > 0)
                    refueling.getJSONObject(0).optDouble("price_per_liter", 0.0) else 0.0

                withContext(Dispatchers.Main) {
                    if (avgConsumption > 0) {
                        editTextConsumption.setText("%.1f".format(avgConsumption))
                        textViewHint.text = "Средний расход из истории заправок за 3 месяца"
                    }
                    if (lastPrice > 0) {
                        editTextPrice.setText("%.2f".format(lastPrice))
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun calculate() {
        val distanceStr = editTextDistance.text.toString().replace(",", ".")
        val consumptionStr = editTextConsumption.text.toString().replace(",", ".")
        val priceStr = editTextPrice.text.toString().replace(",", ".")

        if (distanceStr.isEmpty()) { Toast.makeText(this, "Введите расстояние", Toast.LENGTH_SHORT).show(); return }
        if (consumptionStr.isEmpty()) { Toast.makeText(this, "Введите расход топлива", Toast.LENGTH_SHORT).show(); return }
        if (priceStr.isEmpty()) { Toast.makeText(this, "Введите цену топлива", Toast.LENGTH_SHORT).show(); return }

        val distance = distanceStr.toDoubleOrNull() ?: run { Toast.makeText(this, "Неверное расстояние", Toast.LENGTH_SHORT).show(); return }
        val consumption = consumptionStr.toDoubleOrNull() ?: run { Toast.makeText(this, "Неверный расход", Toast.LENGTH_SHORT).show(); return }
        val price = priceStr.toDoubleOrNull() ?: run { Toast.makeText(this, "Неверная цена", Toast.LENGTH_SHORT).show(); return }

        val volume = distance * consumption / 100.0
        val cost = volume * price

        textViewVolume.text = "%.1f л".format(volume)
        textViewCost.text = "%,d руб".format(cost.toInt())
        textViewHint.text = "%.0f км × %.1f л/100км × %.2f руб/л".format(distance, consumption, price)
        resultCard.visibility = View.VISIBLE
    }
}
