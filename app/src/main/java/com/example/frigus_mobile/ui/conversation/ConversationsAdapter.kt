package com.example.frigus_mobile.ui.conversation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ConversationItem
import com.google.android.material.imageview.ShapeableImageView

class ConversationsAdapter(
    private val onItemClick: (ConversationItem) -> Unit,
    private val onItemLongClick: (ConversationItem) -> Unit
) : RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder>() {

    private val items = mutableListOf<ConversationItem>()

    fun submitList(newItems: List<ConversationItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowConversation: View = itemView.findViewById(R.id.rowConversation)
        private val ivAvatar: ShapeableImageView = itemView.findViewById(R.id.ivConversationAvatar)
        private val ivSecondAvatar: ShapeableImageView =
            itemView.findViewById(R.id.ivConversationGroupAvatarSecond)
        private val ivThirdAvatar: ShapeableImageView =
            itemView.findViewById(R.id.ivConversationGroupAvatarThird)
        private val tvGroupCount: TextView = itemView.findViewById(R.id.tvConversationGroupCount)
        private val tvName: TextView = itemView.findViewById(R.id.tvConversationName)
        private val tvPreview: TextView = itemView.findViewById(R.id.tvConversationPreview)
        private val tvTime: TextView = itemView.findViewById(R.id.tvConversationTime)
        private val tvUnreadCount: TextView = itemView.findViewById(R.id.tvConversationUnreadCount)

        fun bind(item: ConversationItem) {
            val context = itemView.context
            tvName.text = item.title
            tvPreview.text = item.lastMessage
            tvTime.text = item.lastMessageTime

            if ((item.unreadCount ?: 0) > 0) {
                tvUnreadCount.visibility = View.VISIBLE
                tvUnreadCount.text = item.unreadCount.toString()
            } else {
                tvUnreadCount.visibility = View.GONE
            }

            if (item.isGroup) {
                val members = item.memberNames
                val firstMemberName = members.firstOrNull() ?: item.title
                ivAvatar.setImageDrawable(AvatarUtils.createAvatarDrawable(context, firstMemberName, 58))

                if (members.size >= 2) {
                    ivSecondAvatar.visibility = View.VISIBLE
                    ivSecondAvatar.setImageDrawable(
                        AvatarUtils.createAvatarDrawable(context, members[1], 46)
                    )
                } else {
                    ivSecondAvatar.visibility = View.GONE
                }

                if (members.size >= 3) {
                    ivThirdAvatar.visibility = View.VISIBLE
                    ivThirdAvatar.setImageDrawable(
                        AvatarUtils.createAvatarDrawable(context, members[2], 46)
                    )
                } else {
                    ivThirdAvatar.visibility = View.GONE
                }

                if (members.size > 3) {
                    tvGroupCount.visibility = View.VISIBLE
                    tvGroupCount.text = context.getString(R.string.conversation_extra_members, members.size - 3)
                } else {
                    tvGroupCount.visibility = View.GONE
                }
            } else {
                ivAvatar.setImageDrawable(
                    AvatarUtils.createAvatarDrawable(context, item.title, 58)
                )
                ivSecondAvatar.visibility = View.GONE
                ivThirdAvatar.visibility = View.GONE
                tvGroupCount.visibility = View.GONE
            }

            rowConversation.setOnClickListener { onItemClick(item) }
            rowConversation.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }
}
