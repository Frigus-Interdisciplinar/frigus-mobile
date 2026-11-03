package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName

data class UserLoginResponse(
    @SerializedName("accessToken")
    var accessToken: String? = null,

    @SerializedName("refreshToken")
    var refreshToken: String? = null,

    @SerializedName("user")
    var user: UserResponse? = null
)
