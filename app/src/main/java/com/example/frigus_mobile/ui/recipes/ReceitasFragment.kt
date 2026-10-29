package com.example.frigus_mobile.ui.recipes

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.ui.recipes.adapter.RecipePopularAdapter
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat

class ReceitasFragment : Fragment(R.layout.fragment_receitas) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val model = ViewModelProvider(requireActivity())[RecipesViewModel::class.java]
        val catalogAdapter = RecipePopularAdapter(emptyList(), ::openDetail)
        val suggestionAdapter = RecipePopularAdapter(emptyList(), ::openDetail, showAvailability = true)
        fun bindList(id: Int, adapter: RecipePopularAdapter) {
            view.findViewById<RecyclerView>(id).apply {
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = adapter
            }
        }
        bindList(R.id.recipeCatalogList, catalogAdapter)
        bindList(R.id.recipeSuggestionsList, suggestionAdapter)
        view.findViewById<EditText>(R.id.recipeSearch).apply {
            setText(model.query)
            doAfterTextChanged { model.setSearchQuery(it?.toString().orEmpty()) }
        }
        view.findViewById<View>(R.id.recipeCatalogRefresh).setOnClickListener { model.refreshCatalog() }
        view.findViewById<View>(R.id.recipeCatalogRetry).setOnClickListener { model.retryCatalog() }
        view.findViewById<View>(R.id.recipeCatalogMore).setOnClickListener { model.loadMore() }
        view.findViewById<View>(R.id.recipeSuggestionsRefresh).setOnClickListener { model.refreshSuggestions() }
        model.catalog.observe(viewLifecycleOwner) { state ->
            catalogAdapter.updateList(state.items)
            view.findViewById<View>(R.id.recipeCatalogProgress).visibility = if (state.loading) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.recipeCatalogRefresh).isEnabled = !state.loading
            view.findViewById<View>(R.id.recipeCatalogRetry).visibility = if (state.error != null) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.recipeCatalogMore).apply {
                visibility = if (state.nextPage < state.totalPages && state.error == null) View.VISIBLE else View.GONE
                isEnabled = !state.loading
            }
            view.findViewById<TextView>(R.id.recipeCatalogStatus).text = when {
                state.loading -> getString(R.string.recipe_browser_loading)
                state.error != null -> errorText(state.error)
                state.items.isEmpty() -> getString(R.string.recipe_browser_empty_catalog)
                else -> getString(R.string.recipe_browser_loaded,
                    NumberFormat.getIntegerInstance().format(state.items.size),
                    NumberFormat.getIntegerInstance().format(state.totalElements))
            }
        }
        model.suggestions.observe(viewLifecycleOwner) { state ->
            suggestionAdapter.updateList(state.items)
            view.findViewById<View>(R.id.recipeSuggestionsProgress).visibility = if (state.loading) View.VISIBLE else View.GONE
            view.findViewById<MaterialButton>(R.id.recipeSuggestionsRefresh).apply {
                isEnabled = !state.loading
                setText(if (state.error == null) R.string.recipe_browser_refresh else R.string.recipe_browser_retry)
            }
            view.findViewById<TextView>(R.id.recipeSuggestionsStatus).text = when {
                state.loading -> getString(R.string.recipe_browser_loading)
                state.error != null -> errorText(state.error)
                state.items.isEmpty() -> getString(R.string.recipe_browser_empty_suggestions)
                else -> getString(R.string.recipe_browser_suggestions_note)
            }
        }
        model.loadIfNeeded()
    }

    private fun openDetail(recipe: RecipeResponse) {
        val id = recipe.recipeId?.takeIf { it > 0 } ?: return
        startActivity(Intent(requireContext(), ReceitaDetalhesActivity::class.java)
            .putExtra(ReceitaDetalhesActivity.EXTRA_RECIPE_ID, id))
    }

    private fun errorText(error: ApiError): String = error.userMessage(getString(when {
        error.code == "INVALID_RESPONSE" -> R.string.recipe_browser_invalid_response
        error.code == "NETWORK_ERROR" -> R.string.recipe_browser_network_error
        error.status == 403 -> R.string.recipe_browser_forbidden
        else -> R.string.recipe_browser_load_error
    }))
}
