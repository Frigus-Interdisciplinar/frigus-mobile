package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.ui.stock.adapter.EstoqueProdutoAdapter
import java.util.Locale

/** Reuses the original category layouts; network and filtering state belong to the ViewModel. */
abstract class CategoryBrowserActivity : AppCompatActivity() {
    private val model by lazy { ViewModelProvider(this)[CategoryBrowserViewModel::class.java] }
    private lateinit var session: SessionManager
    private var category: String? = null
    private var storagePlace: String? = null
    private var adapter: EstoqueProdutoAdapter? = null
    private var leaving = false
    private val categories get() = listOf(
        Triple(if (storagePlace == "PANTRY") "GRAIN" else "MEAT", R.id.itemCarnes, R.id.tvQtdTodasCarnes),
        Triple("DAIRY", R.id.itemLaticinios, R.id.tvQtdTodasLaticinios),
        Triple("VEGETABLE", R.id.itemHortifruti, R.id.tvQtdTodasHortifruti),
        Triple("BEVERAGE", R.id.itemBebidas, R.id.tvQtdTodasBebidas)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.isLoggedIn) { openLogin(); return }
        val detail = this is CategoriaItensActivity
        storagePlace = intent.getStringExtra(EXTRA_STORAGE_PLACE)?.takeIf { it in setOf("FRIDGE", "FREEZER", "PANTRY") }
        category = intent.getStringExtra(EXTRA_CATEGORY)?.takeIf { code -> categories.any { it.first == code } }
        if (detail && category == null) { finish(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(if (detail) R.layout.activity_categoria_itens else R.layout.activity_todas_categorias)
        val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val padding = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(padding[0] + bars.left, padding[1] + bars.top, padding[2] + bars.right, padding[3] + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
        model.configure(category, storagePlace)
        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<View>(R.id.categoryBrowserStatus).setOnClickListener { model.refresh() }
        val input = findViewById<EditText>(if (detail) R.id.etBuscarItensCategoria else R.id.etBuscarCategorias)
        input.setText(model.state.value?.query.orEmpty())
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { model.search(s.toString()) }
        })
        if (detail) {
            findViewById<TextView>(R.id.tvCategoriaTitulo).setText(categoryName(category!!))
            adapter = EstoqueProdutoAdapter(this, emptyList())
            findViewById<RecyclerView>(R.id.rvItensCategoria).apply {
                layoutManager = LinearLayoutManager(this@CategoryBrowserActivity)
                adapter = this@CategoryBrowserActivity.adapter
            }
        } else {
            if (storagePlace == "PANTRY") {
                findViewById<TextView>(R.id.tvCategoryBrowserPrimary).setText(R.string.category_browser_grains)
                findViewById<ImageView>(R.id.imgCategoryBrowserPrimary).setImageResource(R.drawable.ic_cat_grains)
            }
            categories.forEach { (code, row, _) -> findViewById<View>(row).setOnClickListener {
                startActivity(Intent(this, CategoriaItensActivity::class.java).putExtra(EXTRA_CATEGORY, code)
                    .putExtra(EXTRA_STORAGE_PLACE, storagePlace))
            } }
        }
        model.state.observe(this, ::render)
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
    }

    override fun onResume() { super.onResume(); if (::session.isInitialized && session.isLoggedIn) model.refresh() }

    private fun render(state: CategoryBrowserState) {
        if (!session.isLoggedIn) return
        val query = state.query.trim().lowercase(Locale.ROOT)
        val products = state.visibleItems
        adapter?.updateList(products)
        if (category == null) categories.forEach { (code, row, count) ->
            findViewById<View>(row).visibility = if (query.isEmpty() || getString(categoryName(code))
                .lowercase(Locale.ROOT).contains(query)) View.VISIBLE else View.GONE
            findViewById<TextView>(count).text = if (state.loaded) {
                val value = state.count(code)
                resources.getQuantityString(R.plurals.category_browser_item_count, value, value)
            } else getString(R.string.category_browser_unknown)
        }
        findViewById<TextView>(R.id.categoryBrowserStatus).apply {
            text = when {
                state.error != null -> state.error.userMessage(getString(R.string.category_browser_error))
                state.loading -> getString(R.string.inventory_loading)
                category != null && state.loaded && products.isEmpty() -> getString(R.string.category_browser_empty)
                else -> ""
            }
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            isClickable = state.error != null && !state.loading
        }
    }

    private fun categoryName(code: String) = when (code) {
        "DAIRY" -> R.string.category_browser_dairy
        "VEGETABLE" -> R.string.category_browser_produce
        "BEVERAGE" -> R.string.category_browser_beverages
        "GRAIN" -> R.string.category_browser_grains
        else -> R.string.category_browser_meat
    }

    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    companion object {
        const val EXTRA_CATEGORY = "CATEGORY_CODE"
        const val EXTRA_STORAGE_PLACE = "category_storage_place"
    }
}
