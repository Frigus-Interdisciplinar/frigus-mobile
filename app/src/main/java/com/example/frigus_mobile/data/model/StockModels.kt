package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class StockResponse(
    @SerializedName("id")
    val id: Int,

    @SerializedName("groupId")
    val groupId: String? = null,

    @SerializedName("name")
    val name: String? = null
) : Serializable

data class StockCreateRequest(
    @SerializedName("groupId")
    val groupId: String,

    @SerializedName("name")
    val name: String
)

data class StockProductProductResponse(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("category")
    val category: String? = null,

    @SerializedName("storagePlace")
    val storagePlace: String? = null,

    @SerializedName("unitPrice")
    val unitPrice: Double? = null,

    @SerializedName("unitOfMeasure")
    val unitOfMeasure: String? = null,

    @SerializedName("brand")
    val brand: String? = null,

    @SerializedName("imageUrl")
    val imageUrl: String? = null
) : Serializable

data class StockProductResponse(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("productId")
    val productId: Int? = null,

    @SerializedName("product")
    val product: StockProductProductResponse? = null,

    @SerializedName("stockId")
    val stockId: Int? = null,

    @SerializedName("quantity")
    var quantity: Int? = null,

    @SerializedName("minimalQuantity")
    val minimalQuantity: Int? = null,

    @SerializedName("purchaseUnitPrice")
    val purchaseUnitPrice: Double? = null,

    @SerializedName("expireDate")
    var expireDate: String? = null,

    @SerializedName("productStatus")
    var productStatus: String? = null,

    @SerializedName("category")
    var category: String? = null
) : Serializable

data class StockProductCreateRequest(
    @SerializedName("productId")
    val productId: Int,

    @SerializedName("stockId")
    val stockId: Int,

    @SerializedName("quantity")
    val quantity: Int,

    @SerializedName("minimalQuantity")
    val minimalQuantity: Int = 1,

    @SerializedName("purchaseUnitPrice")
    val purchaseUnitPrice: Double = 0.0,

    @SerializedName("expireDate")
    val expireDate: String? = null
)

data class StockProductUpdateRequest(
    @SerializedName("quantity")
    val quantity: Int? = null,

    @SerializedName("minimalQuantity")
    val minimalQuantity: Int? = null,

    @SerializedName("purchaseUnitPrice")
    val purchaseUnitPrice: Double? = null,

    @SerializedName("expireDate")
    val expireDate: String? = null,

    @SerializedName("storagePlace")
    val storagePlace: String? = null
)
