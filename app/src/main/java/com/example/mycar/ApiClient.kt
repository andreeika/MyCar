package com.example.mycar

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit


object ApiClient {

    private const val BASE_URL = "https://fruitlessly-supreme-minnow.cloudpub.ru"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()


    private fun get(path: String): JSONObject? {
        val req = Request.Builder().url("$BASE_URL$path").get().build()
        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return null
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            JSONObject(body)
        }
    }

    private fun getArray(path: String): JSONArray {
        val req = Request.Builder().url("$BASE_URL$path").get().build()
        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return JSONArray()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            JSONArray(body)
        }
    }

    private fun post(path: String, json: JSONObject): JSONObject {
        val body = json.toString().toRequestBody(JSON)
        val req = Request.Builder().url("$BASE_URL$path").post(body).build()
        return client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) throw ApiException(resp.code, respBody)
            JSONObject(respBody)
        }
    }

    private fun put(path: String, json: JSONObject): JSONObject {
        val body = json.toString().toRequestBody(JSON)
        val req = Request.Builder().url("$BASE_URL$path").put(body).build()
        return client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) throw ApiException(resp.code, respBody)
            JSONObject(respBody)
        }
    }

    private fun delete(path: String): JSONObject {
        val req = Request.Builder().url("$BASE_URL$path").delete().build()
        return client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) throw ApiException(resp.code, respBody)
            JSONObject(respBody)
        }
    }


    fun login(username: String, password: String): JSONObject {
        return post("/auth/login", JSONObject().apply {
            put("username", username)
            put("password", password)
        })
    }

    fun register(fullName: String, email: String, username: String, password: String): JSONObject {
        return post("/auth/register", JSONObject().apply {
            put("full_name", fullName)
            put("email", email)
            put("username", username)
            put("password", password)
        })
    }

    fun sendVerificationCode(email: String): JSONObject {
        return post("/auth/send-code", JSONObject().apply {
            put("email", email)
        })
    }

    fun verifyAndRegister(fullName: String, email: String, username: String, password: String, code: String): JSONObject {
        return post("/auth/verify-register", JSONObject().apply {
            put("full_name", fullName)
            put("email", email)
            put("username", username)
            put("password", password)
            put("code", code)
        })
    }


    fun getCars(userId: Int): JSONArray = getArray("/users/$userId/cars")

    fun addCar(
        userId: Int,
        brandId: Int?, brandName: String?,
        modelId: Int?, modelName: String?,
        mileage: Double,
        photoBase64: String? = null
    ): JSONObject {
        val json = JSONObject().apply {
            if (brandId != null) put("brand_id", brandId) else put("brand_id", JSONObject.NULL)
            if (brandName != null) put("brand_name", brandName) else put("brand_name", JSONObject.NULL)
            if (modelId != null) put("model_id", modelId) else put("model_id", JSONObject.NULL)
            if (modelName != null) put("model_name", modelName) else put("model_name", JSONObject.NULL)
            put("mileage", mileage)
            if (photoBase64 != null) put("photo", photoBase64) else put("photo", JSONObject.NULL)
        }
        return post("/users/$userId/cars", json)
    }

    fun updateCar(
        userId: Int, carId: Int,
        brandId: Int?, brandName: String?,
        modelId: Int?, modelName: String?,
        mileage: Double?,
        photoBase64: String? = null
    ): JSONObject {
        val json = JSONObject().apply {
            if (brandId != null) put("brand_id", brandId) else put("brand_id", JSONObject.NULL)
            if (brandName != null) put("brand_name", brandName) else put("brand_name", JSONObject.NULL)
            if (modelId != null) put("model_id", modelId) else put("model_id", JSONObject.NULL)
            if (modelName != null) put("model_name", modelName) else put("model_name", JSONObject.NULL)
            if (mileage != null) put("mileage", mileage) else put("mileage", JSONObject.NULL)
            if (photoBase64 != null) put("photo", photoBase64) else put("photo", JSONObject.NULL)
        }
        return put("/users/$userId/cars/$carId", json)
    }

    fun deleteCar(userId: Int, carId: Int): JSONObject = delete("/users/$userId/cars/$carId")


    private val photoCache = mutableMapOf<Int, ByteArray>()

    fun getCarPhotoBytes(userId: Int, carId: Int): ByteArray? {
        photoCache[carId]?.let { return it }
        val req = Request.Builder().url("$BASE_URL/users/$userId/cars/$carId/photo").get().build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else resp.body?.bytes()?.also { photoCache[carId] = it }
        }
    }

    fun invalidatePhotoCache(carId: Int) {
        photoCache.remove(carId)
    }

    fun clearPhotoCache() {
        photoCache.clear()
    }


    fun getBrands(): JSONArray = getArray("/brands")

    fun getModels(brandId: Int): JSONArray = getArray("/brands/$brandId/models")

    fun getRefueling(carId: Int): JSONArray = getArray("/cars/$carId/refueling")

    fun addRefueling(
        carId: Int, fuelId: Int,
        stationId: Int?, stationName: String?,
        date: String, mileage: Double,
        volume: Double, pricePerLiter: Double, fullTank: Boolean
    ): JSONObject {
        val json = JSONObject().apply {
            put("fuel_id", fuelId)
            if (stationId != null) put("station_id", stationId) else put("station_id", JSONObject.NULL)
            if (stationName != null) put("station_name", stationName) else put("station_name", JSONObject.NULL)
            put("date", date)
            put("mileage", mileage)
            put("volume", volume)
            put("price_per_liter", pricePerLiter)
            put("full_tank", fullTank)
        }
        return post("/cars/$carId/refueling", json)
    }

    fun updateRefueling(
        carId: Int, refuelingId: Int, fuelId: Int,
        stationId: Int?, stationName: String?,
        date: String, mileage: Double,
        volume: Double, pricePerLiter: Double, fullTank: Boolean
    ): JSONObject {
        val json = JSONObject().apply {
            put("fuel_id", fuelId)
            if (stationId != null) put("station_id", stationId) else put("station_id", JSONObject.NULL)
            if (stationName != null) put("station_name", stationName) else put("station_name", JSONObject.NULL)
            put("date", date)
            put("mileage", mileage)
            put("volume", volume)
            put("price_per_liter", pricePerLiter)
            put("full_tank", fullTank)
        }
        return put("/cars/$carId/refueling/$refuelingId", json)
    }

    fun deleteRefueling(carId: Int, refuelingId: Int): JSONObject =
        delete("/cars/$carId/refueling/$refuelingId")


    fun getFuels(): JSONArray = getArray("/fuels")

    fun getStations(): JSONArray = getArray("/stations")


    fun getMaintenance(carId: Int): JSONArray = getArray("/cars/$carId/maintenance")

    fun addMaintenance(
        carId: Int, serviceTypeId: Int, date: String, mileage: Int,
        totalAmount: Double, description: String?,
        nextServiceMileage: Int?, nextServiceDate: String?
    ): JSONObject {
        val json = JSONObject().apply {
            put("service_type_id", serviceTypeId)
            put("date", date)
            put("mileage", mileage)
            put("total_amount", totalAmount)
            put("description", description ?: JSONObject.NULL)
            if (nextServiceMileage != null) put("next_service_mileage", nextServiceMileage)
            else put("next_service_mileage", JSONObject.NULL)
            if (nextServiceDate != null) put("next_service_date", nextServiceDate)
            else put("next_service_date", JSONObject.NULL)
        }
        return post("/cars/$carId/maintenance", json)
    }

    fun updateMaintenance(
        carId: Int, maintenanceId: Int, serviceTypeId: Int, date: String, mileage: Int,
        totalAmount: Double, description: String?,
        nextServiceMileage: Int?, nextServiceDate: String?
    ): JSONObject {
        val json = JSONObject().apply {
            put("service_type_id", serviceTypeId)
            put("date", date)
            put("mileage", mileage)
            put("total_amount", totalAmount)
            put("description", description ?: JSONObject.NULL)
            if (nextServiceMileage != null) put("next_service_mileage", nextServiceMileage)
            else put("next_service_mileage", JSONObject.NULL)
            if (nextServiceDate != null) put("next_service_date", nextServiceDate)
            else put("next_service_date", JSONObject.NULL)
        }
        return put("/cars/$carId/maintenance/$maintenanceId", json)
    }

    fun deleteMaintenance(carId: Int, maintenanceId: Int): JSONObject =
        delete("/cars/$carId/maintenance/$maintenanceId")


    fun getStatistics(carId: Int, dateFrom: String, dateTo: String): JSONObject {
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        return get("/cars/$carId/statistics?date_from=${enc(dateFrom)}&date_to=${enc(dateTo)}") ?: JSONObject()
    }

    fun getMonthlyStatistics(carId: Int, dateFrom: String, dateTo: String): JSONArray {
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        return getArray("/cars/$carId/statistics/monthly?date_from=${enc(dateFrom)}&date_to=${enc(dateTo)}")
    }

    fun getFuelConsumption(carId: Int, dateFrom: String, dateTo: String): JSONObject {
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        return get("/cars/$carId/statistics/fuel-consumption?date_from=${enc(dateFrom)}&date_to=${enc(dateTo)}") ?: JSONObject()
    }


    fun getServiceTypes(): JSONArray = getArray("/service-types")

    fun getServiceCategories(): JSONArray = getArray("/service-categories")

    fun addServiceType(name: String, intervalKm: Int, categoryId: Int): JSONObject {
        return post("/service-types", JSONObject().apply {
            put("name", name)
            put("interval_km", intervalKm)
            put("category_id", categoryId)
        })
    }


    fun updateProfile(userId: Int, fullName: String, username: String, email: String): JSONObject {
        return put("/users/$userId/profile", JSONObject().apply {
            put("full_name", fullName)
            put("username", username)
            put("email", email)
        })
    }

    fun updatePassword(userId: Int, currentPassword: String, newPassword: String): JSONObject {
        return put("/users/$userId/password", JSONObject().apply {
            put("current_password", currentPassword)
            put("new_password", newPassword)
        })
    }

    fun sendChangeCode(userId: Int, changeType: String, newEmail: String? = null): JSONObject {
        return post("/users/$userId/send-change-code", JSONObject().apply {
            put("user_id", userId)
            put("change_type", changeType)
            if (newEmail != null) put("new_email", newEmail) else put("new_email", JSONObject.NULL)
        })
    }

    fun verifyChange(userId: Int, changeType: String, code: String,
                     currentPassword: String? = null, newPassword: String? = null,
                     newEmail: String? = null): JSONObject {
        return post("/users/$userId/verify-change", JSONObject().apply {
            put("user_id", userId)
            put("change_type", changeType)
            put("code", code)
            if (currentPassword != null) put("current_password", currentPassword)
            if (newPassword != null) put("new_password", newPassword)
            if (newEmail != null) put("new_email", newEmail)
        })
    }

    fun deleteAccount(userId: Int): JSONObject = delete("/users/$userId")

    fun parseReceipt(qrRaw: String): JSONObject {
        return post("/receipt/parse", JSONObject().apply { put("qr_raw", qrRaw) })
    }
}

class ApiException(val code: Int, message: String) : Exception(message)

fun friendlyError(ex: Exception): String = when (ex) {
    is java.net.ConnectException,
    is java.net.SocketTimeoutException,
    is java.net.UnknownHostException -> "Сервер недоступен. Проверьте подключение к интернету."
    is java.io.IOException -> "Ошибка сети. Проверьте подключение к интернету."
    is ApiException -> when (ex.code) {
        401 -> "Неверный логин или пароль."
        404 -> "Данные не найдены."
        409 -> "Такой пользователь уже существует."
        in 500..599 -> "Ошибка сервера. Попробуйте позже."
        else -> "Ошибка: ${ex.code}"
    }
    else -> "Что-то пошло не так. Попробуйте ещё раз."
}