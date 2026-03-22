package com.example.mycar

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64

class MainActivity : AppCompatActivity() {

    private val sessionManager by lazy { SessionManager(this) }
    private lateinit var sharedPreferences: SharedPreferences
    private val notificationManager = NotificationManager()

    companion object {
        private const val TAG = "MainActivity"
        private const val PREF_CAR_ID = "current_car_id"
        private const val PREF_CAR_NAME = "current_car_name"
        private const val REQUEST_CODE_ADD_CAR = 1001
        private const val REQUEST_CODE_REFUELING = 1002
        private const val REQUEST_CODE_MAINTENANCE = 1003
    }

    private lateinit var buttonAddCar: Button
    private lateinit var textView_Brand: TextView
    private lateinit var textView_Name: TextView
    private lateinit var textView_Probeg: TextView
    private lateinit var imageView: ImageView
    private lateinit var refueling: ImageView
    private lateinit var service: ImageView
    private lateinit var statistics: ImageView
    private lateinit var notification: ImageView
    private lateinit var mainContainer: ConstraintLayout
    private lateinit var carCardView: CardView
    private lateinit var textView_Car: TextView

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var imageViewMenu: ImageView

    private lateinit var carsContainer: LinearLayout
    private lateinit var noCarsText: TextView
    private lateinit var navUserName: TextView
    private lateinit var navUserEmail: TextView
    private lateinit var progressOverlay: android.widget.FrameLayout
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private var userEmail: String = ""

    private val userCars = mutableListOf<Car>()
    private var selectedCarId: Int = -1
    private var isCarsLoaded = false

    data class Car(
        val id: Int,
        val brand: String,
        val model: String,
        var mileage: Double,
        val photoBytes: ByteArray?,
        val displayName: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Car

            if (id != other.id) return false
            if (brand != other.brand) return false
            if (model != other.model) return false
            if (mileage != other.mileage) return false
            if (displayName != other.displayName) return false
            if (photoBytes != null) {
                if (other.photoBytes == null) return false
                if (!photoBytes.contentEquals(other.photoBytes)) return false
            } else if (other.photoBytes != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id
            result = 31 * result + brand.hashCode()
            result = 31 * result + model.hashCode()
            result = 31 * result + mileage.hashCode()
            result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
            result = 31 * result + displayName.hashCode()
            return result
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("my_car_prefs", MODE_PRIVATE)

        initViews()
        setupStatusBarColors()
        setupNavigationDrawer()
        loadUserCars()
        setupClickListeners()
        setupCarCardClickListener()
        restoreLastSelectedCar()
        setupCarTooltip()
    }

    private fun initViews() {
        textView_Brand = findViewById(R.id.textView)
        textView_Name = findViewById(R.id.textView4)
        textView_Probeg = findViewById(R.id.textView5)
        buttonAddCar = findViewById(R.id.button)
        imageView = findViewById(R.id.imageView)
        refueling = findViewById(R.id.imageView4)
        statistics = findViewById(R.id.imageView7)
        service = findViewById(R.id.imageView5)
        notification = findViewById(R.id.imageView2)
        mainContainer = findViewById(R.id.main)
        textView_Car = findViewById(R.id.textView8)
        carCardView = findViewById(R.id.carCardView)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        imageViewMenu = findViewById(R.id.imageViewMenu)

        val headerView = navigationView.getHeaderView(0)
        navUserName = headerView.findViewById<TextView>(R.id.navUserName)
        navUserEmail = headerView.findViewById<TextView>(R.id.navUserEmail)
        carsContainer = headerView.findViewById(R.id.carsContainer)
        noCarsText = headerView.findViewById(R.id.noCarsText)
        progressOverlay = findViewById(R.id.progressOverlay)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeColors(android.graphics.Color.parseColor("#228BE6"))
        swipeRefresh.setOnRefreshListener { loadUserCars(forceReload = true) }
    }

    private fun setupStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }

