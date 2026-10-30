package com.example.frigus_mobile.ui.recipes.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.ui.recipes.RecipeFavoriteAction
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat

class RecipePopularAdapter(
    private var recipes: List<RecipeResponse>,
    private val onRecipeClick: (RecipeResponse) -> Unit,
    private val showAvailability: Boolean = false,
    private val onFavoriteClick: ((RecipeResponse) -> Unit)? = null
) : RecyclerView.Adapter<RecipePopularAdapter.RecipeViewHolder>() {
    private var actions: Map<Int, RecipeFavoriteAction> = emptyMap()
    fun updateActions(newActions: Map<Int, RecipeFavoriteAction>) {
        actions = newActions
        notifyDataSetChanged()
    }
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
        private val favoriteButton: MaterialButton = itemView.findViewById(R.id.recipeRowFavorite)
        private val favoriteStatus: TextView = itemView.findViewById(R.id.recipeRowFavoriteStatus)
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
            val action = recipe.recipeId?.let(actions::get)
            favoriteButton.visibility = if (onFavoriteClick == null) View.GONE else View.VISIBLE
            favoriteButton.isEnabled = recipe.recipeId != null &&
                (action?.uncertain == true || recipe.favorite != null) && action?.busy != true
            favoriteButton.setText(when {
                action?.busy == true -> R.string.recipe_favorite_working
                action?.uncertain == true -> R.string.recipe_favorite_verify
                recipe.favorite == true -> R.string.recipe_favorite_remove
                recipe.favorite == false -> R.string.recipe_favorite_add
                else -> R.string.recipe_favorite_unknown
            })
            favoriteButton.setOnClickListener { onFavoriteClick?.invoke(recipe) }
            favoriteStatus.visibility = if (action?.error != null || recipe.favorite == null) View.VISIBLE else View.GONE
            favoriteStatus.text = when {
                action?.uncertain == true -> context.getString(R.string.recipe_favorite_uncertain)
                action?.error?.status == 403 -> context.getString(R.string.recipe_favorite_forbidden)
                action?.error != null -> action.error.userMessage(context.getString(R.string.recipe_favorite_error))
                recipe.favorite == null -> context.getString(R.string.recipe_favorite_not_informed)
                else -> ""
            }
            itemView.setOnClickListener { onRecipeClick(recipe) }
        }
    }
}
