package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.ui.stock.adapter.EstoqueLocalAdapter
import com.example.frigus_mobile.utils.launchSafeNetwork
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class EstoqueLocalActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORAGE_PLACE = "EXTRA_STORAGE_PLACE"
        const val EXTRA_CATEGORY_CODE = "EXTRA_CATEGORY_CODE"
    }

    private lateinit var repository: StockRepository
    private lateinit var adapter: EstoqueLocalAdapter

    private lateinit var tvTitulo: TextView
    private lateinit var btnDropdown: View
    private lateinit var etBuscar: EditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var rvProdutos: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvVazio: TextView
    private lateinit var btnFiltro: ImageView
    private var isSortByExpirationActive = false

    private var currentStoragePlace = "FRIDGE" // FRIDGE, FREEZER, PANTRY
    private var allProducts = listOf<StockProductResponse>()
    private var selectedCategoryLabel: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_estoque_local)

        repository = StockRepository(this)

        currentStoragePlace = intent.getStringExtra(EXTRA_STORAGE_PLACE) ?: "FRIDGE"
        val preSelectedCategoryCode = intent.getStringExtra(EXTRA_CATEGORY_CODE)

        tvTitulo = findViewById(R.id.tvTituloLocal)
        btnDropdown = findViewById(R.id.btnDropdownLocal)
        etBuscar = findViewById(R.id.etBuscarAlimentosLocal)
        chipGroup = findViewById(R.id.chipGroupCategorias)
        rvProdutos = findViewById(R.id.rvProdutosLocal)
        progressBar = findViewById(R.id.progressLocal)
        tvVazio = findViewById(R.id.tvLocalVazio)
        btnFiltro = findViewById(R.id.btnFiltroLocal)

        btnFiltro.setOnClickListener {
            isSortByExpirationActive = !isSortByExpirationActive
            val tintColor = if (isSortByExpirationActive) R.color.blue_primary else R.color.color_text_secondary
            btnFiltro.setColorFilter(ContextCompat.getColor(this, tintColor))
            aplicarFiltros()
        }

        findViewById<View>(R.id.btnVoltarLocal).setOnClickListener {
            finish()
        }

        atualizarTituloLocal()

        btnDropdown.setOnClickListener {
            mostrarPopupLocais()
        }

        configurarChips(mapearCodigoParaLabel(preSelectedCategoryCode))

        rvProdutos.layoutManager = LinearLayoutManager(this)
        adapter = EstoqueLocalAdapter(this, emptyList()) { item ->
            val intent = Intent(this, AlimentoDetalhesActivity::class.java).apply {
                putExtra("STOCK_PRODUCT", item)
            }
            startActivity(intent)
        }
        rvProdutos.adapter = adapter

        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltros()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        carregarProdutos()
    }

    override fun onResume() {
        super.onResume()
        carregarProdutos()
    }

    private fun atualizarTituloLocal() {
        tvTitulo.text = when (currentStoragePlace.uppercase()) {
            "FREEZER" -> "Freezer"
            "PANTRY" -> "Despensa"
            else -> "Geladeira"
        }
    }

    private fun mostrarPopupLocais() {
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.popup_locais_armazenamento, null)

        val popupWindow = PopupWindow(
            popupView,
            500,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.isOutsideTouchable = true
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.elevation = 16f

        val checkGeladeira = popupView.findViewById<ImageView>(R.id.checkGeladeira)
        val checkFreezer = popupView.findViewById<ImageView>(R.id.checkFreezer)
        val checkDespensa = popupView.findViewById<ImageView>(R.id.checkDespensa)

        checkGeladeira.visibility = if (currentStoragePlace.equals("FRIDGE", true)) VISIBLE else GONE
        checkFreezer.visibility = if (currentStoragePlace.equals("FREEZER", true)) VISIBLE else GONE
        checkDespensa.visibility = if (currentStoragePlace.equals("PANTRY", true)) VISIBLE else GONE

        popupView.findViewById<View>(R.id.opcaoGeladeira).setOnClickListener {
            popupWindow.dismiss()
            trocarLocal("FRIDGE")
        }

        popupView.findViewById<View>(R.id.opcaoFreezer).setOnClickListener {
            popupWindow.dismiss()
            trocarLocal("FREEZER")
        }

        popupView.findViewById<View>(R.id.opcaoDespensa).setOnClickListener {
            popupWindow.dismiss()
            trocarLocal("PANTRY")
        }

        popupWindow.showAsDropDown(btnDropdown, -20, 8)
    }

    private fun trocarLocal(novoLocal: String) {
        if (currentStoragePlace == novoLocal) return
        currentStoragePlace = novoLocal
        atualizarTituloLocal()
        selectedCategoryLabel = null
        configurarChips()
        aplicarFiltros()
    }

    private fun configurarChips(categoriaInicial: String? = null) {
        chipGroup.removeAllViews()

        // Categorias de acordo com a especificação do usuário:
        // Geladeira e Freezer: Carnes, Laticínios, Hortifruti, Bebidas
        // Despensa: Grãos, Hortifruti, Laticínios, Bebidas
        val categorias = when (currentStoragePlace.uppercase()) {
            "PANTRY" -> listOf("Grãos", "Hortifruti", "Laticínios", "Bebidas")
            else -> listOf("Carnes", "Laticínios", "Hortifruti", "Bebidas")
        }

        selectedCategoryLabel = if (categoriaInicial != null && categorias.contains(categoriaInicial)) {
            categoriaInicial
        } else {
            null
        }

        for (cat in categorias) {
            val chip = Chip(this).apply {
                text = cat
                isCheckable = false
                isClickable = true
                setEnsureMinTouchTargetSize(false)
                shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(32f).build()
                chipStrokeWidth = 3f

                val isSelected = cat == selectedCategoryLabel
                atualizarEstiloChip(this, isSelected)

                setOnClickListener {
                    if (selectedCategoryLabel == cat) {
                        // Clicar novamente no chip ativo desmarca e exibe todos
                        selectedCategoryLabel = null
                    } else {
                        selectedCategoryLabel = cat
                    }

                    for (i in 0 until chipGroup.childCount) {
                        val child = chipGroup.getChildAt(i) as? Chip ?: continue
                        val sel = child.text.toString() == selectedCategoryLabel
                        atualizarEstiloChip(child, sel)
                    }
                    aplicarFiltros()
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun atualizarEstiloChip(chip: Chip, isSelected: Boolean) {
        if (isSelected) {
            chip.setChipBackgroundColorResource(R.color.blue_primary)
            chip.setTextColor(ContextCompat.getColor(this, R.color.pure_white))
            chip.chipStrokeColor = ContextCompat.getColorStateList(this, R.color.blue_primary)
        } else {
            chip.setChipBackgroundColorResource(R.color.white)
            chip.setTextColor(ContextCompat.getColor(this, R.color.blue_primary))
            chip.chipStrokeColor = ContextCompat.getColorStateList(this, R.color.blue_primary)
        }
    }

    private fun carregarProdutos() {
        progressBar.visibility = View.VISIBLE
        launchSafeNetwork(::carregarProdutos) {
            try {
                allProducts = repository.getStockProducts()
                aplicarFiltros()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun aplicarFiltros() {
        val query = etBuscar.text.toString().trim().lowercase()

        val filtered = allProducts.filter { item ->
            val placeMatches = isProductInStoragePlace(item, currentStoragePlace)
            val categoryMatches = isProductInCategory(item, selectedCategoryLabel)

            val name = (item.product?.name ?: "").lowercase()
            val nameMatches = query.isEmpty() || name.contains(query)

            placeMatches && categoryMatches && nameMatches
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val finalList = if (isSortByExpirationActive) {
            filtered.sortedBy { item ->
                try {
                    item.expireDate?.let { dateFormat.parse(it)?.time } ?: Long.MAX_VALUE
                } catch (_: Exception) {
                    Long.MAX_VALUE
                }
            }
        } else {
            filtered
        }

        adapter.updateList(finalList)
        tvVazio.visibility = if (finalList.isEmpty()) View.VISIBLE else View.GONE
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

    private fun isProductInCategory(item: StockProductResponse, selectedCategory: String?): Boolean {
        if (selectedCategory.isNullOrBlank()) {
            return true
        }

        val catAlimento = (item.category ?: item.product?.category ?: "").uppercase().trim()
        val nameAlimento = (item.product?.name ?: "").lowercase().trim()

        return when (selectedCategory) {
            "Carnes" -> {
                catAlimento == "MEAT" || catAlimento.contains("CARNE") || catAlimento.contains("PEIXE") ||
                        nameAlimento.contains("carne") || nameAlimento.contains("frango") ||
                        nameAlimento.contains("linguiça") || nameAlimento.contains("filé") ||
                        nameAlimento.contains("peixe") || nameAlimento.contains("tilápia")
            }
            "Laticínios" -> {
                catAlimento == "DAIRY" || catAlimento.contains("LATICINIO") ||
                        nameAlimento.contains("leite") || nameAlimento.contains("queijo") ||
                        nameAlimento.contains("iogurte") || nameAlimento.contains("manteiga") ||
                        nameAlimento.contains("requeijão") || nameAlimento.contains("sorvete") ||
                        nameAlimento.contains("picolé")
            }
            "Hortifruti" -> {
                catAlimento == "VEGETABLE" || catAlimento == "FRUIT" || catAlimento.contains("HORTIFRUTI") ||
                        nameAlimento.contains("tomate") || nameAlimento.contains("banana") ||
                        nameAlimento.contains("maçã") || nameAlimento.contains("ervilha") ||
                        nameAlimento.contains("milho") || nameAlimento.contains("legume") ||
                        nameAlimento.contains("verdura") || nameAlimento.contains("batata") ||
                        nameAlimento.contains("polpa") || nameAlimento.contains("açaí")
            }
            "Bebidas" -> {
                catAlimento == "BEVERAGE" || catAlimento.contains("BEBIDA") ||
                        nameAlimento.contains("suco") || nameAlimento.contains("refrigerante") ||
                        nameAlimento.contains("café") || nameAlimento.contains("cha") ||
                        nameAlimento.contains("cerveja") || nameAlimento.contains("água") ||
                        nameAlimento.contains("gelo")
            }
            "Grãos" -> {
                catAlimento == "GRAIN" || catAlimento.contains("GRAO") ||
                        nameAlimento.contains("arroz") || nameAlimento.contains("feijão") ||
                        nameAlimento.contains("aveia") || nameAlimento.contains("lentilha") ||
                        nameAlimento.contains("grão") || nameAlimento.contains("farinha")
            }
            else -> true
        }
    }

    private fun mapearCodigoParaLabel(code: String?): String? {
        return when (code?.uppercase()) {
            "MEAT" -> "Carnes"
            "DAIRY" -> "Laticínios"
            "VEGETABLE", "FRUIT", "HORTIFRUTI" -> "Hortifruti"
            "BEVERAGE" -> "Bebidas"
            "GRAIN" -> "Grãos"
            else -> null
        }
    }
}
