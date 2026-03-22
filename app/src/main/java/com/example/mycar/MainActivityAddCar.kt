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

class MainActivityAddCar : AppCompatActivity() {
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

    private var isBrandSelectedFromList = false
    private var isModelSelectedFromList = false

    private var isBrandInDb = false
    private var isModelInDb = false

    private var manualBrandName: String = ""
    private var manualModelName: String = ""

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

            modelAutoComplete.isEnabled = false
            modelTextInputLayout.isEnabled = false
            saveButton.isEnabled = false
            deleteButton.visibility = View.GONE

            brandAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
            modelAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())

            brandAutoComplete.setAdapter(brandAdapter)
            modelAutoComplete.setAdapter(modelAdapter)

            brandAutoComplete.threshold = 1
            modelAutoComplete.threshold = 1

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
        brandAutoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!isBrandSelectedFromList) {
                    val brandText = s?.toString()?.trim() ?: ""
                    if (brandText.isNotEmpty()) {
                        checkBrandInDatabase(brandText)
                    } else {
                        selectedBrandId = 0
                        isBrandInDb = false
                        manualBrandName = ""
                    }

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

        brandAutoComplete.setOnItemClickListener { parent, view, position, id ->
            isBrandSelectedFromList = true
            val selectedBrandName = parent.getItemAtPosition(position) as String
            val selectedBrand = brands.find { it.brandName == selectedBrandName }
            if (selectedBrand != null) {
                selectedBrandId = selectedBrand.brandId
                isBrandInDb = true
                manualBrandName = ""
                Log.d(TAG, "Selected brand from DB: $selectedBrandName (ID: $selectedBrandId)")
                loadModelsFromDatabase(selectedBrandId)
            }
        }

        modelAutoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!isModelSelectedFromList) {
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
                val arr = ApiClient.getBrands()
                val found = (0 until arr.length()).map { arr.getJSONObject(it) }
                    .find { it.getString("name").equals(brandName, ignoreCase = true) }
                withContext(Dispatchers.Main) {
                    if (found != null) {
                        selectedBrandId = found.getInt("brand_id"); isBrandInDb = true; manualBrandName = ""
                        loadModelsFromDatabase(selectedBrandId)
                    } else {
                        selectedBrandId = 0; isBrandInDb = false; manualBrandName = brandName
                        modelAutoComplete.isEnabled = true; modelTextInputLayout.isEnabled = true
                        modelTextInputLayout.hint = "Введите модель"
                        modelAutoComplete.setText(""); modelAdapter.clear(); modelAdapter.notifyDataSetChanged()
                    }
                }
            } catch (ex: Exception) { Log.e(TAG, "Error checking brand: ${ex.message}", ex) }
        }
    }

    private fun checkModelInDatabase(modelName: String, brandId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arr = if (brandId > 0) ApiClient.getModels(brandId) else org.json.JSONArray()
                val found = (0 until arr.length()).map { arr.getJSONObject(it) }
                    .find { it.getString("name").equals(modelName, ignoreCase = true) }
                withContext(Dispatchers.Main) {
                    if (found != null) {
                        selectedModelId = found.getInt("model_id"); isModelInDb = true; manualModelName = ""
                    } else {
                        selectedModelId = 0; isModelInDb = false; manualModelName = modelName
                    }
                    validateAndEnableSaveButton()
                }
            } catch (ex: Exception) { Log.e(TAG, "Error checking model: ${ex.message}", ex) }
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arr = ApiClient.getBrands()
                brands.clear()
                val brandNames = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    brands.add(CarBrand(obj.getInt("brand_id"), obj.getString("name")))
                    brandNames.add(obj.getString("name"))
                }
                withContext(Dispatchers.Main) { updateBrandAutoComplete(brandNames) }
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arr = ApiClient.getModels(brandId)
                models.clear()
                val modelNames = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    models.add(CarModel(obj.getInt("model_id"), obj.getString("name")))
                    modelNames.add(obj.getString("name"))
                }
                withContext(Dispatchers.Main) { updateModelAutoComplete(modelNames) }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading models: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityAddCar, "Ошибка загрузки моделей", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateModelAutoComplete(modelNames: List<String>) {
        Log.d(TAG, "Updating model autocomplete with ${modelNames.size} models")

        try {
            modelAdapter.clear()

            if (modelNames.isNotEmpty()) {
                modelAdapter.addAll(modelNames)
                modelAdapter.notifyDataSetChanged()

                modelAutoComplete.isEnabled = true
                modelTextInputLayout.isEnabled = true
                modelTextInputLayout.hint = "Выберите модель"

                modelAutoComplete.setAdapter(modelAdapter)

                if (modelAutoComplete.text.toString().trim().isEmpty()) {
                    modelAutoComplete.setText("")
                }

                modelAutoComplete.post {
                    modelAutoComplete.showDropDown()
                    Log.d(TAG, "Showing dropdown with ${modelNames.size} items")
                }

                Log.d(TAG, "Model adapter updated with ${modelNames.size} items")
            } else {
                modelAutoComplete.isEnabled = true
                modelTextInputLayout.isEnabled = true
                modelTextInputLayout.hint = "Нет моделей для этой марки"
                modelAdapter.notifyDataSetChanged()
                Log.d(TAG, "No models found for this brand")
            }

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
        val sessionManager = SessionManager(this)
        val userId = sessionManager.getUserId()
        if (userId == 0) { Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show(); return }

        deleteButton.isEnabled = false; deleteButton.text = "Удаление..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.deleteCar(userId, currentCarId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityAddCar, "Автомобиль удалён", Toast.LENGTH_SHORT).show()
                    val resultIntent = Intent().apply {
                        putExtra("car_deleted", true); putExtra("car_id", currentCarId)
                    }
                    setResult(RESULT_CAR_DELETED, resultIntent); finish()
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    deleteButton.isEnabled = true; deleteButton.text = "Удалить"
                    Toast.makeText(this@MainActivityAddCar, "Ошибка удаления: ${friendlyError(ex)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveCarToDatabase() {
        val brandText   = brandAutoComplete.text.toString().trim()
        val modelText   = modelAutoComplete.text.toString().trim()
        val mileageText = mileageEditText.text.toString().trim()

        if (brandText.isEmpty())   { Toast.makeText(this, "Введите марку", Toast.LENGTH_SHORT).show(); return }
        if (modelText.isEmpty())   { Toast.makeText(this, "Введите модель", Toast.LENGTH_SHORT).show(); return }
        if (mileageText.isEmpty()) { Toast.makeText(this, "Введите пробег", Toast.LENGTH_SHORT).show(); return }
        val mileage = mileageText.toIntOrNull()
        if (mileage == null || mileage < 0) { Toast.makeText(this, "Некорректный пробег", Toast.LENGTH_SHORT).show(); return }

        val sessionManager = SessionManager(this)
        val userId = sessionManager.getUserId()
        if (userId == 0) { Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show(); return }

        saveButton.isEnabled = false
        saveButton.text = if (isEditMode) "Сохранение..." else "Добавление..."

        val photoBase64 = photoBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isEditMode) {
                    ApiClient.updateCar(
                        userId, currentCarId,
                        if (isBrandInDb) selectedBrandId else null,
                        if (!isBrandInDb) manualBrandName.ifEmpty { brandText } else null,
                        if (isModelInDb) selectedModelId else null,
                        if (!isModelInDb) manualModelName.ifEmpty { modelText } else null,
                        mileage.toDouble(), photoBase64
                    )
                } else {
                    ApiClient.addCar(
                        userId,
                        if (isBrandInDb) selectedBrandId else null,
                        if (!isBrandInDb) manualBrandName.ifEmpty { brandText } else null,
                        if (isModelInDb) selectedModelId else null,
                        if (!isModelInDb) manualModelName.ifEmpty { modelText } else null,
                        mileage.toDouble(), photoBase64
                    )
                }
                withContext(Dispatchers.Main) {
                    if (isEditMode) ApiClient.invalidatePhotoCache(currentCarId)
                    val msg = if (isEditMode) "Автомобиль обновлён!" else "Автомобиль добавлен!"
                    Toast.makeText(this@MainActivityAddCar, msg, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK); finish()
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    saveButton.isEnabled = true
                    saveButton.text = if (isEditMode) "Сохранить изменения" else "Добавить авто"
                    Toast.makeText(this@MainActivityAddCar, "Ошибка: ${friendlyError(ex)}", Toast.LENGTH_LONG).show()
                }
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