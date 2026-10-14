package com.example.frigus_mobile.ui.recipes.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.RecipeIngredientItem

class RecipeIngredientAdapter(
    private var ingredients: List<RecipeIngredientItem>
) : RecyclerView.Adapter<RecipeIngredientAdapter.IngredientViewHolder>() {

    fun updateList(newList: List<RecipeIngredientItem>) {
        this.ingredients = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe_ingredient, parent, false)
        return IngredientViewHolder(view)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        val isLast = position == ingredients.size - 1
        holder.bind(ingredients[position], isLast)
    }

    override fun getItemCount(): Int = ingredients.size

    inner class IngredientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgIcon: ImageView = itemView.findViewById(R.id.imgIngredientIcon)
        private val txtQuantity: TextView = itemView.findViewById(R.id.txtIngredientQuantity)
        private val txtName: TextView = itemView.findViewById(R.id.txtIngredientName)
        private val viewDivider: View = itemView.findViewById(R.id.viewDivider)

        fun bind(item: RecipeIngredientItem, isLast: Boolean) {
            txtQuantity.text = item.formattedQuantity
            txtName.text = item.productName ?: "Ingrediente"
            viewDivider.visibility = if (isLast) View.GONE else View.VISIBLE

            val iconRes = resolveIngredientIcon(item)
            imgIcon.setImageResource(iconRes)
        }

        private fun resolveIngredientIcon(item: RecipeIngredientItem): Int {
            val name = (item.productName ?: "").lowercase()
            val category = (item.productCategory ?: "").uppercase()

            return when {
                name.contains("frango") || name.contains("carne") || name.contains("peito") || category.contains("CARNE") -> {
                    R.drawable.ic_food_meat
                }
                name.contains("creme") || name.contains("leite") || name.contains("caixa") || name.contains("queijo") || category.contains("LATICINIO") -> {
                    R.drawable.ic_food_package
                }
                name.contains("alho") || name.contains("tempero") || category.contains("TEMPERO") -> {
                    R.drawable.ic_food_garlic
                }
                name.contains("tomate") || name.contains("cebola") || name.contains("legume") || category.contains("HORTIFRUTI") -> {
                    R.drawable.ic_food_veggie
                }
                else -> R.drawable.ic_food_package
            }
        }
    }
}
