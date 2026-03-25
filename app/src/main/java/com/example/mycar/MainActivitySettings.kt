package com.example.mycar

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
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
import java.util.Date

class MainActivitySettings : BaseActivity() {

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
    private lateinit var progressOverlay: android.widget.FrameLayout

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
        editUserName = findViewById(R.id.editUserName)
        editUserLogin = findViewById(R.id.editUserLogin)
        editUserEmail = findViewById(R.id.editUserEmail)
        layoutUserName = findViewById(R.id.layoutUserName)
        layoutUserLogin = findViewById(R.id.layoutUserLogin)
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
        progressOverlay = findViewById(R.id.progressOverlay)

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
        editUserName.setText(sessionManager.getUserName() ?: "")
        editUserLogin.setText(sessionManager.getUsername() ?: "")
        editUserEmail.setText(sessionManager.getUserEmail() ?: "")

        switchNotifications.isChecked = sharedPreferences.getBoolean("notifications_enabled", true)

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
            if (isChecked) {
                MaintenanceCheckWorker.schedule(this)
            } else {
                MaintenanceCheckWorker.cancel(this)
            }
            Toast.makeText(this,
                if (isChecked) "Уведомления включены" else "Уведомления отключены",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateProfile(): Boolean {
        var isValid = true

        val userName = editUserName.text.toString().trim()
        val userLogin = editUserLogin.text.toString().trim()
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

        if (userEmail.isEmpty()) {
            layoutUserEmail.error = "Введите email"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            layoutUserEmail.error = "Некорректный email"
            isValid = false
        } else {
            val emailError = EmailValidator.validate(userEmail)
            if (emailError != null) {
                layoutUserEmail.error = emailError
                isValid = false
            } else {
                layoutUserEmail.error = null
            }
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

    private fun updateProfile() {
        val userId   = sessionManager.getUserId()
        val fullName = editUserName.text.toString().trim()
        val username = editUserLogin.text.toString().trim()
        val newEmail = editUserEmail.text.toString().trim()
        val currentEmail = sessionManager.getUserEmail() ?: ""

        // Если email не изменился — сохраняем без кода
        if (newEmail.equals(currentEmail, ignoreCase = true)) {
            saveProfileDirectly(userId, fullName, username, newEmail)
            return
        }

        // Email изменился — отправляем код на новый email
        btnSaveProfile.isEnabled = false
        progressOverlay.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ApiClient.sendChangeCode(userId, "email", newEmail)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnSaveProfile.isEnabled = true
                    showChangeCodeDialog(
                        title = "Подтверждение нового email",
                        message = "Код отправлен на $newEmail",
                        onConfirm = { code ->
                            verifyEmailChange(userId, fullName, username, newEmail, code)
                        },
                        onResend = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try { ApiClient.sendChangeCode(userId, "email", newEmail) } catch (_: Exception) {}
                            }
                        }
                    )
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnSaveProfile.isEnabled = true
                    val msg = if (ex is ApiException && ex.code == 409) ex.message ?: "Email уже используется"
                              else friendlyError(ex)
                    Toast.makeText(this@MainActivitySettings, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveProfileDirectly(userId: Int, fullName: String, username: String, email: String) {
        btnSaveProfile.isEnabled = false
        progressOverlay.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ApiClient.updateProfile(userId, fullName, username, email)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    sessionManager.saveUserName(fullName)
                    sessionManager.saveUserUsername(username)
                    sessionManager.saveUserEmail(email)
                    Toast.makeText(this@MainActivitySettings, "Профиль обновлён", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnSaveProfile.isEnabled = true
                    Toast.makeText(this@MainActivitySettings, friendlyError(ex), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun verifyEmailChange(userId: Int, fullName: String, username: String, newEmail: String, code: String) {
        progressOverlay.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ApiClient.verifyChange(userId, "email", code, newEmail = newEmail)
                // После подтверждения email — сохраняем остальной профиль
                ApiClient.updateProfile(userId, fullName, username, newEmail)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    sessionManager.saveUserName(fullName)
                    sessionManager.saveUserUsername(username)
                    sessionManager.saveUserEmail(newEmail)
                    Toast.makeText(this@MainActivitySettings, "Профиль обновлён", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    val msg = if (ex is ApiException && ex.code == 400) ex.message ?: "Неверный код"
                              else friendlyError(ex)
                    Toast.makeText(this@MainActivitySettings, msg, Toast.LENGTH_LONG).show()
                    if (ex is ApiException && ex.code == 400) {
                        showChangeCodeDialog(
                            title = "Подтверждение нового email",
                            message = "Код отправлен на $newEmail",
                            onConfirm = { c -> verifyEmailChange(userId, fullName, username, newEmail, c) },
                            onResend = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    try { ApiClient.sendChangeCode(userId, "email", newEmail) } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun updatePassword() {
        val userId          = sessionManager.getUserId()
        val currentPassword = editCurrentPassword.text.toString().trim()
        val newPassword     = editNewPassword.text.toString().trim()
        val currentEmail    = sessionManager.getUserEmail() ?: ""

        btnSavePassword.isEnabled = false
        progressOverlay.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Сначала проверяем текущий пароль через обычный эндпоинт (чтобы не слать код при неверном пароле)
                ApiClient.updatePassword(userId, currentPassword, currentPassword) // dry-run: меняем на тот же
            } catch (ex: ApiException) {
                if (ex.code == 401) {
                    withContext(Dispatchers.Main) {
                        progressOverlay.visibility = View.GONE
                        btnSavePassword.isEnabled = true
                        layoutCurrentPassword.error = "Неверный текущий пароль"
                    }
                    return@launch
                }
                // Иначе продолжаем (другие ошибки игнорируем на dry-run)
            } catch (_: Exception) {}

            try {
                ApiClient.sendChangeCode(userId, "password")
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnSavePassword.isEnabled = true
                    showChangeCodeDialog(
                        title = "Подтверждение смены пароля",
                        message = "Код отправлен на $currentEmail",
                        onConfirm = { code ->
                            verifyPasswordChange(userId, currentPassword, newPassword, code)
                        },
                        onResend = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try { ApiClient.sendChangeCode(userId, "password") } catch (_: Exception) {}
                            }
                        }
                    )
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnSavePassword.isEnabled = true
                    Toast.makeText(this@MainActivitySettings, friendlyError(ex), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun verifyPasswordChange(userId: Int, currentPassword: String, newPassword: String, code: String) {
        progressOverlay.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ApiClient.verifyChange(userId, "password", code, currentPassword, newPassword)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@MainActivitySettings, "Пароль изменён", Toast.LENGTH_SHORT).show()
                    editCurrentPassword.text?.clear()
                    editNewPassword.text?.clear()
                    editConfirmPassword.text?.clear()
                    btnSavePassword.isEnabled = true
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    val msg = if (ex is ApiException && ex.code == 400) ex.message ?: "Неверный код"
                              else friendlyError(ex)
                    Toast.makeText(this@MainActivitySettings, msg, Toast.LENGTH_LONG).show()
                    if (ex is ApiException && ex.code == 400) {
                        val email = sessionManager.getUserEmail() ?: ""
                        showChangeCodeDialog(
                            title = "Подтверждение смены пароля",
                            message = "Код отправлен на $email",
                            onConfirm = { c -> verifyPasswordChange(userId, currentPassword, newPassword, c) },
                            onResend = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    try { ApiClient.sendChangeCode(userId, "password") } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun showChangeCodeDialog(
        title: String,
        message: String,
        onConfirm: (code: String) -> Unit,
        onResend: () -> Unit
    ) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        val info = android.widget.TextView(this).apply {
            text = message
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        val codeEdit = android.widget.EditText(this).apply {
            hint = "Введите 6-значный код"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.3f
        }
        layout.addView(info)
        layout.addView(codeEdit)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("Подтвердить", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Отправить снова") { _, _ -> onResend() }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val code = codeEdit.text.toString().trim()
            if (code.length != 6) { codeEdit.error = "Введите 6 цифр"; return@setOnClickListener }
            dialog.dismiss()
            onConfirm(code)
        }
        codeEdit.requestFocus()
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
                val userId = sessionManager.getUserId()
                ApiClient.deleteAccount(userId)
                withContext(Dispatchers.Main) {
                    sharedPreferences.edit().clear().apply()
                    sessionManager.logout()
                    Toast.makeText(this@MainActivitySettings, "Аккаунт удалён", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MainActivitySettings, MainActivityLogin::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivitySettings, "Ошибка удаления: ${friendlyError(e)}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
    }
}