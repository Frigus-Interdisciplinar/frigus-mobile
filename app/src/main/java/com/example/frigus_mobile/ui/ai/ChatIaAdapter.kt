package com.example.frigus_mobile.ui.ai

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.AiMessageDelivery
import com.example.frigus_mobile.data.model.ChatUiMessage

class ChatIaAdapter(private val context: Context) : RecyclerView.Adapter<ChatIaAdapter.ViewHolder>() {
    private var messages = emptyList<ChatUiMessage>()
    fun submit(items: List<ChatUiMessage>) {
        if (messages === items) return
        messages = items
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false))
    override fun getItemCount() = messages.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.column.gravity = if (message.isUser) Gravity.END else Gravity.START
        holder.avatar.visibility = if (message.isUser) View.GONE else View.VISIBLE
        holder.text.text = responseText(message)
        holder.text.setBackgroundResource(if (message.isUser) R.drawable.bg_chat_user else R.drawable.bg_chat_bot)
        holder.text.setTextColor(ContextCompat.getColor(context,
            if (message.isUser) R.color.pure_white else R.color.color_text_primary))
        holder.text.alpha = if (message.isLoading) 0.65f else 1f
        holder.text.setTypeface(null, if (message.isLoading) Typeface.ITALIC else Typeface.NORMAL)
        holder.delivery.visibility = if (message.isUser && message.delivery != null) View.VISIBLE else View.GONE
        message.delivery?.let { holder.delivery.setText(when (it) {
            AiMessageDelivery.SENDING -> R.string.ai_chat_sending
            AiMessageDelivery.CONFIRMED -> R.string.ai_chat_confirmed
            AiMessageDelivery.UNCERTAIN -> R.string.ai_chat_not_confirmed
            AiMessageDelivery.REJECTED -> R.string.ai_chat_rejected
        }) }
    }
    private fun responseText(message: ChatUiMessage): String {
        val reply = message.reply ?: return message.text
        val parts = mutableListOf<String>()
        reply.chatMessage?.trim()?.takeIf(String::isNotBlank)?.let(parts::add)
        val name = reply.recipeName?.trim()?.takeIf(String::isNotBlank)
        if (name != null && reply.chatMessage?.contains(name, true) != true) {
            parts += context.getString(R.string.ai_chat_recipe_name, name)
            reply.recipeDescription?.trim()?.takeIf(String::isNotBlank)?.let(parts::add)
        }
        reply.recipeInstructions?.trim()?.takeIf(String::isNotBlank)?.let { instructions ->
            if (reply.chatMessage?.contains(instructions) != true)
                parts += context.getString(R.string.ai_chat_recipe_instructions, instructions)
        }
        return parts.joinToString("\n\n")
    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val column: LinearLayout = view.findViewById(R.id.chatBubbleColumn)
        val avatar: ImageView = view.findViewById(R.id.aiMessageAvatar)
        val text: TextView = view.findViewById(R.id.tvChatMessage)
        val delivery: TextView = view.findViewById(R.id.chatDeliveryStatus)
    }
}
