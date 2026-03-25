package com.example.mycar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityRegister : BaseActivity() {

    private lateinit var editFullName: EditText
    private lateinit var editEmail: EditText
    private lateinit var editUsername: EditText
    private lateinit var editPassword: EditText
    private lateinit var editConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvLogin: TextView
    private lateinit var ivCancel: ImageView
    private lateinit var progressOverlay: FrameLayout

    private var pendingFullName = ""
    private var pendingEmail = ""
    private var pendingUsername = ""
    private var pendingPassword = ""

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
                pendingFullName = fullName
                pendingEmail    = email
                pendingUsername = username
                pendingPassword = password
                sendCode(email)
            }
        }

        tvLogin.setOnClickListener {
            startActivity(Intent(this, MainActivityLogin::class.java)); finish()
        }
        ivCancel.setOnClickListener { finish() }
    }

    private fun validate(fullName: String, email: String, username: String,
                         password: String, confirm: String): Boolean {
        if (fullName.length < 2)                              { editFullName.error = "Минимум 2 символа"; return false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { editEmail.error = "Некорректный email"; return false }
        EmailValidator.validate(email)?.let { editEmail.error = it; return false }
        if (username.length < 3)                              { editUsername.error = "Минимум 3 символа"; return false }
        if (password.length < 4)                              { editPassword.error = "Минимум 4 символа"; return false }
        if (password != confirm)                              { editConfirmPassword.error = "Пароли не совпадают"; return false }
        return true
    }

    private fun sendCode(email: String) {
        btnRegister.isEnabled = false
        btnRegister.text = "Отправка кода..."
        progressOverlay.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.sendVerificationCode(email)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnRegister.isEnabled = true
                    btnRegister.text = "Зарегистрироваться"
                    showCodeDialog()
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnRegister.isEnabled = true
                    btnRegister.text = "Зарегистрироваться"
                    val msg = if (ex is ApiException && ex.code == 409)
                        ex.message ?: "Email уже используется"
                    else if (ex is ApiException && ex.code == 500)
                        ex.message ?: "Ошибка отправки письма"
                    else friendlyError(ex)
                    Toast.makeText(this@MainActivityRegister, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showCodeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val info = TextView(this).apply {
            text = "Код отправлен на\n$pendingEmail"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }

        val codeEdit = EditText(this).apply {
            hint = "Введите 6-значный код"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 20f
            gravity = android.view.Gravity.CENTER
        }

        layout.addView(info)
        layout.addView(codeEdit)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Подтверждение email")
            .setView(layout)
            .setPositiveButton("Подтвердить", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Отправить снова") { _, _ -> sendCode(pendingEmail) }
            .create()

        dialog.show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val code = codeEdit.text.toString().trim()
            if (code.length != 6) {
                codeEdit.error = "Введите 6 цифр"
                return@setOnClickListener
            }
            dialog.dismiss()
            registerWithCode(code)
        }

        codeEdit.requestFocus()
    }

    private fun registerWithCode(code: String) {
        btnRegister.isEnabled = false
        progressOverlay.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp   = ApiClient.verifyAndRegister(pendingFullName, pendingEmail, pendingUsername, pendingPassword, code)
                val userId = resp.getInt("user_id")
                val uname  = resp.getString("username")
                val name   = resp.getString("full_name")
                val mail   = resp.optString("email", "")
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    SessionManager(this@MainActivityRegister).saveAuthToken(userId, name, uname, mail)
                    Toast.makeText(this@MainActivityRegister, "Добро пожаловать, $name!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MainActivityRegister, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    finish()
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnRegister.isEnabled = true
                    val msg = when {
                        ex is ApiException && ex.code == 400 -> ex.message ?: "Неверный или истёкший код"
                        ex is ApiException && ex.code == 409 -> ex.message ?: "Пользователь уже существует"
                        else -> friendlyError(ex)
                    }
                    Toast.makeText(this@MainActivityRegister, msg, Toast.LENGTH_LONG).show()
                    if (ex is ApiException && ex.code == 400) showCodeDialog()
                }
            }
        }
    }
}
