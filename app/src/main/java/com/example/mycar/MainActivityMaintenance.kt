package com.example.mycar

import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivityMaintenance : AppCompatActivity() {

    private lateinit var serviceTypeSpinner: Spinner
    private lateinit var dateEditText: TextView
    private lateinit var mileageEditText: EditText
    private lateinit var amountEditText: EditText
    private lateinit var nextServiceMileageEditText: EditText
    private lateinit var nextServiceDateEditText: TextView
    private lateinit var descriptionEditText: EditText
    private lateinit var addMaintenanceButton: Button
    private lateinit var cancelImageView: ImageView
    private lateinit var progressOverlay: android.widget.FrameLayout


    private lateinit var history: ImageView
    private val serviceTypes = mutableListOf<ServiceType>()
    private val categories = mutableListOf<ServiceCategory>()
    private var selectedServiceTypeId: Int = 0

    private var currentCarId: Int = 0
    private var currentMaintenanceId: Int? = null
    private var isEditMode: Boolean = false

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val TAG = "MainActivityMaintenance"
        private const val PREF_CAR_ID = "current_car_id"
        private const val AVG_YEARLY_MILEAGE = 15000
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

        currentCarId = intent.getIntExtra("car_id", 0)
        currentMaintenanceId = if (intent.hasExtra("maintenance_id")) {
            intent.getIntExtra("maintenance_id", 0)
        } else null

        isEditMode = intent.getStringExtra("mode") == "edit" || currentMaintenanceId != null

        if (currentCarId == 0) {
            Toast.makeText(this, "Ошибка: автомобиль не выбран", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (isEditMode && currentMaintenanceId != null) {
            addMaintenanceButton.text = "Обновить"
            loadMaintenanceData()
        } else {
            addMaintenanceButton.text = "Добавить"
            loadCurrentCarMileage()
        }
    }

    private fun initializeViews() {
        serviceTypeSpinner = findViewById(R.id.serviceTypeSpinner)
        dateEditText = findViewById(R.id.dateEditText)
        mileageEditText = findViewById(R.id.mileageEditText)
        amountEditText = findViewById(R.id.amountEditText)
        nextServiceMileageEditText = findViewById(R.id.nextServiceMileageEditText)
        nextServiceDateEditText = findViewById(R.id.nextServiceDateEditText)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        addMaintenanceButton = findViewById(R.id.addMaintenanceButton)
        cancelImageView = findViewById(R.id.imageViewCancel)
        history = findViewById(R.id.imageView12)
        progressOverlay = findViewById(R.id.progressOverlay)

    }

    private fun setupStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }

    private fun setCurrentDate() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        if (!isEditMode) {
            dateEditText.setText(currentDate)
        }
    }

    private fun loadMaintenanceData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arr = ApiClient.getMaintenance(currentCarId)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getInt("maintenance_id") == currentMaintenanceId) {
                        withContext(Dispatchers.Main) {
                            dateEditText.setText(obj.optString("date", ""))
                            mileageEditText.setText(obj.optInt("mileage", 0).toString())
                            amountEditText.setText(String.format(Locale.getDefault(), "%.2f", obj.optDouble("total_amount", 0.0)))
                            descriptionEditText.setText(obj.optString("description", ""))
                            val nsm = obj.optInt("next_service_mileage", 0)
                            if (nsm > 0) nextServiceMileageEditText.setText(nsm.toString())
                            val nsd = obj.optString("next_service_date", "")
                            if (nsd.isNotEmpty()) nextServiceDateEditText.setText(nsd)
                            val stId = obj.optInt("service_type_id", 0)
                            selectedServiceTypeId = stId
                            selectServiceTypeInSpinner(stId)
                        }
                        return@launch
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading maintenance data: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityMaintenance, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun selectServiceTypeInSpinner(serviceTypeId: Int) {
        val service = serviceTypes.find { it.serviceTypeId == serviceTypeId }
        if (service != null) {
            var position = 0
            var found = false

            for (category in categories) {
                if (found) break
                position++

                val categoryServices = serviceTypes.filter { it.categoryId == category.categoryId }
                for ((index, s) in categoryServices.withIndex()) {
                    if (s.serviceTypeId == serviceTypeId) {
                        found = true
                        break
                    }
                    position++
                }
            }

            if (found && position < serviceTypeSpinner.adapter.count) {
                serviceTypeSpinner.setSelection(position)
            }
        }
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
            if (isEditMode && currentMaintenanceId != null) {
                updateMaintenanceInDatabase()
            } else {
                saveMaintenanceToDatabase()
            }
        }

        mileageEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                calculateNextServiceInfo()
            }
        }

        dateEditText.setOnClickListener {
            showDatePicker(dateEditText)
        }

        nextServiceMileageEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                calculateNextServiceDateFromMileage()
            }
        }

        nextServiceDateEditText.setOnClickListener {
            showDatePicker(nextServiceDateEditText)
        }


        history.setOnClickListener {
            val intent = Intent(this@MainActivityMaintenance, MainActivityHistoryMainte::class.java)
            startActivity(intent)
        }
    }

    private fun handleServiceTypeSelection(position: Int) {
        var serviceIndex = -1
        var itemCount = 0

        for ((catIndex, category) in categories.withIndex()) {
            if (position == itemCount) {
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
            calculateNextServiceInfo()
        }
    }

    private fun showDatePicker(editText: TextView) {
        val calendar = Calendar.getInstance()

        try {
            val currentDate = editText.text.toString().trim()
            if (currentDate.isNotEmpty()) {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val date = dateFormat.parse(currentDate)
                if (date != null) {
                    calendar.time = date
                }
            }
        } catch (e: Exception) {
        }

        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, day)
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            editText.setText(dateFormat.format(selectedDate.time))

            if (editText.id == R.id.nextServiceDateEditText) {
                calculateNextServiceMileageFromDate()
            }
        }

        DatePickerDialog(
            this,
            dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun calculateNextServiceInfo() {
        if (selectedServiceTypeId == 0 || mileageEditText.text.toString().trim().isEmpty()) return

        try {
            val currentMileage = mileageEditText.text.toString().trim().toInt()
            val selectedService = serviceTypes.find { it.serviceTypeId == selectedServiceTypeId }

            selectedService?.let { service ->
                if (service.intervalKm > 0) {
                    val nextServiceMileage = currentMileage + service.intervalKm
                    nextServiceMileageEditText.setText(nextServiceMileage.toString())

                    calculateNextServiceDate(nextServiceMileage, currentMileage)
                } else {
                    nextServiceMileageEditText.text.clear()

                }
            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Error calculating next service mileage: ${e.message}")
        }
    }

    private fun calculateNextServiceDate(nextServiceMileage: Int, currentMileage: Int) {
        try {
            val mileageDifference = nextServiceMileage - currentMileage
            val monthsUntilNextService = (mileageDifference.toFloat() / AVG_YEARLY_MILEAGE * 12).toInt()

            if (monthsUntilNextService > 0) {
                val currentDateText = dateEditText.text.toString().trim()
                if (currentDateText.isNotEmpty()) {
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    val currentDate = dateFormat.parse(currentDateText)

                    if (currentDate != null) {
                        val calendar = Calendar.getInstance()
                        calendar.time = currentDate
                        calendar.add(Calendar.MONTH, monthsUntilNextService)

                        val nextServiceDate = dateFormat.format(calendar.time)
                        nextServiceDateEditText.setText(nextServiceDate)
                        return
                    }
                }
            }


        } catch (e: Exception) {
            Log.e(TAG, "Error calculating next service date: ${e.message}")

        }
    }

    private fun calculateNextServiceDateFromMileage() {
        try {
            val nextServiceMileage = nextServiceMileageEditText.text.toString().trim().toIntOrNull()
            val currentMileage = mileageEditText.text.toString().trim().toIntOrNull()

            if (nextServiceMileage != null && currentMileage != null && nextServiceMileage > currentMileage) {
                calculateNextServiceDate(nextServiceMileage, currentMileage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating date from mileage: ${e.message}")
        }
    }

    private fun calculateNextServiceMileageFromDate() {
        try {
            val nextServiceDateText = nextServiceDateEditText.text.toString().trim()
            val currentDateText = dateEditText.text.toString().trim()
            val currentMileage = mileageEditText.text.toString().trim().toIntOrNull()

            if (nextServiceDateText.isNotEmpty() && currentDateText.isNotEmpty() && currentMileage != null) {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val currentDate = dateFormat.parse(currentDateText)
                val nextDate = dateFormat.parse(nextServiceDateText)

                if (currentDate != null && nextDate != null) {
                    val calendarCurrent = Calendar.getInstance()
                    val calendarNext = Calendar.getInstance()
                    calendarCurrent.time = currentDate
                    calendarNext.time = nextDate

                    val monthsDifference = (calendarNext.get(Calendar.YEAR) - calendarCurrent.get(Calendar.YEAR)) * 12 +
                            (calendarNext.get(Calendar.MONTH) - calendarCurrent.get(Calendar.MONTH))

                    if (monthsDifference > 0) {
                        val mileageToAdd = (monthsDifference.toFloat() / 12 * AVG_YEARLY_MILEAGE).toInt()
                        val nextServiceMileage = currentMileage + mileageToAdd
                        nextServiceMileageEditText.setText(nextServiceMileage.toString())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating mileage from date: ${e.message}")
        }
    }

    private fun loadCurrentCarMileage() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = SessionManager(this@MainActivityMaintenance).getUserId()
                val arr = ApiClient.getCars(userId)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getInt("car_id") == currentCarId) {
                        val mileage = obj.optInt("mileage", 0)
                        withContext(Dispatchers.Main) {
                            if (mileage > 0) mileageEditText.setText(mileage.toString())
                        }
                        break
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading mileage: ${ex.message}", ex)
            }
        }
    }

    private fun loadServiceTypesWithCategories() {
        progressOverlay.visibility = android.view.View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arr = ApiClient.getServiceTypes()
                serviceTypes.clear()
                categories.clear()
                val catMap = mutableMapOf<Int, String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val catId   = obj.getInt("category_id")
                    val catName = obj.optString("category", "")
                    catMap[catId] = catName
                    serviceTypes.add(ServiceType(
                        obj.getInt("service_type_id"), obj.getString("name"),
                        catId, catName, obj.optInt("interval_km", 0)
                    ))
                }
                catMap.forEach { (id, name) -> categories.add(ServiceCategory(id, name)) }
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    updateServiceTypeSpinner()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading service types: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    Toast.makeText(this@MainActivityMaintenance, "Ошибка загрузки типов обслуживания", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateServiceTypeSpinner() {
        val serviceTypeItems = mutableListOf<String>()

        val servicesByCategory = serviceTypes.groupBy { it.categoryId }

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

        if (isEditMode && selectedServiceTypeId != 0) {
            selectServiceTypeInSpinner(selectedServiceTypeId)
        }
    }

    private fun saveMaintenanceToDatabase() {
        if (!validateInput()) return
        addMaintenanceButton.isEnabled = false
        progressOverlay.visibility = android.view.View.VISIBLE
        val date = dateEditText.text.toString().trim()
        val mileage = mileageEditText.text.toString().trim().toInt()
        val amount = amountEditText.text.toString().trim().toDoubleOrNull() ?: 0.0
        val nextServiceMileage = nextServiceMileageEditText.text.toString().trim().toIntOrNull()
        val nextServiceDate = nextServiceDateEditText.text.toString().trim().ifEmpty { null }
        val description = descriptionEditText.text.toString().trim().ifEmpty { null }
        val carId = getCurrentCarId()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.addMaintenance(
                    carId, selectedServiceTypeId, date, mileage,
                    amount, description, nextServiceMileage, nextServiceDate
                )
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    showSuccessMessage("Обслуживание успешно добавлено!")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error saving maintenance: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    addMaintenanceButton.isEnabled = true
                    Toast.makeText(this@MainActivityMaintenance, "Ошибка сохранения: ${friendlyError(ex)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateMaintenanceInDatabase() {
        if (!validateInput()) return
        addMaintenanceButton.isEnabled = false
        progressOverlay.visibility = android.view.View.VISIBLE
        val date = dateEditText.text.toString().trim()
        val mileage = mileageEditText.text.toString().trim().toInt()
        val amount = amountEditText.text.toString().trim().toDoubleOrNull() ?: 0.0
        val nextServiceMileage = nextServiceMileageEditText.text.toString().trim().toIntOrNull()
        val nextServiceDate = nextServiceDateEditText.text.toString().trim().ifEmpty { null }
        val description = descriptionEditText.text.toString().trim().ifEmpty { null }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.updateMaintenance(
                    currentCarId, currentMaintenanceId!!, selectedServiceTypeId, date, mileage,
                    amount, description, nextServiceMileage, nextServiceDate
                )
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    Toast.makeText(this@MainActivityMaintenance, "Обслуживание успешно обновлено", Toast.LENGTH_SHORT).show()
                    val resultIntent = Intent()
                    resultIntent.putExtra("maintenance_id", currentMaintenanceId)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error updating maintenance: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    addMaintenanceButton.isEnabled = true
                    Toast.makeText(this@MainActivityMaintenance, "Ошибка обновления: ${friendlyError(ex)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSuccessMessage(message: String = "Обслуживание успешно добавлено!") {
        Toast.makeText(this@MainActivityMaintenance, message, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun getCurrentCarId(): Int {
        return sharedPreferences.getInt(PREF_CAR_ID, 1)
    }

    private fun validateInput(): Boolean {
        if (dateEditText.text.toString().trim().isEmpty()) {
            dateEditText.error = "Введите дату обслуживания"
            dateEditText.requestFocus()
            return false
        }

        if (mileageEditText.text.toString().trim().isEmpty()) {
            mileageEditText.error = "Введите пробег автомобиля"
            mileageEditText.requestFocus()
            return false
        }

        if (selectedServiceTypeId == 0) {
            Toast.makeText(this, "Выберите тип обслуживания", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!isValidDate(dateEditText.text.toString().trim())) {
            dateEditText.error = "Неверный формат даты (дд.мм.гггг)"
            dateEditText.requestFocus()
            return false
        }

        val mileage = mileageEditText.text.toString().trim().toIntOrNull()
        if (mileage == null || mileage < 0) {
            mileageEditText.error = "Пробег должен быть положительным числом"
            mileageEditText.requestFocus()
            return false
        }

        val nextMileage = nextServiceMileageEditText.text.toString().trim().toIntOrNull()
        if (nextMileage != null && nextMileage <= mileage) {
            nextServiceMileageEditText.error = "Следующий пробег должен быть больше текущего"
            nextServiceMileageEditText.requestFocus()
            return false
        }

        val nextServiceDate = nextServiceDateEditText.text.toString().trim()
        if (nextServiceDate.isNotEmpty() && !isValidDate(nextServiceDate)) {
            nextServiceDateEditText.error = "Неверный формат даты следующего обслуживания"
            nextServiceDateEditText.requestFocus()
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