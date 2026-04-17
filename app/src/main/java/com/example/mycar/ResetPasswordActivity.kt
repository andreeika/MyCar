package com.example.mycar

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.*

class ResetPasswordActivity : BaseActivity() {

    private lateinit var layoutStep1: ConstraintLayout
    private lateinit var layoutStep2: ConstraintLayout
    private lateinit var etEmail: EditText
    private lateinit var etCode: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnSendCode: Button
    private lateinit var btnResetPassword: Button
    private lateinit var tvResendCode: TextView
    private lateinit var tvBackToLogin: TextView
    private lateinit var progressOverlay: FrameLayout

    private var email: String = ""
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        layoutStep1 = findViewById(R.id.layoutStep1)
        layoutStep2 = findViewById(R.id.layoutStep2)
        etEmail = findViewById(R.id.etEmail)
        etCode = findViewById(R.id.etCode)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSendCode = findViewById(R.id.btnSendCode)
        btnResetPassword = findViewById(R.id.btnResetPassword)
        tvResendCode = findViewById(R.id.tvResendCode)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)
        progressOverlay = findViewById(R.id.progressOverlay)

        // если пришли с экрана логина с prefilled email
        intent.getStringExtra("email")?.let { etEmail.setText(it) }

        btnSendCode.setOnClickListener { sendCode() }
        btnResetPassword.setOnClickListener { resetPassword() }
        tvResendCode.setOnClickListener { sendCode() }
        tvBackToLogin.setOnClickListener { finish() }
    }

    private fun sendCode() {
        val inputEmail = etEmail.text.toString().trim()
        if (inputEmail.isEmpty()) {
            etEmail.error = "Введите email"
            return
        }
        email = inputEmail
        progressOverlay.visibility = View.VISIBLE
        btnSendCode.isEnabled = false

        scope.launch(Dispatchers.IO) {
            try {
                ApiClient.resetPasswordSendCode(email)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnSendCode.isEnabled = true
                    showStep2()
                    Toast.makeText(this@ResetPasswordActivity, "Код отправлен на $email", Toast.LENGTH_LONG).show()
                }
            } catch (e: ApiException) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnSendCode.isEnabled = true
                    val msg = when (e.code) {
                        404 -> "Email не найден"
                        else -> "Ошибка: ${e.message}"
                    }
                    Toast.makeText(this@ResetPasswordActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnSendCode.isEnabled = true
                    Toast.makeText(this@ResetPasswordActivity, "Нет связи с сервером", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resetPassword() {
        val code = etCode.text.toString().trim()
        val newPass = etNewPassword.text.toString().trim()
        val confirm = etConfirmPassword.text.toString().trim()

        if (code.length != 6) { etCode.error = "Введите 6-значный код"; return }
        if (newPass.length < 6) { etNewPassword.error = "Минимум 6 символов"; return }
        if (newPass != confirm) { etConfirmPassword.error = "Пароли не совпадают"; return }

        progressOverlay.visibility = View.VISIBLE
        btnResetPassword.isEnabled = false

        scope.launch(Dispatchers.IO) {
            try {
                ApiClient.resetPasswordVerify(email, code, newPass)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@ResetPasswordActivity, "Пароль успешно изменён!", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@ResetPasswordActivity, MainActivityLogin::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                    finish()
                }
            } catch (e: ApiException) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnResetPassword.isEnabled = true
                    val msg = when (e.code) {
                        400 -> e.message ?: "Неверный или истёкший код"
                        else -> "Ошибка: ${e.message}"
                    }
                    Toast.makeText(this@ResetPasswordActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    btnResetPassword.isEnabled = true
                    Toast.makeText(this@ResetPasswordActivity, "Нет связи с сервером", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showStep2() {
        layoutStep1.visibility = View.GONE
        layoutStep2.visibility = View.VISIBLE
        // tvBackToLogin привязан к layoutStep1, перепривяжем к layoutStep2
        val params = tvBackToLogin.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        params.topToBottom = R.id.layoutStep2
        tvBackToLogin.layoutParams = params
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
