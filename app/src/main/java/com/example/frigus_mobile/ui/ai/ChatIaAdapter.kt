package com.example.frigus_mobile.ui.ai

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ChatUiMessage

class ChatIaAdapter(
    private val context: Context,
    private val messages: MutableList<ChatUiMessage>
) : RecyclerView.Adapter<ChatIaAdapter.ViewHolder>() {

    fun addMessage(msg: ChatUiMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        holder.tvMessage.text = msg.text

        val layoutParams = holder.tvMessage.layoutParams as LinearLayout.LayoutParams

        if (msg.isUser) {
            holder.rootLayout.gravity = Gravity.END
            holder.tvMessage.setBackgroundResource(R.drawable.bg_chat_user)
            holder.tvMessage.setTextColor(ContextCompat.getColor(context, R.color.pure_white))
        } else {
            holder.rootLayout.gravity = Gravity.START
            holder.tvMessage.setBackgroundResource(R.drawable.bg_chat_bot)
            holder.tvMessage.setTextColor(ContextCompat.getColor(context, R.color.color_text_primary))
        }
        holder.tvMessage.layoutParams = layoutParams
    }

    override fun getItemCount(): Int = messages.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rootLayout: LinearLayout = itemView.findViewById(R.id.layoutMessageRoot)
        val tvMessage: TextView = itemView.findViewById(R.id.tvChatMessage)
    }
}
