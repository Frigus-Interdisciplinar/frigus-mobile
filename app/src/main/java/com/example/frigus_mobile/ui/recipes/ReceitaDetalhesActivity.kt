package com.example.frigus_mobile.ui.recipes

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.ui.recipes.adapter.RecipeIngredientAdapter

class ReceitaDetalhesActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.isLoggedIn) { openLogin(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_receita_detalhes)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recipeDetailRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(findViewById(R.id.recipeDetailRoot))
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
        val id = intent.getIntExtra(EXTRA_RECIPE_ID, 0)
        val model = ViewModelProvider(this)[RecipeDetailViewModel::class.java]
        findViewById<View>(R.id.btnDetailBack).setOnClickListener { finish() }
        findViewById<View>(R.id.recipeDetailRetry).setOnClickListener { model.load(id) }
        val ingredients = RecipeIngredientAdapter(emptyList())
        findViewById<RecyclerView>(R.id.rvDetailIngredients).apply {
            layoutManager = LinearLayoutManager(this@ReceitaDetalhesActivity)
            adapter = ingredients
        }
        model.state.observe(this) { state ->
            findViewById<View>(R.id.recipeDetailProgress).visibility = if (state.loading) View.VISIBLE else View.GONE
            findViewById<View>(R.id.recipeDetailRetry).visibility = if (state.error != null) View.VISIBLE else View.GONE
            findViewById<View>(R.id.recipeDetailContent).visibility = if (state.recipe != null) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.recipeDetailStatus).apply {
                visibility = if (state.loading || state.error != null) View.VISIBLE else View.GONE
                val error = state.error
                text = when {
                    state.loading -> getString(R.string.recipe_browser_loading)
                    error != null -> error.userMessage(getString(when {
                        error.status == 404 -> R.string.recipe_browser_not_found
                        error.status == 403 -> R.string.recipe_browser_forbidden
                        error.code == "INVALID_RESPONSE" -> R.string.recipe_browser_invalid_response
                        error.code == "NETWORK_ERROR" -> R.string.recipe_browser_network_error
                        else -> R.string.recipe_browser_load_error
                    }))
                    else -> ""
                }
            }
            state.recipe?.let { recipe ->
                val name = recipe.recipeName?.takeIf(String::isNotBlank) ?: getString(R.string.recipe_browser_unnamed)
                findViewById<TextView>(R.id.txtDetailRecipeTitle).text = name
                findViewById<TextView>(R.id.txtDetailRecipeDescription).text =
                    recipe.recipeDescription?.takeIf(String::isNotBlank) ?: getString(R.string.recipe_browser_no_description)
                val items = recipe.ingredients.orEmpty().filterNotNull()
                ingredients.updateList(items)
                findViewById<TextView>(R.id.recipeIngredientsState).apply {
                    visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }
                findViewById<View>(R.id.btnVerModoPreparo).setOnClickListener {
                    if (supportFragmentManager.findFragmentByTag(PREPARATION_TAG) == null) {
                        ModoPreparoBottomSheet.newInstance(name, recipe.recipeInstructions.orEmpty())
                            .show(supportFragmentManager, PREPARATION_TAG)
                    }
                }
            }
        }
        model.loadIfNeeded(id)
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !session.isLoggedIn) openLogin()
    }

    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        ))
        finish()
    }

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
        private const val PREPARATION_TAG = "recipe_preparation"
    }
}
