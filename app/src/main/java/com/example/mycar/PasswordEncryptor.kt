package com.example.mycar

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.security.MessageDigest

object PasswordEncryptor {
    fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(password.toByteArray())
            Base64.encodeToString(hash, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            password
        }
    }

    fun checkPassword(inputPassword: String, storedHashedPassword: String): Boolean {
        return try {
            val hashedInput = hashPassword(inputPassword)
            hashedInput == storedHashedPassword
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

}