package com.example.frigus_mobile.ui.stock

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.ui.stock.adapter.EstoqueProdutoAdapter
import kotlinx.coroutines.launch

class CategoriaItensActivity : AppCompatActivity() {

    private val repository = StockRepository()
    private lateinit var adapter: EstoqueProdutoAdapter
    private var categoryCode = "MEAT"
    private var categoryName = "Carnes"
    private var allCategoryProducts = listOf<StockProductResponse>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categoria_itens)

        categoryCode = intent.getStringExtra("CATEGORY_CODE") ?: "MEAT"
        categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "Carnes"

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.tvCategoriaTitulo).text = categoryName

        val rv = findViewById<RecyclerView>(R.id.rvItensCategoria)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = EstoqueProdutoAdapter(this, emptyList())
        rv.adapter = adapter

        val etBuscar = findViewById<EditText>(R.id.etBuscarItensCategoria)
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrar(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        carregarItens()
    }

    override fun onResume() {
        super.onResume()
        carregarItens()
    }

    private fun carregarItens() {
        lifecycleScope.launch {
            val all = repository.getStockProducts(stockId = 1)
            allCategoryProducts = all.filter {
                (it.category ?: it.product?.category ?: "").equals(categoryCode, ignoreCase = true) ||
                (categoryCode == "VEGETABLE" && (it.category == "FRUIT" || it.product?.category == "FRUIT"))
            }
            adapter.updateList(allCategoryProducts)
        }
    }

    private fun filtrar(query: String) {
        val q = query.trim().lowercase()
        val filtered = allCategoryProducts.filter {
            q.isEmpty() || (it.product?.name ?: "").lowercase().contains(q)
        }
        adapter.updateList(filtered)
    }
}
