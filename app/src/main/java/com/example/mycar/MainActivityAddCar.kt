package com.example.mycar

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

class MainActivityAddCar : AppCompatActivity() {
    // AutoCompleteTextView для марки и модели
    private lateinit var brandAutoComplete: AutoCompleteTextView
    private lateinit var modelAutoComplete: AutoCompleteTextView
    private lateinit var brandTextInputLayout: TextInputLayout
    private lateinit var modelTextInputLayout: TextInputLayout
    private lateinit var mileageTextInputLayout: TextInputLayout

    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private lateinit var mileageEditText: EditText
    private lateinit var carImageView: ImageView
    private lateinit var cancelImageView: ImageView
    private lateinit var editimageView: ImageView
    private lateinit var titleTextView: TextView

    private val brands = mutableListOf<CarBrand>()
    private val models = mutableListOf<CarModel>()
    private var selectedBrandId: Int = 0
    private var selectedModelId: Int = 0
    private var selectedImageUri: Uri? = null
    private var photoBytes: ByteArray? = null
    private var isEditMode = false
    private var currentCarId: Int = 0
    private var originalBrand: String = ""
    private var originalModel: String = ""
    private var originalMileage: Int = 0

    // Флаги для отслеживания выбора из выпадающего списка
    private var isBrandSelectedFromList = false
    private var isModelSelectedFromList = false

    // Флаги для проверки наличия в БД
    private var isBrandInDb = false
    private var isModelInDb = false

    // Введенные вручную значения
    private var manualBrandName: String = ""
    private var manualModelName: String = ""

    // Адаптеры для автодополнения
    private lateinit var brandAdapter: ArrayAdapter<String>
    private lateinit var modelAdapter: ArrayAdapter<String>

    companion object {
        private const val TAG = "MainActivityAddCar"
        private const val RESULT_CAR_DELETED = 200
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                carImageView.setImageURI(uri)
                convertImageToByteArray(uri)
                Toast.makeText(this, "Фото выбрано", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_add_car)

        Log.d(TAG, "onCreate started")

        initializeViews()
        checkEditMode()
        setClickListeners()
        setupStatusBarColors()
        loadBrandsFromDatabase()
        setupTextWatchers()
    }

