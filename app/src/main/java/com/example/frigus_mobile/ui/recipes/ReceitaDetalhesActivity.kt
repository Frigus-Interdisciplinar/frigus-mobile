package com.example.frigus_mobile.ui.recipes

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.ui.login.LoginActivity
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
    private lateinit var session: SessionManager
    private val model by lazy { ViewModelProvider(this)[RecipeDetailViewModel::class.java] }
    private var leaving = false
    private var lastReadError: ApiError? = null
    private var lastFavoriteError: ApiError? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.isLoggedIn) { openLogin(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_receita_detalhes)
        (findViewById<TextView>(R.id.txtDetailRecipeRating).parent as View).visibility = View.GONE
        findViewById<TextView>(R.id.txtDetailRecipeDescription).text = ""
        findViewById<ChipGroup>(R.id.chipGroupIngredientsInStock).removeAllViews()
        findViewById<View>(R.id.layoutRecipeInstructions).visibility = View.GONE
        val root = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        root.fitsSystemWindows = false
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
        val legacy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_RECIPE, RecipeResponse::class.java)
        } else {
            @Suppress("DEPRECATION")
            (intent.getSerializableExtra(EXTRA_RECIPE) as? RecipeResponse)
        }
        val id = intent.getIntExtra(EXTRA_RECIPE_ID, legacy?.recipeId ?: 0)
        findViewById<View>(R.id.btnDetailBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnVerModoPreparo).isEnabled = false
        findViewById<View>(R.id.btnDetailFavorite).isEnabled = false
        findViewById<ImageView>(R.id.imgRecipeHero).apply {
            setImageResource(R.drawable.ic_restaurant)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = getString(R.string.recipe_recovery_photo_unknown)
        }
        model.state.observe(this) { state ->
            findViewById<View>(R.id.btnVerModoPreparo).isEnabled = state.recipe != null && !state.loading
            findViewById<View>(R.id.btnDetailFavorite).isEnabled = state.recipe != null && !state.favoriteBusy && !state.loading
            state.recipe?.let { recipe = it; setupViews() }
            if (state.loading && state.recipe == null) findViewById<TextView>(R.id.txtDetailRecipeTitle).setText(R.string.recipe_browser_loading)
            if (state.error != null && state.error != lastReadError) {
                lastReadError = state.error
                AlertDialog.Builder(this).setMessage(state.error.userMessage(getString(
                    if (state.error.status == 403) R.string.recipe_browser_forbidden else R.string.recipe_browser_load_error)))
                    .setPositiveButton(R.string.recipe_browser_retry) { _, _ -> model.load(id) }
                    .setNegativeButton(R.string.recipe_browser_back) { _, _ -> finish() }.show()
            }
            if (state.favoriteError != null && state.favoriteError != lastFavoriteError) {
                lastFavoriteError = state.favoriteError
                AlertDialog.Builder(this).setMessage(if (state.favoriteUncertain) getString(R.string.recipe_favorite_uncertain)
                    else state.favoriteError.userMessage(getString(R.string.recipe_favorite_error)))
                    .setPositiveButton(R.string.recipe_favorite_verify) { _, _ -> model.load(id) }
                    .setNegativeButton(R.string.recipe_browser_close, null).show()
            }
        }
        model.loadIfNeeded(id)
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

        // Recipe imagery is not supplied by the current mobile contract.
        imgHero.setImageResource(R.drawable.ic_restaurant)
        imgHero.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imgHero.contentDescription = getString(R.string.recipe_recovery_photo_unknown)

        // Floating Back Button
        btnBack.setOnClickListener {
            finish()
        }

        // Keep the original floating control connected to confirmed BFF state.
        val state = model.state.value
        imgFavorite.setImageResource(if (currentRecipe.favorite == true) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        btnFavorite.contentDescription = getString(when {
            state?.favoriteUncertain == true -> R.string.recipe_favorite_verify
            currentRecipe.favorite == true -> R.string.recipe_favorite_remove
            currentRecipe.favorite == false -> R.string.recipe_favorite_add
            else -> R.string.recipe_favorite_unknown
        })
        btnFavorite.setOnClickListener {
            if (state?.favoriteUncertain == true || currentRecipe.favorite == null) currentRecipe.recipeId?.let(model::load)
            else model.setFavorite(!currentRecipe.favorite)
        }

        // Texts
        txtTitle.text = currentRecipe.recipeName ?: getString(R.string.recipe_browser_unnamed)
        (txtRating.parent as View).visibility = View.GONE
        txtDescription.text = currentRecipe.recipeDescription ?: ""

        // Chips "Usa ingredientes que você tem"
        chipGroup.removeAllViews()
        val available = if (currentRecipe.availability != null)
            currentRecipe.availability.filter { it.available == true }.mapNotNull { it.productName?.takeIf(String::isNotBlank) }
            else currentRecipe.availableIngredients.mapNotNull { it.productName?.takeIf(String::isNotBlank) }
        val chipParent = chipGroup.parent as android.view.ViewGroup
        chipParent.getChildAt(chipParent.indexOfChild(chipGroup) - 1).visibility = if (available.isEmpty()) View.GONE else View.VISIBLE
        chipGroup.visibility = if (available.isEmpty()) View.GONE else View.VISIBLE
            for (name in available) {
                val chipView = TextView(this).apply {
                    text = name
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

        // Ingredientes (N)
        val ingredientsList = currentRecipe.ingredients ?: emptyList()
        txtIngredientsTitle.text = getString(R.string.recipe_recovery_ingredient_count, java.text.NumberFormat.getIntegerInstance().format(ingredientsList.size))

        rvIngredients.layoutManager = LinearLayoutManager(this)
        rvIngredients.adapter = RecipeIngredientAdapter(ingredientsList)

        findViewById<View>(R.id.layoutRecipeInstructions).visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtDetailRecipeInstructions).text =
            currentRecipe.recipeInstructions?.takeIf(String::isNotBlank) ?: getString(R.string.recipe_browser_no_instructions)

        // Modo de preparo
        btnVerModoPreparo.setOnClickListener {
            val bottomSheet = ModoPreparoBottomSheet.newInstance(
                recipeName = currentRecipe.recipeName ?: getString(R.string.recipe_browser_unnamed),
                instructions = currentRecipe.recipeInstructions ?: getString(R.string.recipe_browser_no_instructions)
            )
            bottomSheet.show(supportFragmentManager, "ModoPreparoBottomSheet")
        }
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !session.isLoggedIn) openLogin()
    }

    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
        const val EXTRA_RECIPE = "extra_recipe"
    }
}