    private fun setupNavigationDrawer() {
        updateNavigationHeader()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            handleNavigationItem(menuItem.itemId)
        }

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                imageViewMenu.rotation = slideOffset * 90
            }
            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })
    }

    private fun updateNavigationHeader() {
        navUserName.text = sessionManager.getUserName()?.ifEmpty { "Пользователь" }

        val email = userEmail.ifEmpty {
            sessionManager.getUserEmail()?.ifEmpty { "email@example.com" }
        }
        navUserEmail.text = email
    }

    private fun displayCarsInMenu() {
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until carsContainer.childCount) {
            val child = carsContainer.getChildAt(i)
            if (child.tag == "car_item" || child.tag == "divider") {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { carsContainer.removeView(it) }

        if (userCars.isEmpty()) {
            noCarsText.visibility = View.VISIBLE
            return
        }

        noCarsText.visibility = View.GONE

        userCars.forEachIndexed { index, car ->
            val carView = layoutInflater.inflate(R.layout.nav_car_item, carsContainer, false)

            val carNameTextView = carView.findViewById<TextView>(R.id.carName)
            val carMileageTextView = carView.findViewById<TextView>(R.id.carMileage)
            val selectedIndicator = carView.findViewById<ImageView>(R.id.carSelectedIndicator)

            carNameTextView.text = car.displayName
            carMileageTextView.text = "Пробег: ${car.mileage.toInt()} км"

            if (car.id == selectedCarId) {
                selectedIndicator.visibility = View.VISIBLE
                carView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryLight))
            } else {
                selectedIndicator.visibility = View.GONE
                carView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
            }

            carView.setOnClickListener { selectCarFromMenu(car) }

            carView.tag = "car_item"
            carsContainer.addView(carView)

            if (index < userCars.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        marginStart = 16
                        marginEnd = 16
                    }
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                    tag = "divider"
                }
                carsContainer.addView(divider)
            }
        }
    }

    private fun selectCarFromMenu(car: Car) {
        selectedCarId = car.id
        displayCarInfo(car)
        saveCarSelection(car.id, car.displayName)
        updateCarSelectionInMenu()
        drawerLayout.close()
        Toast.makeText(this, "Выбран: ${car.displayName}", Toast.LENGTH_SHORT).show()

        // Загружаем фото если его ещё нет
        if (car.photoBytes == null) {
            val userId = sessionManager.getUserId()
            lifecycleScope.launch(Dispatchers.IO) {
                val photoBytes = ApiClient.getCarPhotoBytes(userId, car.id)
                if (photoBytes != null) {
                    val idx = userCars.indexOfFirst { it.id == car.id }
                    if (idx != -1) {
                        userCars[idx] = userCars[idx].copy(photoBytes = photoBytes)
                        withContext(Dispatchers.Main) {
                            if (selectedCarId == car.id) displayCarInfo(userCars[idx])
                        }
                    }
                }
            }
        }
    }

    private fun updateCarSelectionInMenu() {
        for (i in 0 until carsContainer.childCount) {
            val child = carsContainer.getChildAt(i)
            if (child.tag == "car_item") {
                val indicator = child.findViewById<ImageView>(R.id.carSelectedIndicator)
                val carNameView = child.findViewById<TextView>(R.id.carName)

                indicator?.visibility = View.GONE
                child.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))

                if (carNameView?.text.toString() == userCars.find { it.id == selectedCarId }?.displayName) {
                    indicator?.visibility = View.VISIBLE
                    child.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryLight))
                }
            }
        }
    }

    private fun handleNavigationItem(itemId: Int): Boolean {
        when (itemId) {
            R.id.nav_help -> {
                startActivity(Intent(this, MainActivityHelp::class.java))
                drawerLayout.close()
                return true
            }
            R.id.nav_logout -> {
                showLogoutConfirmation()
                drawerLayout.close()
                return true
            }
            R.id.nav_settings -> {
                drawerLayout.close()
                progressOverlay.visibility = View.VISIBLE
                val intent = Intent(this, MainActivitySettings::class.java)
                startActivityForResult(intent, 100)
                return true
            }
            else -> return false
        }
    }

    private fun setupClickListeners() {
        buttonAddCar.setOnClickListener {
            val intent = Intent(this@MainActivity, MainActivityAddCar::class.java)
            startActivityForResult(intent, REQUEST_CODE_ADD_CAR)
        }

        refueling.setOnClickListener {
            if (selectedCarId != -1) {
                val intent = Intent(this@MainActivity, MainActivityRefueling::class.java)
                intent.putExtra("car_id", selectedCarId)
                intent.putExtra("car_model", getCarModelById(selectedCarId))
                startActivityForResult(intent, REQUEST_CODE_REFUELING)
            } else {
                Toast.makeText(this, "Сначала выберите автомобиль", Toast.LENGTH_SHORT).show()
            }
        }

        service.setOnClickListener {
            if (selectedCarId != -1) {
                val intent = Intent(this@MainActivity, MainActivityMaintenance::class.java)
                intent.putExtra("car_id", selectedCarId)
                startActivityForResult(intent, REQUEST_CODE_MAINTENANCE)
            } else {
                Toast.makeText(this, "Сначала выберите автомобиль", Toast.LENGTH_SHORT).show()
            }
        }

        statistics.setOnClickListener {
            if (selectedCarId != -1) {
                val intent = Intent(this@MainActivity, MainActivityStatistics::class.java)
                intent.putExtra("car_id", selectedCarId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Сначала выберите автомобиль", Toast.LENGTH_SHORT).show()
            }
        }

        notification.setOnClickListener {
            startActivity(Intent(this@MainActivity, MainActivityNotifications::class.java))
        }

        imageViewMenu.setOnClickListener {
            drawerLayout.open()
        }
    }

    private fun getCarModelById(carId: Int): String {
        return userCars.find { it.id == carId }?.displayName ?: ""
    }

    private fun loadUserCars(forceReload: Boolean = false) {
        if (isCarsLoaded && !forceReload) {
            displayCarsInMenu()
            return
        }

        progressOverlay.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userId = sessionManager.getUserId()
                val carsArray = ApiClient.getCars(userId)
                val newCarsList = mutableListOf<Car>()

                for (i in 0 until carsArray.length()) {
                    val obj = carsArray.getJSONObject(i)
                    val carId   = obj.getInt("car_id")
                    val brand   = obj.optString("brand", "")
                    val model   = obj.optString("model", "")
                    val mileage = obj.optDouble("mileage", 0.0)
                    val displayName = if (brand.isNotEmpty() && model.isNotEmpty()) "$brand $model"
                                      else "Автомобиль ${newCarsList.size + 1}"
                    // фото грузим отдельно только для выбранного авто (экономим трафик)
                    newCarsList.add(Car(carId, brand, model, mileage, null, displayName))
                }

                val resolvedCarId = withContext(Dispatchers.Main) {
                    val oldSelectedCarId = selectedCarId
                    userCars.clear()
                    userCars.addAll(newCarsList)

                    if (userCars.isNotEmpty()) {
                        val prev = if (oldSelectedCarId != -1) userCars.find { it.id == oldSelectedCarId } else null
                        if (prev != null) { selectedCarId = prev.id; displayCarInfo(prev) }
                        else restoreCarSelection()
                    } else {
                        showNoCarsMessage()
                    }

                    displayCarsInMenu()
                    updateNavigationHeader()
                    isCarsLoaded = true
                    progressOverlay.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    selectedCarId
                }

                // загружаем фото выбранного авто (используем resolvedCarId из Main-потока)
                if (resolvedCarId != -1) {
                    val photoBytes = ApiClient.getCarPhotoBytes(userId, resolvedCarId)
                    if (photoBytes != null) {
                        val idx = userCars.indexOfFirst { it.id == resolvedCarId }
                        if (idx != -1) {
                            userCars[idx] = userCars[idx].copy(photoBytes = photoBytes)
                            withContext(Dispatchers.Main) {
                                displayCarInfo(userCars[idx])
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading cars: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(this@MainActivity, "${friendlyError(ex)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showTooltip(view: View, message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setupCarTooltip() {
        textView_Car.setOnLongClickListener {
            showTooltip(it, "Нажмите для выбора другого автомобиля")
            true
        }
        carCardView.setOnLongClickListener {
            showTooltip(it, "Нажмите для редактирования информации об автомобиле")
            true
        }
    }

    private fun restoreLastSelectedCar() {
        val savedCarId = sharedPreferences.getInt(PREF_CAR_ID, -1)
        val savedCarName = sharedPreferences.getString(PREF_CAR_NAME, "")

        if (savedCarId != -1 && !savedCarName.isNullOrEmpty()) {
            textView_Brand.text = savedCarName
            textView_Name.text = "Загрузка..."
            textView_Probeg.text = "км"
        }
    }

    private fun restoreCarSelection() {
        val savedCarId = sharedPreferences.getInt(PREF_CAR_ID, -1)

        if (savedCarId != -1) {
            val savedCar = userCars.find { it.id == savedCarId }
            if (savedCar != null) {
                displayCarInfo(savedCar)
                this.selectedCarId = savedCar.id
                return
            }
        }

        if (userCars.isNotEmpty()) {
            val firstCar = userCars[0]
            displayCarInfo(firstCar)
            this.selectedCarId = firstCar.id
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
        textView_Probeg.text = "${car.mileage.toInt()} км"

        if (car.photoBytes != null && car.photoBytes.isNotEmpty()) {
            val bitmap = BitmapFactory.decodeByteArray(car.photoBytes, 0, car.photoBytes.size)
            imageView.setImageBitmap(bitmap)
        } else {
            imageView.setImageResource(R.drawable.ph)
        }
    }

    private fun showNoCarsMessage() {
        textView_Brand.text = "Нет автомобилей"
        textView_Name.text = "Добавьте данные"
        textView_Probeg.text = "0 км"
        imageView.setImageResource(R.drawable.ph)

        sharedPreferences.edit()
            .remove(PREF_CAR_ID)
            .remove(PREF_CAR_NAME)
            .apply()
    }

    private fun showLogoutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выход из аккаунта")
            .setMessage("Вы уверены, что хотите выйти?")
            .setPositiveButton("Выйти") { _, _ -> performLogout() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun performLogout() {
        sharedPreferences.edit()
            .remove(PREF_CAR_ID)
            .remove(PREF_CAR_NAME)
            .apply()

        ApiClient.clearPhotoCache()
        sessionManager.logout()
        Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MainActivityLogin::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun setupCarCardClickListener() {
        carCardView.setOnClickListener {
            if (userCars.isNotEmpty() && selectedCarId != -1) {
                userCars.find { it.id == selectedCarId }?.let { car ->
                    openCarForEditing(car)
                }
            } else {
                val intent = Intent(this@MainActivity, MainActivityAddCar::class.java)
                startActivityForResult(intent, REQUEST_CODE_ADD_CAR)
            }
            sharedPreferences.edit().putInt("current_car_id", selectedCarId).apply()
        }
    }

    private fun openCarForEditing(car: Car) {
        val intent = Intent(this@MainActivity, MainActivityAddCar::class.java).apply {
            putExtra("is_edit_mode", true)
            putExtra("car_id", car.id)
            putExtra("brand", car.brand)
            putExtra("model", car.model)
            putExtra("mileage", car.mileage.toInt())
            if (car.photoBytes != null && car.photoBytes.isNotEmpty()) {
                putExtra("photo_bytes", car.photoBytes)
            }
        }
        Log.d(TAG, "Opening car for editing: ${car.displayName}, ID: ${car.id}")
        startActivityForResult(intent, REQUEST_CODE_ADD_CAR)
    }

    private fun updateCurrentCarMileage() {
        if (selectedCarId == -1) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userId = sessionManager.getUserId()
                val carsArray = ApiClient.getCars(userId)
                for (i in 0 until carsArray.length()) {
                    val obj = carsArray.getJSONObject(i)
                    if (obj.getInt("car_id") == selectedCarId) {
                        val newMileage = obj.optDouble("mileage", 0.0)
                        withContext(Dispatchers.Main) {
                            val idx = userCars.indexOfFirst { it.id == selectedCarId }
                            if (idx != -1) {
                                userCars[idx] = userCars[idx].copy(mileage = newMileage)
                                displayCarInfo(userCars[idx])
                                displayCarsInMenu()
                            }
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating mileage: ${e.message}", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        progressOverlay.visibility = View.GONE
        updateNavigationHeader()
        updateCurrentCarMileage()

        if (isCarsLoaded) {
            displayCarsInMenu()
        }

        for (i in 0 until navigationView.menu.size()) {
            navigationView.menu.getItem(i)?.isChecked = false
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isOpen) {
            drawerLayout.close()
        } else {
            super.onBackPressed()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (drawerLayout.isOpen) {
            return super.dispatchTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        when (requestCode) {
            REQUEST_CODE_ADD_CAR -> {
                loadUserCars(forceReload = true)
            }
            REQUEST_CODE_REFUELING, REQUEST_CODE_MAINTENANCE -> {
                if (resultCode == RESULT_OK) {
                    updateCurrentCarMileage()
                }
                loadUserCars(forceReload = true)
            }
            100 -> {
                progressOverlay.visibility = View.GONE
                if (resultCode == RESULT_OK) {
                    updateNavigationHeader()
                }
            }
        }
    }
}