    private fun initializeViews() {
        Log.d(TAG, "Initializing views")

        try {
            // Инициализация AutoCompleteTextView
            brandAutoComplete = findViewById(R.id.brandAutoComplete)
            modelAutoComplete = findViewById(R.id.modelAutoComplete)
            brandTextInputLayout = findViewById(R.id.brandTextInputLayout)
            modelTextInputLayout = findViewById(R.id.modelTextInputLayout)
            mileageTextInputLayout = findViewById(R.id.mileageTextInputLayout)

            saveButton = findViewById(R.id.button)
            deleteButton = findViewById(R.id.deleteButton)
            mileageEditText = findViewById(R.id.textInputEditText)
            carImageView = findViewById(R.id.imageView11)
            cancelImageView = findViewById(R.id.imageView2)
            titleTextView = findViewById(R.id.textView2)
            editimageView = findViewById(R.id.editimageView)

            // Настройка начального состояния
            modelAutoComplete.isEnabled = false
            modelTextInputLayout.isEnabled = false
            saveButton.isEnabled = false
            deleteButton.visibility = View.GONE

            // Инициализация адаптеров
            brandAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
            modelAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())

            brandAutoComplete.setAdapter(brandAdapter)
            modelAutoComplete.setAdapter(modelAdapter)

            // Установка threshold для автодополнения
            brandAutoComplete.threshold = 1
            modelAutoComplete.threshold = 1

            // НАСТРОЙКА: показывать выпадающий список сразу при фокусе
            brandAutoComplete.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus && brandAdapter.count > 0) {
                    brandAutoComplete.showDropDown()
                }
            }

            modelAutoComplete.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus && modelAdapter.count > 0) {
                    modelAutoComplete.showDropDown()
                }
            }

            // Также показывать список при клике
            brandAutoComplete.setOnClickListener {
                if (brandAdapter.count > 0) {
                    brandAutoComplete.showDropDown()
                }
            }

            modelAutoComplete.setOnClickListener {
                if (modelAdapter.count > 0) {
                    modelAutoComplete.showDropDown()
                }
            }

            Log.d(TAG, "Views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views: ${e.message}", e)
            Toast.makeText(this, "Ошибка инициализации интерфейса", Toast.LENGTH_SHORT).show()
        }
    }


    private fun setupTextWatchers() {
        // TextWatcher для марки
        brandAutoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!isBrandSelectedFromList) {
                    // Пользователь ввел текст вручную
                    val brandText = s?.toString()?.trim() ?: ""
                    if (brandText.isNotEmpty()) {
                        checkBrandInDatabase(brandText)
                    } else {
                        selectedBrandId = 0
                        isBrandInDb = false
                        manualBrandName = ""
                    }

                    // Очищаем модель и делаем её доступной для ручного ввода
                    modelAutoComplete.setText("")
                    modelAutoComplete.isEnabled = true
                    modelTextInputLayout.isEnabled = true
                    modelTextInputLayout.hint = "Введите модель"
                    modelAdapter.clear()
                    modelAdapter.notifyDataSetChanged()
                    selectedModelId = 0
                    isModelInDb = false
                }
                isBrandSelectedFromList = false
                validateAndEnableSaveButton()
            }
        })

        // Обработчик выбора из выпадающего списка для марки
        brandAutoComplete.setOnItemClickListener { parent, view, position, id ->
            isBrandSelectedFromList = true
            val selectedBrandName = parent.getItemAtPosition(position) as String
            val selectedBrand = brands.find { it.brandName == selectedBrandName }
            if (selectedBrand != null) {
                selectedBrandId = selectedBrand.brandId
                isBrandInDb = true
                manualBrandName = ""
                Log.d(TAG, "Selected brand from DB: $selectedBrandName (ID: $selectedBrandId)")
                // Загружаем модели для выбранной марки
                loadModelsFromDatabase(selectedBrandId)
            }
        }

        // TextWatcher для модели
        modelAutoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!isModelSelectedFromList) {
                    // Пользователь ввел текст вручную
                    val modelText = s?.toString()?.trim() ?: ""
                    if (modelText.isNotEmpty() && selectedBrandId > 0) {
                        checkModelInDatabase(modelText, selectedBrandId)
                    } else {
                        selectedModelId = 0
                        isModelInDb = false
                        manualModelName = modelText
                    }
                }
                isModelSelectedFromList = false
                validateAndEnableSaveButton()
            }
        })

        // Обработчик выбора из выпадающего списка для модели
        modelAutoComplete.setOnItemClickListener { parent, view, position, id ->
            isModelSelectedFromList = true
            val selectedModelName = parent.getItemAtPosition(position) as String
            val selectedModel = models.find { it.modelName == selectedModelName }
            if (selectedModel != null) {
                selectedModelId = selectedModel.modelId
                isModelInDb = true
                manualModelName = ""
                Log.d(TAG, "Selected model from DB: $selectedModelName (ID: $selectedModelId)")
            }
            validateAndEnableSaveButton()
        }

        // TextWatcher для пробега
        mileageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateAndEnableSaveButton()
            }
        })
    }

    private fun checkBrandInDatabase(brandName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val query = "SELECT brand_id FROM CarBrands WHERE name = ?"
                    val preparedStatement = connect.prepareStatement(query)
                    preparedStatement.setString(1, brandName)
                    val resultSet = preparedStatement.executeQuery()

                    if (resultSet.next()) {
                        // Марка найдена в БД
                        selectedBrandId = resultSet.getInt("brand_id")
                        isBrandInDb = true
                        manualBrandName = ""
                        Log.d(TAG, "Brand found in DB: $brandName (ID: $selectedBrandId)")

                        // Загружаем модели для этой марки
                        withContext(Dispatchers.Main) {
                            loadModelsFromDatabase(selectedBrandId)
                        }
                    } else {
                        // Марка не найдена, будет добавлена позже
                        selectedBrandId = 0
                        isBrandInDb = false
                        manualBrandName = brandName
                        Log.d(TAG, "Brand not in DB, will be added: $brandName")

                        withContext(Dispatchers.Main) {
                            // Включаем поле модели для ручного ввода
                            modelAutoComplete.isEnabled = true
                            modelTextInputLayout.isEnabled = true
                            modelTextInputLayout.hint = "Введите модель"
                            modelAutoComplete.setText("")
                            modelAdapter.clear()
                            modelAdapter.notifyDataSetChanged()
                        }
                    }

                    resultSet.close()
                    preparedStatement.close()
                    connect.close()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error checking brand: ${ex.message}", ex)
            }
        }
    }

    private fun checkModelInDatabase(modelName: String, brandId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val query = "SELECT model_id FROM CarModels WHERE name = ? AND brand_id = ?"
                    val preparedStatement = connect.prepareStatement(query)
                    preparedStatement.setString(1, modelName)
                    preparedStatement.setInt(2, brandId)
                    val resultSet = preparedStatement.executeQuery()

                    if (resultSet.next()) {
                        // Модель найдена в БД
                        selectedModelId = resultSet.getInt("model_id")
                        isModelInDb = true
                        manualModelName = ""
                        Log.d(TAG, "Model found in DB: $modelName (ID: $selectedModelId)")
                    } else {
                        // Модель не найдена, будет добавлена позже
                        selectedModelId = 0
                        isModelInDb = false
                        manualModelName = modelName
                        Log.d(TAG, "Model not in DB, will be added: $modelName")
                    }

                    resultSet.close()
                    preparedStatement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        validateAndEnableSaveButton()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error checking model: ${ex.message}", ex)
            }
        }
    }

    private fun validateAndEnableSaveButton() {
        val brandText = brandAutoComplete.text.toString().trim()
        val modelText = modelAutoComplete.text.toString().trim()
        val mileageText = mileageEditText.text.toString().trim()

        saveButton.isEnabled = brandText.isNotEmpty() &&
                modelText.isNotEmpty() &&
                mileageText.isNotEmpty()
    }

    private fun checkEditMode() {
        Log.d(TAG, "Checking edit mode")

        try {
            isEditMode = intent.getBooleanExtra("is_edit_mode", false)
            currentCarId = intent.getIntExtra("car_id", 0)

            if (isEditMode) {
                titleTextView.text = "Редактировать"
                saveButton.text = "Сохранить изменения"
                deleteButton.visibility = View.VISIBLE

                originalBrand = intent.getStringExtra("brand") ?: ""
                originalModel = intent.getStringExtra("model") ?: ""
                originalMileage = intent.getIntExtra("mileage", 0)

                val photoBytesExtra = intent.getByteArrayExtra("photo_bytes")
                if (photoBytesExtra != null && photoBytesExtra.isNotEmpty()) {
                    photoBytes = photoBytesExtra
                }

                mileageEditText.setText(originalMileage.toString())

                if (photoBytes != null && photoBytes!!.isNotEmpty()) {
                    try {
                        val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes!!.size)
                        carImageView.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting photo: ${e.message}")
                        carImageView.setImageResource(R.drawable.ph)
                    }
                } else {
                    carImageView.setImageResource(R.drawable.ph)
                }

                Log.d(TAG, "Edit mode: Brand=$originalBrand, Model=$originalModel, Mileage=$originalMileage, CarID=$currentCarId")
            } else {
                titleTextView.text = "Добавить авто"
                saveButton.text = "Добавить авто"
                carImageView.setImageResource(R.drawable.ph)
                editimageView.visibility = View.GONE
                deleteButton.visibility = View.GONE
                Log.d(TAG, "Add mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkEditMode: ${e.message}", e)
            Toast.makeText(this, "Ошибка загрузки данных автомобиля", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBrandsFromDatabase() {
        Log.d(TAG, "Loading brands from database")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val statement: Statement = connect.createStatement()
                    val resultSet: ResultSet = statement.executeQuery("SELECT brand_id, name FROM CarBrands ORDER BY name")

                    brands.clear()
                    val brandNames = mutableListOf<String>()

                    while (resultSet.next()) {
                        val brandId = resultSet.getInt("brand_id")
                        val brandName = resultSet.getString("name")
                        brands.add(CarBrand(brandId, brandName))
                        brandNames.add(brandName)
                    }

                    resultSet.close()
                    statement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        updateBrandAutoComplete(brandNames)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityAddCar, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading brands: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityAddCar, "Ошибка загрузки марок", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateBrandAutoComplete(brandNames: List<String>) {
        Log.d(TAG, "Updating brand autocomplete with ${brandNames.size} brands")

        try {
            brandAdapter.clear()
            brandAdapter.addAll(brandNames)
            brandAdapter.notifyDataSetChanged()

            if (isEditMode && originalBrand.isNotEmpty()) {
                brandAutoComplete.setText(originalBrand)

                // Проверяем, есть ли такая марка в БД
                val brand = brands.find { it.brandName.equals(originalBrand, ignoreCase = true) }
                if (brand != null) {
                    selectedBrandId = brand.brandId
                    isBrandInDb = true
                    isBrandSelectedFromList = true
                    manualBrandName = ""
                    Log.d(TAG, "Edit mode: Found brand in DB: ${brand.brandName} with ID: $selectedBrandId")
                    loadModelsFromDatabase(selectedBrandId)
                } else {
                    // Марка не найдена в БД (была введена вручную)
                    selectedBrandId = 0
                    isBrandInDb = false
                    manualBrandName = originalBrand
                    Log.d(TAG, "Edit mode: Brand not in DB, manual: $originalBrand")
                    modelAutoComplete.isEnabled = true
                    modelTextInputLayout.isEnabled = true
                    modelTextInputLayout.hint = "Введите модель"

                    if (originalModel.isNotEmpty()) {
                        modelAutoComplete.setText(originalModel)
                        checkModelInDatabase(originalModel, 0)
                    }
                }
            }

            Toast.makeText(this, "Марки загружены", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating brand autocomplete: ${e.message}", e)
            Toast.makeText(this, "Ошибка обновления списка марок", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadModelsFromDatabase(brandId: Int) {
        Log.d(TAG, "Loading models for brand ID: $brandId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    models.clear()
                    val modelNames = mutableListOf<String>()

                    // Используем прямой SQL запрос вместо хранимой процедуры
                    val query = "SELECT model_id, name FROM CarModels WHERE brand_id = ? ORDER BY name"
                    val preparedStatement: PreparedStatement = connect.prepareStatement(query)
                    preparedStatement.setInt(1, brandId)

                    val resultSet: ResultSet = preparedStatement.executeQuery()

                    while (resultSet.next()) {
                        val modelId = resultSet.getInt("model_id")
                        val modelName = resultSet.getString("name")
                        models.add(CarModel(modelId, modelName))
                        modelNames.add(modelName)
                        Log.d(TAG, "Found model: $modelName with ID: $modelId")
                    }

                    Log.d(TAG, "Total models loaded: ${models.size}")

                    resultSet.close()
                    preparedStatement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        updateModelAutoComplete(modelNames)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityAddCar, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading models: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityAddCar, "Ошибка загрузки моделей: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateModelAutoComplete(modelNames: List<String>) {
        Log.d(TAG, "Updating model autocomplete with ${modelNames.size} models")

        try {
            // Очищаем адаптер
            modelAdapter.clear()

            if (modelNames.isNotEmpty()) {
                // Добавляем модели в адаптер
                modelAdapter.addAll(modelNames)
                modelAdapter.notifyDataSetChanged()

                // Настраиваем поле модели
                modelAutoComplete.isEnabled = true
                modelTextInputLayout.isEnabled = true
                modelTextInputLayout.hint = "Выберите модель"

                // Устанавливаем адаптер заново для гарантии
                modelAutoComplete.setAdapter(modelAdapter)

                // Устанавливаем пустой текст, чтобы не было предзаполнения
                if (modelAutoComplete.text.toString().trim().isEmpty()) {
                    modelAutoComplete.setText("")
                }

                // Показываем выпадающий список сразу после загрузки
                modelAutoComplete.post {
                    modelAutoComplete.showDropDown()
                    Log.d(TAG, "Showing dropdown with ${modelNames.size} items")
                }

                Log.d(TAG, "Model adapter updated with ${modelNames.size} items")
            } else {
                // Если моделей нет
                modelAutoComplete.isEnabled = true
                modelTextInputLayout.isEnabled = true
                modelTextInputLayout.hint = "Нет моделей для этой марки"
                modelAdapter.notifyDataSetChanged()
                Log.d(TAG, "No models found for this brand")
            }

            // Обработка режима редактирования
            if (isEditMode && originalModel.isNotEmpty()) {
                if (!isModelInDb) {
                    modelAutoComplete.setText(originalModel)
                    val model = models.find { it.modelName.equals(originalModel, ignoreCase = true) }
                    if (model != null) {
                        selectedModelId = model.modelId
                        isModelInDb = true
                        isModelSelectedFromList = true
                        manualModelName = ""
                        Log.d(TAG, "Edit mode: Found model in DB: ${model.modelName} with ID: $selectedModelId")
                    } else {
                        selectedModelId = 0
                        isModelInDb = false
                        manualModelName = originalModel
                        Log.d(TAG, "Edit mode: Model not in DB, manual: $originalModel")
                    }
                }
            } else {
                // Если не режим редактирования, показываем список автоматически
                if (modelNames.isNotEmpty()) {
                    modelAutoComplete.post {
                        modelAutoComplete.showDropDown()
                    }
                }
            }

            validateAndEnableSaveButton()
            Log.d(TAG, "Model autocomplete updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating model autocomplete: ${e.message}", e)
            Toast.makeText(this, "Ошибка обновления списка моделей: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setClickListeners() {
        Log.d(TAG, "Setting up click listeners")

        try {
            carImageView.setOnClickListener {
                selectImageFromGallery()
            }

            cancelImageView.setOnClickListener {
                finish()
            }

            deleteButton.setOnClickListener {
                showDeleteConfirmationDialog()
            }

            saveButton.setOnClickListener {
                saveCarToDatabase()
            }

            // Добавляем обработчик клика на поле модели для показа списка
            modelAutoComplete.setOnClickListener {
                if (modelAdapter.count > 0) {
                    modelAutoComplete.showDropDown()
                }
            }

            Log.d(TAG, "Click listeners set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting click listeners: ${e.message}", e)
            Toast.makeText(this, "Ошибка настройки интерфейса", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectImageFromGallery() {
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting image: ${e.message}", e)
            Toast.makeText(this, "Ошибка выбора изображения", Toast.LENGTH_SHORT).show()
        }
    }

    private fun convertImageToByteArray(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                inputStream?.use { stream ->
                    photoBytes = stream.readBytes()
                    Log.d(TAG, "Image converted to byte array, size: ${photoBytes?.size ?: 0} bytes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting image to byte array: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityAddCar, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Удаление автомобиля")
            .setMessage("Вы уверены, что хотите удалить этот автомобиль? Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                deleteCar()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteCar() {
        Log.d(TAG, "Deleting car with ID: $currentCarId")

        val sessionManager = SessionManager(this)
        val userId = sessionManager.getUserId()

        if (userId == 0) {
            Toast.makeText(this, "Ошибка: пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        deleteButton.isEnabled = false
        deleteButton.text = "Удаление..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    // Начинаем транзакцию
                    connect.autoCommit = false

                    try {
                        // 1. Сначала удаляем все заправки для этого автомобиля
                        val deleteRefuelingQuery = "DELETE FROM refueling WHERE car_id = ?"
                        val refuelingStatement: PreparedStatement = connect.prepareStatement(deleteRefuelingQuery)
                        refuelingStatement.setInt(1, currentCarId)
                        val deletedRefueling = refuelingStatement.executeUpdate()
                        refuelingStatement.close()
                        Log.d(TAG, "Deleted $deletedRefueling refueling records")

                        // 2. Удаляем все записи обслуживания для этого автомобиля
                        val deleteMaintenanceQuery = "DELETE FROM Maintenance WHERE car_id = ?"
                        val maintenanceStatement: PreparedStatement = connect.prepareStatement(deleteMaintenanceQuery)
                        maintenanceStatement.setInt(1, currentCarId)
                        val deletedMaintenance = maintenanceStatement.executeUpdate()
                        maintenanceStatement.close()
                        Log.d(TAG, "Deleted $deletedMaintenance maintenance records")

                        // 3. Удаляем сам автомобиль
                        val deleteCarQuery = "DELETE FROM Cars WHERE car_id = ? AND user_id = ?"
                        val carStatement: PreparedStatement = connect.prepareStatement(deleteCarQuery)
                        carStatement.setInt(1, currentCarId)
                        carStatement.setInt(2, userId)

                        val rowsAffected = carStatement.executeUpdate()
                        carStatement.close()

                        // Подтверждаем транзакцию
                        connect.commit()

                        withContext(Dispatchers.Main) {
                            deleteButton.isEnabled = true
                            deleteButton.text = "Удалить"

                            if (rowsAffected > 0) {
                                Toast.makeText(
                                    this@MainActivityAddCar,
                                    "Автомобиль и все связанные записи успешно удалены",
                                    Toast.LENGTH_LONG
                                ).show()

                                val resultIntent = Intent()
                                resultIntent.putExtra("car_deleted", true)
                                resultIntent.putExtra("car_id", currentCarId)
                                setResult(RESULT_CAR_DELETED, resultIntent)

                                finish()
                            } else {
                                Toast.makeText(
                                    this@MainActivityAddCar,
                                    "Ошибка при удалении автомобиля",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (ex: Exception) {
                        // В случае ошибки откатываем транзакцию
                        connect.rollback()
                        throw ex
                    } finally {
                        connect.autoCommit = true
                        connect.close()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        deleteButton.isEnabled = true
                        deleteButton.text = "Удалить"
                        Toast.makeText(this@MainActivityAddCar, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error deleting car: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    deleteButton.isEnabled = true
                    deleteButton.text = "Удалить"
                    Toast.makeText(this@MainActivityAddCar, "Ошибка удаления: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveCarToDatabase() {
        Log.d(TAG, "Saving car to database")

        try {
            val brandText = brandAutoComplete.text.toString().trim()
            val modelText = modelAutoComplete.text.toString().trim()
            val mileageText = mileageEditText.text.toString().trim()

            // Валидация
            if (brandText.isEmpty()) {
                Toast.makeText(this, "Введите марку автомобиля", Toast.LENGTH_SHORT).show()
                brandAutoComplete.requestFocus()
                return
            }

            if (modelText.isEmpty()) {
                Toast.makeText(this, "Введите модель автомобиля", Toast.LENGTH_SHORT).show()
                modelAutoComplete.requestFocus()
                return
            }

            if (mileageText.isEmpty()) {
                Toast.makeText(this, "Введите пробег автомобиля", Toast.LENGTH_SHORT).show()
                mileageEditText.requestFocus()
                return
            }

            val mileage = try {
                mileageText.toInt()
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Пробег должен быть числом", Toast.LENGTH_SHORT).show()
                mileageEditText.requestFocus()
                return
            }

            if (mileage < 0) {
                Toast.makeText(this, "Пробег не может быть отрицательным", Toast.LENGTH_SHORT).show()
                mileageEditText.requestFocus()
                return
            }

            val sessionManager = SessionManager(this)
            val userId = sessionManager.getUserId()

            if (userId == 0) {
                Toast.makeText(this, "Ошибка: пользователь не авторизован", Toast.LENGTH_SHORT).show()
                return
            }

            saveButton.isEnabled = false
            saveButton.text = if (isEditMode) "Сохранение..." else "Добавление..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val connectionHelper = ConnectionHelper()
                    val connect = connectionHelper.connectionclass()

                    if (connect != null) {
                        connect.autoCommit = false

                        try {
                            if (isEditMode) {
                                updateCarInDatabase(connect, userId, mileage, brandText, modelText)
                            } else {
                                insertCarIntoDatabase(connect, userId, mileage, brandText, modelText)
                            }

                            connect.commit()
                        } catch (ex: Exception) {
                            connect.rollback()
                            throw ex
                        } finally {
                            connect.autoCommit = true
                            connect.close()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            saveButton.isEnabled = true
                            saveButton.text = if (isEditMode) "Сохранить изменения" else "Добавить автомобиль"
                            Toast.makeText(this@MainActivityAddCar, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error saving car: ${ex.message}", ex)
                    withContext(Dispatchers.Main) {
                        saveButton.isEnabled = true
                        saveButton.text = if (isEditMode) "Сохранить изменения" else "Добавить автомобиль"
                        Toast.makeText(this@MainActivityAddCar, "Ошибка сохранения: ${ex.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveCarToDatabase: ${e.message}", e)
            Toast.makeText(this, "Ошибка при сохранении", Toast.LENGTH_SHORT).show()
            saveButton.isEnabled = true
            saveButton.text = if (isEditMode) "Сохранить изменения" else "Добавить автомобиль"
        }
    }

    private suspend fun insertCarIntoDatabase(connect: java.sql.Connection, userId: Int, mileage: Int, brandText: String, modelText: String) {
        Log.d(TAG, "Inserting new car into database")

        var finalBrandId = selectedBrandId
        var finalModelId = selectedModelId

        // Если марка не в БД (введена вручную), добавляем её
        if (!isBrandInDb) {
            val insertBrandQuery = "INSERT INTO CarBrands (name) OUTPUT INSERTED.brand_id VALUES (?)"
            val brandStatement: PreparedStatement = connect.prepareStatement(insertBrandQuery)
            brandStatement.setString(1, manualBrandName.ifEmpty { brandText })
            val brandResult = brandStatement.executeQuery()
            if (brandResult.next()) {
                finalBrandId = brandResult.getInt(1)
                Log.d(TAG, "Inserted new brand: ${manualBrandName.ifEmpty { brandText }} with ID: $finalBrandId")
            }
            brandResult.close()
            brandStatement.close()
        } else {
            finalBrandId = selectedBrandId
        }

        // Если модель не в БД (введена вручную), добавляем её
        if (!isModelInDb) {
            val insertModelQuery = "INSERT INTO CarModels (brand_id, name) OUTPUT INSERTED.model_id VALUES (?, ?)"
            val modelStatement: PreparedStatement = connect.prepareStatement(insertModelQuery)
            modelStatement.setInt(1, finalBrandId)
            modelStatement.setString(2, manualModelName.ifEmpty { modelText })
            val modelResult = modelStatement.executeQuery()
            if (modelResult.next()) {
                finalModelId = modelResult.getInt(1)
                Log.d(TAG, "Inserted new model: ${manualModelName.ifEmpty { modelText }} with ID: $finalModelId")
            }
            modelResult.close()
            modelStatement.close()
        } else {
            finalModelId = selectedModelId
        }

        val query = """
            INSERT INTO Cars (user_id, model_id, mileage, photo) 
            VALUES (?, ?, ?, ?)
        """
        val preparedStatement: PreparedStatement = connect.prepareStatement(query)
        preparedStatement.setInt(1, userId)
        preparedStatement.setInt(2, finalModelId)
        preparedStatement.setInt(3, mileage)

        if (photoBytes != null) {
            preparedStatement.setBytes(4, photoBytes)
        } else {
            preparedStatement.setNull(4, java.sql.Types.VARBINARY)
        }

        val rowsAffected = preparedStatement.executeUpdate()
        preparedStatement.close()

        withContext(Dispatchers.Main) {
            saveButton.isEnabled = true
            saveButton.text = "Добавить автомобиль"

            if (rowsAffected > 0) {
                Toast.makeText(this@MainActivityAddCar, "Автомобиль успешно добавлен!", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this@MainActivityAddCar, "Ошибка при добавлении автомобиля", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun updateCarInDatabase(connect: java.sql.Connection, userId: Int, mileage: Int, brandText: String, modelText: String) {
        Log.d(TAG, "Updating car in database, ID: $currentCarId")

        var finalBrandId = selectedBrandId
        var finalModelId = selectedModelId

        // Проверяем, изменилась ли марка
        if (brandText != originalBrand) {
            if (!isBrandInDb) {
                // Добавляем новую марку
                val insertBrandQuery = "INSERT INTO CarBrands (name) OUTPUT INSERTED.brand_id VALUES (?)"
                val brandStatement: PreparedStatement = connect.prepareStatement(insertBrandQuery)
                brandStatement.setString(1, manualBrandName.ifEmpty { brandText })
                val brandResult = brandStatement.executeQuery()
                if (brandResult.next()) {
                    finalBrandId = brandResult.getInt(1)
                    Log.d(TAG, "Inserted new brand for update: ${manualBrandName.ifEmpty { brandText }} with ID: $finalBrandId")
                }
                brandResult.close()
                brandStatement.close()
            } else {
                finalBrandId = selectedBrandId
            }
        }

        // Проверяем, изменилась ли модель
        if (modelText != originalModel) {
            if (!isModelInDb) {
                // Добавляем новую модель
                val insertModelQuery = "INSERT INTO CarModels (brand_id, name) OUTPUT INSERTED.model_id VALUES (?, ?)"
                val modelStatement: PreparedStatement = connect.prepareStatement(insertModelQuery)
                modelStatement.setInt(1, finalBrandId)
                modelStatement.setString(2, manualModelName.ifEmpty { modelText })
                val modelResult = modelStatement.executeQuery()
                if (modelResult.next()) {
                    finalModelId = modelResult.getInt(1)
                    Log.d(TAG, "Inserted new model for update: ${manualModelName.ifEmpty { modelText }} with ID: $finalModelId")
                }
                modelResult.close()
                modelStatement.close()
            } else {
                finalModelId = selectedModelId
            }
        }

        val query = """
            UPDATE Cars 
            SET model_id = ?, mileage = ?, photo = ?
            WHERE car_id = ? AND user_id = ?
        """
        val preparedStatement: PreparedStatement = connect.prepareStatement(query)
        preparedStatement.setInt(1, finalModelId)
        preparedStatement.setInt(2, mileage)

        if (photoBytes != null) {
            preparedStatement.setBytes(3, photoBytes)
        } else {
            preparedStatement.setNull(3, java.sql.Types.VARBINARY)
        }

        preparedStatement.setInt(4, currentCarId)
        preparedStatement.setInt(5, userId)

        val rowsAffected = preparedStatement.executeUpdate()
        preparedStatement.close()

        withContext(Dispatchers.Main) {
            saveButton.isEnabled = true
            saveButton.text = "Сохранить изменения"

            if (rowsAffected > 0) {
                Toast.makeText(this@MainActivityAddCar, "Автомобиль успешно обновлен!", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this@MainActivityAddCar, "Ошибка при обновлении автомобиля", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }
}

data class CarBrand(
    val brandId: Int,
    val brandName: String
)

data class CarModel(
    val modelId: Int,
    val modelName: String
)