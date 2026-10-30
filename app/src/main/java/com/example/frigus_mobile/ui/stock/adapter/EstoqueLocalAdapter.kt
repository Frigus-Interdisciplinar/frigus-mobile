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
import com.example.frigus_mobile.utils.ProductCategoryHelper

class EstoqueLocalAdapter(
    private val context: Context,
    private var items: List<StockProductResponse>,
    private val onItemClick: (StockProductResponse) -> Unit
) : RecyclerView.Adapter<EstoqueLocalAdapter.ViewHolder>() {

    fun updateList(newList: List<StockProductResponse>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_estoque_local_produto, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val product = item.product

        holder.tvNome.text = product?.name ?: "Alimento"

        val qtd = item.quantity ?: 1
        val unit = product?.unitOfMeasure?.takeIf { it.isNotBlank() } ?: "un"
        holder.tvQuantidade.text = "$qtd $unit"

        val exp = ProductCategoryHelper.calcularStatusValidade(item.expireDate)
        if (exp != null) {
            holder.tvDataValidade.visibility = View.VISIBLE
            holder.tvDataValidade.text = exp.formattedDate
            holder.tvValidadeStatus.visibility = View.VISIBLE
            holder.tvValidadeStatus.text = exp.statusText
            holder.tvValidadeStatus.setTextColor(ContextCompat.getColor(context, exp.colorRes))
        } else {
            holder.tvDataValidade.visibility = View.GONE
            holder.tvValidadeStatus.visibility = View.GONE
        }

        val imgRes = ProductCategoryHelper.getImagemParaProduto(product?.name, item.category ?: product?.category)
        if (imgRes == R.drawable.img_frango) {
            holder.imgProduto.setPadding(0, 0, 0, 0)
            holder.imgProduto.clearColorFilter()
            holder.imgProduto.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.imgProduto.setImageResource(imgRes)
        } else {
            holder.imgProduto.setPadding(0, 0, 0, 0)
            holder.imgProduto.setColorFilter(ContextCompat.getColor(context, R.color.blue_primary))
            holder.imgProduto.scaleType = ImageView.ScaleType.FIT_CENTER
            holder.imgProduto.setImageResource(imgRes)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduto: ImageView = itemView.findViewById(R.id.imgProdutoLocal)
        val tvNome: TextView = itemView.findViewById(R.id.tvNomeProdutoLocal)
        val tvQuantidade: TextView = itemView.findViewById(R.id.tvQuantidadeLocal)
        val tvValidadeStatus: TextView = itemView.findViewById(R.id.tvValidadeStatusLocal)
        val tvDataValidade: TextView = itemView.findViewById(R.id.tvDataValidadeLocal)
    }
}
