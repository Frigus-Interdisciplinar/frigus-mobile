package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName

data class UserRegisterRequest(
    @SerializedName("name")
    var name: String = "",

    @SerializedName("birthDate")
    var birthDate: String = "",

    @SerializedName("email")
    var email: String = "",

    @SerializedName("rawPassword")
    var rawPassword: String = ""
)
