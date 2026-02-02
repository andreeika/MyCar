package com.example.mycar

import SessionManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.*


class MainActivityRefueling : AppCompatActivity() {

    private lateinit var fuelSpinner: Spinner
    private lateinit var stationSpinner: Spinner
    private lateinit var mileageEditText: EditText
    private lateinit var volumeEditText: EditText
    private lateinit var priceEditText: EditText
    private lateinit var dateEditText: EditText
    private lateinit var fullTankCheckBox: CheckBox
    private lateinit var totalAmountTextView: TextView
    private lateinit var addRefuelingButton: Button
    private lateinit var imageViewCancel: ImageView
    private lateinit var imageViewService: ImageView
    private lateinit var imageViewStatistics: ImageView
    private lateinit var imageViewFuel: ImageView
    private lateinit var history: ImageView

    private val fuels = mutableListOf<Fuel>()
    private val stations = mutableListOf<GasStation>()
    private var selectedFuelId = 0
    private var selectedStationId = 0

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val dateFormatDisplay = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val dateFormatDatabase = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    companion object {
        private const val TAG = "RefuelingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_refueling)

        initializeViews()
        setupDate()
        setupDateInputMask()
        setAutoCalculateListener()
        setClickListeners()
        loadFuelData()
        loadStationData()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    private fun initializeViews() {
        fuelSpinner = findViewById(R.id.fuelSpinner)
        stationSpinner = findViewById(R.id.stationSpinner)
        mileageEditText = findViewById(R.id.mileageEditText)
        volumeEditText = findViewById(R.id.volumeEditText)
        priceEditText = findViewById(R.id.priceEditText)
        dateEditText = findViewById(R.id.dateEditText)
        fullTankCheckBox = findViewById(R.id.fullTankCheckBox)
        totalAmountTextView = findViewById(R.id.totalAmountTextView)
        addRefuelingButton = findViewById(R.id.addRefuelingButton)
        imageViewCancel = findViewById(R.id.imageViewCancel)
        imageViewService = findViewById(R.id.imageView5)
        imageViewStatistics = findViewById(R.id.imageView7)
        imageViewFuel = findViewById(R.id.imageView4)
        history = findViewById(R.id.imageView8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
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
                saveRefueling()
            }
        }

        imageViewCancel.setOnClickListener {
            finish()
        }

        imageViewService.setOnClickListener {
            val intent = Intent(this@MainActivityRefueling, MainActivityMaintenance::class.java)
            startActivity(intent)
        }

        imageViewStatistics.setOnClickListener {
            val intent = Intent(this@MainActivityRefueling, MainActivityStatistics::class.java)
            startActivity(intent)
        }

        imageViewFuel.setOnClickListener {
            Toast.makeText(this, "Вы уже в окне заправки", Toast.LENGTH_SHORT).show()
        }

        history.setOnClickListener {
            val intent = Intent(this@MainActivityRefueling, MainActivityHistoryRef::class.java)
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

        if (selectedStationId == 0) {
            Toast.makeText(this, "Выберите АЗС", Toast.LENGTH_SHORT).show()
            stationSpinner.requestFocus()
            return false
        }

        return true
    }

    private fun saveRefueling() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val sessionManager = SessionManager(this@MainActivityRefueling)
                val userId = sessionManager.getUserId()

                val carId = getCarId(userId)

                if (carId == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityRefueling, "Сначала добавьте автомобиль", Toast.LENGTH_SHORT).show()
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

                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    connect.autoCommit = false

                    try {
                        val insertRefuelingQuery = """
                        INSERT INTO refueling (car_id, fuel_id, station_id, date, mileage, volume, price_per_liter, total_amount, full_tank)
                        VALUES (?, ?, ?, CONVERT(DATETIME, ?, 112), ?, ?, ?, ?, ?)
                    """

                        val preparedStatement = connect.prepareStatement(insertRefuelingQuery)
                        preparedStatement.setInt(1, carId)
                        preparedStatement.setInt(2, selectedFuelId)
                        preparedStatement.setInt(3, selectedStationId)
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
                            updateStatement.setInt(2, carId)
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

    private suspend fun getCarId(userId: Int): Int {
        return try {
            val connectionHelper = ConnectionHelper()
            val connect = connectionHelper.connectionclass()

            if (connect != null) {
                val query = "SELECT TOP 1 car_id FROM Cars WHERE user_id = ?"
                val preparedStatement = connect.prepareStatement(query)
                preparedStatement.setInt(1, userId)
                val rs: ResultSet = preparedStatement.executeQuery()

                var carId = 0
                if (rs.next()) {
                    carId = rs.getInt("car_id")
                }

                rs.close()
                preparedStatement.close()
                connect.close()

                carId
            } else {
                0
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error getting car ID: ${ex.message}", ex)
            0
        }
    }

    private fun loadFuelData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val query = "SELECT fuel_id, name, marking FROM Fuel WHERE fuel_id IS NOT NULL"
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
                    val query = "SELECT station_id, name, address FROM GasStations WHERE station_id IS NOT NULL"
                    val st: Statement = connect.createStatement()
                    val rs: ResultSet = st.executeQuery(query)

                    stations.clear()
                    while (rs.next()) {
                        val stationId = rs.getInt("station_id")
                        val name = rs.getString("name") ?: ""
                        val address = rs.getString("address") ?: ""
                        stations.add(GasStation(stationId, name, address))
                    }

                    rs.close()
                    st.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        updateStationSpinner()
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

    private fun updateStationSpinner() {
        val stationNames = stations.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, stationNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        stationSpinner.adapter = adapter

        stationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (position >= 0) {
                    selectedStationId = stations[position].stationId
                    Log.d(TAG, "Selected station ID: $selectedStationId")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }


}