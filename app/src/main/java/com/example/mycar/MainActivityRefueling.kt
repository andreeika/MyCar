package com.example.mycar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.*

class MainActivityRefueling : AppCompatActivity() {

    private lateinit var fuelSpinner: Spinner
    private lateinit var stationAutoComplete: AutoCompleteTextView
    private lateinit var stationTextInputLayout: TextInputLayout
    private lateinit var mileageEditText: EditText
    private lateinit var volumeEditText: EditText
    private lateinit var priceEditText: EditText
    private lateinit var dateEditText: EditText
    private lateinit var fullTankCheckBox: CheckBox
    private lateinit var totalAmountTextView: TextView
    private lateinit var addRefuelingButton: Button
    private lateinit var imageViewCancel: ImageView

    private lateinit var history: ImageView

    private val fuels = mutableListOf<Fuel>()
    private val stations = mutableListOf<GasStation>()
    private var selectedFuelId = 0
    private var selectedStationId = 0

    private var isStationSelectedFromList = false
    private var isStationInDb = false
    private var manualStationName: String = ""

    private var currentCarId: Int = 0
    private var currentCarModel: String = ""

    private var refuelingId: Int = -1
    private var isEditMode: Boolean = false

    private lateinit var stationAdapter: ArrayAdapter<String>

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val dateFormatDisplay = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val dateFormatDatabase = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    companion object {
        private const val TAG = "RefuelingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_refueling)

        currentCarId = intent.getIntExtra("car_id", 0)
        currentCarModel = intent.getStringExtra("car_model") ?: ""

        refuelingId = intent.getIntExtra("refueling_id", -1)
        isEditMode = refuelingId != -1

        if (currentCarId == 0) {
            Toast.makeText(this, "Ошибка: автомобиль не выбран", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "Car ID: $currentCarId, Refueling ID: $refuelingId, Edit Mode: $isEditMode")

        initializeViews()
        setupDate()
        setupDateInputMask()
        setAutoCalculateListener()
        setClickListeners()
        loadFuelData()
        loadStationData()
        setupTextWatchers()

        if (isEditMode) {
            loadRefuelingData()
            supportActionBar?.title = "Редактирование: $currentCarModel"
            addRefuelingButton.text = "Сохранить изменения"
        } else {
            loadCurrentCarMileage()
            supportActionBar?.title = "Заправка: $currentCarModel"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    private fun initializeViews() {
        fuelSpinner = findViewById(R.id.fuelSpinner)
        stationAutoComplete = findViewById(R.id.stationAutoComplete)
        stationTextInputLayout = findViewById(R.id.stationTextInputLayout)
        mileageEditText = findViewById(R.id.mileageEditText)
        volumeEditText = findViewById(R.id.volumeEditText)
        priceEditText = findViewById(R.id.priceEditText)
        dateEditText = findViewById(R.id.dateEditText)
        fullTankCheckBox = findViewById(R.id.fullTankCheckBox)
        totalAmountTextView = findViewById(R.id.totalAmountTextView)
        addRefuelingButton = findViewById(R.id.addRefuelingButton)
        imageViewCancel = findViewById(R.id.imageViewCancel)
        history = findViewById(R.id.imageView8)

        stationAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        stationAutoComplete.setAdapter(stationAdapter)
        stationAutoComplete.threshold = 1

        stationAutoComplete.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus && stationAdapter.count > 0) {
                stationAutoComplete.showDropDown()
            }
        }

        stationAutoComplete.setOnClickListener {
            if (stationAdapter.count > 0) {
                stationAutoComplete.showDropDown()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }

    private fun loadRefuelingData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val query = """
                        SELECT r.*, f.name as fuel_name, f.marking, g.name as station_name
                        FROM refueling r
                        LEFT JOIN Fuel f ON r.fuel_id = f.fuel_id
                        LEFT JOIN GasStations g ON r.station_id = g.station_id
                        WHERE r.refueling_id = ? AND r.car_id = ?
                    """.trimIndent()

                    val stmt: PreparedStatement = connect.prepareStatement(query)
                    stmt.setInt(1, refuelingId)
                    stmt.setInt(2, currentCarId)
                    val rs: ResultSet = stmt.executeQuery()

                    if (rs.next()) {
                        withContext(Dispatchers.Main) {
                            val date = rs.getDate("date")
                            if (date != null) {
                                dateEditText.setText(dateFormatDisplay.format(date))
                            }

                            mileageEditText.setText(rs.getDouble("mileage").toInt().toString())
                            volumeEditText.setText(rs.getDouble("volume").toString())
                            priceEditText.setText(rs.getDouble("price_per_liter").toString())
                            fullTankCheckBox.isChecked = rs.getBoolean("full_tank")

                            val fuelId = rs.getInt("fuel_id")
                            val fuelIndex = fuels.indexOfFirst { it.fuelId == fuelId }
                            if (fuelIndex >= 0) {
                                fuelSpinner.setSelection(fuelIndex)
                                selectedFuelId = fuelId
                            }

                            val stationName = rs.getString("station_name") ?: ""
                            stationAutoComplete.setText(stationName, false)
                            selectedStationId = rs.getInt("station_id")
                            isStationInDb = true

                            updateTotalAmount()

                            Log.d(TAG, "Loaded refueling data: ID=$refuelingId")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivityRefueling, "Запись не найдена", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }

                    rs.close()
                    stmt.close()
                    connect.close()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading refueling: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityRefueling, "Ошибка загрузки: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun loadCurrentCarMileage() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val query = "SELECT mileage FROM Cars WHERE car_id = ?"
                    val preparedStatement = connect.prepareStatement(query)
                    preparedStatement.setInt(1, currentCarId)
                    val rs: ResultSet = preparedStatement.executeQuery()

                    if (rs.next()) {
                        val currentMileage = rs.getInt("mileage")
                        withContext(Dispatchers.Main) {
                            if (currentMileage > 0) {
                                mileageEditText.setText(currentMileage.toString())
                                mileageEditText.isEnabled = true
                            } else {
                                mileageEditText.hint = "Введите пробег"
                                mileageEditText.isEnabled = true
                            }
                        }
                    }

                    rs.close()
                    preparedStatement.close()
                    connect.close()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading car mileage: ${ex.message}", ex)
            }
        }
    }

    private fun setupTextWatchers() {
        stationAutoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isStationSelectedFromList) {
                    val stationText = s?.toString()?.trim() ?: ""
                    if (stationText.isNotEmpty()) {
                        checkStationInDatabase(stationText)
                    } else {
                        selectedStationId = 0
                        isStationInDb = false
                        manualStationName = ""
                    }
                }
                isStationSelectedFromList = false
            }
        })

        stationAutoComplete.setOnItemClickListener { parent, view, position, id ->
            isStationSelectedFromList = true
            val selectedStationName = parent.getItemAtPosition(position) as String
            val selectedStation = stations.find { it.name == selectedStationName }
            if (selectedStation != null) {
                selectedStationId = selectedStation.stationId
                isStationInDb = true
                manualStationName = ""
                Log.d(TAG, "Selected station from DB: $selectedStationName (ID: $selectedStationId)")
            }
        }
    }

    private fun checkStationInDatabase(stationName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val query = "SELECT station_id FROM GasStations WHERE name = ?"
                    val preparedStatement = connect.prepareStatement(query)
                    preparedStatement.setString(1, stationName)
                    val resultSet = preparedStatement.executeQuery()

                    if (resultSet.next()) {
                        selectedStationId = resultSet.getInt("station_id")
                        isStationInDb = true
                        manualStationName = ""
                        Log.d(TAG, "Station found in DB: $stationName (ID: $selectedStationId)")
                    } else {
                        selectedStationId = 0
                        isStationInDb = false
                        manualStationName = stationName
                        Log.d(TAG, "Station not in DB, will be added: $stationName")
                    }

                    resultSet.close()
                    preparedStatement.close()
                    connect.close()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error checking station: ${ex.message}", ex)
            }
        }
    }

    private fun setupDate() {
        val currentDate = Date()
        dateEditText.setText(dateFormatDisplay.format(currentDate))
    }

    private fun setupDateInputMask() {
        dateEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString()
                if (text.length == 2 && before == 0) {
                    dateEditText.setText("$text.")
                    dateEditText.setSelection(3)
                } else if (text.length == 5 && before == 0) {
                    dateEditText.setText("$text.")
                    dateEditText.setSelection(6)
                }
            }
            override fun afterTextChanged(s: Editable?) {
                validateDate()
            }
        })
    }

    private fun validateDate(): Boolean {
        val dateText = dateEditText.text.toString().trim()

        if (dateText.isEmpty()) {
            dateEditText.error = "Введите дату"
            return false
        }

        if (dateText.length != 10) {
            dateEditText.error = "Формат: дд.мм.гггг"
            return false
        }

        try {
            if (!dateText.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) {
                dateEditText.error = "Неверный формат даты"
                return false
            }

            val date = dateFormatDisplay.parse(dateText)
            if (date == null) {
                dateEditText.error = "Неверная дата"
                return false
            }

            if (date.after(Date())) {
                dateEditText.error = "Дата не может быть в будущем"
                return false
            }

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.YEAR, -10)
            if (date.before(calendar.time)) {
                dateEditText.error = "Дата слишком старая"
                return false
            }

            dateEditText.error = null
            return true

        } catch (e: Exception) {
            dateEditText.error = "Ошибка в дате"
            return false
        }
    }

    private fun setAutoCalculateListener() {
        val calculateListener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateTotalAmount()
            }
        }

        volumeEditText.addTextChangedListener(calculateListener)
        priceEditText.addTextChangedListener(calculateListener)
    }

    private fun updateTotalAmount() {
        try {
            val volume = volumeEditText.text.toString().toDoubleOrNull() ?: 0.0
            val price = priceEditText.text.toString().toDoubleOrNull() ?: 0.0
            val total = volume * price
            totalAmountTextView.text = "%.2f руб".format(total)
        } catch (e: Exception) {
            totalAmountTextView.text = "0.00 руб"
        }
    }

    private fun setClickListeners() {
        addRefuelingButton.setOnClickListener {
            if (validateInput()) {
                if (isEditMode) {
                    updateRefueling()
                } else {
                    saveRefueling()
                }
            }
        }

        imageViewCancel.setOnClickListener {
            finish()
        }


        history.setOnClickListener {
            val intent = Intent(this@MainActivityRefueling, MainActivityHistoryRef::class.java)
            intent.putExtra("car_id", currentCarId)
            intent.putExtra("car_model", currentCarModel)
            startActivity(intent)
        }
    }

    private fun validateInput(): Boolean {
        if (!validateDate()) {
            dateEditText.requestFocus()
            return false
        }

        if (mileageEditText.text.toString().trim().isEmpty()) {
            mileageEditText.error = "Введите пробег"
            mileageEditText.requestFocus()
            return false
        }

        val mileage = mileageEditText.text.toString().toDoubleOrNull()
        if (mileage == null || mileage <= 0) {
            mileageEditText.error = "Пробег должен быть положительным числом"
            mileageEditText.requestFocus()
            return false
        }

        if (volumeEditText.text.toString().trim().isEmpty()) {
            volumeEditText.error = "Введите количество литров"
            volumeEditText.requestFocus()
            return false
        }

        val volume = volumeEditText.text.toString().toDoubleOrNull()
        if (volume == null || volume <= 0) {
            volumeEditText.error = "Количество должно быть положительным числом"
            volumeEditText.requestFocus()
            return false
        }

        if (priceEditText.text.toString().trim().isEmpty()) {
            priceEditText.error = "Введите цену за литр"
            priceEditText.requestFocus()
            return false
        }

        val price = priceEditText.text.toString().toDoubleOrNull()
        if (price == null || price <= 0) {
            priceEditText.error = "Цена должна быть положительным числом"
            priceEditText.requestFocus()
            return false
        }

        if (selectedFuelId == 0) {
            Toast.makeText(this, "Выберите вид топлива", Toast.LENGTH_SHORT).show()
            fuelSpinner.requestFocus()
            return false
        }

        val stationText = stationAutoComplete.text.toString().trim()
        if (stationText.isEmpty()) {
            Toast.makeText(this, "Введите или выберите АЗС", Toast.LENGTH_SHORT).show()
            stationAutoComplete.requestFocus()
            return false
        }

        return true
    }

    private fun updateRefueling() {
        addRefuelingButton.isEnabled = false

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val sessionManager = SessionManager(this@MainActivityRefueling)
                val userId = sessionManager.getUserId()

                if (!isCarBelongsToUser(currentCarId, userId)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityRefueling, "Ошибка: доступ запрещен", Toast.LENGTH_SHORT).show()
                        addRefuelingButton.isEnabled = true
                    }
                    return@launch
                }

                val mileage = mileageEditText.text.toString().toDouble()
                val volume = volumeEditText.text.toString().toDouble()
                val price = priceEditText.text.toString().toDouble()
                val total = volume * price
                val fullTank = fullTankCheckBox.isChecked

                val dateText = dateEditText.text.toString().trim()
                val date = dateFormatDisplay.parse(dateText)
                if (date == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityRefueling, "Ошибка в дате", Toast.LENGTH_SHORT).show()
                        addRefuelingButton.isEnabled = true
                    }
                    return@launch
                }

                val calendar = Calendar.getInstance()
                calendar.time = date
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val sqlDate = String.format(Locale.US, "%04d%02d%02d", year, month, day)

                val stationText = stationAutoComplete.text.toString().trim()
                var finalStationId = selectedStationId

                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    connect.autoCommit = false

                    try {
                        if (!isStationInDb) {
                            val insertStationQuery = "INSERT INTO GasStations (name) OUTPUT INSERTED.station_id VALUES (?)"
                            val stationStatement = connect.prepareStatement(insertStationQuery)
                            stationStatement.setString(1, manualStationName.ifEmpty { stationText })
                            val stationResult = stationStatement.executeQuery()
                            if (stationResult.next()) {
                                finalStationId = stationResult.getInt(1)
                            }
                            stationResult.close()
                            stationStatement.close()
                        }

                        val updateQuery = """
                            UPDATE refueling 
                            SET fuel_id = ?, station_id = ?, date = CONVERT(DATETIME, ?, 112),
                                mileage = ?, volume = ?, price_per_liter = ?, 
                                total_amount = ?, full_tank = ?
                            WHERE refueling_id = ? AND car_id = ?
                        """.trimIndent()

                        val preparedStatement = connect.prepareStatement(updateQuery)
                        preparedStatement.setInt(1, selectedFuelId)
                        preparedStatement.setInt(2, finalStationId)
                        preparedStatement.setString(3, sqlDate)
                        preparedStatement.setDouble(4, mileage)
                        preparedStatement.setDouble(5, volume)
                        preparedStatement.setDouble(6, price)
                        preparedStatement.setDouble(7, total)
                        preparedStatement.setBoolean(8, fullTank)
                        preparedStatement.setInt(9, refuelingId)
                        preparedStatement.setInt(10, currentCarId)

                        val rowsAffected = preparedStatement.executeUpdate()
                        preparedStatement.close()

                        if (rowsAffected > 0) {
                            val updateMileageQuery = """
                                UPDATE Cars 
                                SET mileage = ? 
                                WHERE car_id = ? AND (mileage IS NULL OR mileage < ?)
                            """
                            val updateStatement = connect.prepareStatement(updateMileageQuery)
                            updateStatement.setDouble(1, mileage)
                            updateStatement.setInt(2, currentCarId)
                            updateStatement.setDouble(3, mileage)
                            updateStatement.executeUpdate()
                            updateStatement.close()

                            connect.commit()

                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivityRefueling, "Запись обновлена!", Toast.LENGTH_SHORT).show()
                                setResult(RESULT_OK)
                                finish()
                            }
                        } else {
                            connect.rollback()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivityRefueling, "Ошибка: запись не найдена", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@MainActivityRefueling, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                        addRefuelingButton.isEnabled = true
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error updating refueling: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityRefueling, "Ошибка: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
                    addRefuelingButton.isEnabled = true
                }
            }
        }
    }

    private fun saveRefueling() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val sessionManager = SessionManager(this@MainActivityRefueling)
                val userId = sessionManager.getUserId()

                if (!isCarBelongsToUser(currentCarId, userId)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityRefueling, "Ошибка: автомобиль не найден", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val mileage = mileageEditText.text.toString().toDouble()
                val volume = volumeEditText.text.toString().toDouble()
                val price = priceEditText.text.toString().toDouble()
                val total = volume * price
                val fullTank = fullTankCheckBox.isChecked

                val dateText = dateEditText.text.toString().trim()
                Log.d(TAG, "Input date: $dateText")

                val date = dateFormatDisplay.parse(dateText)
                if (date == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityRefueling, "Ошибка в дате", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val calendar = Calendar.getInstance()
                calendar.time = date
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val sqlDate = String.format(Locale.US, "%04d%02d%02d", year, month, day)
                Log.d(TAG, "SQL date format: $sqlDate")

                val stationText = stationAutoComplete.text.toString().trim()

                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    connect.autoCommit = false

                    try {
                        var finalStationId = selectedStationId

                        if (!isStationInDb) {
                            val insertStationQuery = "INSERT INTO GasStations (name) OUTPUT INSERTED.station_id VALUES (?)"
                            val stationStatement = connect.prepareStatement(insertStationQuery)
                            stationStatement.setString(1, manualStationName.ifEmpty { stationText })
                            val stationResult = stationStatement.executeQuery()
                            if (stationResult.next()) {
                                finalStationId = stationResult.getInt(1)
                                Log.d(TAG, "Inserted new station: ${manualStationName.ifEmpty { stationText }} with ID: $finalStationId")
                            }
                            stationResult.close()
                            stationStatement.close()
                        } else {
                            finalStationId = selectedStationId
                        }

                        val insertRefuelingQuery = """
                        INSERT INTO refueling (car_id, fuel_id, station_id, date, mileage, volume, price_per_liter, total_amount, full_tank)
                        VALUES (?, ?, ?, CONVERT(DATETIME, ?, 112), ?, ?, ?, ?, ?)
                    """

                        val preparedStatement = connect.prepareStatement(insertRefuelingQuery)
                        preparedStatement.setInt(1, currentCarId)
                        preparedStatement.setInt(2, selectedFuelId)
                        preparedStatement.setInt(3, finalStationId)
                        preparedStatement.setString(4, sqlDate)
                        preparedStatement.setDouble(5, mileage)
                        preparedStatement.setDouble(6, volume)
                        preparedStatement.setDouble(7, price)
                        preparedStatement.setDouble(8, total)
                        preparedStatement.setBoolean(9, fullTank)

                        val rowsAffected = preparedStatement.executeUpdate()
                        preparedStatement.close()

                        if (rowsAffected > 0) {
                            val updateMileageQuery = """
                            UPDATE Cars 
                            SET mileage = ? 
                            WHERE car_id = ? AND (mileage IS NULL OR mileage < ?)
                        """

                            val updateStatement = connect.prepareStatement(updateMileageQuery)
                            updateStatement.setDouble(1, mileage)
                            updateStatement.setInt(2, currentCarId)
                            updateStatement.setDouble(3, mileage)

                            val updateRowsAffected = updateStatement.executeUpdate()
                            updateStatement.close()

                            connect.commit()

                            withContext(Dispatchers.Main) {
                                if (updateRowsAffected > 0) {
                                    Toast.makeText(this@MainActivityRefueling, "Заправка добавлена и пробег обновлен!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@MainActivityRefueling, "Заправка добавлена! Пробег не изменился (уже больше текущего)", Toast.LENGTH_SHORT).show()
                                }
                                finish()
                            }
                        } else {
                            connect.rollback()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivityRefueling, "Ошибка при добавлении заправки", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@MainActivityRefueling, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error saving refueling: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityRefueling, "Ошибка сохранения: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun isCarBelongsToUser(carId: Int, userId: Int): Boolean {
        return try {
            val connectionHelper = ConnectionHelper()
            val connect = connectionHelper.connectionclass()

            if (connect != null) {
                val query = "SELECT COUNT(*) as count FROM Cars WHERE car_id = ? AND user_id = ?"
                val preparedStatement = connect.prepareStatement(query)
                preparedStatement.setInt(1, carId)
                preparedStatement.setInt(2, userId)
                val rs: ResultSet = preparedStatement.executeQuery()

                var count = 0
                if (rs.next()) {
                    count = rs.getInt("count")
                }

                rs.close()
                preparedStatement.close()
                connect.close()

                count > 0
            } else {
                false
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error checking car ownership: ${ex.message}", ex)
            false
        }
    }

    private fun loadFuelData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val query = "SELECT fuel_id, name, marking FROM Fuel WHERE fuel_id IS NOT NULL ORDER BY name"
                    val st: Statement = connect.createStatement()
                    val rs: ResultSet = st.executeQuery(query)

                    fuels.clear()
                    while (rs.next()) {
                        val fuelId = rs.getInt("fuel_id")
                        val name = rs.getString("name") ?: ""
                        val marking = rs.getString("marking") ?: ""
                        fuels.add(Fuel(fuelId, name, marking))
                    }

                    rs.close()
                    st.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        updateFuelSpinner()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading fuel data: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityRefueling, "Ошибка загрузки видов топлива", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateFuelSpinner() {
        val fuelNames = fuels.map { "${it.name} (${it.marking})" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fuelNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fuelSpinner.adapter = adapter

        fuelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (position >= 0) {
                    selectedFuelId = fuels[position].fuelId
                    Log.d(TAG, "Selected fuel ID: $selectedFuelId")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun loadStationData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val query = "SELECT station_id, name FROM GasStations WHERE station_id IS NOT NULL ORDER BY name"
                    val st: Statement = connect.createStatement()
                    val rs: ResultSet = st.executeQuery(query)

                    stations.clear()
                    val stationNames = mutableListOf<String>()

                    while (rs.next()) {
                        val stationId = rs.getInt("station_id")
                        val name = rs.getString("name") ?: ""
                        stations.add(GasStation(stationId, name))
                        stationNames.add(name)
                    }

                    rs.close()
                    st.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        updateStationAutoComplete(stationNames)
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading station data: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityRefueling, "Ошибка загрузки АЗС", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateStationAutoComplete(stationNames: List<String>) {
        stationAdapter.clear()
        stationAdapter.addAll(stationNames)
        stationAdapter.notifyDataSetChanged()
        stationTextInputLayout.hint = "Выберите или введите АЗС"
    }
}

data class Fuel(
    val fuelId: Int,
    val name: String,
    val marking: String
)

data class GasStation(
    val stationId: Int,
    val name: String
)