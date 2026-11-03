package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName

data class PageResponse<T>(
    @SerializedName("content")
    val content: List<T> = emptyList(),
    @SerializedName("totalElements")
    val totalElements: Long = 0,
    @SerializedName("totalPages")
    val totalPages: Int = 0,
    @SerializedName("number")
    val number: Int = 0,
    @SerializedName("size")
    val size: Int = 0
)
