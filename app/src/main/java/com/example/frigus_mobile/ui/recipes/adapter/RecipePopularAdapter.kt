package com.example.frigus_mobile.ui.recipes.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.RecipeResponse

class RecipePopularAdapter(
    private var recipes: List<RecipeResponse>,
    private val onRecipeClick: (RecipeResponse) -> Unit
) : RecyclerView.Adapter<RecipePopularAdapter.RecipeViewHolder>() {

    fun updateList(newList: List<RecipeResponse>) {
        this.recipes = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe_popular, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(recipes[position])
    }

    override fun getItemCount(): Int = recipes.size

    inner class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgThumbnail: ImageView = itemView.findViewById(R.id.imgRecipeThumbnail)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtRecipePopularTitle)
        private val txtTime: TextView = itemView.findViewById(R.id.txtRecipePopularTime)
        private val txtRating: TextView = itemView.findViewById(R.id.txtRecipePopularRating)

        fun bind(recipe: RecipeResponse) {
            txtTitle.text = recipe.recipeName ?: "Receita"
            txtTime.text = recipe.formattedTime
            txtRating.text = recipe.formattedRating

            if (recipe.imageDrawableRes != null) {
                imgThumbnail.setImageResource(recipe.imageDrawableRes!!)
            } else {
                imgThumbnail.setImageResource(R.drawable.img_frango_molho_cremoso)
            }

            itemView.setOnClickListener {
                onRecipeClick(recipe)
            }
        }
    }
}
