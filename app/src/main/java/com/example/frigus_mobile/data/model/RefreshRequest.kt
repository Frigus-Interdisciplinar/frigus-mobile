package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName

data class RefreshRequest(
    @SerializedName("refreshToken")
    val refreshToken: String
)
