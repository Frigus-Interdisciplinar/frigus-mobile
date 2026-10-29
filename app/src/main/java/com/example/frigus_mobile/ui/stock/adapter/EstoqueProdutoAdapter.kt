package com.example.frigus_mobile.ui.stock.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockProductResponse
import java.text.NumberFormat
import com.example.frigus_mobile.ui.stock.stockCategoryIcon
import com.example.frigus_mobile.ui.stock.lotUnitLabel

class EstoqueProdutoAdapter(
    private val context: Context,
    private var items: List<StockProductResponse>,
    private val onClick: ((StockProductResponse) -> Unit)? = null
) : RecyclerView.Adapter<EstoqueProdutoAdapter.ViewHolder>() {

    fun updateList(newList: List<StockProductResponse>) {
        if (items == newList) return
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_estoque_produto, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.itemView.isClickable = onClick != null && (item.id ?: 0) > 0
        holder.itemView.isFocusable = holder.itemView.isClickable
        holder.itemView.setOnClickListener(if (onClick != null && (item.id ?: 0) > 0)
            View.OnClickListener { onClick.invoke(item) } else null)
        holder.tvNome.text = item.product?.name?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.inventory_unnamed_product)
        val quantity = item.quantity?.let { NumberFormat.getIntegerInstance().format(it) }
            ?: context.getString(R.string.inventory_unknown)
        val unit = context.lotUnitLabel(item.product?.unitOfMeasure)
        val status = when (item.productStatus) {
            "FRESH" -> R.string.inventory_fresh
            "NEAR_EXPIRATION" -> R.string.inventory_near_expiration
            "EXPIRED" -> R.string.inventory_expired
            else -> R.string.inventory_unclassified
        }
        holder.tvSubtitulo.text = listOf(
            context.getString(R.string.inventory_quantity, quantity, unit),
            context.getString(R.string.inventory_expiry, item.expireDate?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.inventory_unknown)),
            context.getString(status),
            context.getString(R.string.inventory_lot_identity, item.id, item.stockId)
        ).joinToString("\n")
        holder.imgProduto.setImageResource(stockCategoryIcon(item.category ?: item.product?.category))
        val color = when (item.productStatus) {
            "FRESH" -> R.color.color_green
            "NEAR_EXPIRATION" -> R.color.color_gold
            "EXPIRED" -> R.color.color_red
            else -> null
        }
        holder.imgStatusBadge.visibility = if (color == null) View.GONE else View.VISIBLE
        if (color != null) {
            holder.imgStatusBadge.setImageResource(
                if (item.productStatus == "FRESH") R.drawable.ic_circle_check else R.drawable.ic_cancel
            )
            holder.imgStatusBadge.setColorFilter(ContextCompat.getColor(context, color))
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduto: ImageView = itemView.findViewById(R.id.imgProduto)
        val tvNome: TextView = itemView.findViewById(R.id.tvNomeProduto)
        val tvSubtitulo: TextView = itemView.findViewById(R.id.tvSubtituloProduto)
        val imgStatusBadge: ImageView = itemView.findViewById(R.id.imgStatusBadge)
    }
}
