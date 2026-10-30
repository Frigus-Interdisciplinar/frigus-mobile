package com.example.frigus_mobile.ui.stock.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.ui.stock.AlimentoDetalhesActivity
import com.example.frigus_mobile.utils.ProductCategoryHelper

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
        val productName = item.product?.name?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.inventory_unnamed_product)
        val qty = item.quantity?.toString() ?: "-"
        val unit = when (item.product?.unitOfMeasure) {
            "GRAM" -> "g"
            "KILOGRAM" -> "kg"
            "LITER" -> "L"
            "MILLILITER" -> "ml"
            else -> "un"
        }
        val expireDate = item.expireDate ?: "Sem data"

        holder.tvNome.text = productName
        holder.tvSubtitulo.text = "$qty$unit • Vence $expireDate"

        // Ícone unificado baseado na categoria
        holder.imgProduto.clearColorFilter()
        holder.imgProduto.setImageResource(
            ProductCategoryHelper.getIconeCategoria(item.category ?: item.product?.category)
        )

        // Status badge
        when (item.productStatus) {
            "EXPIRED" -> {
                holder.imgStatusBadge.setImageResource(R.drawable.ic_cancel)
                holder.imgStatusBadge.setColorFilter(ContextCompat.getColor(context, R.color.color_red))
                holder.imgStatusBadge.visibility = View.VISIBLE
            }
            "NEAR_EXPIRATION" -> {
                holder.imgStatusBadge.setImageResource(R.drawable.ic_cancel)
                holder.imgStatusBadge.setColorFilter(ContextCompat.getColor(context, R.color.color_gold))
                holder.imgStatusBadge.visibility = View.VISIBLE
            }
            else -> {
                holder.imgStatusBadge.setImageResource(R.drawable.ic_circle_check)
                holder.imgStatusBadge.setColorFilter(ContextCompat.getColor(context, R.color.color_green))
                holder.imgStatusBadge.visibility = View.VISIBLE
            }
        }

        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener {
            if (onClick != null) {
                onClick.invoke(item)
            } else {
                val intent = Intent(context, AlimentoDetalhesActivity::class.java).apply {
                    putExtra("STOCK_PRODUCT", item)
                    putExtra(AlimentoDetalhesActivity.EXTRA_LOT_ID, item.id ?: 0)
                }
                context.startActivity(intent)
            }
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
