package com.example.mycar

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences("mycar_app", Context.MODE_PRIVATE)

    companion object {
        const val USER_ID = "user_id"
        const val USER_NAME = "user_name"
        const val USER_USERNAME = "user_username"
        const val USER_EMAIL = "user_email"
        const val IS_LOGGED_IN = "is_logged_in"
        const val CURRENT_CAR_ID = "current_car_id"
        const val USER_CARS = "user_cars"
    }

    // Метод для сохранения данных после регистрации/входа (полная версия)
    fun saveAuthToken(userId: Int, name: String, username: String, email: String) {
        val editor = prefs.edit()
        editor.putInt(USER_ID, userId)
        editor.putString(USER_NAME, name)
        editor.putString(USER_USERNAME, username)
        editor.putString(USER_EMAIL, email)
        editor.putBoolean(IS_LOGGED_IN, true)
        editor.apply()
    }

    // Перегруженный метод для обратной совместимости
    fun saveAuthToken(userId: Int, name: String, username: String) {
        saveAuthToken(userId, name, username, "")
    }

    // Сохранение имени пользователя
    fun saveUserName(fullName: String) {
        prefs.edit().putString(USER_NAME, fullName).apply()
    }

    // Сохранение логина
    fun saveUserUsername(username: String) {
        prefs.edit().putString(USER_USERNAME, username).apply()
    }

    // Сохранение email
    fun saveUserEmail(email: String) {
        prefs.edit().putString(USER_EMAIL, email).apply()
    }

    // Получение email
    fun getUserEmail(): String? {
        return prefs.getString(USER_EMAIL, null)
    }

    // Получение ID пользователя
    fun getUserId(): Int {
        return prefs.getInt(USER_ID, 0)
    }

    // Получение имени пользователя
    fun getUserName(): String? {
        return prefs.getString(USER_NAME, null)
    }

    // Получение логина
    fun getUsername(): String? {
        return prefs.getString(USER_USERNAME, null)
    }

    // Проверка, залогинен ли пользователь
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(IS_LOGGED_IN, false)
    }

    // Выход из аккаунта
    fun logout() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }

    // Установка текущего автомобиля
    fun setCurrentCar(carId: Int) {
        prefs.edit().putInt(CURRENT_CAR_ID, carId).apply()
    }

    // Получение текущего автомобиля
    fun getCurrentCarId(): Int {
        return prefs.getInt(CURRENT_CAR_ID, 0)
    }

    // Сохранение списка ID автомобилей пользователя
    fun setUserCarIds(carIds: List<Int>) {
        val editor = prefs.edit()
        val carIdsString = carIds.joinToString(",")
        editor.putString(USER_CARS, carIdsString)
        editor.apply()
    }

    // Получение списка ID автомобилей пользователя
    fun getUserCarIds(): List<Int> {
        val carIdsString = prefs.getString(USER_CARS, "")
        return if (carIdsString.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                carIdsString.split(",").mapNotNull { it.toIntOrNull() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // Проверка, сохранен ли email (полезно для миграции старых пользователей)
    fun hasEmail(): Boolean {
        return !getUserEmail().isNullOrEmpty()
    }

    // Получение полной информации о пользователе
    fun getUserInfo(): Map<String, Any?> {
        return mapOf(
            "id" to getUserId(),
            "name" to getUserName(),
            "username" to getUsername(),
            "email" to getUserEmail(),
            "isLoggedIn" to isLoggedIn()
        )
    }
}