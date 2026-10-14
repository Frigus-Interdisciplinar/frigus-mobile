package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName

data class GroupResponse(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("ownerId")
    val ownerId: String? = null,

    @SerializedName("ownerName")
    val ownerName: String? = null,

    @SerializedName("isOwner")
    val isOwner: Boolean? = null,

    @SerializedName("defaultConversationId")
    val defaultConversationId: String? = null,

    @SerializedName("membersCount")
    val membersCount: Int = 0
)
