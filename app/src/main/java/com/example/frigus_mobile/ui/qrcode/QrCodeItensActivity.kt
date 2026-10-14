package com.example.frigus_mobile.ui.qrcode

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockProductProductResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.utils.NetworkGuard
import kotlinx.coroutines.launch

class QrCodeItensActivity : AppCompatActivity() {

    private lateinit var repository: StockRepository
    private lateinit var adapter: QrCodeProdutoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_itens)

        repository = StockRepository(this)

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        // IDs reais e existentes no catálogo do banco de dados da API:
        // 21 = Leite Integral, 2 = Banana Prata, 12 = Tomate Salada, 31 = Peito de Frango
        val items = mutableListOf(
            NotaFiscalItem(21, "Leite Integral", 1, "un", 4.79, "DAIRY"),
            NotaFiscalItem(2, "Banana Prata", 1, "un", 0.59, "FRUIT"),
            NotaFiscalItem(12, "Tomate Salada", 2, "kg", 6.99, "VEGETABLE"),
            NotaFiscalItem(31, "Peito de Frango", 1, "kg", 16.90, "MEAT")
        )

        val rv = findViewById<RecyclerView>(R.id.rvItensNotaFiscal)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = QrCodeProdutoAdapter(items)
        rv.adapter = adapter

        // Tenta enriquecer com dados atualizados do catálogo da API
        lifecycleScope.launch {
            try {
                val catalog = repository.getCatalogProducts()
                if (catalog.isNotEmpty()) {
                    val realItems = mutableListOf<NotaFiscalItem>()
                    val leite = catalog.firstOrNull { it.name?.contains("Leite", ignoreCase = true) == true && it.id != null }
                    val banana = catalog.firstOrNull { it.name?.contains("Banana", ignoreCase = true) == true && it.id != null }
                    val tomate = catalog.firstOrNull { it.name?.contains("Tomate", ignoreCase = true) == true && it.id != null }
                    val frango = catalog.firstOrNull { it.name?.contains("Frango", ignoreCase = true) == true && it.id != null }

                    val found = listOfNotNull(leite, banana, tomate, frango)
                    if (found.isNotEmpty()) {
                        found.forEach { p ->
                            realItems.add(
                                NotaFiscalItem(
                                    id = p.id!!,
                                    name = p.name ?: "Produto",
                                    quantity = 1,
                                    unit = if (p.unitOfMeasure == "KILOGRAM") "kg" else "un",
                                    price = p.unitPrice ?: 5.0,
                                    category = p.category ?: "OTHER"
                                )
                            )
                        }
                        adapter.updateItems(realItems)
                    }
                }
            } catch (e: Exception) {
                // Mantém a lista com os IDs válidos padrão
            }
        }

        findViewById<View>(R.id.btnSalvarItensNota).setOnClickListener {
            salvarItens()
        }
    }

    private fun salvarItens() {
        if (!NetworkGuard.ensureConnected(this) { salvarItens() }) return

        val btnSalvar = findViewById<View>(R.id.btnSalvarItensNota)
        btnSalvar.isEnabled = false

        lifecycleScope.launch {
            try {
                // Formato ISO yyyy-MM-dd exigido pelo Spring Boot (LocalDate)
                val isoDate = java.time.LocalDate.now().plusDays(15).toString()

                for (nfItem in adapter.getItems()) {
                    val stockItem = StockProductResponse(
                        id = nfItem.id,
                        productId = nfItem.id,
                        product = StockProductProductResponse(
                            id = nfItem.id,
                            name = nfItem.name,
                            category = nfItem.category,
                            storagePlace = "FRIDGE",
                            unitPrice = nfItem.price,
                            unitOfMeasure = if (nfItem.unit == "kg") "KILOGRAM" else "UNIT"
                        ),
                        quantity = nfItem.quantity,
                        minimalQuantity = 1,
                        purchaseUnitPrice = if (nfItem.price > 0.0) nfItem.price else 1.0,
                        expireDate = isoDate,
                        productStatus = "FRESH",
                        category = nfItem.category
                    )
                    repository.addStockProduct(stockItem)
                }

                val intent = Intent(this@QrCodeItensActivity, QrCodeConfirmacaoActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                btnSalvar.isEnabled = true
                NetworkGuard.showBlockingError(this@QrCodeItensActivity, e.message) {
                    salvarItens()
                }
            }
        }
    }
}
