package com.example.frigus_mobile.data.model

data class HumanConversation(
    val id: String? = null, val conversationType: String? = null,
    val groupId: String? = null, val groupName: String? = null, val name: String? = null,
    val participants: List<ConversationParticipant>? = null,
    val latestMessage: HumanMessage? = null, val createdAt: String? = null, val updatedAt: String? = null
)
data class ConversationParticipant(val userId: String? = null, val name: String? = null,
    val joinedAt: String? = null, val leftAt: String? = null)
data class HumanMessage(val id: Int? = null, val conversationId: String? = null,
    val senderId: String? = null, val senderName: String? = null,
    val messageType: String? = null, val content: String? = null, val createdAt: String? = null)
data class ConversationSendRequest(val messageType: String = "TEXT", val content: String)

data class ConversationItem(val id: String, val title: String, val isGroup: Boolean,
    val memberCount: Int, val memberNames: List<String>, val lastMessage: String,
    val lastMessageTime: String, val unreadCount: Int? = null)
data class ConversationMessage(val id: String, val senderName: String, val message: String,
    val timestamp: String, val isFromMe: Boolean)

fun conversationMutationUncertain(status: Int?): Boolean = status == null || status == 408 || status >= 500
