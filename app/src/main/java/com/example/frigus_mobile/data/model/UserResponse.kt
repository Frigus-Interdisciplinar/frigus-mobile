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
    var birthDate: String? = null,

    @SerializedName("profilePictureUrl")
    var profilePictureUrl: String? = null
)

data class PlanResponse(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("planCode")
    val planCode: String? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("price")
    val price: Double? = null
)

data class SubscriptionResponse(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("userId")
    val userId: String? = null,

    @SerializedName("userEmail")
    val userEmail: String? = null,

    @SerializedName("userName")
    val userName: String? = null,

    @SerializedName("plan")
    val plan: PlanResponse? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("currentPeriodEnd")
    val currentPeriodEnd: String? = null
)
