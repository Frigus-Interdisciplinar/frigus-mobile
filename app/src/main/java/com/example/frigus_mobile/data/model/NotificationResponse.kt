package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class NotificationResponse(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("groupId")
    val groupId: String? = null,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("body")
    val body: String? = null,

    @SerializedName("referenceId")
    val referenceId: String? = null,

    @SerializedName("readAt")
    var readAt: String? = null,

    @SerializedName("createdAt")
    val createdAt: String? = null
) : Serializable
