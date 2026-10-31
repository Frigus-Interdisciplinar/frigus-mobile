package com.example.frigus_mobile.data.model

data class PlanBenefit(val code: String? = null, val label: String? = null, val enabled: Boolean = false, val limit: Int? = null)
data class PlanLimits(
    val planCode: String? = null, val maxGroupsCreated: Int? = null, val maxGroupMembers: Int? = null,
    val maxStocks: Int? = null, val maxProductsPerStock: Int? = null,
    val allowOwnProducts: Boolean? = null, val allowSavedRecipes: Boolean? = null,
    val allowMoneySaving: Boolean? = null, val hasMonthlyReport: Boolean? = null,
    val enterprise: Boolean? = null, val costPerPublishedAd: Double? = null
)
data class ActivePlan(
    val id: Int? = null, val planCode: String? = null, val name: String? = null,
    val description: String? = null, val price: Double? = null,
    val billingInterval: String? = null, val active: Boolean = false,
    val limits: PlanLimits? = null, val benefits: List<PlanBenefit>? = null
)
data class Subscription(
    val id: String? = null, val plan: ActivePlan? = null, val status: String? = null,
    val currentPeriodEnd: String? = null, val canceledAt: String? = null,
    val limits: PlanLimits? = null, val autoRenew: Boolean? = null
)
data class BillingTransaction(
    val id: String? = null, val status: String? = null, val amount: Double? = null,
    val paymentMethod: String? = null, val planCode: String? = null,
    val errorMessage: String? = null, val createdAt: String? = null
)
data class BillingTransactionPage(
    val content: List<BillingTransaction>? = null, val number: Int = 0,
    val totalPages: Int = 0, val totalElements: Long = 0
)
data class GroupMember(
    val userId: String? = null, val name: String? = null, val email: String? = null,
    val isOwner: Boolean = false, val memberRole: String? = null
)
data class FamilyGroup(
    val id: String? = null, val name: String? = null, val isOwner: Boolean = false,
    val membersCount: Int? = null, val members: List<GroupMember>? = null
)
data class GroupInvitation(
    val id: String? = null, val groupId: String? = null, val email: String? = null,
    val name: String? = null, val memberRole: String? = null, val expiresAt: String? = null,
    val acceptedAt: String? = null, val revokedAt: String? = null
)
data class InviteMemberRequest(val email: String, val memberRole: String)
data class CheckoutRequest(val planCode: String)
data class GroupCreateRequest(val name: String)
data class StockCreateRequest(val groupId: String, val name: String)
