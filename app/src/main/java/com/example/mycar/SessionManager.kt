import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences("mycar_app", Context.MODE_PRIVATE)

    companion object {
        const val USER_ID = "user_id"
        const val USER_NAME = "user_name"
        const val USER_USERNAME = "user_username"
        const val IS_LOGGED_IN = "is_logged_in"
        const val CURRENT_CAR_ID = "current_car_id"
        const val USER_CARS = "user_cars"
    }

    fun saveAuthToken(userId: Int, name: String, username: String) {
        val editor = prefs.edit()
        editor.putInt(USER_ID, userId)
        editor.putString(USER_NAME, name)
        editor.putString(USER_USERNAME, username)
        editor.putBoolean(IS_LOGGED_IN, true)
        editor.apply()
    }

    fun getUserId(): Int {
        return prefs.getInt(USER_ID, 0)
    }

    fun getUserName(): String? {
        return prefs.getString(USER_NAME, null)
    }

    fun getUsername(): String? {
        return prefs.getString(USER_USERNAME, null)
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(IS_LOGGED_IN, false)
    }

    fun logout() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }

    fun setCurrentCar(carId: Int) {
        val editor = prefs.edit()
        editor.putInt(CURRENT_CAR_ID, carId)
        editor.apply()
    }

    fun getCurrentCarId(): Int {
        return prefs.getInt(CURRENT_CAR_ID, 0)
    }

    fun setUserCarIds(carIds: List<Int>) {
        val editor = prefs.edit()
        val carIdsString = carIds.joinToString(",")
        editor.putString(USER_CARS, carIdsString)
        editor.apply()
    }

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

}