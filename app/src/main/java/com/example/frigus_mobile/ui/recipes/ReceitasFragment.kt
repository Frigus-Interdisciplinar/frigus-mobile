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
    private lateinit var model: RecipesViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model = ViewModelProvider(requireActivity())[RecipesViewModel::class.java]
        val catalogAdapter = RecipePopularAdapter(emptyList(), ::openDetail,
            onFavoriteClick = ::onFavoriteClick)
        val suggestionAdapter = RecipePopularAdapter(emptyList(), ::openDetail,
            showAvailability = true, onFavoriteClick = ::onFavoriteClick)
        val favoriteAdapter = RecipePopularAdapter(emptyList(), ::openDetail,
            onFavoriteClick = ::onFavoriteClick)
        fun bindList(id: Int, adapter: RecipePopularAdapter) {
            view.findViewById<RecyclerView>(id).apply {
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = adapter
            }
        }
        bindList(R.id.recipeCatalogList, catalogAdapter)
        bindList(R.id.recipeSuggestionsList, suggestionAdapter)
        bindList(R.id.recipeFavoritesList, favoriteAdapter)
        fun showSection() {
            view.findViewById<View>(R.id.recipeCatalogSection).visibility =
                if (model.showingFavorites) View.GONE else View.VISIBLE
            view.findViewById<View>(R.id.recipeFavoritesSection).visibility =
                if (model.showingFavorites) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.recipeCatalogTab).isSelected = !model.showingFavorites
            view.findViewById<View>(R.id.recipeFavoritesTab).isSelected = model.showingFavorites
        }
        view.findViewById<View>(R.id.recipeCatalogTab).setOnClickListener {
            model.selectFavorites(false); showSection()
        }
        view.findViewById<View>(R.id.recipeFavoritesTab).setOnClickListener {
            model.selectFavorites(true); showSection()
        }
        showSection()
        view.findViewById<EditText>(R.id.recipeSearch).apply {
            setText(model.query)
            doAfterTextChanged { model.setSearchQuery(it?.toString().orEmpty()) }
        }
        view.findViewById<View>(R.id.recipeCatalogRefresh).setOnClickListener { model.refreshCatalog() }
        view.findViewById<View>(R.id.recipeCatalogRetry).setOnClickListener { model.retryCatalog() }
        view.findViewById<View>(R.id.recipeCatalogMore).setOnClickListener { model.loadMore() }
        view.findViewById<View>(R.id.recipeSuggestionsRefresh).setOnClickListener { model.refreshSuggestions() }
        view.findViewById<View>(R.id.recipeFavoritesRefresh).setOnClickListener { model.refreshFavorites() }
        view.findViewById<View>(R.id.recipeFavoritesRetry).setOnClickListener { model.retryFavorites() }
        view.findViewById<View>(R.id.recipeFavoritesMore).setOnClickListener { model.loadMoreFavorites() }
        model.favoriteActions.observe(viewLifecycleOwner) { actions ->
            catalogAdapter.updateActions(actions)
            suggestionAdapter.updateActions(actions)
            favoriteAdapter.updateActions(actions)
        }
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
        model.favorites.observe(viewLifecycleOwner) { state ->
            favoriteAdapter.updateList(state.items)
            view.findViewById<View>(R.id.recipeFavoritesProgress).visibility = if (state.loading) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.recipeFavoritesRefresh).isEnabled = !state.loading
            view.findViewById<View>(R.id.recipeFavoritesRetry).visibility = if (state.error != null) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.recipeFavoritesMore).apply {
                visibility = if (state.nextPage < state.totalPages && state.error == null) View.VISIBLE else View.GONE
                isEnabled = !state.loading
            }
            view.findViewById<TextView>(R.id.recipeFavoritesStatus).text = when {
                state.loading -> getString(R.string.recipe_browser_loading)
                state.error != null -> errorText(state.error)
                state.items.isEmpty() -> getString(R.string.recipe_favorites_empty)
                else -> getString(R.string.recipe_favorites_loaded,
                    NumberFormat.getIntegerInstance().format(state.items.size),
                    NumberFormat.getIntegerInstance().format(state.totalElements))
            }
        }
        model.loadIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) model.consumeDetailReturn()?.let(model::refreshAfterDetail)
    }

    private fun onFavoriteClick(recipe: RecipeResponse) {
        val id = recipe.recipeId?.takeIf { it > 0 } ?: return
        if (model.favoriteActions.value.orEmpty()[id]?.uncertain == true) model.reconcileFavorite(id)
        else recipe.favorite?.let { model.setFavorite(recipe, !it) }
    }

    private fun openDetail(recipe: RecipeResponse) {
        val id = recipe.recipeId?.takeIf { it > 0 } ?: return
        if (RecipeFavoriteMutationGate.isBusy(id)) return
        model.markDetailOpened(id)
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
