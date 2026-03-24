package com.example.mycar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityLogin : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: TextView
    private lateinit var progressOverlay: android.widget.FrameLayout
    private val sessionManager by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_login)

        if (sessionManager.isLoggedIn()) { startMain(); return }

        etUsername = findViewById(R.id.editTextText)
        etPassword = findViewById(R.id.editTextTextPassword)
        btnLogin   = findViewById(R.id.button)
        btnRegister = findViewById(R.id.textView12)
        progressOverlay = findViewById(R.id.progressOverlay)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            if (username.isEmpty()) { etUsername.error = "Введите логин"; return@setOnClickListener }
            if (password.isEmpty()) { etPassword.error = "Введите пароль"; return@setOnClickListener }
            loginUser(username, password)
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, MainActivityRegister::class.java))
        }
    }

    private fun loginUser(username: String, password: String) {
        btnLogin.isEnabled = false
        progressOverlay.visibility = android.view.View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = ApiClient.login(username, password)
                val userId   = resp.getInt("user_id")
                val fullName = resp.getString("full_name")
                val uname    = resp.getString("username")
                val email    = resp.optString("email", "")
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    sessionManager.saveAuthToken(userId, fullName, uname, email)
                    Toast.makeText(this@MainActivityLogin, "Добро пожаловать, $fullName!", Toast.LENGTH_SHORT).show()
                    startMain()
                }
            } catch (e: ApiException) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    btnLogin.isEnabled = true
                    val msg = when (e.code) {
                        401 -> "Неверный логин или пароль"
                        else -> "Ошибка: ${e.message}"
                    }
                    Toast.makeText(this@MainActivityLogin, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    btnLogin.isEnabled = true
                    Toast.makeText(this@MainActivityLogin, "Нет связи с сервером", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startMain() {
        // Создать канал и запустить воркер если уведомления включены
        MaintenanceCheckWorker.createNotificationChannel(this)
        val prefs = getSharedPreferences("my_car_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("notifications_enabled", true)) {
            MaintenanceCheckWorker.schedule(this)
        }
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
