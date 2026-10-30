package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.ui.qrcode.QrCodeScannerActivity
import com.example.frigus_mobile.ui.shopping.AddShoppingListItemFragment
import com.example.frigus_mobile.ui.stock.adapter.EstoqueProdutoAdapter
import com.example.frigus_mobile.utils.ProductCategoryHelper
import com.example.frigus_mobile.utils.launchSafeNetwork

class EstoqueFragment : Fragment() {

    private lateinit var repository: StockRepository
    private lateinit var adapter: EstoqueProdutoAdapter
    private var allProducts = listOf<StockProductResponse>()
    private var currentStoragePlace = "FRIDGE" // FRIDGE, FREEZER, PANTRY

    private lateinit var tabGeladeira: TextView
    private lateinit var tabFreezer: TextView
    private lateinit var tabDespensa: TextView
    private lateinit var etBuscar: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_estoque, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = StockRepository(requireContext())

        // Botão Ler QR Code
        view.findViewById<View>(R.id.btnEstoqueQrScanner)?.setOnClickListener {
            startActivity(Intent(requireContext(), QrCodeScannerActivity::class.java))
        }

        // Botão Adicionar Alimento Manualmente no Estoque
        view.findViewById<View>(R.id.btnAdicionarAlimentoEstoque)?.setOnClickListener {
            val fragment = AddShoppingListItemFragment.newInstance(
                initialDestination = AddShoppingListItemFragment.DESTINATION_STOCK
            )
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }

        // Tabs
        tabGeladeira = view.findViewById(R.id.tabGeladeira)
        tabFreezer = view.findViewById(R.id.tabFreezer)
        tabDespensa = view.findViewById(R.id.tabDespensa)

        tabGeladeira.setOnClickListener { selectTab("FRIDGE") }
        tabFreezer.setOnClickListener { selectTab("FREEZER") }
        tabDespensa.setOnClickListener { selectTab("PANTRY") }

        // Categorias Clicks
        view.findViewById<View>(R.id.catCarnes).setOnClickListener {
            val code = if (currentStoragePlace == "PANTRY") "GRAIN" else "MEAT"
            abrirLocalArmazenamento(code)
        }
        view.findViewById<View>(R.id.catLaticinios).setOnClickListener {
            abrirLocalArmazenamento("DAIRY")
        }
        view.findViewById<View>(R.id.catHortifruti).setOnClickListener {
            abrirLocalArmazenamento("VEGETABLE")
        }
        view.findViewById<View>(R.id.catBebidas).setOnClickListener {
            abrirLocalArmazenamento("BEVERAGE")
        }

        // Botão "Ver mais >"
        view.findViewById<View>(R.id.btnVerTodasCategorias).setOnClickListener {
            abrirLocalArmazenamento()
        }

        // RecyclerView
        val rv = view.findViewById<RecyclerView>(R.id.rvProximosVencimento)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = EstoqueProdutoAdapter(requireContext(), emptyList()) { item ->
            val intent = Intent(requireContext(), AlimentoDetalhesActivity::class.java).apply {
                putExtra("STOCK_PRODUCT", item)
                putExtra(AlimentoDetalhesActivity.EXTRA_LOT_ID, item.id ?: 0)
            }
            startActivity(intent)
        }
        rv.adapter = adapter

        // Busca
        etBuscar = view.findViewById(R.id.etBuscarAlimento)
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarProdutos(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val inventoryViewModel = ViewModelProvider(requireActivity())[InventoryViewModel::class.java]
        inventoryViewModel.state.observe(viewLifecycleOwner) { state ->
            val place = state.filters.storagePlace
            if (place != null && place != currentStoragePlace) {
                selectTab(place)
            }
        }

        carregarDados()
    }

    override fun onResume() {
        super.onResume()
        carregarDados()
    }

    private fun carregarDados() {
        if (!::repository.isInitialized) return
        launchSafeNetwork(::carregarDados) {
            allProducts = repository.getStockProducts()
            atualizarResumo()
            aplicarFiltroAtual()
        }
    }

    private fun isProductInStoragePlace(item: StockProductResponse, targetPlace: String): Boolean {
        val rawPlace = (item.storagePlace ?: item.product?.storagePlace ?: "").uppercase().trim()
        val target = targetPlace.uppercase().trim()

        return when (target) {
            "FREEZER" -> rawPlace == "FREEZER"
            "PANTRY" -> rawPlace == "PANTRY" || rawPlace == "DESPENSA"
            else -> rawPlace == "FRIDGE" || rawPlace == "GELADEIRA" || rawPlace.isEmpty()
        }
    }

