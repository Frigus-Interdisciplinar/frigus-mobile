package com.example.frigus_mobile.ui.stock.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockMovementResponse
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StockMovementAdapter : RecyclerView.Adapter<StockMovementAdapter.Holder>() {
    private var items: List<StockMovementResponse> = emptyList()
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

    fun updateList(value: List<StockMovementResponse>) {
        items = value
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_stock_movement, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val unknown = context.getString(R.string.account_not_informed)
        val type = when (item.movementType) {
            "IN" -> context.getString(R.string.movement_type_in)
            "OUT" -> context.getString(R.string.movement_type_out)
            "ADJUSTMENT" -> context.getString(R.string.movement_type_adjustment)
            else -> item.movementType?.takeIf(String::isNotBlank) ?: unknown
        }
        val quantity = item.quantity?.let { NumberFormat.getIntegerInstance().format(it) } ?: unknown
        val balance = item.balanceAfter?.let { NumberFormat.getIntegerInstance().format(it) } ?: unknown
        val date = item.date?.takeIf(String::isNotBlank)?.let {
            runCatching { dateFormat.format(Instant.parse(it)) }.getOrDefault(it)
        } ?: unknown
        holder.title.text = context.getString(R.string.movement_item_title, type, quantity)
        holder.detail.text = context.getString(R.string.movement_item_detail, date, balance)
        holder.observation.apply {
            text = item.observation.orEmpty()
            visibility = if (item.observation.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.movementItemTitle)
        val detail: TextView = view.findViewById(R.id.movementItemDetail)
        val observation: TextView = view.findViewById(R.id.movementItemObservation)
    }
}
