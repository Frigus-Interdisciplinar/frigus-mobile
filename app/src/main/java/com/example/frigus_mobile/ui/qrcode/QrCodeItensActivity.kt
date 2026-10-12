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

        val items = mutableListOf(
            NotaFiscalItem(201, "Leite integral", 1, "un", 5.49, "DAIRY"),
            NotaFiscalItem(202, "Banana", 1, "un", 6.99, "FRUIT"),
            NotaFiscalItem(203, "Tomate", 2, "kg", 7.98, "VEGETABLE"),
            NotaFiscalItem(204, "Filé de Frango", 1, "kg", 15.90, "MEAT")
        )

        val rv = findViewById<RecyclerView>(R.id.rvItensNotaFiscal)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = QrCodeProdutoAdapter(items)
        rv.adapter = adapter

        findViewById<View>(R.id.btnSalvarItensNota).setOnClickListener {
            salvarItens()
        }
    }

    private fun salvarItens() {
        lifecycleScope.launch {
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
                    stockId = 1,
                    quantity = nfItem.quantity,
                    minimalQuantity = nfItem.quantity,
                    purchaseUnitPrice = nfItem.price,
                    expireDate = "30/05/2026",
                    productStatus = "FRESH",
                    category = nfItem.category
                )
                repository.addStockProduct(stockItem)
            }

            val intent = Intent(this@QrCodeItensActivity, QrCodeConfirmacaoActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
