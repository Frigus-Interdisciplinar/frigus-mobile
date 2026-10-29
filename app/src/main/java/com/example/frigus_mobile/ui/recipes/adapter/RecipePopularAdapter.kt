package com.example.frigus_mobile.ui.recipes.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.RecipeResponse
import java.text.NumberFormat

class RecipePopularAdapter(
    private var recipes: List<RecipeResponse>,
    private val onRecipeClick: (RecipeResponse) -> Unit,
    private val showAvailability: Boolean = false
) : RecyclerView.Adapter<RecipePopularAdapter.RecipeViewHolder>() {
    fun updateList(newList: List<RecipeResponse>) {
        if (recipes == newList) return
        recipes = newList
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = RecipeViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_recipe_popular, parent, false)
    )
    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) = holder.bind(recipes[position])
    override fun getItemCount(): Int = recipes.size

    inner class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.txtRecipePopularTitle)
        private val description: TextView = itemView.findViewById(R.id.recipeRowDescription)
        private val availability: TextView = itemView.findViewById(R.id.recipeRowAvailability)
        fun bind(recipe: RecipeResponse) {
            val context = itemView.context
            title.text = recipe.recipeName?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.recipe_browser_unnamed)
            description.text = recipe.recipeDescription?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.recipe_browser_no_description)
            val matched = recipe.matchedIngredients?.takeIf { it >= 0 }
            val missing = recipe.missingIngredients?.takeIf { it >= 0 }
            availability.visibility = if (showAvailability && matched != null && missing != null) View.VISIBLE else View.GONE
            if (matched != null && missing != null) availability.text = context.getString(
                R.string.recipe_browser_match_counts,
                NumberFormat.getIntegerInstance().format(matched),
                NumberFormat.getIntegerInstance().format(missing)
            )
            itemView.setOnClickListener { onRecipeClick(recipe) }
        }
    }
}
