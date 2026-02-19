package com.example.mycar

import SessionManager
import kotlinx.coroutines.cancel
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.PreparedStatement
import java.sql.ResultSet

class MainActivityRegister : AppCompatActivity() {

    private lateinit var editTextFullName: EditText
    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
    private lateinit var buttonRegister: Button
    private lateinit var textViewLogin: TextView
    private lateinit var imageViewCancel: ImageView

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "RegisterActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_register)

        initializeViews()
        setupUI()
        setClickListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    private fun initializeViews() {
        editTextFullName = findViewById(R.id.editTextFullName)
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword)
        buttonRegister = findViewById(R.id.buttonRegister)
        textViewLogin = findViewById(R.id.textViewLogin)
        imageViewCancel = findViewById(R.id.imageViewCancel)
    }

    private fun setupUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }

    private fun setClickListeners() {
        buttonRegister.setOnClickListener {
            val fullName = editTextFullName.text.toString().trim()
            val username = editTextUsername.text.toString().trim()
            val password = editTextPassword.text.toString().trim()
            val confirmPassword = editTextConfirmPassword.text.toString().trim()

            if (validateInput(fullName, username, password, confirmPassword)) {
                registerUser(fullName, username, password)
            }
        }

        textViewLogin.setOnClickListener {
            val intent = Intent(this, MainActivityLogin::class.java)
            startActivity(intent)
            finish()
        }

        imageViewCancel.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(fullName: String, username: String, password: String, confirmPassword: String): Boolean {
        // Валидация ФИО
        when {
            fullName.isEmpty() -> {
                editTextFullName.error = "Введите имя"
                editTextFullName.requestFocus()
                return false
            }
            fullName.length < 2 -> {
                editTextFullName.error = "Имя должно содержать минимум 2 символа"
                editTextFullName.requestFocus()
                return false
            }
            fullName.trim() != fullName -> {
                editTextFullName.error = "Уберите пробелы в начале или конце имени"
                editTextFullName.requestFocus()
                return false
            }
            fullName.contains("  ") -> {
                editTextFullName.error = "Уберите лишние пробелы в имени"
                editTextFullName.requestFocus()
                return false
            }
        }

        // Валидация логина
        when {
            username.isEmpty() -> {
                editTextUsername.error = "Введите логин"
                editTextUsername.requestFocus()
                return false
            }
            username.length < 3 -> {
                editTextUsername.error = "Логин должен содержать минимум 3 символа"
                editTextUsername.requestFocus()
                return false
            }
            username.contains(" ") -> {
                editTextUsername.error = "Логин не должен содержать пробелы"
                editTextUsername.requestFocus()
                return false
            }
            username.trim() != username -> {
                editTextUsername.error = "Уберите пробелы в начале или конце логина"
                editTextUsername.requestFocus()
                return false
            }
        }

        // Валидация пароля
        when {
            password.isEmpty() -> {
                editTextPassword.error = "Введите пароль"
                editTextPassword.requestFocus()
                return false
            }
            password.length < 4 -> {
                editTextPassword.error = "Пароль должен содержать минимум 4 символа"
                editTextPassword.requestFocus()
                return false
            }
            password.contains(" ") -> {
                editTextPassword.error = "Пароль не должен содержать пробелы"
                editTextPassword.requestFocus()
                return false
            }
        }

        // Валидация подтверждения пароля
        when {
            confirmPassword.isEmpty() -> {
                editTextConfirmPassword.error = "Повторите пароль"
                editTextConfirmPassword.requestFocus()
                return false
            }
            confirmPassword.contains(" ") -> {
                editTextConfirmPassword.error = "Подтверждение пароля не должно содержать пробелы"
                editTextConfirmPassword.requestFocus()
                return false
            }
            password != confirmPassword -> {
                editTextConfirmPassword.error = "Пароли не совпадают"
                editTextConfirmPassword.requestFocus()
                return false
            }
        }

        return true
    }

    private fun registerUser(fullName: String, username: String, password: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                showProgress(true)

                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    if (isUsernameExists(username)) {
                        withContext(Dispatchers.Main) {
                            showProgress(false)
                            editTextUsername.error = "Пользователь с таким логином уже существует"
                            editTextUsername.requestFocus()
                        }
                        return@launch
                    }

                    val encryptedPassword = PasswordEncryptor.hashPassword(password)
                    Log.d(TAG, "Password encrypted: ${password.length} chars -> ${encryptedPassword.length} chars")

                    val query = "INSERT INTO users (full_name, username, password) VALUES (?, ?, ?)"
                    val preparedStatement: PreparedStatement = connect.prepareStatement(query)
                    preparedStatement.setString(1, fullName)
                    preparedStatement.setString(2, username)
                    preparedStatement.setString(3, encryptedPassword)

                    val rowsAffected = preparedStatement.executeUpdate()

                    preparedStatement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        showProgress(false)

                        if (rowsAffected > 0) {
                            Toast.makeText(this@MainActivityRegister, "Регистрация успешна!", Toast.LENGTH_SHORT).show()
                            autoLoginAfterRegistration(username, password)
                        } else {
                            Toast.makeText(this@MainActivityRegister, "Ошибка при регистрации", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showProgress(false)
                        Toast.makeText(this@MainActivityRegister, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error registering user: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    Toast.makeText(this@MainActivityRegister, "Ошибка регистрации: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun isUsernameExists(username: String): Boolean {
        return try {
            val connectionHelper = ConnectionHelper()
            val connect = connectionHelper.connectionclass()

            if (connect != null) {
                val query = "SELECT user_id FROM users WHERE username = ?"
                val preparedStatement = connect.prepareStatement(query)
                preparedStatement.setString(1, username)
                val resultSet: ResultSet = preparedStatement.executeQuery()

                val exists = resultSet.next()

                resultSet.close()
                preparedStatement.close()
                connect.close()

                exists
            } else {
                false
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error checking username: ${ex.message}", ex)
            false
        }
    }

    private fun autoLoginAfterRegistration(username: String, password: String) {
        coroutineScope.launch(Dispatchers.IO) {
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
                        val isPasswordCorrect = PasswordEncryptor.checkPassword(password, storedEncryptedPassword)

                        if (isPasswordCorrect) {
                            val sessionManager = SessionManager(this@MainActivityRegister)
                            sessionManager.saveAuthToken(userId, fullName, dbUsername)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivityRegister, "Добро пожаловать, $fullName!", Toast.LENGTH_SHORT).show()

                                val intent = Intent(this@MainActivityRegister, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            throw Exception("Ошибка проверки пароля после регистрации")
                        }
                    }

                    resultSet.close()
                    preparedStatement.close()
                    connect.close()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error auto login: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@MainActivityRegister, MainActivityLogin::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }


    private fun showProgress(show: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            buttonRegister.isEnabled = !show
            if (show) {
                buttonRegister.text = "Регистрация..."
            } else {
                buttonRegister.text = "Зарегистрироваться"
            }
        }
    }
}