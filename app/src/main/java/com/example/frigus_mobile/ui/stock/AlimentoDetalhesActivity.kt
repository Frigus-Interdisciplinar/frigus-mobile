package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.utils.NetworkGuard
import com.example.frigus_mobile.utils.ProductCategoryHelper
import kotlinx.coroutines.launch

class AlimentoDetalhesActivity : AppCompatActivity() {

    private lateinit var repository: StockRepository
    private var item: StockProductResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alimento_detalhes)

        repository = StockRepository(this)
        item = intent.getSerializableExtra("STOCK_PRODUCT") as? StockProductResponse

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.btnRemoverEstoque).setOnClickListener {
            confirmarRemocao()
        }
        findViewById<View>(R.id.btnEditarAlimento).setOnClickListener {
            val intent = Intent(this, EditarAlimentoActivity::class.java).apply {
                putExtra("STOCK_PRODUCT", item)
            }
            startActivity(intent)
        }

        preencherDados()
    }

    override fun onResume() {
        super.onResume()
        atualizarDetalhesDaApi()
    }

    private fun atualizarDetalhesDaApi() {
        val id = item?.id ?: return
        if (!NetworkGuard.ensureConnected(this) { atualizarDetalhesDaApi() }) return

        lifecycleScope.launch {
            try {
                val updated = repository.getStockProductById(id)
                if (updated != null) {
                    item = updated
                    preencherDados()
                }
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(this@AlimentoDetalhesActivity, e.message) {
                    atualizarDetalhesDaApi()
                }
            }
        }
    }

    private fun preencherDados() {
        val prod = item ?: return
        val productName = prod.product?.name ?: "Alimento"
        val categoryName = when (prod.category ?: prod.product?.category) {
            "MEAT" -> "Carnes"
            "DAIRY" -> "Laticínios"
            "FRUIT", "VEGETABLE" -> "Hortifruti"
            "BEVERAGE" -> "Bebidas"
            "GRAIN" -> "Grãos"
            else -> "Outros"
        }
        val storagePlaceName = when (prod.storagePlace ?: prod.product?.storagePlace) {
            "FREEZER" -> "Freezer"
            "PANTRY", "DESPENSA" -> "Despensa"
            else -> "Geladeira"
        }
        val unit = when (prod.product?.unitOfMeasure) {
            "GRAM" -> "g"
            "KILOGRAM" -> "kg"
            "LITER" -> "L"
            "MILLILITER" -> "ml"
            else -> "un"
        }
        val currentQty = prod.quantity ?: 0
        val originalQty = prod.minimalQuantity ?: (currentQty * 2)
        val percent = if (originalQty > 0) ((currentQty.toFloat() / originalQty) * 100).toInt().coerceIn(0, 100) else 100

        findViewById<TextView>(R.id.tvDetalhesNome).text = productName
        findViewById<TextView>(R.id.chipCategoria).text = categoryName
        findViewById<TextView>(R.id.chipLocal).text = storagePlaceName
        findViewById<TextView>(R.id.tvPercentualRestante).text = "$percent%"
        findViewById<ProgressBar>(R.id.pbQuantidadeRestante).progress = percent
        findViewById<TextView>(R.id.tvFracaoQuantidade).text = "$currentQty$unit de $originalQty$unit"

        findViewById<TextView>(R.id.tvInfoValidade).text = prod.expireDate ?: "-"
        findViewById<TextView>(R.id.tvInfoCategoria).text = categoryName
        findViewById<TextView>(R.id.tvInfoQuantidadeOriginal).text = "$originalQty$unit"

        val img = findViewById<ImageView>(R.id.imgDetalhesAlimento)
        val imgRes = ProductCategoryHelper.getImagemParaProduto(prod.product?.name, prod.category ?: prod.product?.category)
        if (imgRes == R.drawable.img_frango) {
            img.clearColorFilter()
            img.setImageResource(imgRes)
        } else {
            img.setImageResource(imgRes)
        }
    }

    private fun confirmarRemocao() {
        val prod = item ?: return
        val dialogView = layoutInflater.inflate(R.layout.activity_stock_removal_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val unit = when (prod.product?.unitOfMeasure) {
            "GRAM" -> "g"
            "KILOGRAM" -> "kg"
            "LITER" -> "L"
            "MILLILITER" -> "ml"
            else -> "un"
        }

        dialogView.findViewById<TextView>(R.id.tvNomeAlimentoRemocao)?.text = prod.product?.name ?: "Alimento"
        dialogView.findViewById<TextView>(R.id.tvQuantidadeAlimentoRemocao)?.text = "${prod.quantity ?: 1}$unit"
        dialogView.findViewById<TextView>(R.id.tvValidadeAlimentoRemocao)?.text = "Validade: ${prod.expireDate ?: "-"}"

        val imgRem = dialogView.findViewById<ImageView>(R.id.imgAlimentoRemocao)
        val imgRes = ProductCategoryHelper.getImagemParaProduto(prod.product?.name, prod.category ?: prod.product?.category)
        if (imgRes == R.drawable.img_frango) {
            imgRem?.clearColorFilter()
            imgRem?.setImageResource(imgRes)
        } else {
            imgRem?.setImageResource(imgRes)
        }

        dialogView.findViewById<View>(R.id.btnCancelarRemocao)?.setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btnConfirmarRemocao)?.setOnClickListener {
            dialog.dismiss()
            removerAlimento()
        }

        dialog.show()
    }

    private fun removerAlimento() {
        val id = item?.id ?: return
        if (!NetworkGuard.ensureConnected(this) { removerAlimento() }) return

        lifecycleScope.launch {
            try {
                repository.deleteStockProduct(id)
                Toast.makeText(this@AlimentoDetalhesActivity, "Alimento removido com sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(this@AlimentoDetalhesActivity, e.message) {
                    removerAlimento()
                }
            }
        }
    }
}
