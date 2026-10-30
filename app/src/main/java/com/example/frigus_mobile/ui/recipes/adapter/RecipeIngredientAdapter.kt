package com.example.frigus_mobile.ui.recipes.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.RecipeIngredientItem
import java.text.NumberFormat

class RecipeIngredientAdapter(private var ingredients: List<RecipeIngredientItem>) :
    RecyclerView.Adapter<RecipeIngredientAdapter.IngredientViewHolder>() {
    fun updateList(newList: List<RecipeIngredientItem>) {
        if (ingredients == newList) return
        ingredients = newList
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = IngredientViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_recipe_ingredient, parent, false)
    )
    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) =
        holder.bind(ingredients[position], position == ingredients.lastIndex)
    override fun getItemCount(): Int = ingredients.size

    class IngredientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: RecipeIngredientItem, last: Boolean) {
            val context = itemView.context
            val quantity = item.quantity?.let { NumberFormat.getNumberInstance().format(it) }
                ?: context.getString(R.string.recipe_browser_not_informed)
            val unit = item.unit?.takeIf(String::isNotBlank)
            itemView.findViewById<TextView>(R.id.txtIngredientQuantity).text =
                if (unit == null) quantity else context.getString(R.string.recipe_browser_quantity_unit, quantity, unit)
            itemView.findViewById<TextView>(R.id.txtIngredientName).text =
                item.productName?.takeIf(String::isNotBlank) ?: context.getString(R.string.recipe_browser_unnamed_ingredient)
            itemView.findViewById<ImageView>(R.id.imgIngredientIcon).setImageResource(R.drawable.ic_food_package)
            itemView.findViewById<View>(R.id.viewDivider).visibility = if (last) View.GONE else View.VISIBLE
        }
    }
}
