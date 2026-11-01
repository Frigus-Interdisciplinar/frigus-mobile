package com.example.frigus_mobile.ui.conversation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ConversationMessage
import com.google.android.material.imageview.ShapeableImageView

class ConversationMessagesAdapter(
    private val isGroup: Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ConversationMessage>()

    fun submitList(newMessages: List<ConversationMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: ConversationMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isFromMe) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            val view = inflater.inflate(R.layout.item_conversation_message_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_conversation_message_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = messages[position]
        if (holder is SentViewHolder) {
            holder.bind(item)
        } else if (holder is ReceivedViewHolder) {
            holder.bind(item, isGroup)
        }
    }

    override fun getItemCount(): Int = messages.size

    class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView? = itemView.findViewById(R.id.tvMessageSender)
        private val tvBody: TextView = itemView.findViewById(R.id.tvMessageBody)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)

        fun bind(item: ConversationMessage) {
            tvSender?.visibility = View.GONE
            tvBody.text = item.message
            tvTime.text = item.timestamp
        }
    }

    class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tvMessageSender)
        private val ivAvatar: ShapeableImageView = itemView.findViewById(R.id.ivMessageSenderAvatar)
        private val tvBody: TextView = itemView.findViewById(R.id.tvMessageBody)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)

        fun bind(item: ConversationMessage, isGroup: Boolean) {
            val context = itemView.context
            tvBody.text = item.message
            tvTime.text = item.timestamp

            if (isGroup) {
                tvSender.visibility = View.VISIBLE
                tvSender.text = item.senderName
                ivAvatar.visibility = View.VISIBLE
                ivAvatar.setImageDrawable(
                    AvatarUtils.createAvatarDrawable(context, item.senderName, 32)
                )
            } else {
                tvSender.visibility = View.GONE
                ivAvatar.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
}
