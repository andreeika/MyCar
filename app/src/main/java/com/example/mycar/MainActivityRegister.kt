package com.example.mycar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityRegister : AppCompatActivity() {

    private lateinit var editFullName: EditText
    private lateinit var editEmail: EditText
    private lateinit var editUsername: EditText
    private lateinit var editPassword: EditText
    private lateinit var editConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvLogin: TextView
    private lateinit var ivCancel: ImageView
    private lateinit var progressOverlay: android.widget.FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_register)

        editFullName        = findViewById(R.id.editTextFullName)
        editEmail           = findViewById(R.id.editTextEmail)
        editUsername        = findViewById(R.id.editTextUsername)
        editPassword        = findViewById(R.id.editTextPassword)
        editConfirmPassword = findViewById(R.id.editTextConfirmPassword)
        btnRegister         = findViewById(R.id.buttonRegister)
        tvLogin             = findViewById(R.id.textViewLogin)
        ivCancel            = findViewById(R.id.imageViewCancel)
        progressOverlay     = findViewById(R.id.progressOverlay)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }

        btnRegister.setOnClickListener {
            val fullName = editFullName.text.toString().trim()
            val email    = editEmail.text.toString().trim()
            val username = editUsername.text.toString().trim()
            val password = editPassword.text.toString().trim()
            val confirm  = editConfirmPassword.text.toString().trim()
            if (validate(fullName, email, username, password, confirm)) {
                registerUser(fullName, email, username, password)
            }
        }

        tvLogin.setOnClickListener {
            startActivity(Intent(this, MainActivityLogin::class.java)); finish()
        }
        ivCancel.setOnClickListener { finish() }
    }

    private fun validate(fullName: String, email: String, username: String,
                         password: String, confirm: String): Boolean {
        if (fullName.length < 2)                          { editFullName.error = "Минимум 2 символа"; return false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { editEmail.error = "Некорректный email"; return false }
        if (username.length < 3)                          { editUsername.error = "Минимум 3 символа"; return false }
        if (password.length < 4)                          { editPassword.error = "Минимум 4 символа"; return false }
        if (password != confirm)                          { editConfirmPassword.error = "Пароли не совпадают"; return false }
        return true
    }

    private fun registerUser(fullName: String, email: String, username: String, password: String) {
        btnRegister.isEnabled = false
        btnRegister.text = "Регистрация..."
        progressOverlay.visibility = android.view.View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp     = ApiClient.register(fullName, email, username, password)
                val userId   = resp.getInt("user_id")
                val uname    = resp.getString("username")
                val name     = resp.getString("full_name")
                val mail     = resp.optString("email", "")
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    SessionManager(this@MainActivityRegister).saveAuthToken(userId, name, uname, mail)
                    Toast.makeText(this@MainActivityRegister, "Добро пожаловать, $name!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MainActivityRegister, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    finish()
                }
            } catch (e: ApiException) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    btnRegister.isEnabled = true
                    btnRegister.text = "Зарегистрироваться"
                    val msg = when (e.code) {
                        409 -> e.message ?: "Пользователь уже существует"
                        else -> "Ошибка регистрации"
                    }
                    Toast.makeText(this@MainActivityRegister, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = android.view.View.GONE
                    btnRegister.isEnabled = true
                    btnRegister.text = "Зарегистрироваться"
                    Toast.makeText(this@MainActivityRegister, "Нет связи с сервером", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
