package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName

data class UserResponse(
    @SerializedName("id")
    var id: String? = null,

    @SerializedName("name")
    var name: String? = null,

    @SerializedName("email")
    var email: String? = null,

    @SerializedName("accountType")
    var accountType: String? = null,

    @SerializedName("birthDate")
    var birthDate: String? = null
)
