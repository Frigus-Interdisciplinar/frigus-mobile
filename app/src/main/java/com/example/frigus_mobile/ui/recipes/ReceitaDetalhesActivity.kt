package com.example.frigus_mobile.ui.recipes

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.ui.recipes.adapter.RecipeIngredientAdapter
import com.google.android.material.chip.ChipGroup

class ReceitaDetalhesActivity : AppCompatActivity() {

    private var recipe: RecipeResponse? = null
    private var isFavorite: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receita_detalhes)

        recipe = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_RECIPE, RecipeResponse::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_RECIPE) as? RecipeResponse
        }

        setupViews()
    }

    private fun setupViews() {
        val currentRecipe = recipe ?: return

        val imgHero = findViewById<ImageView>(R.id.imgRecipeHero)
        val btnBack = findViewById<View>(R.id.btnDetailBack)
        val btnFavorite = findViewById<View>(R.id.btnDetailFavorite)
        val imgFavorite = findViewById<ImageView>(R.id.imgFavoriteIcon)

        val txtTitle = findViewById<TextView>(R.id.txtDetailRecipeTitle)
        val txtRating = findViewById<TextView>(R.id.txtDetailRecipeRating)
        val txtTime = findViewById<TextView>(R.id.txtDetailRecipeTime)
        val txtDifficulty = findViewById<TextView>(R.id.txtDetailRecipeDifficulty)
        val txtDescription = findViewById<TextView>(R.id.txtDetailRecipeDescription)
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupIngredientsInStock)
        val txtIngredientsTitle = findViewById<TextView>(R.id.txtDetailIngredientsSectionTitle)
        val rvIngredients = findViewById<RecyclerView>(R.id.rvDetailIngredients)
        val btnVerModoPreparo = findViewById<Button>(R.id.btnVerModoPreparo)

        // Hero Image
        if (currentRecipe.imageDrawableRes != null) {
            imgHero.setImageResource(currentRecipe.imageDrawableRes!!)
        } else {
            imgHero.setImageResource(R.drawable.img_frango_molho_cremoso)
        }

        // Floating Back Button
        btnBack.setOnClickListener {
            finish()
        }

        // Floating Favorite Button
        btnFavorite.setOnClickListener {
            isFavorite = !isFavorite
            if (isFavorite) {
                imgFavorite.setImageResource(R.drawable.ic_heart_filled)
                Toast.makeText(this, "Receita salva nos favoritos!", Toast.LENGTH_SHORT).show()
            } else {
                imgFavorite.setImageResource(R.drawable.ic_heart)
                Toast.makeText(this, "Receita removida dos favoritos.", Toast.LENGTH_SHORT).show()
            }
        }

        // Texts
        txtTitle.text = currentRecipe.recipeName ?: "Receita"
        txtRating.text = currentRecipe.formattedRating
        txtTime.text = currentRecipe.formattedTime
        txtDifficulty.text = currentRecipe.difficulty
        txtDescription.text = currentRecipe.recipeDescription ?: ""

        // Chips "Usa ingredientes que você tem"
        chipGroup.removeAllViews()
        val available = currentRecipe.availableIngredients
        if (available.isNotEmpty()) {
            for (item in available) {
                val chipView = TextView(this).apply {
                    text = item.productName ?: ""
                    setBackgroundResource(R.drawable.bg_chip_ingredient)
                    setTextColor(ContextCompat.getColor(context, R.color.blue_primary))
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(30, 16, 30, 16)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 16, 12)
                    }
                    layoutParams = params
                }
                chipGroup.addView(chipView)
            }
        } else {
            // Se nenhum estiver marcado, exibe o ingrediente principal
            val fallbackChip = TextView(this).apply {
                text = currentRecipe.recipeName?.split(" ")?.firstOrNull() ?: "Ingrediente"
                setBackgroundResource(R.drawable.bg_chip_ingredient)
                setTextColor(ContextCompat.getColor(context, R.color.blue_primary))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(30, 16, 30, 16)
            }
            chipGroup.addView(fallbackChip)
        }

        // Ingredientes (N)
        val ingredientsList = currentRecipe.ingredients ?: emptyList()
        txtIngredientsTitle.text = "Ingredientes (${ingredientsList.size})"

        rvIngredients.layoutManager = LinearLayoutManager(this)
        rvIngredients.adapter = RecipeIngredientAdapter(ingredientsList)

        // Modo de preparo
        btnVerModoPreparo.setOnClickListener {
            val bottomSheet = ModoPreparoBottomSheet.newInstance(
                recipeName = currentRecipe.recipeName ?: "Receita",
                instructions = currentRecipe.recipeInstructions ?: "Siga as instruções descritas para preparar este prato."
            )
            bottomSheet.show(supportFragmentManager, "ModoPreparoBottomSheet")
        }
    }

    companion object {
        const val EXTRA_RECIPE = "extra_recipe"
    }
}
