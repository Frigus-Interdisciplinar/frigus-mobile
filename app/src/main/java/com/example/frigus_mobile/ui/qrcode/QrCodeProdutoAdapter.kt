package com.example.frigus_mobile.ui.qrcode

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R

data class NotaFiscalItem(
    val id: Int,
    val name: String,
    var quantity: Int,
    val unit: String,
    val price: Double,
    val category: String
)

class QrCodeProdutoAdapter(
    private val items: MutableList<NotaFiscalItem>
) : RecyclerView.Adapter<QrCodeProdutoAdapter.ViewHolder>() {

    fun getItems(): List<NotaFiscalItem> = items

    fun updateItems(newItems: List<NotaFiscalItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_qr_code_produto, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvNome.text = item.name
        holder.tvPreco.text = "${item.quantity} ${item.unit} • R$ ${String.format("%.2f", item.price)}"
        holder.tvQtd.text = item.quantity.toString()

        when (item.category) {
            "MEAT" -> holder.imgIcon.setImageResource(R.drawable.ic_cat_meat)
            "DAIRY" -> holder.imgIcon.setImageResource(R.drawable.ic_cat_dairy)
            "VEGETABLE", "FRUIT" -> holder.imgIcon.setImageResource(R.drawable.ic_cat_hortifruti)
            "BEVERAGE" -> holder.imgIcon.setImageResource(R.drawable.ic_cat_beverages)
            else -> holder.imgIcon.setImageResource(R.drawable.ic_food_package)
        }

        holder.btnAumentar.setOnClickListener {
            item.quantity++
            notifyItemChanged(position)
        }

        holder.btnDiminuir.setOnClickListener {
            if (item.quantity > 1) {
                item.quantity--
                notifyItemChanged(position)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgIcon: ImageView = itemView.findViewById(R.id.imgQrItem)
        val tvNome: TextView = itemView.findViewById(R.id.tvQrItemNome)
        val tvPreco: TextView = itemView.findViewById(R.id.tvQrItemPreco)
        val tvQtd: TextView = itemView.findViewById(R.id.tvQrItemQtd)
        val btnAumentar: TextView = itemView.findViewById(R.id.btnAumentarQtd)
        val btnDiminuir: TextView = itemView.findViewById(R.id.btnDiminuirQtd)
    }
}
