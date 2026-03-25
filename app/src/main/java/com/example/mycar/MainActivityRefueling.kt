package com.example.mycar

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivityRefueling : BaseActivity() {

    private lateinit var fuelSpinner: Spinner
    private lateinit var stationAutoComplete: AutoCompleteTextView
    private lateinit var stationTextInputLayout: TextInputLayout
    private lateinit var mileageEditText: EditText
    private lateinit var volumeEditText: EditText
    private lateinit var priceEditText: EditText
    private lateinit var dateEditText: TextView
    private lateinit var fullTankCheckBox: CheckBox
    private lateinit var totalAmountTextView: TextView
    private lateinit var addRefuelingButton: Button
    private lateinit var imageViewCancel: ImageView
    private lateinit var progressOverlay: android.widget.FrameLayout

    private lateinit var history: ImageView
    private lateinit var imageViewScanQr: ImageView

    private val qrLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val qrRaw = result.data?.getStringExtra(QrScannerActivity.RESULT_QR) ?: return@registerForActivityResult
            handleQrResult(qrRaw)
        }
    }

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
        progressOverlay = findViewById(R.id.progressOverlay)
        imageViewScanQr = findViewById(R.id.imageViewScanQr)

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


    }

    private fun loadRefuelingData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val arr = ApiClient.getRefueling(currentCarId)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getInt("refueling_id") == refuelingId) {
                        withContext(Dispatchers.Main) {
                            val dateStr = obj.optString("date", "")
                            if (dateStr.isNotEmpty()) {
                                try {
                                    val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateStr)
                                    if (parsed != null) dateEditText.setText(dateFormatDisplay.format(parsed))
                                } catch (_: Exception) { dateEditText.setText(dateStr) }
                            }
                            mileageEditText.setText(obj.optDouble("mileage", 0.0).toInt().toString())
                            volumeEditText.setText(obj.optDouble("volume", 0.0).toString())
                            priceEditText.setText(obj.optDouble("price_per_liter", 0.0).toString())
                            fullTankCheckBox.isChecked = obj.optBoolean("full_tank", false)

                            val fuelName = obj.optString("fuel", "")
                            val fuelIndex = fuels.indexOfFirst { it.name == fuelName }
                            if (fuelIndex >= 0) { fuelSpinner.setSelection(fuelIndex); selectedFuelId = fuels[fuelIndex].fuelId }

                            val stationName = obj.optString("station", "")
                            stationAutoComplete.setText(stationName, false)
                            val st = stations.find { it.name == stationName }
                            if (st != null) { selectedStationId = st.stationId; isStationInDb = true }

                            updateTotalAmount()
                        }
                        return@launch
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityRefueling, "Запись не найдена", Toast.LENGTH_SHORT).show(); finish()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading refueling: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityRefueling, "Ошибка загрузки", Toast.LENGTH_SHORT).show(); finish()
                }
            }
        }
    }

    private fun loadCurrentCarMileage() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val userId = SessionManager(this@MainActivityRefueling).getUserId()
                val arr = ApiClient.getCars(userId)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getInt("car_id") == currentCarId) {
                        val mileage = obj.optDouble("mileage", 0.0).toInt()
                        withContext(Dispatchers.Main) {
                            if (mileage > 0) mileageEditText.setText(mileage.toString())
                            mileageEditText.isEnabled = true
                        }
                        break
                    }
                }
            } catch (ex: Exception) { Log.e(TAG, "Error loading mileage: ${ex.message}", ex) }
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
                val arr = ApiClient.getStations()
                val found = (0 until arr.length()).map { arr.getJSONObject(it) }
                    .find { it.getString("name").equals(stationName, ignoreCase = true) }
                if (found != null) {
                    selectedStationId = found.getInt("station_id"); isStationInDb = true; manualStationName = ""
                } else {
                    selectedStationId = 0; isStationInDb = false; manualStationName = stationName
                }
            } catch (ex: Exception) { Log.e(TAG, "Error checking station: ${ex.message}", ex) }
        }
    }

    private fun setupDate() {
        val currentDate = Date()
        dateEditText.setText(dateFormatDisplay.format(currentDate))
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        val current = dateEditText.text.toString()
        if (current.isNotEmpty()) {
            try { cal.time = dateFormatDisplay.parse(current)!! } catch (_: Exception) {}
        }
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            dateEditText.setText(dateFormatDisplay.format(cal.time))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun setupDateInputMask() {
        // не используется — дата выбирается через DatePickerDialog
    }

    private fun validateDate(): Boolean {
        val dateText = dateEditText.text.toString().trim()
        if (dateText.isEmpty()) {
            Toast.makeText(this, "Выберите дату", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
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

        dateEditText.setOnClickListener {
            showDatePicker()
        }


        history.setOnClickListener {
            val intent = Intent(this@MainActivityRefueling, MainActivityHistoryRef::class.java)
            intent.putExtra("car_id", currentCarId)
            intent.putExtra("car_model", currentCarModel)
            startActivity(intent)
        }

        imageViewScanQr.setOnClickListener {
            qrLauncher.launch(Intent(this, QrScannerActivity::class.java))
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
        progressOverlay.visibility = android.view.View.VISIBLE
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val dateText    = dateEditText.text.toString().trim()
                val mileage     = mileageEditText.text.toString().toDouble()
                val volume      = volumeEditText.text.toString().toDouble()
                val price       = priceEditText.text.toString().toDouble()
                val fullTank    = fullTankCheckBox.isChecked
                val stationText = stationAutoComplete.text.toString().trim()

                ApiClient.updateRefueling(
                    currentCarId, refuelingId, selectedFuelId,
                    if (isStationInDb) selectedStationId else null,
                    if (!isStationInDb) manualStationName.ifEmpty { stationText } else null,
                    dateText, mileage, volume, price, fullTank
                )
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    Toast.makeText(this@MainActivityRefueling, "Запись обновлена!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK); finish()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error updating refueling: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    addRefuelingButton.isEnabled = true
                    Toast.makeText(this@MainActivityRefueling, "${friendlyError(ex)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveRefueling() {
        addRefuelingButton.isEnabled = false
        progressOverlay.visibility = android.view.View.VISIBLE
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val dateText    = dateEditText.text.toString().trim()
                val mileage     = mileageEditText.text.toString().toDouble()
                val volume      = volumeEditText.text.toString().toDouble()
                val price       = priceEditText.text.toString().toDouble()
                val fullTank    = fullTankCheckBox.isChecked
                val stationText = stationAutoComplete.text.toString().trim()

                ApiClient.addRefueling(
                    currentCarId, selectedFuelId,
                    if (isStationInDb) selectedStationId else null,
                    if (!isStationInDb) manualStationName.ifEmpty { stationText } else null,
                    dateText, mileage, volume, price, fullTank
                )
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    addRefuelingButton.isEnabled = true
                    Toast.makeText(this@MainActivityRefueling, "Заправка добавлена!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK); finish()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error saving refueling: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    addRefuelingButton.isEnabled = true
                    Toast.makeText(this@MainActivityRefueling, "Ошибка сохранения: ${friendlyError(ex)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun isCarBelongsToUser(carId: Int, userId: Int): Boolean {
        return try {
            val arr = ApiClient.getCars(userId)
            (0 until arr.length()).any { arr.getJSONObject(it).getInt("car_id") == carId }
        } catch (ex: Exception) { false }
    }

    private fun handleQrResult(qrRaw: String) {
        progressOverlay.visibility = View.VISIBLE
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = ApiClient.parseReceipt(qrRaw)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    result.optString("date").takeIf { it.isNotEmpty() }?.let { dateEditText.setText(it) }
                    result.optDouble("volume").takeIf { !it.isNaN() && it > 0 }?.let { volumeEditText.setText(it.toString()) }
                    result.optDouble("price_per_liter").takeIf { !it.isNaN() && it > 0 }?.let { priceEditText.setText(it.toString()) }

                    // Station name
                    val stationNameRaw = result.optString("station_name", "")
                    if (stationNameRaw.isNotEmpty()) {
                        val matchedStation = stations.find { it.name.equals(stationNameRaw, ignoreCase = true) }
                        if (matchedStation != null) {
                            selectedStationId = matchedStation.stationId
                            isStationInDb = true
                            manualStationName = ""
                        } else {
                            selectedStationId = 0
                            isStationInDb = false
                            manualStationName = stationNameRaw
                        }
                        stationAutoComplete.setText(stationNameRaw, false)
                    }

                    // Match fuel name from receipt to spinner
                    val fuelNameRaw = result.optString("fuel_name", "").lowercase()
                    if (fuelNameRaw.isNotEmpty()) {
                        val matchIndex = fuels.indexOfFirst { fuel ->
                            fuelNameRaw.contains(fuel.name.lowercase()) ||
                            fuelNameRaw.contains(fuel.marking.lowercase()) ||
                            fuel.name.lowercase().let { n -> fuelNameRaw.contains(n) }
                        }
                        if (matchIndex >= 0) {
                            fuelSpinner.setSelection(matchIndex)
                            selectedFuelId = fuels[matchIndex].fuelId
                        }
                    }

                    updateTotalAmount()
                    Toast.makeText(this@MainActivityRefueling, "Данные из чека подставлены", Toast.LENGTH_SHORT).show()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "QR parse error: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@MainActivityRefueling, "Не удалось распознать чек: ${friendlyError(ex)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadFuelData() {
        progressOverlay.visibility = android.view.View.VISIBLE
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val arr = ApiClient.getFuels()
                fuels.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    fuels.add(Fuel(obj.getInt("fuel_id"), obj.getString("name"), obj.optString("marking", "")))
                }
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    updateFuelSpinner()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading fuels: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    Toast.makeText(this@MainActivityRefueling, "Ошибка загрузки топлива", Toast.LENGTH_SHORT).show()
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
                val arr = ApiClient.getStations()
                stations.clear()
                val stationNames = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    stations.add(GasStation(obj.getInt("station_id"), obj.getString("name")))
                    stationNames.add(obj.getString("name"))
                }
                withContext(Dispatchers.Main) { updateStationAutoComplete(stationNames) }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading stations: ${ex.message}", ex)
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