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
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.Resource
import com.example.frigus_mobile.ui.recipes.adapter.RecipePopularAdapter
import com.google.android.material.card.MaterialCardView

class ReceitasFragment : Fragment() {

    private lateinit var viewModel: RecipesViewModel
    private lateinit var popularAdapter: RecipePopularAdapter

    private var currentFeaturedRecipe: RecipeResponse? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_receitas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[RecipesViewModel::class.java]

        setupViews(view)
        setupObservers(view)
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
            Toast.makeText(requireContext(), "Nenhuma notificação nova no momento", Toast.LENGTH_SHORT).show()
        }

        // Filtro
        btnFilter.setOnClickListener {
            Toast.makeText(requireContext(), "Filtrando por ingredientes próximos do vencimento", Toast.LENGTH_SHORT).show()
        }

        // Busca em tempo real
        editSearch.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString() ?: "")
        }

        // Card "Para você"
        cardFeatured.setOnClickListener {
            currentFeaturedRecipe?.let { recipe ->
                abrirDetalhesReceita(recipe)
            }
        }

        // RecyclerView "Mais populares"
        popularAdapter = RecipePopularAdapter(emptyList()) { recipe ->
            abrirDetalhesReceita(recipe)
        }
        rvPopular.layoutManager = LinearLayoutManager(requireContext())
        rvPopular.adapter = popularAdapter
    }

    private fun setupObservers(view: View) {
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBarRecipes)
        val cardFeatured = view.findViewById<MaterialCardView>(R.id.cardFeaturedRecipe)
        val imgFeatured = view.findViewById<ImageView>(R.id.imgFeaturedRecipe)
        val txtFeaturedTitle = view.findViewById<TextView>(R.id.txtFeaturedRecipeTitle)
        val txtFeaturedSubtitle = view.findViewById<TextView>(R.id.txtFeaturedRecipeSubtitle)
        val txtFeaturedRating = view.findViewById<TextView>(R.id.txtFeaturedRecipeRating)
        val txtFeaturedTime = view.findViewById<TextView>(R.id.txtFeaturedRecipeTime)
        val txtSectionParaVoce = view.findViewById<TextView>(R.id.txtSectionParaVoce)
        val txtSectionMaisPopulares = view.findViewById<TextView>(R.id.txtSectionMaisPopulares)

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
                    Toast.makeText(requireContext(), resource.message ?: "Erro ao carregar receitas", Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.featuredRecipe.observe(viewLifecycleOwner) { recipe ->
            currentFeaturedRecipe = recipe
            if (recipe != null) {
                txtSectionParaVoce.visibility = View.VISIBLE
                cardFeatured.visibility = View.VISIBLE

                txtFeaturedTitle.text = recipe.recipeName ?: "Receita Especial"
                txtFeaturedSubtitle.text = recipe.expiringBadgeText
                txtFeaturedRating.text = recipe.formattedRating
                txtFeaturedTime.text = recipe.formattedTime

                if (recipe.imageDrawableRes != null) {
                    imgFeatured.setImageResource(recipe.imageDrawableRes!!)
                } else {
                    imgFeatured.setImageResource(R.drawable.img_frango_molho_cremoso)
                }
            } else {
                txtSectionParaVoce.visibility = View.GONE
                cardFeatured.visibility = View.GONE
            }
        }

        viewModel.popularRecipes.observe(viewLifecycleOwner) { list ->
            popularAdapter.updateList(list)
            txtSectionMaisPopulares.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun abrirDetalhesReceita(recipe: RecipeResponse) {
        val intent = Intent(requireContext(), ReceitaDetalhesActivity::class.java).apply {
            putExtra(ReceitaDetalhesActivity.EXTRA_RECIPE, recipe)
        }
        startActivity(intent)
    }
}
