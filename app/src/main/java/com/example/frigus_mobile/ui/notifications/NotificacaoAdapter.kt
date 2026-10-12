package com.example.frigus_mobile.ui.notifications

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.NotificationResponse

class NotificacaoAdapter(
    private val context: Context,
    private var items: List<NotificationResponse>
) : RecyclerView.Adapter<NotificacaoAdapter.ViewHolder>() {

    fun updateList(newList: List<NotificationResponse>) {
        this.items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notificacao, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title ?: "Notificação"
        holder.tvBody.text = item.body ?: ""
        holder.tvTime.text = item.createdAt ?: ""

        when (item.type) {
            "EXPIRATION" -> holder.imgIcon.setImageResource(R.drawable.ic_cat_meat)
            "RECIPE" -> holder.imgIcon.setImageResource(R.drawable.ic_restaurant)
            "LIST" -> holder.imgIcon.setImageResource(R.drawable.ic_receipt_long)
            else -> holder.imgIcon.setImageResource(R.drawable.ic_notifications)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgIcon: ImageView = itemView.findViewById(R.id.imgNotifIcon)
        val tvTitle: TextView = itemView.findViewById(R.id.tvNotifTitle)
        val tvBody: TextView = itemView.findViewById(R.id.tvNotifBody)
        val tvTime: TextView = itemView.findViewById(R.id.tvNotifTime)
    }
}
