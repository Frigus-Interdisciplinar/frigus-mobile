package com.example.frigus_mobile.ui.shopping

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ShoppingListItem

sealed class ShoppingListRow {
    data class CategoryHeader(val title: String) : ShoppingListRow()
    data class ProductItem(val item: ShoppingListItem) : ShoppingListRow()
}

class ShoppingListAdapter(
    private val onItemClick: (ShoppingListItem) -> Unit,
    private val onCheckChanged: (ShoppingListItem, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<ShoppingListRow>()
    private var enabled = true
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        notifyDataSetChanged()
    }

    fun setItems(items: List<ShoppingListItem>) {
        rows.clear()
        val grouped = items.groupBy { it.category }
        for ((category, categoryItems) in grouped) {
            rows.add(ShoppingListRow.CategoryHeader(category))
            for (item in categoryItems) {
                rows.add(ShoppingListRow.ProductItem(item))
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is ShoppingListRow.CategoryHeader -> VIEW_TYPE_CATEGORY
            is ShoppingListRow.ProductItem -> VIEW_TYPE_PRODUCT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_CATEGORY) {
            val view = inflater.inflate(R.layout.item_shopping_list_category, parent, false)
            CategoryViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_shopping_list_product, parent, false)
            ProductViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ShoppingListRow.CategoryHeader -> (holder as CategoryViewHolder).bind(row.title)
            is ShoppingListRow.ProductItem -> (holder as ProductViewHolder).bind(row.item)
        }
    }

    override fun getItemCount(): Int = rows.size

    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvCategory: TextView = view.findViewById(R.id.tvCategoriaListaCompras)

        fun bind(title: String) {
            tvCategory.text = title
        }
    }

    inner class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: View = view.findViewById(R.id.cardProdutoListaCompras)
        private val checkBox: CheckBox = view.findViewById(R.id.checkProdutoListaCompras)
        private val tvName: TextView = view.findViewById(R.id.tvNomeProdutoLista)
        private val tvDetails: TextView = view.findViewById(R.id.tvDetalhesProdutoLista)

        fun bind(item: ShoppingListItem) {
            tvName.text = item.name

            val detailText = itemView.context.getString(if (item.observation.isNotBlank())
                R.string.shopping_remote_details_note else R.string.shopping_remote_details,
                item.quantity.toString(), shoppingUnitLabel(itemView.context, item.unitCode ?: item.unit), item.observation)
            tvDetails.text = detailText
            tvDetails.visibility = View.VISIBLE

            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = item.isChecked
            checkBox.isEnabled = enabled
            card.isEnabled = enabled

            applyCheckedStyle(item.isChecked)

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                applyCheckedStyle(isChecked)
                onCheckChanged(item, isChecked)
            }

            card.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun applyCheckedStyle(isChecked: Boolean) {
            if (isChecked) {
                tvName.paintFlags = tvName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvName.alpha = 0.5f
                tvDetails.alpha = 0.5f
            } else {
                tvName.paintFlags = tvName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvName.alpha = 1.0f
                tvDetails.alpha = 1.0f
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
        private const val VIEW_TYPE_PRODUCT = 1
    }
}
