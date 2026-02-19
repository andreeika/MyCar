package com.example.mycar

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
import java.text.SimpleDateFormat
import java.util.*

class MainActivityHistoryMainte : AppCompatActivity() {
    private lateinit var listViewMaintenance: ListView
    private lateinit var emptyTextView: TextView
    private lateinit var backButton: ImageView

    private val maintenanceList = mutableListOf<Maintenance>()
    private lateinit var sharedPreferences: android.content.SharedPreferences

    companion object {
        private const val PREF_CAR_ID = "current_car_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_history_mainte)

        sharedPreferences = getSharedPreferences("my_car_prefs", MODE_PRIVATE)
        initializeViews()
        setupStatusBarColors()
        setupClickListeners()
        loadMaintenanceFromDatabase()
    }

    private fun initializeViews() {
        listViewMaintenance = findViewById(R.id.listViewRefuelings)
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

        listViewMaintenance.setOnItemClickListener { parent, view, position, id ->
            val maintenance = maintenanceList[position]
            Toast.makeText(this, "Обслуживание: ${maintenance.serviceTypeName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadMaintenanceFromDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val carId = getCurrentCarId()

                    val query = """
                        SELECT 
                            m.maintenance_id,
                            m.date,
                            m.mileage,
                            m.total_amount,
                            m.description,
                            m.next_service_date,
                            st.name as service_type_name
                        FROM Maintenance m
                        LEFT JOIN ServiceTypes st ON m.service_type_id = st.service_type_id
                        WHERE m.car_id = ?
                        ORDER BY m.date DESC, m.mileage DESC
                    """

                    val preparedStatement = connect.prepareStatement(query)
                    preparedStatement.setInt(1, carId)
                    val resultSet: ResultSet = preparedStatement.executeQuery()

                    maintenanceList.clear()
                    while (resultSet.next()) {
                        val maintenanceId = resultSet.getInt("maintenance_id")
                        val date = resultSet.getDate("date")
                        val mileage = resultSet.getDouble("mileage")
                        val totalAmount = resultSet.getDouble("total_amount")
                        val description = resultSet.getString("description") ?: ""
                        val nextServiceDate = resultSet.getDate("next_service_date")
                        val serviceTypeName = resultSet.getString("service_type_name") ?: "Неизвестно"

                        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                        val formattedDate = if (date != null) dateFormat.format(date) else "Нет данных"
                        val formattedNextDate = if (nextServiceDate != null) dateFormat.format(nextServiceDate) else "Не указана"

                        maintenanceList.add(Maintenance(
                            maintenanceId,
                            formattedDate,
                            mileage,
                            totalAmount,
                            description,
                            formattedNextDate,
                            serviceTypeName
                        ))
                    }

                    resultSet.close()
                    preparedStatement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        updateMaintenanceList()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityHistoryMainte, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityHistoryMainte, "Ошибка загрузки обслуживаний", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateMaintenanceList() {
        if (maintenanceList.isEmpty()) {
            emptyTextView.visibility = android.view.View.VISIBLE
            listViewMaintenance.visibility = android.view.View.GONE
            emptyTextView.text = "Нет данных об обслуживаниях"
        } else {
            emptyTextView.visibility = android.view.View.GONE
            listViewMaintenance.visibility = android.view.View.VISIBLE

            val adapter = MaintenanceAdapter(this, maintenanceList)
            listViewMaintenance.adapter = adapter
        }
    }

    private fun getCurrentCarId(): Int {
        return sharedPreferences.getInt(PREF_CAR_ID, 1)
    }

    data class Maintenance(
        val id: Int,
        val date: String,
        val mileage: Double,
        val totalAmount: Double,
        val description: String,
        val nextServiceDate: String,
        val serviceTypeName: String
    )
}