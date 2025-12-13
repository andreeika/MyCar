package com.example.mycar

import android.app.DatePickerDialog
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.*

class MainActivityMaintenance : AppCompatActivity() {

    private lateinit var serviceTypeSpinner: Spinner
    private lateinit var dateEditText: EditText
    private lateinit var mileageEditText: EditText
    private lateinit var amountEditText: EditText
    private lateinit var nextServiceMileageEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var addMaintenanceButton: Button
    private lateinit var cancelImageView: ImageView

    private val serviceTypes = mutableListOf<ServiceType>()
    private val categories = mutableListOf<ServiceCategory>()
    private var selectedServiceTypeId: Int = 0
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val TAG = "MainActivityMaintenance"
        private const val PREF_CAR_ID = "current_car_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_maintenance)

        sharedPreferences = getSharedPreferences("my_car_prefs", MODE_PRIVATE)
        initializeViews()
        setupClickListeners()
        loadServiceTypesWithCategories()
        setCurrentDate()
        setupStatusBarColors()
    }

    private fun initializeViews() {
        serviceTypeSpinner = findViewById(R.id.serviceTypeSpinner)
        dateEditText = findViewById(R.id.dateEditText)
        mileageEditText = findViewById(R.id.mileageEditText)
        amountEditText = findViewById(R.id.amountEditText)
        nextServiceMileageEditText = findViewById(R.id.nextServiceDateEditText)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        addMaintenanceButton = findViewById(R.id.addMaintenanceButton)
        cancelImageView = findViewById(R.id.imageViewCancel)

        mileageEditText.hint = "Текущий пробег, км"
        amountEditText.hint = "0.00"
        nextServiceMileageEditText.hint = "Следующий пробег, км"
    }

    private fun setupStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }

    private fun setCurrentDate() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        dateEditText.setText(currentDate)
    }

    private fun setupClickListeners() {
        serviceTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                handleServiceTypeSelection(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        cancelImageView.setOnClickListener {
            finish()
        }

        addMaintenanceButton.setOnClickListener {
            saveMaintenanceToDatabase()
        }

        mileageEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                calculateNextServiceMileage()
            }
        }

        dateEditText.setOnClickListener {
            showDatePicker(dateEditText)
        }

        nextServiceMileageEditText.setOnClickListener {
            // Можете добавить дополнительную логику здесь
        }
    }

    private fun handleServiceTypeSelection(position: Int) {
        // Получаем реальную позицию сервиса в списке serviceTypes
        // Пропускаем заголовки категорий в спиннере
        var serviceIndex = -1
        var itemCount = 0

        for ((catIndex, category) in categories.withIndex()) {
            if (position == itemCount) {
                // Это заголовок категории - не выбираем
                return
            }
            itemCount++

            val catServices = serviceTypes.filter { it.categoryId == category.categoryId }
            for ((servicePos, service) in catServices.withIndex()) {
                if (position == itemCount) {
                    serviceIndex = serviceTypes.indexOf(service)
                    break
                }
                itemCount++
            }
            if (serviceIndex != -1) break
        }

        if (serviceIndex >= 0 && serviceIndex < serviceTypes.size) {
            selectedServiceTypeId = serviceTypes[serviceIndex].serviceTypeId
            val selectedService = serviceTypes[serviceIndex].name
            Log.d(TAG, "Selected service type: $selectedService (ID: $selectedServiceTypeId)")
            calculateNextServiceMileage()
        }
    }

    private fun showDatePicker(editText: EditText) {
        val calendar = Calendar.getInstance()
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, day)
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            editText.setText(dateFormat.format(selectedDate.time))
        }

        DatePickerDialog(
            this,
            dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun calculateNextServiceMileage() {
        if (selectedServiceTypeId == 0 || mileageEditText.text.toString().trim().isEmpty()) return

        try {
            val currentMileage = mileageEditText.text.toString().trim().toInt()
            val selectedService = serviceTypes.find { it.serviceTypeId == selectedServiceTypeId }

            selectedService?.let { service ->
                if (service.intervalKm > 0) {
                    val nextServiceMileage = currentMileage + service.intervalKm
                    nextServiceMileageEditText.setText(nextServiceMileage.toString())
                } else {
                    nextServiceMileageEditText.text.clear()
                }
            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Error calculating next service mileage: ${e.message}")
        }
    }

    private fun loadServiceTypesWithCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    // Загружаем категории
                    val categoryQuery = """
                        SELECT category_id, name 
                        FROM ServiceCategory 
                        ORDER BY category_id
                    """

                    val categoryStatement: Statement = connect.createStatement()
                    val categoryResultSet: ResultSet = categoryStatement.executeQuery(categoryQuery)

                    categories.clear()
                    while (categoryResultSet.next()) {
                        val categoryId = categoryResultSet.getInt("category_id")
                        val categoryName = categoryResultSet.getString("name")
                        categories.add(ServiceCategory(categoryId, categoryName))
                    }
                    categoryResultSet.close()
                    categoryStatement.close()

                    // Загружаем сервисы с JOIN по category_id
                    val serviceQuery = """
                        SELECT st.service_type_id, st.name, st.category_id, st.interval_km, sc.name as category_name
                        FROM ServiceTypes st
                        LEFT JOIN ServiceCategory sc ON st.category_id = sc.category_id
                        ORDER BY st.category_id, st.name
                    """

                    val serviceStatement: Statement = connect.createStatement()
                    val serviceResultSet: ResultSet = serviceStatement.executeQuery(serviceQuery)

                    serviceTypes.clear()
                    while (serviceResultSet.next()) {
                        val serviceTypeId = serviceResultSet.getInt("service_type_id")
                        val name = serviceResultSet.getString("name")
                        val categoryId = serviceResultSet.getInt("category_id")
                        val intervalKm = serviceResultSet.getInt("interval_km")
                        val categoryName = serviceResultSet.getString("category_name")
                        serviceTypes.add(ServiceType(serviceTypeId, name, categoryId, categoryName, intervalKm))
                    }

                    serviceResultSet.close()
                    serviceStatement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        updateServiceTypeSpinner()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityMaintenance, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading service types: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityMaintenance, "Ошибка загрузки типов обслуживания", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateServiceTypeSpinner() {
        val serviceTypeItems = mutableListOf<String>()

        // Группируем сервисы по категориям
        val servicesByCategory = serviceTypes.groupBy { it.categoryId }

        // Создаем список для спиннера с заголовками категорий
        for (category in categories) {
            val categoryServices = servicesByCategory[category.categoryId]
            if (categoryServices != null && categoryServices.isNotEmpty()) {
                serviceTypeItems.add("--- ${category.name} ---")
                categoryServices.forEach { service ->
                    val intervalText = if (service.intervalKm > 0) "каждые ${service.intervalKm} км" else "по необходимости"
                    val displayName = "${service.name} ($intervalText)"
                    serviceTypeItems.add(displayName)
                }
            }
        }

        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            serviceTypeItems
        ) {
            override fun isEnabled(position: Int): Boolean {
                return !serviceTypeItems[position].startsWith("---")
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                view.setBackgroundColor(ContextCompat.getColor(this@MainActivityMaintenance, R.color.white))
                return view
            }
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serviceTypeSpinner.adapter = adapter

        Log.d(TAG, "Service types loaded: ${serviceTypes.size}, Categories: ${categories.size}")
    }

    private fun saveMaintenanceToDatabase() {
        if (!validateInput()) return

        val date = dateEditText.text.toString().trim()
        val mileage = mileageEditText.text.toString().trim().toInt()
        val amount = amountEditText.text.toString().trim().toDoubleOrNull() ?: 0.0
        val nextServiceMileage = nextServiceMileageEditText.text.toString().trim().toIntOrNull()
        val description = descriptionEditText.text.toString().trim()
        val carId = getCurrentCarId()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    connect.autoCommit = false

                    try {
                        val insertQuery = """
                        INSERT INTO Maintenance 
                        (car_id, service_type_id, date, mileage, total_amount, description, next_service_date) 
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                    """

                        val preparedStatement: PreparedStatement = connect.prepareStatement(insertQuery)
                        preparedStatement.setInt(1, carId)
                        preparedStatement.setInt(2, selectedServiceTypeId)
                        preparedStatement.setString(3, date)
                        preparedStatement.setInt(4, mileage)
                        preparedStatement.setDouble(5, amount)
                        preparedStatement.setString(6, description)

                        if (nextServiceMileage != null) {
                            preparedStatement.setInt(7, nextServiceMileage)
                        } else {
                            preparedStatement.setNull(7, java.sql.Types.INTEGER)
                        }

                        val rowsAffected = preparedStatement.executeUpdate()
                        preparedStatement.close()

                        if (rowsAffected > 0) {
                            val updateMileageQuery = """
                            UPDATE Cars 
                            SET mileage = ? 
                            WHERE car_id = ? AND (mileage IS NULL OR mileage < ?)
                        """

                            val updateStatement = connect.prepareStatement(updateMileageQuery)
                            updateStatement.setInt(1, mileage)
                            updateStatement.setInt(2, carId)
                            updateStatement.setInt(3, mileage)

                            val updateRowsAffected = updateStatement.executeUpdate()
                            updateStatement.close()

                            connect.commit()

                            withContext(Dispatchers.Main) {
                                if (updateRowsAffected > 0) {
                                    showSuccessMessage("Обслуживание добавлено и пробег обновлен!")
                                } else {
                                    showSuccessMessage("Обслуживание добавлено! Пробег не изменился")
                                }
                            }
                        } else {
                            connect.rollback()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivityMaintenance, "Ошибка при добавлении обслуживания", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (ex: Exception) {
                        connect.rollback()
                        throw ex
                    } finally {
                        connect.autoCommit = true
                        connect.close()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityMaintenance, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error saving maintenance: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityMaintenance, "Ошибка сохранения: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSuccessMessage(message: String = "Обслуживание успешно добавлено!") {
        Toast.makeText(this@MainActivityMaintenance, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun getCurrentCarId(): Int {
        return sharedPreferences.getInt(PREF_CAR_ID, 1)
    }

    private fun validateInput(): Boolean {
        if (dateEditText.text.toString().trim().isEmpty()) {
            showError("Введите дату обслуживания", dateEditText)
            return false
        }

        if (mileageEditText.text.toString().trim().isEmpty()) {
            showError("Введите пробег автомобиля", mileageEditText)
            return false
        }

        if (selectedServiceTypeId == 0) {
            Toast.makeText(this, "Выберите тип обслуживания", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!isValidDate(dateEditText.text.toString().trim())) {
            showError("Неверный формат даты (дд.мм.гггг)", dateEditText)
            return false
        }

        val mileage = mileageEditText.text.toString().trim().toIntOrNull()
        if (mileage == null || mileage < 0) {
            showError("Пробег должен быть положительным числом", mileageEditText)
            return false
        }

        val nextMileage = nextServiceMileageEditText.text.toString().trim().toIntOrNull()
        if (nextMileage != null && nextMileage <= mileage) {
            showError("Следующий пробег должен быть больше текущего", nextServiceMileageEditText)
            return false
        }

        return true
    }

    private fun isValidDate(date: String): Boolean {
        return try {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            dateFormat.isLenient = false
            dateFormat.parse(date)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showError(message: String, editText: EditText? = null) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        editText?.requestFocus()
    }

}

data class ServiceType(
    val serviceTypeId: Int,
    val name: String,
    val categoryId: Int,
    val categoryName: String,
    val intervalKm: Int
)

data class ServiceCategory(
    val categoryId: Int,
    val name: String
)