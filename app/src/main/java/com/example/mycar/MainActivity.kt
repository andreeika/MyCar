package com.example.mycar

import SessionManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

class MainActivity : AppCompatActivity() {
    private val sessionManager by lazy { SessionManager(this) }
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val TAG = "MainActivity"
        private const val PREF_CAR_ID = "current_car_id"
        private const val PREF_CAR_NAME = "current_car_name"
    }

    private lateinit var buttonAddCar: Button
    private lateinit var textView_Brand: TextView
    private lateinit var textView_Name: TextView
    private lateinit var textView_Probeg: TextView
    private lateinit var imageView: ImageView
    private lateinit var exit: ImageView
    private lateinit var refueling: ImageView
    private lateinit var service: ImageView
    private lateinit var statistics : ImageView
    private lateinit var notification: ImageView
    private lateinit var carSpinner: Spinner
    private lateinit var spinnerContainer: LinearLayout
    private lateinit var mainContainer: ConstraintLayout

    var connect: Connection? = null
    var connectionResult: String = ""

    private val userCars = mutableListOf<Car>()
    private var selectedCarId: Int = -1
    private var isSpinnerVisible = false

    data class Car(
        val id: Int,
        val brand: String,
        val model: String,
        val mileage: Double,
        val photoBytes: ByteArray?,
        val displayName: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("my_car_prefs", MODE_PRIVATE)
        initViews()
        setupStatusBarColors()
        loadUserCars()
        setupClickListeners()
        setupCarCardClickListener()

        // Восстанавливаем последний выбранный автомобиль при запуске
        restoreLastSelectedCar()
    }

    private fun initViews() {
        textView_Brand = findViewById(R.id.textView)
        textView_Name = findViewById(R.id.textView4)
        textView_Probeg = findViewById(R.id.textView5)
        buttonAddCar = findViewById(R.id.button)
        imageView = findViewById(R.id.imageView)
        exit = findViewById(R.id.imageView3)
        refueling = findViewById(R.id.imageView4)
        statistics = findViewById(R.id.imageView7)
        service = findViewById(R.id.imageView5)
        notification = findViewById(R.id.imageView2)
        carSpinner = findViewById(R.id.carSpinner)
        spinnerContainer = findViewById(R.id.spinnerContainer)
        mainContainer = findViewById(R.id.main)

        // Изначально скрываем спиннер
        spinnerContainer.visibility = View.GONE
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
        buttonAddCar.setOnClickListener {
            val intent = Intent(this@MainActivity, MainActivityAddCar::class.java)
            startActivity(intent)
        }

        refueling.setOnClickListener {
            val intent = Intent(this@MainActivity, MainActivityRefueling::class.java)
            startActivity(intent)
        }

        service.setOnClickListener {
            val intent = Intent(this@MainActivity, MainActivityMaintenance::class.java)
            startActivity(intent)
        }

        statistics.setOnClickListener {
            val intent = Intent(this@MainActivity, MainActivityStatistics::class.java)
            startActivity(intent)
        }

        notification.setOnClickListener {
            val intent = Intent(this@MainActivity, MainActivityNotifications::class.java)
            startActivity(intent)
        }

        // Кнопка выхода из аккаунта
        exit.setOnClickListener {
            showLogoutConfirmation()
        }

        // Обработчик нажатия на заголовок "Моя машина" для показа спиннера
        val titleTextView = findViewById<TextView>(R.id.textView8)
        titleTextView.setOnClickListener {
            if (isSpinnerVisible) {
                hideSpinner()
            } else {
                showSpinner()
            }
        }
    }

    private fun showSpinner() {
        if (userCars.isEmpty()) {
            Toast.makeText(this, "У вас нет добавленных автомобилей", Toast.LENGTH_SHORT).show()
            return
        }

        isSpinnerVisible = true
        spinnerContainer.visibility = View.VISIBLE

        // Принудительно запрашиваем перерисовку layout
        spinnerContainer.requestLayout()
        mainContainer.requestLayout()
    }

    private fun hideSpinner() {
        if (isSpinnerVisible) {
            isSpinnerVisible = false
            spinnerContainer.visibility = View.GONE
        }
    }

    private fun loadUserCars() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                connect = connectionHelper.connectionclass()
                val userId = sessionManager.getUserId()

                if (connect != null) {
                    val query: String = "EXEC GetCarsWithBrandInfo @UserId = $userId"
                    val st: Statement = connect!!.createStatement()
                    val rs: ResultSet = st.executeQuery(query)

                    userCars.clear()

                    while (rs.next()) {
                        val carId = rs.getInt("car_id")
                        val brand = rs.getString("brand_name") ?: ""
                        val model = rs.getString("model_name") ?: ""
                        val mileage = rs.getDouble("mileage")
                        val imageBytes: ByteArray? = rs.getBytes("photo")
                        val displayName = if (brand.isNotEmpty() && model.isNotEmpty()) {
                            "$brand $model"
                        } else {
                            "Автомобиль ${userCars.size + 1}"
                        }

                        userCars.add(Car(carId, brand, model, mileage, imageBytes, displayName))
                    }

                    rs.close()
                    st.close()

                    launch(Dispatchers.Main) {
                        setupCarSpinner()
                        if (userCars.isNotEmpty()) {
                            // Восстанавливаем выбранный автомобиль или выбираем первый
                            restoreCarSelection()
                        } else {
                            showNoCarsMessage()
                        }
                    }
                } else {
                    connectionResult = "Check Connection"
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, connectionResult, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                connectionResult = "Error: ${ex.message}"
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, connectionResult, Toast.LENGTH_SHORT).show()
                    textView_Brand.text = connectionResult
                }
            }
        }
    }

    private fun setupCarSpinner() {
        if (userCars.isEmpty()) return

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            userCars.map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        carSpinner.adapter = adapter

        carSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in userCars.indices) {
                    val selectedCar = userCars[position]
                    selectedCarId = selectedCar.id
                    displayCarInfo(selectedCar)
                    saveCarSelection(selectedCar.id, selectedCar.displayName)
                    hideSpinner()
                    Toast.makeText(this@MainActivity, "Выбран: ${selectedCar.displayName}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun restoreLastSelectedCar() {
        val savedCarId = sharedPreferences.getInt(PREF_CAR_ID, -1)
        val savedCarName = sharedPreferences.getString(PREF_CAR_NAME, "")

        if (savedCarId != -1 && savedCarName?.isNotEmpty() == true) {
            // Показываем сохраненный автомобиль до загрузки списка
            textView_Brand.text = savedCarName
            textView_Name.text = "Загрузка..."
            textView_Probeg.text = "км"
        }
    }

    private fun restoreCarSelection() {
        val savedCarId = sharedPreferences.getInt(PREF_CAR_ID, -1)

        if (savedCarId != -1) {
            val savedCarIndex = userCars.indexOfFirst { it.id == savedCarId }
            if (savedCarIndex != -1) {
                // Выбираем сохраненный автомобиль в спиннере
                carSpinner.setSelection(savedCarIndex)
                val selectedCar = userCars[savedCarIndex]
                displayCarInfo(selectedCar)
                selectedCarId = selectedCar.id
                return
            }
        }

        // Если сохраненный автомобиль не найден, выбираем первый
        if (userCars.isNotEmpty()) {
            carSpinner.setSelection(0)
            val firstCar = userCars[0]
            displayCarInfo(firstCar)
            selectedCarId = firstCar.id
            saveCarSelection(firstCar.id, firstCar.displayName)
        }
    }

    private fun saveCarSelection(carId: Int, carName: String) {
        sharedPreferences.edit()
            .putInt(PREF_CAR_ID, carId)
            .putString(PREF_CAR_NAME, carName)
            .apply()

        Log.d(TAG, "Saved car selection: ID=$carId, Name=$carName")
    }

    private fun displayCarInfo(car: Car) {
        textView_Brand.text = car.brand.ifEmpty { "Марка не указана" }
        textView_Name.text = car.model.ifEmpty { "Модель не указана" }
        textView_Probeg.text = "${car.mileage} км"

        if (car.photoBytes != null && car.photoBytes.isNotEmpty()) {
            val bitmap = BitmapFactory.decodeByteArray(car.photoBytes, 0, car.photoBytes.size)
            imageView.setImageBitmap(bitmap)
        } else {
            imageView.setImageResource(R.drawable.add_photo)
        }
    }

    private fun showNoCarsMessage() {
        textView_Brand.text = "Нет автомобилей"
        textView_Name.text = "Добавьте первый автомобиль"
        textView_Probeg.text = "0 км"
        imageView.setImageResource(R.drawable.add_photo)

        // Очищаем сохраненный выбор
        sharedPreferences.edit()
            .remove(PREF_CAR_ID)
            .remove(PREF_CAR_NAME)
            .apply()
    }

    private fun showLogoutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выход из аккаунта")
            .setMessage("Вы уверены, что хотите выйти?")
            .setPositiveButton("Выйти") { dialog, which -> performLogout() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun performLogout() {
        // Очищаем сохраненные данные при выходе
        sharedPreferences.edit()
            .remove(PREF_CAR_ID)
            .remove(PREF_CAR_NAME)
            .apply()

        sessionManager.logout()
        Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivityLogin::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        loadUserCars()
    }

    override fun onBackPressed() {
        if (isSpinnerVisible) {
            hideSpinner()
        } else {
            super.onBackPressed()
        }
    }

    // Обработка нажатия на экран для скрытия спиннера
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && isSpinnerVisible) {
            val spinnerView = spinnerContainer

            val spinnerLocation = IntArray(2)
            spinnerView.getLocationOnScreen(spinnerLocation)

            val x = ev.rawX
            val y = ev.rawY

            val isTouchInsideSpinner = (x >= spinnerLocation[0] && x <= spinnerLocation[0] + spinnerView.width &&
                    y >= spinnerLocation[1] && y <= spinnerLocation[1] + spinnerView.height)

            // Если нажатие было вне спиннера - скрываем его
            if (!isTouchInsideSpinner) {
                hideSpinner()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupCarCardClickListener() {
        val carCardView = findViewById<com.google.android.material.card.MaterialCardView>(R.id.carCardView)
        carCardView.setOnClickListener {
            if (userCars.isNotEmpty() && selectedCarId != -1) {
                val selectedCar = userCars.find { it.id == selectedCarId }
                selectedCar?.let { car ->
                    openCarForEditing(car)
                }
            } else {
                // Если нет автомобилей, открываем пустую форму для добавления
                val intent = Intent(this@MainActivity, MainActivityAddCar::class.java)
                startActivity(intent)
            }
        }
    }

    private fun openCarForEditing(car: Car) {
        val intent = Intent(this@MainActivity, MainActivityAddCar::class.java)
        intent.putExtra("is_edit_mode", true)
        intent.putExtra("car_id", car.id)
        intent.putExtra("brand", car.brand)
        intent.putExtra("model", car.model)
        intent.putExtra("mileage", car.mileage.toInt())

        // Безопасная передача фото
        if (car.photoBytes != null && car.photoBytes.isNotEmpty()) {
            intent.putExtra("photo_bytes", car.photoBytes)
        }

        Log.d(TAG, "Opening car for editing: ${car.displayName}, ID: ${car.id}")
        startActivity(intent)
    }
}