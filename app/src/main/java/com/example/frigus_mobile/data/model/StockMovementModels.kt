package com.example.frigus_mobile.data.model

data class StockMovementRequest(
    val movementType: String,
    val quantity: Int,
    val observation: String? = null
)

data class StockMovementResponse(
    val id: Int? = null,
    val stockProductId: Int? = null,
    val userId: String? = null,
    val movementType: String? = null,
    val quantity: Int? = null,
    val balanceAfter: Int? = null,
    val date: String? = null,
    val observation: String? = null,
    val purpose: String? = null
)
