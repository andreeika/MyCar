package com.example.mycar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.*

class MainActivityHistoryRef : AppCompatActivity() {
    private lateinit var listViewRefuelings: ListView
    private lateinit var emptyTextView: TextView
    private lateinit var backButton: ImageView

    private val refuelings = mutableListOf<Refueling>()
    private lateinit var sharedPreferences: android.content.SharedPreferences

    companion object {
        private const val TAG = "RefuelingHistoryActivity"
        private const val PREF_CAR_ID = "current_car_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_history_ref)

        sharedPreferences = getSharedPreferences("my_car_prefs", MODE_PRIVATE)
        initializeViews()
        setupStatusBarColors()
        setupClickListeners()
        loadRefuelingsFromDatabase()
    }

    private fun initializeViews() {
        listViewRefuelings = findViewById(R.id.listViewRefuelings)
        emptyTextView = findViewById(R.id.emptyTextView)
        backButton = findViewById(R.id.imageViewCancel)
    }

    private fun setupStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }
    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        listViewRefuelings.setOnItemClickListener { parent, view, position, id ->
            val refueling = refuelings[position]
            Toast.makeText(this, "Заправка от ${refueling.date}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRefuelingsFromDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val carId = getCurrentCarId()

                    val query = """
                        SELECT 
                            r.refueling_id,
                            r.date,
                            r.mileage,
                            r.volume,
                            r.price_per_liter,
                            r.total_amount,
                            r.full_tank,
                            f.name as fuel_name,
                            s.name as station_name
                        FROM Refueling r
                        LEFT JOIN Fuel f ON r.fuel_id = f.fuel_id
                        LEFT JOIN GasStations s ON r.station_id = s.station_id
                        WHERE r.car_id = ?
                        ORDER BY r.date DESC, r.mileage DESC
                    """

                    val preparedStatement = connect.prepareStatement(query)
                    preparedStatement.setInt(1, carId)
                    val resultSet: ResultSet = preparedStatement.executeQuery()

                    refuelings.clear()
                    while (resultSet.next()) {
                        val refuelingId = resultSet.getInt("refueling_id")
                        val date = resultSet.getDate("date")
                        val mileage = resultSet.getDouble("mileage")
                        val volume = resultSet.getDouble("volume")
                        val pricePerLiter = resultSet.getDouble("price_per_liter")
                        val totalAmount = resultSet.getDouble("total_amount")
                        val fullTank = resultSet.getBoolean("full_tank")
                        val fuelName = resultSet.getString("fuel_name") ?: "Неизвестно"
                        val stationName = resultSet.getString("station_name") ?: "Неизвестно"

                        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                        val formattedDate = dateFormat.format(date)

                        refuelings.add(Refueling(
                            refuelingId,
                            formattedDate,
                            mileage,
                            volume,
                            pricePerLiter,
                            totalAmount,
                            fullTank,
                            fuelName,
                            stationName
                        ))
                    }

                    resultSet.close()
                    preparedStatement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        updateRefuelingsList()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityHistoryRef, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityHistoryRef, "Ошибка загрузки заправок", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateRefuelingsList() {
        if (refuelings.isEmpty()) {
            emptyTextView.visibility = android.view.View.VISIBLE
            listViewRefuelings.visibility = android.view.View.GONE
            emptyTextView.text = "Нет данных о заправках"
        } else {
            emptyTextView.visibility = android.view.View.GONE
            listViewRefuelings.visibility = android.view.View.VISIBLE

            val adapter = RefuelingAdapter(this, refuelings)
            listViewRefuelings.adapter = adapter
        }
    }

    private fun getCurrentCarId(): Int {
        return sharedPreferences.getInt(PREF_CAR_ID, 1)
    }

    data class Refueling(
        val id: Int,
        val date: String,
        val mileage: Double,
        val volume: Double,
        val pricePerLiter: Double,
        val totalAmount: Double,
        val fullTank: Boolean,
        val fuelName: String,
        val stationName: String
    )
}