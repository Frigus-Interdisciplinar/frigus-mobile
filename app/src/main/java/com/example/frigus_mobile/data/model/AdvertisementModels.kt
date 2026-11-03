package com.example.frigus_mobile.data.model

data class AdvertisementResponse(
    val id: String? = null,
    val advertiserId: String? = null,
    val advertiserName: String? = null,
    val name: String? = null,
    val productName: String? = null,
    val category: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val destinationUrl: String? = null,
    val price: Double? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: String? = null,
    val impressions: Long? = null,
    val clicks: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
