package com.example.frigus_mobile.ui.recipes

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.Resource
import com.example.frigus_mobile.ui.notifications.NotificacoesActivity
import com.example.frigus_mobile.ui.recipes.adapter.RecipePopularAdapter
import com.example.frigus_mobile.utils.NetworkGuard
import com.google.android.material.card.MaterialCardView

class ReceitasFragment : Fragment() {

    private lateinit var viewModel: RecipesViewModel
    private lateinit var popularAdapter: RecipePopularAdapter

    private var currentFeaturedRecipe: RecipeResponse? = null
    private var hasPopularRecipes = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_receitas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[RecipesViewModel::class.java]

        setupViews(view)
        setupObservers(view)
        viewModel.loadIfNeeded()
    }

    private fun setupViews(view: View) {
        val btnBack = view.findViewById<View>(R.id.btnBack)
        val btnNotification = view.findViewById<View>(R.id.btnNotification)
        val editSearch = view.findViewById<EditText>(R.id.editSearchRecipe)
        val btnFilter = view.findViewById<View>(R.id.btnFilter)
        val cardFeatured = view.findViewById<MaterialCardView>(R.id.cardFeaturedRecipe)
        val rvPopular = view.findViewById<RecyclerView>(R.id.rvPopularRecipes)

        // Botão Voltar
        btnBack.setOnClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        }

        // Notificações
        btnNotification.setOnClickListener {
            startActivity(Intent(requireContext(), NotificacoesActivity::class.java))
        }

        // Filtro
        btnFilter.setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle(R.string.recipe_recovery_filter)
                .setItems(R.array.recipe_recovery_actions) { _, action ->
                    when (action) {
                        0, 1 -> {
                            viewModel.selectFavorites(action == 1)
                            view.findViewById<TextView>(R.id.txtSectionMaisPopulares).setText(
                                if (action == 1) R.string.recipe_favorites_title else R.string.recipe_recovery_popular)
                            renderSections(view)
                        }
                        2 -> if (viewModel.showingFavorites) viewModel.loadMoreFavorites() else viewModel.loadMore()
                        3 -> viewModel.loadRecipes()
                    }
                }.setNegativeButton(R.string.recipe_browser_close, null).show()
        }

        // Busca em tempo real
        editSearch.setText(viewModel.query)
        editSearch.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString() ?: "")
        }

        // Card "Para você"
        cardFeatured.setOnClickListener {
            currentFeaturedRecipe?.let { recipe ->
                abrirDetalhesReceita(recipe)
            }
        }
        view.findViewById<View>(R.id.headerSuggestions).setOnClickListener { viewModel.toggleSuggestions() }
        view.findViewById<View>(R.id.headerCatalog).setOnClickListener { viewModel.toggleCatalog() }

        // RecyclerView "Mais populares"
        popularAdapter = RecipePopularAdapter(emptyList()) { recipe ->
            abrirDetalhesReceita(recipe)
        }
        rvPopular.layoutManager = LinearLayoutManager(requireContext())
        rvPopular.adapter = popularAdapter
        view.findViewById<TextView>(R.id.txtSectionMaisPopulares).setText(
            if (viewModel.showingFavorites) R.string.recipe_favorites_title else R.string.recipe_recovery_popular)
    }

    private fun setupObservers(view: View) {
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBarRecipes)
        val imgFeatured = view.findViewById<ImageView>(R.id.imgFeaturedRecipe)
        val txtFeaturedTitle = view.findViewById<TextView>(R.id.txtFeaturedRecipeTitle)
        val txtFeaturedSubtitle = view.findViewById<TextView>(R.id.txtFeaturedRecipeSubtitle)
        val txtFeaturedRating = view.findViewById<TextView>(R.id.txtFeaturedRecipeRating)
        (txtFeaturedRating.parent as View).visibility = View.GONE

        viewModel.recipesResource.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Resource.Status.LOADING -> {
                    progressBar.visibility = View.VISIBLE
                }
                Resource.Status.SUCCESS -> {
                    progressBar.visibility = View.GONE
                }
                Resource.Status.ERROR -> {
                    progressBar.visibility = View.GONE
                    NetworkGuard.showBlockingError(activity, resource.message) {
                        viewModel.loadRecipes()
                    }
                }
            }
        }

        viewModel.featuredRecipe.observe(viewLifecycleOwner) { recipe ->
            currentFeaturedRecipe = recipe
            if (recipe != null) {
                txtFeaturedTitle.text = recipe.recipeName ?: getString(R.string.recipe_browser_unnamed)
                val matched = recipe.matchedIngredients
                val missing = recipe.missingIngredients
                txtFeaturedSubtitle.text = if (matched != null && matched >= 0 && missing != null && missing >= 0)
                    getString(R.string.recipe_browser_match_counts, matched.toString(), missing.toString())
                    else recipe.recipeDescription ?: getString(R.string.recipe_browser_no_description)
                imgFeatured.setImageResource(R.drawable.ic_restaurant)
                imgFeatured.scaleType = ImageView.ScaleType.CENTER_INSIDE
                imgFeatured.contentDescription = getString(R.string.recipe_recovery_photo_unknown)
            }
            renderSections(view)
        }

        viewModel.popularRecipes.observe(viewLifecycleOwner) { list ->
            popularAdapter.updateList(list)
            hasPopularRecipes = list.isNotEmpty()
            renderSections(view)
        }
        viewModel.sections.observe(viewLifecycleOwner) { renderSections(view) }
    }

    /** 60b9196 toggles adapted to the recovered card/list, with Activity-owned state. */
    private fun renderSections(view: View) {
        val sections = viewModel.sections.value ?: RecipeSectionsState()
        fun header(id: Int, titleId: Int, arrowId: Int, expanded: Boolean, available: Boolean) {
            view.findViewById<View>(id).apply {
                visibility = if (available) View.VISIBLE else View.GONE
                contentDescription = getString(if (expanded) R.string.recipe_accordion_collapse else R.string.recipe_accordion_expand,
                    view.findViewById<TextView>(titleId).text)
                ViewCompat.setStateDescription(this, getString(if (expanded)
                    R.string.recipe_accordion_expanded else R.string.recipe_accordion_collapsed))
            }
            view.findViewById<View>(arrowId).animate().rotation(if (expanded) 0f else 180f).setDuration(200).start()
        }
        header(R.id.headerSuggestions, R.id.txtSectionParaVoce, R.id.ivSuggestionsChevron,
            sections.suggestionsExpanded, currentFeaturedRecipe != null)
        header(R.id.headerCatalog, R.id.txtSectionMaisPopulares, R.id.ivCatalogChevron,
            sections.catalogExpanded, hasPopularRecipes)
        view.findViewById<View>(R.id.cardFeaturedRecipe).visibility =
            if (currentFeaturedRecipe != null && sections.suggestionsExpanded) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.rvPopularRecipes).visibility =
            if (hasPopularRecipes && sections.catalogExpanded) View.VISIBLE else View.GONE
    }

    private fun abrirDetalhesReceita(recipe: RecipeResponse) {
        val id = recipe.recipeId?.takeIf { it > 0 } ?: return
        if (RecipeFavoriteMutationGate.isBusy(id)) return
        viewModel.markDetailOpened(id)
        val intent = Intent(requireContext(), ReceitaDetalhesActivity::class.java).apply {
            putExtra(ReceitaDetalhesActivity.EXTRA_RECIPE_ID, id)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.loadIfNeeded()
            viewModel.consumeDetailReturn()?.let(viewModel::refreshAfterDetail)
        }
    }
}
