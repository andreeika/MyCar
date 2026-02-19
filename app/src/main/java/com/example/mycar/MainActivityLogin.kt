package com.example.mycar

import SessionManager
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.PreparedStatement
import java.sql.ResultSet

class MainActivityLogin : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: TextView

    private val sessionManager by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_login)

        if (sessionManager.isLoggedIn()) {
            startMainActivity()
            return
        }

        initViews()
        setClickListeners()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }

    private fun initViews() {
        etUsername = findViewById(R.id.editTextText)
        etPassword = findViewById(R.id.editTextTextPassword)
        btnLogin = findViewById(R.id.button)
        btnRegister = findViewById(R.id.textView12)
    }

    private fun setClickListeners() {
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (validateInput(username, password)) {
                loginUser(username, password)
            }
        }



        btnRegister.setOnClickListener {
            val intent = Intent(this, MainActivityRegister::class.java)
            startActivity(intent)
        }

    }

    private fun validateInput(username: String, password: String): Boolean {
        if (username.isEmpty()) {
            etUsername.error = "Введите имя пользователя"
            return false
        }

        if (password.isEmpty()) {
            etPassword.error = "Введите пароль"
            return false
        }

        return true
    }

    private fun loginUser(username: String, password: String) {
        btnLogin.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val query = "SELECT user_id, full_name, username, password FROM users WHERE username = ?"
                    val preparedStatement: PreparedStatement = connect.prepareStatement(query)
                    preparedStatement.setString(1, username)

                    val resultSet: ResultSet = preparedStatement.executeQuery()

                    if (resultSet.next()) {
                        val userId = resultSet.getInt("user_id")
                        val fullName = resultSet.getString("full_name")
                        val dbUsername = resultSet.getString("username")
                        val storedEncryptedPassword = resultSet.getString("password")

                        Log.d(TAG, "Stored encrypted password length: ${storedEncryptedPassword?.length ?: 0}")

                        val isPasswordCorrect = PasswordEncryptor.checkPassword(password, storedEncryptedPassword)

                        if (isPasswordCorrect) {
                            sessionManager.saveAuthToken(userId, fullName, dbUsername)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivityLogin, "Добро пожаловать, $fullName!", Toast.LENGTH_SHORT).show()
                                startMainActivity()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                btnLogin.isEnabled = true
                                etPassword.error = "Неверный пароль"
                                Toast.makeText(this@MainActivityLogin, "Неверный пароль", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            btnLogin.isEnabled = true
                            etUsername.error = "Пользователь не найден"
                            Toast.makeText(this@MainActivityLogin, "Пользователь не найден", Toast.LENGTH_SHORT).show()
                        }
                    }

                    resultSet.close()
                    preparedStatement.close()
                    connect.close()
                } else {
                    withContext(Dispatchers.Main) {
                        btnLogin.isEnabled = true
                        Toast.makeText(this@MainActivityLogin, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error during login: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    btnLogin.isEnabled = true
                    Toast.makeText(this@MainActivityLogin, "Ошибка входа: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun enableLoginButton() {
        btnLogin.isEnabled = true
    }

    private fun startMainActivity() {
        val intent = Intent(this@MainActivityLogin, MainActivity::class.java)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.cancel()
    }
}