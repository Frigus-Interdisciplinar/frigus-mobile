package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.utils.NetworkGuard
import kotlinx.coroutines.launch

class TodasCategoriasActivity : AppCompatActivity() {

    private lateinit var repository: StockRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_todas_categorias)

        repository = StockRepository(this)

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.itemCarnes).setOnClickListener {
            abrirCategoria("MEAT", "Carnes")
        }

        findViewById<View>(R.id.itemLaticinios).setOnClickListener {
            abrirCategoria("DAIRY", "Laticínios")
        }

        findViewById<View>(R.id.itemHortifruti).setOnClickListener {
            abrirCategoria("VEGETABLE", "Hortifruti")
        }

        findViewById<View>(R.id.itemBebidas).setOnClickListener {
            abrirCategoria("BEVERAGE", "Bebidas")
        }

        carregarQuantidades()
    }

    override fun onResume() {
        super.onResume()
        carregarQuantidades()
    }

    private fun formatarQtdItens(qtd: Int): String {
        return if (qtd == 1) "1 item" else "$qtd itens"
    }

    private fun carregarQuantidades() {
        if (!::repository.isInitialized) return
        if (!NetworkGuard.ensureConnected(this) { carregarQuantidades() }) return

        lifecycleScope.launch {
            try {
                val allProducts = repository.getStockProducts()
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

                findViewById<TextView>(R.id.tvQtdTodasCarnes)?.text = formatarQtdItens(qtdCarnes)
                findViewById<TextView>(R.id.tvQtdTodasLaticinios)?.text = formatarQtdItens(qtdLaticinios)
                findViewById<TextView>(R.id.tvQtdTodasHortifruti)?.text = formatarQtdItens(qtdHortifruti)
                findViewById<TextView>(R.id.tvQtdTodasBebidas)?.text = formatarQtdItens(qtdBebidas)
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(this@TodasCategoriasActivity, e.message) {
                    carregarQuantidades()
                }
            }
        }
    }

    private fun abrirCategoria(code: String, name: String) {
        val intent = Intent(this, CategoriaItensActivity::class.java).apply {
            putExtra("CATEGORY_CODE", code)
            putExtra("CATEGORY_NAME", name)
        }
        startActivity(intent)
    }
}
