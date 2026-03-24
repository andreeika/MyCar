package com.example.mycar

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
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class MainActivityAddCar : AppCompatActivity() {

    private lateinit var brandView: TextView
    private lateinit var modelView: TextView
    private lateinit var mileageEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private lateinit var carImageView: ImageView
    private lateinit var cancelImageView: ImageView
    private lateinit var editimageView: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var progressOverlay: FrameLayout

    private val brands = mutableListOf<CarBrand>()
    private val models = mutableListOf<CarModel>()
    private var selectedBrandId: Int = 0
    private var selectedModelId: Int = 0
    private var selectedBrandName: String = ""
    private var selectedModelName: String = ""
    private var photoBytes: ByteArray? = null
    private var isEditMode = false
    private var currentCarId: Int = 0

    companion object {
        private const val TAG = "MainActivityAddCar"
        private const val RESULT_CAR_DELETED = 200
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                carImageView.setImageURI(uri)
                convertImageToByteArray(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_add_car)
        initializeViews()
        checkEditMode()
        setClickListeners()
        setupStatusBarColors()
        loadBrands()
    }

    private fun initializeViews() {
        brandView = findViewById(R.id.brandAutoComplete)
        modelView = findViewById(R.id.modelAutoComplete)
        mileageEditText = findViewById(R.id.textInputEditText)
        saveButton = findViewById(R.id.button)
        deleteButton = findViewById(R.id.deleteButton)
        carImageView = findViewById(R.id.imageView11)
        cancelImageView = findViewById(R.id.imageView2)
        titleTextView = findViewById(R.id.textView2)
        editimageView = findViewById(R.id.editimageView)
        progressOverlay = findViewById(R.id.progressOverlay)

        saveButton.isEnabled = false
        deleteButton.visibility = View.GONE
    }

    private fun checkEditMode() {
        isEditMode = intent.getBooleanExtra("is_edit_mode", false)
        currentCarId = intent.getIntExtra("car_id", 0)

        if (isEditMode) {
            titleTextView.text = "Редактировать"
            saveButton.text = "Сохранить изменения"
            deleteButton.visibility = View.VISIBLE

            selectedBrandName = intent.getStringExtra("brand") ?: ""
            selectedModelName = intent.getStringExtra("model") ?: ""
            val mileage = intent.getIntExtra("mileage", 0)

            brandView.text = selectedBrandName
            brandView.setTextColor(android.graphics.Color.BLACK)
            modelView.text = selectedModelName
            modelView.setTextColor(android.graphics.Color.BLACK)
            mileageEditText.setText(mileage.toString())

            val photoBytesExtra = intent.getByteArrayExtra("photo_bytes")
            if (photoBytesExtra != null && photoBytesExtra.isNotEmpty()) {
                photoBytes = photoBytesExtra
                val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes!!.size)
                carImageView.setImageBitmap(bitmap)
            } else {
                carImageView.setImageResource(R.drawable.ph)
            }
            validateSaveButton()
        } else {
            titleTextView.text = "Добавить авто"
            saveButton.text = "Добавить авто"
            carImageView.setImageResource(R.drawable.ph)
            editimageView.visibility = View.GONE
        }
    }

    private fun setClickListeners() {
        brandView.setOnClickListener { showBrandSearchDialog() }
        modelView.setOnClickListener {
            if (brands.isEmpty()) {
                Toast.makeText(this, "Загрузка марок...", Toast.LENGTH_SHORT).show()
            } else {
                showModelSearchDialog()
            }
        }
        carImageView.setOnClickListener { pickImageLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)) }
        cancelImageView.setOnClickListener { finish() }
        deleteButton.setOnClickListener { showDeleteConfirmationDialog() }
        saveButton.setOnClickListener { saveCarToDatabase() }
        mileageEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { validateSaveButton() }
        })
    }

    private fun loadBrands() {
        progressOverlay.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arr = ApiClient.getBrands()
                brands.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    brands.add(CarBrand(obj.getInt("brand_id"), obj.getString("name")))
                }
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    // в режиме редактирования найти brand_id по имени
                    if (isEditMode && selectedBrandName.isNotEmpty()) {
                        val found = brands.find { it.brandName.equals(selectedBrandName, ignoreCase = true) }
                        if (found != null) {
                            selectedBrandId = found.brandId
                            loadModels(selectedBrandId)
                        }
                    }
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@MainActivityAddCar, friendlyError(ex), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadModels(brandId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arr = ApiClient.getModels(brandId)
                models.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    models.add(CarModel(obj.getInt("model_id"), obj.getString("name")))
                }
                if (isEditMode && selectedModelName.isNotEmpty()) {
                    val found = models.find { it.modelName.equals(selectedModelName, ignoreCase = true) }
                    if (found != null) {
                        withContext(Dispatchers.Main) { selectedModelId = found.modelId }
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading models: ${ex.message}", ex)
            }
        }
    }

    private fun showBrandSearchDialog() {
        showSearchDialog(
            title = "Марка автомобиля",
            hint = "Поиск марки...",
            allItems = brands.map { it.brandName },
            allowCustom = true,
            onSelected = { name, isFromList ->
                selectedBrandName = name
                brandView.text = name
                brandView.setTextColor(android.graphics.Color.BLACK)
                // сброс модели
                selectedModelId = 0
                selectedModelName = ""
                modelView.text = ""
                modelView.hint = "Выберите модель..."
                models.clear()

                if (isFromList) {
                    val brand = brands.find { it.brandName == name }
                    if (brand != null) {
                        selectedBrandId = brand.brandId
                        loadModels(selectedBrandId)
                    }
                } else {
                    selectedBrandId = 0
                }
                validateSaveButton()
            }
        )
    }

    private fun showModelSearchDialog() {
        val modelNames = if (models.isNotEmpty()) models.map { it.modelName }
                         else emptyList()
        showSearchDialog(
            title = "Модель автомобиля",
            hint = "Поиск модели...",
            allItems = modelNames,
            allowCustom = true,
            onSelected = { name, isFromList ->
                selectedModelName = name
                modelView.text = name
                modelView.setTextColor(android.graphics.Color.BLACK)
                if (isFromList) {
                    val model = models.find { it.modelName == name }
                    selectedModelId = model?.modelId ?: 0
                } else {
                    selectedModelId = 0
                }
                validateSaveButton()
            }
        )
    }

    private fun showSearchDialog(
        title: String,
        hint: String,
        allItems: List<String>,
        allowCustom: Boolean,
        onSelected: (name: String, isFromList: Boolean) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_service_type_search, null)
        val searchView = dialogView.findViewById<SearchView>(R.id.searchView)
        val listView = dialogView.findViewById<ListView>(R.id.listViewServiceTypes)
        searchView.queryHint = hint

        var filtered = allItems.toMutableList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered)
        listView.adapter = adapter

        var dialog: androidx.appcompat.app.AlertDialog? = null

        listView.setOnItemClickListener { _, _, position, _ ->
            onSelected(filtered[position], true)
            dialog?.dismiss()
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val q = newText?.lowercase()?.trim() ?: ""
                filtered = allItems.filter { it.lowercase().contains(q) }.toMutableList()
                adapter.clear()
                adapter.addAll(filtered)
                adapter.notifyDataSetChanged()
                return true
            }
        })

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setNegativeButton("Отмена", null)

        if (allowCustom) {
            builder.setNeutralButton("Ввести вручную") { _, _ ->
                showManualInputDialog(title, onSelected, onBack = {
                    showSearchDialog(title, hint, allItems, allowCustom, onSelected)
                })
            }
        }

        dialog = builder.create()
        dialog.show()
    }

    private fun showManualInputDialog(
        title: String,
        onSelected: (name: String, isFromList: Boolean) -> Unit,
        onBack: (() -> Unit)? = null
    ) {
        val editText = EditText(this).apply {
            hint = "Введите название"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setPadding(48, 24, 48, 24)
        }

        val d = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("Готово") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) onSelected(name, false)
                else Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Назад") { _, _ -> onBack?.invoke() }
            .create()

        d.show()
        editText.requestFocus()
    }

    private fun validateSaveButton() {
        saveButton.isEnabled = brandView.text.isNotEmpty() &&
                modelView.text.isNotEmpty() &&
                mileageEditText.text.isNotEmpty()
    }

    private fun convertImageToByteArray(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stream: InputStream? = contentResolver.openInputStream(uri)
                stream?.use { photoBytes = it.readBytes() }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting image: ${e.message}", e)
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Удаление автомобиля")
            .setMessage("Вы уверены? Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ -> deleteCar() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteCar() {
        val userId = SessionManager(this).getUserId()
        if (userId == 0) { Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show(); return }
        deleteButton.isEnabled = false
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.deleteCar(userId, currentCarId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityAddCar, "Автомобиль удалён", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_CAR_DELETED, Intent().apply {
                        putExtra("car_deleted", true); putExtra("car_id", currentCarId)
                    })
                    finish()
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    deleteButton.isEnabled = true
                    Toast.makeText(this@MainActivityAddCar, friendlyError(ex), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveCarToDatabase() {
        val mileageText = mileageEditText.text.toString().trim()
        val mileage = mileageText.toIntOrNull()
        if (mileage == null || mileage < 0) { Toast.makeText(this, "Некорректный пробег", Toast.LENGTH_SHORT).show(); return }

        val userId = SessionManager(this).getUserId()
        if (userId == 0) { Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show(); return }

        saveButton.isEnabled = false
        progressOverlay.visibility = View.VISIBLE
        val photoBase64 = photoBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isEditMode) {
                    ApiClient.updateCar(
                        userId, currentCarId,
                        if (selectedBrandId > 0) selectedBrandId else null,
                        if (selectedBrandId == 0) selectedBrandName else null,
                        if (selectedModelId > 0) selectedModelId else null,
                        if (selectedModelId == 0) selectedModelName else null,
                        mileage.toDouble(), photoBase64
                    )
                } else {
                    ApiClient.addCar(
                        userId,
                        if (selectedBrandId > 0) selectedBrandId else null,
                        if (selectedBrandId == 0) selectedBrandName else null,
                        if (selectedModelId > 0) selectedModelId else null,
                        if (selectedModelId == 0) selectedModelName else null,
                        mileage.toDouble(), photoBase64
                    )
                }
                withContext(Dispatchers.Main) {
                    if (isEditMode) ApiClient.invalidatePhotoCache(currentCarId)
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@MainActivityAddCar,
                        if (isEditMode) "Автомобиль обновлён!" else "Автомобиль добавлен!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK); finish()
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    saveButton.isEnabled = true
                    Toast.makeText(this@MainActivityAddCar, friendlyError(ex), Toast.LENGTH_LONG).show()
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

data class CarBrand(val brandId: Int, val brandName: String)
data class CarModel(val modelId: Int, val modelName: String)
