package com.example.mycar

data class Refueling(
    val refuelingId: Int = 0,
    val carId: Int,
    val fuelId: Int,
    val stationId: Int,
    val date: String,
    val mileage: Double,
    val volume: Double,
    val pricePerLiter: Double,
    val totalAmount: Double,
    val fullTank: Boolean
)