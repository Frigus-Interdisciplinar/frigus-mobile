package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName

data class UserLoginRequest(
    @SerializedName("email")
    var email: String = "",

    @SerializedName("rawPassword")
    var password: String = ""
)
