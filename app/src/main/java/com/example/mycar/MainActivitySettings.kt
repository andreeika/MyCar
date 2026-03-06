package com.example.mycar

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.Date

class MainActivitySettings : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var sharedPreferences: SharedPreferences

    // ===== Поля профиля =====
    private lateinit var editUserName: TextInputEditText          // full_name
    private lateinit var editUserLogin: TextInputEditText         // username (логин)
    private lateinit var editUserEmail: TextInputEditText         // email
    private lateinit var layoutUserName: TextInputLayout
    private lateinit var layoutUserLogin: TextInputLayout
    private lateinit var layoutUserEmail: TextInputLayout

    // ===== Поля безопасности =====
    private lateinit var editCurrentPassword: TextInputEditText
    private lateinit var editNewPassword: TextInputEditText
    private lateinit var editConfirmPassword: TextInputEditText
    private lateinit var layoutCurrentPassword: TextInputLayout
    private lateinit var layoutNewPassword: TextInputLayout
    private lateinit var layoutConfirmPassword: TextInputLayout

    // ===== Переключатели =====
    private lateinit var switchNotifications: Switch


    // ===== Кнопки =====
    private lateinit var btnSaveProfile: Button
    private lateinit var btnSavePassword: Button
    private lateinit var btnClearCache: Button
    private lateinit var btnDeleteAccount: Button

    // ===== Информация =====
    private lateinit var textAppVersion: TextView
    private lateinit var textLastLogin: TextView

    companion object {
        private const val TAG = "MainActivitySettings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_settings)

        sessionManager = SessionManager(this)
        sharedPreferences = getSharedPreferences("my_car_prefs", MODE_PRIVATE)

        initViews()
        loadCurrentData()
        setupClickListeners()
        setupSwitchListeners()
        setupStatusBarColors()
    }

    private fun initViews() {
        // 🔹 Профиль - изменены ID для логина
        editUserName = findViewById(R.id.editUserName)
        editUserLogin = findViewById(R.id.editUserLogin)      // вместо editUserPhone
        editUserEmail = findViewById(R.id.editUserEmail)
        layoutUserName = findViewById(R.id.layoutUserName)
        layoutUserLogin = findViewById(R.id.layoutUserLogin)  // вместо layoutUserPhone
        layoutUserEmail = findViewById(R.id.layoutUserEmail)

        // Безопасность
        editCurrentPassword = findViewById(R.id.editCurrentPassword)
        editNewPassword = findViewById(R.id.editNewPassword)
        editConfirmPassword = findViewById(R.id.editConfirmPassword)
        layoutCurrentPassword = findViewById(R.id.layoutCurrentPassword)
        layoutNewPassword = findViewById(R.id.layoutNewPassword)
        layoutConfirmPassword = findViewById(R.id.layoutConfirmPassword)

        // Переключатели
        switchNotifications = findViewById(R.id.switchNotifications)

        // Кнопки
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        btnSavePassword = findViewById(R.id.btnSavePassword)
        btnClearCache = findViewById(R.id.btnClearCache)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)

        // Информация
        textAppVersion = findViewById(R.id.textAppVersion)
        textLastLogin = findViewById(R.id.textLastLogin)

        // Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Настройки"

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_home_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }
    private fun loadCurrentData() {
        // Загрузка данных профиля
        editUserName.setText(sessionManager.getUserName() ?: "")
        editUserLogin.setText(sessionManager.getUsername() ?: "")   // загружаем логин
        editUserEmail.setText(sharedPreferences.getString("user_email", "")) // email локально или из БД

        // Загрузка настроек
        switchNotifications.isChecked = sharedPreferences.getBoolean("notifications_enabled", true)

        // Информация
        textAppVersion.text = "Версия приложения: 1.0.0"
        textLastLogin.text = "Последний вход: ${getLastLoginTime()}"
    }

    private fun getLastLoginTime(): String {
        val lastLogin = sharedPreferences.getLong("last_login_time", 0)
        return if (lastLogin > 0) {
            val date = Date(lastLogin)
            android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", date).toString()
        } else {
            "Неизвестно"
        }
    }

    private fun setupClickListeners() {
        btnSaveProfile.setOnClickListener {
            if (validateProfile()) {
                updateProfile()
            }
        }

        btnSavePassword.setOnClickListener {
            if (validatePassword()) {
                updatePassword()
            }
        }

        btnClearCache.setOnClickListener {
            showClearCacheDialog()
        }

        btnDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    private fun setupSwitchListeners() {
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("notifications_enabled", isChecked).apply()
            Toast.makeText(this,
                if (isChecked) "Уведомления включены" else "Уведомления отключены",
                Toast.LENGTH_SHORT).show()
        }

    }

    private fun validateProfile(): Boolean {
        var isValid = true

        val userName = editUserName.text.toString().trim()
        val userLogin = editUserLogin.text.toString().trim()   // логин
        val userEmail = editUserEmail.text.toString().trim()

        // 🔹 Валидация имени
        if (userName.isEmpty()) {
            layoutUserName.error = "Введите имя"
            isValid = false
        } else if (userName.length < 2) {
            layoutUserName.error = "Имя должно быть не менее 2 символов"
            isValid = false
        } else {
            layoutUserName.error = null
        }

        // 🔹 Валидация логина (username)
        if (userLogin.isEmpty()) {
            layoutUserLogin.error = "Введите логин"
            isValid = false
        } else if (userLogin.length < 3) {
            layoutUserLogin.error = "Логин должен быть не менее 3 символов"
            isValid = false
        } else if (!userLogin.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            layoutUserLogin.error = "Только латиница, цифры и _"
            isValid = false
        } else {
            layoutUserLogin.error = null
        }

        // 🔹 Валидация email
        if (userEmail.isEmpty()) {
            layoutUserEmail.error = "Введите email"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            layoutUserEmail.error = "Некорректный email"
            isValid = false
        } else {
            layoutUserEmail.error = null
        }

        return isValid
    }

    private fun validatePassword(): Boolean {
        var isValid = true

        val currentPassword = editCurrentPassword.text.toString().trim()
        val newPassword = editNewPassword.text.toString().trim()
        val confirmPassword = editConfirmPassword.text.toString().trim()

        if (currentPassword.isEmpty()) {
            layoutCurrentPassword.error = "Введите текущий пароль"
            isValid = false
        } else {
            layoutCurrentPassword.error = null
        }

        if (newPassword.isEmpty()) {
            layoutNewPassword.error = "Введите новый пароль"
            isValid = false
        } else if (newPassword.length < 6) {
            layoutNewPassword.error = "Пароль должен быть не менее 6 символов"
            isValid = false
        } else {
            layoutNewPassword.error = null
        }

        if (confirmPassword.isEmpty()) {
            layoutConfirmPassword.error = "Подтвердите пароль"
            isValid = false
        } else if (newPassword != confirmPassword) {
            layoutConfirmPassword.error = "Пароли не совпадают"
            isValid = false
        } else {
            layoutConfirmPassword.error = null
        }

        return isValid
    }

    // ===== ОБНОВЛЕНИЕ ПРОФИЛЯ В БД =====
    private fun updateProfile() {
        btnSaveProfile.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect: Connection? = connectionHelper.connectionclass()
                val userId = sessionManager.getUserId()

                if (connect != null) {
                    val fullName = editUserName.text.toString().trim()
                    val username = editUserLogin.text.toString().trim()   // новый логин
                    val email = editUserEmail.text.toString().trim()

                    // 🔹 Обновляем full_name, username и email в БД
                    val query = """
                        UPDATE users 
                        SET full_name = ?, username = ?, email = ?
                        WHERE user_id = ?
                    """.trimIndent()

                    val preparedStatement: PreparedStatement = connect.prepareStatement(query)
                    preparedStatement.setString(1, fullName)
                    preparedStatement.setString(2, username)
                    preparedStatement.setString(3, email)
                    preparedStatement.setInt(4, userId)

                    val rowsAffected = preparedStatement.executeUpdate()

                    preparedStatement.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        if (rowsAffected > 0) {
                            // 🔹 Сохраняем в SessionManager
                            sessionManager.saveUserName(fullName)
                            sessionManager.saveUserUsername(username)
                            sharedPreferences.edit().putString("user_email", email).apply()

                            Toast.makeText(this@MainActivitySettings, "Профиль успешно обновлен", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        } else {
                            Toast.makeText(this@MainActivitySettings, "Ошибка при обновлении профиля", Toast.LENGTH_SHORT).show()
                        }
                        btnSaveProfile.isEnabled = true
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivitySettings, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                        btnSaveProfile.isEnabled = true
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error updating profile: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivitySettings, "Ошибка: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
                    btnSaveProfile.isEnabled = true
                }
            }
        }
    }

    // ===== ОБНОВЛЕНИЕ ПАРОЛЯ В БД =====
    private fun updatePassword() {
        btnSavePassword.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect: Connection? = connectionHelper.connectionclass()
                val userId = sessionManager.getUserId()

                if (connect != null) {
                    val currentPassword = editCurrentPassword.text.toString().trim()
                    val newPassword = editNewPassword.text.toString().trim()

                    // Шаг 1: Получаем текущий хеш пароля из БД
                    val selectQuery = "SELECT password FROM users WHERE user_id = ?"
                    val selectStmt: PreparedStatement = connect.prepareStatement(selectQuery)
                    selectStmt.setInt(1, userId)

                    val resultSet = selectStmt.executeQuery()

                    if (resultSet.next()) {
                        val storedEncryptedPassword = resultSet.getString("password")

                        // Шаг 2: Проверяем текущий пароль
                        val isCurrentPasswordCorrect = PasswordEncryptor.checkPassword(currentPassword, storedEncryptedPassword)

                        if (!isCurrentPasswordCorrect) {
                            withContext(Dispatchers.Main) {
                                layoutCurrentPassword.error = "Неверный текущий пароль"
                                btnSavePassword.isEnabled = true
                                Toast.makeText(this@MainActivitySettings, "Неверный текущий пароль", Toast.LENGTH_SHORT).show()
                            }
                            resultSet.close()
                            selectStmt.close()
                            connect.close()
                            return@launch
                        }

                        // Шаг 3: Хешируем новый пароль
                        val newEncryptedPassword = PasswordEncryptor.hashPassword(newPassword)

                        // Шаг 4: Обновляем пароль в БД
                        val updateQuery = "UPDATE users SET password = ? WHERE user_id = ?"
                        val updateStmt: PreparedStatement = connect.prepareStatement(updateQuery)
                        updateStmt.setString(1, newEncryptedPassword)
                        updateStmt.setInt(2, userId)

                        val rowsAffected = updateStmt.executeUpdate()

                        resultSet.close()
                        selectStmt.close()
                        updateStmt.close()
                        connect.close()

                        withContext(Dispatchers.Main) {
                            if (rowsAffected > 0) {
                                Toast.makeText(this@MainActivitySettings, "Пароль успешно изменен", Toast.LENGTH_SHORT).show()

                                editCurrentPassword.text?.clear()
                                editNewPassword.text?.clear()
                                editConfirmPassword.text?.clear()

                                layoutCurrentPassword.error = null
                                layoutNewPassword.error = null
                                layoutConfirmPassword.error = null
                            } else {
                                Toast.makeText(this@MainActivitySettings, "Ошибка при изменении пароля", Toast.LENGTH_SHORT).show()
                            }
                            btnSavePassword.isEnabled = true
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivitySettings, "Пользователь не найден", Toast.LENGTH_SHORT).show()
                            btnSavePassword.isEnabled = true
                        }
                        resultSet.close()
                        selectStmt.close()
                        connect.close()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivitySettings, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                        btnSavePassword.isEnabled = true
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error updating password: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivitySettings, "Ошибка: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
                    btnSavePassword.isEnabled = true
                }
            }
        }
    }

    private fun showClearCacheDialog() {
        AlertDialog.Builder(this)
            .setTitle("Очистить кэш")
            .setMessage("Это удалит временные файлы приложения. Ваши данные не будут затронуты.")
            .setPositiveButton("Очистить") { dialog, _ ->
                clearCache()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun clearCache() {
        try {
            cacheDir.deleteRecursively()
            Toast.makeText(this, "Кэш успешно очищен", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка при очистке кэша", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteAccountDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_account, null)
        val editConfirmDelete = dialogView.findViewById<TextInputEditText>(R.id.editConfirmDelete)

        AlertDialog.Builder(this)
            .setTitle("Удаление аккаунта")
            .setView(dialogView)
            .setMessage("Внимание! Это действие нельзя отменить. Все ваши данные будут удалены.")
            .setPositiveButton("Удалить") { dialog, _ ->
                val confirmText = editConfirmDelete.text.toString().trim()
                if (confirmText == "УДАЛИТЬ") {
                    deleteAccount()
                } else {
                    Toast.makeText(this, "Введите 'УДАЛИТЬ' для подтверждения", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteAccount() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect: Connection? = connectionHelper.connectionclass()
                val userId = sessionManager.getUserId()

                if (connect != null) {
                    val query = "DELETE FROM users WHERE user_id = ?"
                    val stmt: PreparedStatement = connect.prepareStatement(query)
                    stmt.setInt(1, userId)
                    stmt.executeUpdate()
                    stmt.close()
                    connect.close()

                    withContext(Dispatchers.Main) {
                        sharedPreferences.edit().clear().apply()
                        sessionManager.logout()

                        Toast.makeText(this@MainActivitySettings, "Аккаунт удален", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@MainActivitySettings, MainActivityLogin::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivitySettings, "Нет подключения к БД", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error deleting account: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivitySettings, "Ошибка при удалении: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}