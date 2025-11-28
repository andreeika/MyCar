package com.example.mycar

data class Car(
    val id: Int,
    val brand: String,
    val model: String,
    val mileage: Double,
    val photoBytes: ByteArray?,
    val displayName: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Car

        if (id != other.id) return false
        if (brand != other.brand) return false
        if (model != other.model) return false
        if (mileage != other.mileage) return false
        if (displayName != other.displayName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + brand.hashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + mileage.hashCode()
        result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
        result = 31 * result + displayName.hashCode()
        return result
    }
}