    private fun atualizarResumo() {
        val produtosDoLocal = allProducts.filter { isProductInStoragePlace(it, currentStoragePlace) }

        val emDia = produtosDoLocal.count { it.productStatus == "FRESH" }
        val atencao = produtosDoLocal.count { it.productStatus == "NEAR_EXPIRATION" }
        val vencendo = produtosDoLocal.count { it.productStatus == "EXPIRED" }

        view?.findViewById<TextView>(R.id.tvCountEmDia)?.text = emDia.toString()
        view?.findViewById<TextView>(R.id.tvCountAtencao)?.text = atencao.toString()
        view?.findViewById<TextView>(R.id.tvCountVencendo)?.text = vencendo.toString()

        val isDespensa = currentStoragePlace == "PANTRY"
        val tvNomePrimeiraCat = view?.findViewById<TextView>(R.id.tvNomeCatCarnes)
        val imgPrimeiraCat = view?.findViewById<ImageView>(R.id.imgCatCarnes)

        if (isDespensa) {
            tvNomePrimeiraCat?.text = "Grãos"
            imgPrimeiraCat?.clearColorFilter()
            imgPrimeiraCat?.setImageResource(R.drawable.ic_cat_grains)

            val qtdGraos = ProductCategoryHelper.contarPorCategoria(produtosDoLocal, "GRAIN")
            val qtdLaticinios = ProductCategoryHelper.contarPorCategoria(produtosDoLocal, "DAIRY")
            val qtdHortifruti = ProductCategoryHelper.contarPorCategoria(produtosDoLocal, "HORTIFRUTI")
            val qtdBebidas = ProductCategoryHelper.contarPorCategoria(produtosDoLocal, "BEVERAGE")

            view?.findViewById<TextView>(R.id.tvQtdCatCarnes)?.text = ProductCategoryHelper.formatarQtdItens(qtdGraos)
            view?.findViewById<TextView>(R.id.tvQtdCatLaticinios)?.text = ProductCategoryHelper.formatarQtdItens(qtdLaticinios)
            view?.findViewById<TextView>(R.id.tvQtdCatHortifruti)?.text = ProductCategoryHelper.formatarQtdItens(qtdHortifruti)
            view?.findViewById<TextView>(R.id.tvQtdCatBebidas)?.text = ProductCategoryHelper.formatarQtdItens(qtdBebidas)
        } else {
            tvNomePrimeiraCat?.text = "Carnes"
            imgPrimeiraCat?.clearColorFilter()
            imgPrimeiraCat?.setImageResource(R.drawable.ic_cat_meat)

            val qtdCarnes = ProductCategoryHelper.contarPorCategoria(produtosDoLocal, "MEAT")
            val qtdLaticinios = ProductCategoryHelper.contarPorCategoria(produtosDoLocal, "DAIRY")
            val qtdHortifruti = ProductCategoryHelper.contarPorCategoria(produtosDoLocal, "HORTIFRUTI")
            val qtdBebidas = ProductCategoryHelper.contarPorCategoria(produtosDoLocal, "BEVERAGE")

            view?.findViewById<TextView>(R.id.tvQtdCatCarnes)?.text = ProductCategoryHelper.formatarQtdItens(qtdCarnes)
            view?.findViewById<TextView>(R.id.tvQtdCatLaticinios)?.text = ProductCategoryHelper.formatarQtdItens(qtdLaticinios)
            view?.findViewById<TextView>(R.id.tvQtdCatHortifruti)?.text = ProductCategoryHelper.formatarQtdItens(qtdHortifruti)
            view?.findViewById<TextView>(R.id.tvQtdCatBebidas)?.text = ProductCategoryHelper.formatarQtdItens(qtdBebidas)
        }
    }

    private fun selectTab(place: String) {
        currentStoragePlace = place
        val white = ContextCompat.getColor(requireContext(), R.color.pure_white)
        val gray = ContextCompat.getColor(requireContext(), R.color.color_text_secondary)

        tabGeladeira.setBackgroundResource(if (place == "FRIDGE") R.drawable.bg_tab_selected else 0)
        tabGeladeira.setTextColor(if (place == "FRIDGE") white else gray)

        tabFreezer.setBackgroundResource(if (place == "FREEZER") R.drawable.bg_tab_selected else 0)
        tabFreezer.setTextColor(if (place == "FREEZER") white else gray)

        tabDespensa.setBackgroundResource(if (place == "PANTRY") R.drawable.bg_tab_selected else 0)
        tabDespensa.setTextColor(if (place == "PANTRY") white else gray)

        atualizarResumo()
        aplicarFiltroAtual()
    }

    private fun aplicarFiltroAtual() {
        val query = etBuscar.text.toString().trim().lowercase()
        val filtered = allProducts.filter { item ->
            val placeMatches = isProductInStoragePlace(item, currentStoragePlace)
            val nameMatches = query.isEmpty() || (item.product?.name ?: "").lowercase().contains(query)
            placeMatches && nameMatches
        }
        adapter.updateList(filtered)
    }

    private fun filtrarProdutos(query: String) {
        val q = query.trim().lowercase()
        val filtered = allProducts.filter { item ->
            val placeMatches = isProductInStoragePlace(item, currentStoragePlace)
            val nameMatches = q.isEmpty() || (item.product?.name ?: "").lowercase().contains(q)
            placeMatches && nameMatches
        }
        adapter.updateList(filtered)
    }

    private fun abrirLocalArmazenamento(categoryCode: String? = null) {
        val intent = Intent(requireContext(), EstoqueLocalActivity::class.java).apply {
            putExtra(EstoqueLocalActivity.EXTRA_STORAGE_PLACE, currentStoragePlace)
            if (categoryCode != null) {
                putExtra(EstoqueLocalActivity.EXTRA_CATEGORY_CODE, categoryCode)
            }
        }
        startActivity(intent)
    }
}
