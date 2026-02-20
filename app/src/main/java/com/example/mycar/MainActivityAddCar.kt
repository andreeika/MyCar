package com.example.mycar

import SessionManager
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

class MainActivityAddCar : AppCompatActivity() {
    private lateinit var brandSpinner: Spinner
    private lateinit var modelSpinner: Spinner
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
    }

    private fun initializeViews() {
        Log.d(TAG, "Initializing views")

        try {
            brandSpinner = findViewById(R.id.brandSpinner)
            modelSpinner = findViewById(R.id.modelSpinner)
            saveButton = findViewById(R.id.button)
            deleteButton = findViewById(R.id.deleteButton)
            mileageEditText = findViewById(R.id.textInputEditText)
            carImageView = findViewById(R.id.imageView11)
            cancelImageView = findViewById(R.id.imageView2)
            titleTextView = findViewById(R.id.textView2)
            editimageView = findViewById(R.id.editimageView)
            mileageEditText.hint = "Введите пробег"

            modelSpinner.isEnabled = false
            saveButton.isEnabled = false
            deleteButton.visibility = View.GONE

            Log.d(TAG, "Views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views: ${e.message}", e)
            Toast.makeText(this, "Ошибка инициализации интерфейса", Toast.LENGTH_SHORT).show()
        }
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
                    val resultSet: ResultSet = statement.executeQuery("EXEC GetCarBrands")

                    brands.clear()
                    while (resultSet.next()) {
                        val brandId = resultSet.getInt("brand_id")
                        val brandName = resultSet.getString("name")
                        brands.add(CarBrand(brandId, brandName))
                    }

                    resultSet.close()
                    statement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        updateBrandSpinner()
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

    private fun updateBrandSpinner() {
        Log.d(TAG, "Updating brand spinner with ${brands.size} brands")

        try {
            val brandNames = brands.map { it.brandName }
            val brandAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                brandNames
            )
            brandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            brandSpinner.adapter = brandAdapter

            if (isEditMode && originalBrand.isNotEmpty()) {
                val brandIndex = brands.indexOfFirst { it.brandName == originalBrand }
                if (brandIndex != -1) {
                    brandSpinner.setSelection(brandIndex)
                    selectedBrandId = brands[brandIndex].brandId

                    loadModelsFromDatabase(selectedBrandId)
                    Log.d(TAG, "Brand selected: $originalBrand")
                } else {
                    Log.w(TAG, "Brand not found: $originalBrand")
                    if (brands.isNotEmpty()) {
                        brandSpinner.setSelection(0)
                        selectedBrandId = brands[0].brandId
                        loadModelsFromDatabase(selectedBrandId)
                    }
                }
            } else if (brands.isNotEmpty()) {
                brandSpinner.setSelection(0)
                selectedBrandId = brands[0].brandId
                loadModelsFromDatabase(selectedBrandId)
            }

            Toast.makeText(this, "Марки загружены", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating brand spinner: ${e.message}", e)
            Toast.makeText(this, "Ошибка обновления списка марок", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadModelsFromDatabase(brandId: Int) {
        Log.d(TAG, "Loading models for brand ID: $brandId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                models.clear()

                if (connect != null) {
                    val query = "EXEC GetCarModelsByBrand ?"
                    val preparedStatement: PreparedStatement = connect.prepareStatement(query)
                    preparedStatement.setInt(1, brandId)

                    val resultSet: ResultSet = preparedStatement.executeQuery()

                    while (resultSet.next()) {
                        val modelId = resultSet.getInt("model_id")
                        val modelName = resultSet.getString("name")
                        models.add(CarModel(modelId, modelName))
                    }

                    resultSet.close()
                    preparedStatement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        updateModelSpinner()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityAddCar, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading models: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityAddCar, "Ошибка загрузки моделей", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateModelSpinner() {
        Log.d(TAG, "Updating model spinner with ${models.size} models")

        try {
            val modelNames = models.map { it.modelName }
            val modelAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                modelNames
            )
            modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modelSpinner.adapter = modelAdapter
            modelSpinner.isEnabled = true

            if (isEditMode && originalModel.isNotEmpty()) {
                val modelIndex = models.indexOfFirst { it.modelName == originalModel }
                if (modelIndex != -1) {
                    modelSpinner.setSelection(modelIndex)
                    selectedModelId = models[modelIndex].modelId
                    Log.d(TAG, "Model selected: $originalModel")
                } else {
                    Log.w(TAG, "Model not found: $originalModel")
                    if (models.isNotEmpty()) {
                        modelSpinner.setSelection(0)
                        selectedModelId = models[0].modelId
                    }
                }
            } else if (models.isNotEmpty()) {
                modelSpinner.setSelection(0)
                selectedModelId = models[0].modelId
            }

            saveButton.isEnabled = true
            Log.d(TAG, "Model spinner updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating model spinner: ${e.message}", e)
            Toast.makeText(this, "Ошибка обновления списка моделей", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setClickListeners() {
        Log.d(TAG, "Setting up click listeners")

        try {
            brandSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                    if (position >= 0 && position < brands.size) {
                        selectedBrandId = brands[position].brandId
                        val selectedBrand = brands[position].brandName
                        Log.d(TAG, "Selected brand: $selectedBrand (ID: $selectedBrandId)")

                        modelSpinner.isEnabled = false
                        modelSpinner.adapter = ArrayAdapter(this@MainActivityAddCar, android.R.layout.simple_spinner_item, emptyList<String>())
                        saveButton.isEnabled = false
                        loadModelsFromDatabase(selectedBrandId)
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                    modelSpinner.isEnabled = false
                    saveButton.isEnabled = false
                }
            }

            modelSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                    if (position >= 0 && position < models.size) {
                        selectedModelId = models[position].modelId
                        val selectedModel = models[position].modelName
                        Log.d(TAG, "Selected model: $selectedModel (ID: $selectedModelId)")
                        saveButton.isEnabled = true
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                    saveButton.isEnabled = false
                }
            }

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
                    val deleteMaintenanceQuery = "DELETE FROM Maintenance WHERE car_id = ?"
                    val maintenanceStatement: PreparedStatement = connect.prepareStatement(deleteMaintenanceQuery)
                    maintenanceStatement.setInt(1, currentCarId)
                    maintenanceStatement.executeUpdate()
                    maintenanceStatement.close()

                    val deleteCarQuery = "DELETE FROM Cars WHERE car_id = ? AND user_id = ?"
                    val carStatement: PreparedStatement = connect.prepareStatement(deleteCarQuery)
                    carStatement.setInt(1, currentCarId)
                    carStatement.setInt(2, userId)

                    val rowsAffected = carStatement.executeUpdate()
                    carStatement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        deleteButton.isEnabled = true
                        deleteButton.text = "Удалить"

                        if (rowsAffected > 0) {
                            Toast.makeText(this@MainActivityAddCar, "Автомобиль успешно удален", Toast.LENGTH_LONG).show()

                            // Возвращаем результат с пометкой об удалении
                            val resultIntent = Intent()
                            resultIntent.putExtra("car_deleted", true)
                            resultIntent.putExtra("car_id", currentCarId)
                            setResult(RESULT_CAR_DELETED, resultIntent)

                            finish()
                        } else {
                            Toast.makeText(this@MainActivityAddCar, "Ошибка при удалении автомобиля", Toast.LENGTH_SHORT).show()
                        }
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
            val mileageText = mileageEditText.text.toString().trim()

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
            saveButton.text = "Сохранение..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val connectionHelper = ConnectionHelper()
                    val connect = connectionHelper.connectionclass()

                    if (connect != null) {
                        if (isEditMode) {
                            updateCarInDatabase(connect, userId, mileage)
                        } else {
                            insertCarIntoDatabase(connect, userId, mileage)
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

    private suspend fun insertCarIntoDatabase(connect: java.sql.Connection, userId: Int, mileage: Int) {
        Log.d(TAG, "Inserting new car into database")

        val query = """
            INSERT INTO Cars (user_id, model_id, mileage, photo) 
            VALUES (?, ?, ?, ?)
        """
        val preparedStatement: PreparedStatement = connect.prepareStatement(query)
        preparedStatement.setInt(1, userId)
        preparedStatement.setInt(2, selectedModelId)
        preparedStatement.setInt(3, mileage)

        if (photoBytes != null) {
            preparedStatement.setBytes(4, photoBytes)
        } else {
            preparedStatement.setNull(4, java.sql.Types.VARBINARY)
        }

        val rowsAffected = preparedStatement.executeUpdate()

        preparedStatement.close()
        connect.close()

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

    private suspend fun updateCarInDatabase(connect: java.sql.Connection, userId: Int, mileage: Int) {
        Log.d(TAG, "Updating car in database, ID: $currentCarId")

        val query = """
            UPDATE Cars 
            SET model_id = ?, mileage = ?, photo = ?
            WHERE car_id = ? AND user_id = ?
        """
        val preparedStatement: PreparedStatement = connect.prepareStatement(query)
        preparedStatement.setInt(1, selectedModelId)
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
        connect.close()

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