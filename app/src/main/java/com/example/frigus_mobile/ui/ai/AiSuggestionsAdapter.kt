package com.example.frigus_mobile.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.RecipeResponse

class AiSuggestionsAdapter(private val openRecipe: (Int) -> Unit) :
    RecyclerView.Adapter<AiSuggestionsAdapter.SuggestionHolder>() {
    private var items: List<RecipeResponse> = emptyList()

    fun submit(items: List<RecipeResponse>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionHolder =
        SuggestionHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_ai_suggestion, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SuggestionHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.recipeName?.takeIf(String::isNotBlank)
            ?: holder.itemView.context.getString(R.string.recipe_browser_unnamed)
        holder.description.apply {
            text = item.recipeDescription?.takeIf(String::isNotBlank).orEmpty()
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        holder.match.apply {
            val available = item.matchedIngredients
            val missing = item.missingIngredients
            visibility = if (available == null && missing == null) View.GONE else View.VISIBLE
            text = context.getString(R.string.ai_suggestion_counts,
                available?.toString() ?: context.getString(R.string.recipe_browser_not_informed),
                missing?.toString() ?: context.getString(R.string.recipe_browser_not_informed))
        }
        holder.itemView.setOnClickListener { item.recipeId?.let(openRecipe) }
    }

    class SuggestionHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.aiSuggestionName)
        val description: TextView = view.findViewById(R.id.aiSuggestionDescription)
        val match: TextView = view.findViewById(R.id.aiSuggestionMatch)
    }
}
