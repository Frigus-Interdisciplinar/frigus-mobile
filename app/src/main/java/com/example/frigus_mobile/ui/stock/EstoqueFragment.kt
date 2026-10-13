package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.ui.qrcode.QrCodeScannerActivity
import com.example.frigus_mobile.ui.stock.adapter.EstoqueProdutoAdapter
import com.example.frigus_mobile.utils.NetworkGuard
import kotlinx.coroutines.launch

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
        view.findViewById<View>(R.id.btnEstoqueQrScanner).setOnClickListener {
            startActivity(Intent(requireContext(), QrCodeScannerActivity::class.java))
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
            abrirCategoria("MEAT", "Carnes")
        }
        view.findViewById<View>(R.id.catLaticinios).setOnClickListener {
            abrirCategoria("DAIRY", "Laticínios")
        }
        view.findViewById<View>(R.id.catHortifruti).setOnClickListener {
            abrirCategoria("VEGETABLE", "Hortifruti")
        }
        view.findViewById<View>(R.id.catBebidas).setOnClickListener {
            abrirCategoria("BEVERAGE", "Bebidas")
        }

        view.findViewById<View>(R.id.btnVerTodasCategorias).setOnClickListener {
            startActivity(Intent(requireContext(), TodasCategoriasActivity::class.java))
        }

        // RecyclerView
        val rv = view.findViewById<RecyclerView>(R.id.rvProximosVencimento)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = EstoqueProdutoAdapter(requireContext(), emptyList())
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

        carregarDados()
    }

    override fun onResume() {
        super.onResume()
        carregarDados()
    }

    private fun carregarDados() {
        if (!::repository.isInitialized) return
        if (!NetworkGuard.ensureConnected(activity) { carregarDados() }) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                allProducts = repository.getStockProducts()
                atualizarResumo()
                aplicarFiltroAtual()
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(activity, e.message) {
                    carregarDados()
                }
            }
        }
    }

    private fun formatarQtdItens(qtd: Int): String {
        return if (qtd == 1) "1 item" else "$qtd itens"
    }

    private fun atualizarResumo() {
        val emDia = allProducts.count { it.productStatus == "FRESH" }
        val atencao = allProducts.count { it.productStatus == "NEAR_EXPIRATION" }
        val vencendo = allProducts.count { it.productStatus == "EXPIRED" }

        view?.findViewById<TextView>(R.id.tvCountEmDia)?.text = emDia.toString()
        view?.findViewById<TextView>(R.id.tvCountAtencao)?.text = atencao.toString()
        view?.findViewById<TextView>(R.id.tvCountVencendo)?.text = vencendo.toString()

        val qtdCarnes = allProducts.count {
            (it.category ?: it.product?.category ?: "").equals("MEAT", ignoreCase = true)
        }
        val qtdLaticinios = allProducts.count {
            (it.category ?: it.product?.category ?: "").equals("DAIRY", ignoreCase = true)
        }
        val qtdHortifruti = allProducts.count {
            val cat = (it.category ?: it.product?.category ?: "")
            cat.equals("VEGETABLE", ignoreCase = true) || cat.equals("FRUIT", ignoreCase = true)
        }
        val qtdBebidas = allProducts.count {
            (it.category ?: it.product?.category ?: "").equals("BEVERAGE", ignoreCase = true)
        }

        view?.findViewById<TextView>(R.id.tvQtdCatCarnes)?.text = formatarQtdItens(qtdCarnes)
        view?.findViewById<TextView>(R.id.tvQtdCatLaticinios)?.text = formatarQtdItens(qtdLaticinios)
        view?.findViewById<TextView>(R.id.tvQtdCatHortifruti)?.text = formatarQtdItens(qtdHortifruti)
        view?.findViewById<TextView>(R.id.tvQtdCatBebidas)?.text = formatarQtdItens(qtdBebidas)
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

        aplicarFiltroAtual()
    }

    private fun aplicarFiltroAtual() {
        val query = etBuscar.text.toString().trim().lowercase()
        val filtered = allProducts.filter { item ->
            val placeMatches = (item.product?.storagePlace ?: "FRIDGE").equals(currentStoragePlace, ignoreCase = true)
            val nameMatches = query.isEmpty() || (item.product?.name ?: "").lowercase().contains(query)
            placeMatches && nameMatches
        }
        adapter.updateList(filtered)
    }

    private fun filtrarProdutos(query: String) {
        val q = query.trim().lowercase()
        val filtered = allProducts.filter { item ->
            q.isEmpty() || (item.product?.name ?: "").lowercase().contains(q)
        }
        adapter.updateList(filtered)
    }

    private fun abrirCategoria(categoryCode: String, categoryName: String) {
        val intent = Intent(requireContext(), CategoriaItensActivity::class.java).apply {
            putExtra("CATEGORY_CODE", categoryCode)
            putExtra("CATEGORY_NAME", categoryName)
        }
        startActivity(intent)
    }
}
