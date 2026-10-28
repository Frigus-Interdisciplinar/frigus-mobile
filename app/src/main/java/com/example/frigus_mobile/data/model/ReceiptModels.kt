package com.example.frigus_mobile.data.model

data class ReceiptScanRequest(val stockId: Int, val qrPayload: String)

data class ReceiptResponse(
    val receiptId: String? = null,
    val status: String? = null,
    val invoice: ReceiptInvoice? = null,
    val items: List<ReceiptItem>? = null,
    val warnings: List<String>? = null
)

data class ReceiptInvoice(
    val documentType: String? = null,
    val accessKey: String? = null,
    val issuerName: String? = null,
    val issuedAt: String? = null,
    val totalAmount: Double? = null
)

data class ReceiptItem(
    val lineId: String? = null,
    val description: String? = null,
    val normalizedProductName: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val storagePlace: String? = null,
    val unitOfMeasure: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val unitPrice: Double? = null,
    val totalPrice: Double? = null,
    val confidence: Double? = null,
    val evidence: String? = null,
    val matchedProduct: ReceiptMatchedProduct? = null,
    val expireDate: String? = null
)

data class ReceiptMatchedProduct(val productId: Int? = null, val name: String? = null)

data class ReceiptConfirmRequest(val items: List<ReceiptConfirmItem>)

data class ReceiptConfirmItem(
    val lineId: String,
    val action: String,
    val productId: Int? = null,
    val quantity: Int? = null,
    val expireDate: String? = null
)

data class ReceiptConfirmationResponse(
    val receiptId: String? = null,
    val status: String? = null,
    val importedItems: List<ReceiptImportedItem>? = null,
    val ignoredLineIds: List<String>? = null
)

data class ReceiptImportedItem(
    val lineId: String? = null,
    val stockProductId: Int? = null,
    val productId: Int? = null,
    val quantity: Int? = null
)
