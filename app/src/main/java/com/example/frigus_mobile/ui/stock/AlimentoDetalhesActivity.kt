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
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.example.frigus_mobile.utils.StockQuantityTracker
import com.example.frigus_mobile.utils.ProductImageTracker
import com.example.frigus_mobile.utils.StockObservationsTracker
import com.example.frigus_mobile.utils.ProductNameTracker
import android.net.Uri
import kotlinx.coroutines.launch

class AlimentoDetalhesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOT_ID = "extra_lot_id"
    }

    private lateinit var repository: StockRepository
    private var item: StockProductResponse? = null
    private var lotId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alimento_detalhes)

        repository = StockRepository(this)
        item = intent.getSerializableExtra("STOCK_PRODUCT") as? StockProductResponse
        lotId = intent.getIntExtra(EXTRA_LOT_ID, item?.id ?: -1)

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.btnConsumirAlimento)?.setOnClickListener {
            abrirConfirmacaoSaida(isConsumo = true)
        }
        findViewById<View>(R.id.btnDesperdicadoAlimento)?.setOnClickListener {
            abrirConfirmacaoSaida(isConsumo = false)
        }
        findViewById<View>(R.id.btnEditarAlimento).setOnClickListener {
            val intent = Intent(this, EditarAlimentoActivity::class.java).apply {
                putExtra("STOCK_PRODUCT", item)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.btnRemoverEstoque)?.setOnClickListener {
            confirmarRemocao()
        }

        preencherDados()
    }

    override fun onResume() {
        super.onResume()
        atualizarDetalhesDaApi()
    }

    private fun atualizarDetalhesDaApi() {
        val id = item?.id ?: if (lotId > 0) lotId else return
        if (!NetworkGuard.ensureConnected(this) { atualizarDetalhesDaApi() }) return

        lifecycleScope.launch {
            try {
                val updated = repository.getStockProductById(id)
                if (updated != null) {
                    item = updated
                    lotId = updated.id ?: id
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
        val productName = prod.id?.let { ProductNameTracker.getName(this, it) } ?: prod.product?.name ?: "Alimento"
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
        val unit = lotUnitLabel(prod.product?.unitOfMeasure)
        val currentQty = prod.quantity ?: 0
        val originalQty = StockQuantityTracker.getOrSetInitialQuantity(this, prod.id ?: 0, currentQty)
        val minQty = prod.minimalQuantity ?: 0
        val percent = if (originalQty > 0) ((currentQty.toFloat() / originalQty) * 100).toInt().coerceIn(0, 100) else 100

        findViewById<TextView>(R.id.tvDetalhesNome).text = productName
        findViewById<TextView>(R.id.chipCategoria).text = categoryName
        findViewById<TextView>(R.id.chipLocal).text = storagePlaceName
        findViewById<TextView>(R.id.tvPercentualRestante).text = "$percent%"

        val pbRestante = findViewById<ProgressBar>(R.id.pbQuantidadeRestante)
        pbRestante.progress = percent
        if (minQty > 0 && currentQty <= minQty) {
            pbRestante.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_red))
        } else if (percent <= 25) {
            pbRestante.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_gold))
        } else {
            pbRestante.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_green))
        }

        findViewById<TextView>(R.id.tvFracaoQuantidade).text = "$currentQty$unit de $originalQty$unit"

        findViewById<TextView>(R.id.tvInfoValidade).text = stockDetailExpiration(prod.expireDate)
            ?: getString(R.string.lot_detail_not_informed)
        val price = stockDetailUnitPrice(prod.purchaseUnitPrice, prod.product?.unitPrice)
        findViewById<TextView>(R.id.tvInfoPrecoRotulo).setText(
            if (price?.source == StockDetailPriceSource.CATALOG) R.string.lot_detail_catalog_unit_price
            else R.string.lot_detail_purchase_unit_price)
        findViewById<TextView>(R.id.tvInfoPreco).text = price?.let(::stockDetailCurrency)
            ?: getString(R.string.lot_detail_not_informed)
        findViewById<TextView>(R.id.tvInfoCategoria).text = categoryName
        findViewById<TextView>(R.id.tvInfoQuantidadeOriginal).text = "$originalQty$unit"

        val obs = prod.id?.let { StockObservationsTracker.getObservation(this, it) }
        val layoutObs = findViewById<View>(R.id.layoutInfoObservacoes)
        val tvObs = findViewById<TextView>(R.id.tvInfoObservacoes)
        if (!obs.isNullOrBlank()) {
            layoutObs?.visibility = View.VISIBLE
            tvObs?.text = obs
        } else {
            layoutObs?.visibility = View.GONE
        }

        val img = findViewById<ImageView>(R.id.imgDetalhesAlimento)
        val displayName = prod.id?.let { ProductNameTracker.getName(this, it) } ?: prod.product?.name
        val imgRes = ProductCategoryHelper.getImagemParaProduto(displayName, prod.category ?: prod.product?.category)
        val remoteUrl = prod.product?.imageUrl
        if (!remoteUrl.isNullOrBlank()) {
            img.clearColorFilter()
            com.example.frigus_mobile.utils.ProductImageLoader.loadInto(img, remoteUrl, imgRes)
            return
        }
        val customUriStr = prod.id?.let { ProductImageTracker.getImageUri(this, it) }
        if (!customUriStr.isNullOrBlank()) {
            try {
                img.clearColorFilter()
                img.setImageURI(Uri.parse(customUriStr))
            } catch (_: Exception) {
                carregarImagemPadrao(img, prod)
            }
        } else {
            carregarImagemPadrao(img, prod)
        }
    }


    private fun carregarImagemPadrao(img: ImageView, prod: StockProductResponse) {
        val displayName = prod.id?.let { ProductNameTracker.getName(this, it) } ?: prod.product?.name
        val imgRes = ProductCategoryHelper.getImagemParaProduto(displayName, prod.category ?: prod.product?.category)
        img.clearColorFilter()
        img.setImageResource(imgRes)
    }

        private fun abrirConfirmacaoSaida(isConsumo: Boolean) {
        val prod = item ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirmar_saida_estoque, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val unit = lotUnitLabel(prod.product?.unitOfMeasure)
        val prodName = prod.id?.let { ProductNameTracker.getName(this, it) } ?: prod.product?.name ?: "Alimento"
        val maxQty = prod.quantity ?: 1
        var selectedQty = 1

        val imgIcone = dialogView.findViewById<ImageView>(R.id.imgIconeSaida)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvTituloSaida)
        val tvSubtitulo = dialogView.findViewById<TextView>(R.id.tvSubtituloSaida)
        val btnConfirmar = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnConfirmarSaida)

        if (isConsumo) {
            imgIcone?.setImageResource(R.drawable.ic_savings)
            imgIcone?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_green))
            tvTitulo?.text = "Confirmar Consumo"
            tvSubtitulo?.text = "Essa ação contabiliza economia no seu Money-Saving."
            btnConfirmar?.setBackgroundResource(R.drawable.bg_btn_solid_green)
            btnConfirmar?.text = "Consumir"
        } else {
            imgIcone?.setImageResource(R.drawable.ic_stock_remove)
            imgIcone?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_red))
            tvTitulo?.text = "Registrar Desperdício"
            tvSubtitulo?.text = "Essa ação contabiliza o alimento descartado no Money-Saving."
            btnConfirmar?.setBackgroundResource(R.drawable.bg_btn_outlined_red)
            btnConfirmar?.setTextColor(ContextCompat.getColor(this, R.color.color_red))
            btnConfirmar?.text = "Descartar"
        }

        dialogView.findViewById<TextView>(R.id.tvNomeAlimentoSaida)?.text = prodName
        val expireStr = stockDetailExpiration(prod.expireDate) ?: getString(R.string.lot_detail_not_informed)
        dialogView.findViewById<TextView>(R.id.tvDetalhesAlimentoSaida)?.text = "Disponível: $maxQty$unit • Validade: $expireStr"

        val imgRem = dialogView.findViewById<ImageView>(R.id.imgAlimentoSaida)
        val imgRes = ProductCategoryHelper.getImagemParaProduto(prodName, prod.category ?: prod.product?.category)
        imgRem?.clearColorFilter()
        val remoteUrl = prod.product?.imageUrl
        if (!remoteUrl.isNullOrBlank()) {
            com.example.frigus_mobile.utils.ProductImageLoader.loadInto(imgRem, remoteUrl, imgRes)
        } else {
            imgRem?.setImageResource(imgRes)
        }

        val tvQtd = dialogView.findViewById<TextView>(R.id.tvQuantidadeSaida)
        tvQtd?.text = "$selectedQty"

        dialogView.findViewById<View>(R.id.btnDiminuirQtd)?.setOnClickListener {
            if (selectedQty > 1) {
                selectedQty--
                tvQtd?.text = "$selectedQty"
            }
        }

        dialogView.findViewById<View>(R.id.btnAumentarQtd)?.setOnClickListener {
            if (selectedQty < maxQty) {
                selectedQty++
                tvQtd?.text = "$selectedQty"
            }
        }

        dialogView.findViewById<View>(R.id.btnTudoQtd)?.setOnClickListener {
            selectedQty = maxQty
            tvQtd?.text = "$selectedQty"
        }

        dialogView.findViewById<View>(R.id.btnCancelarSaida)?.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmar?.setOnClickListener {
            dialog.dismiss()
            executarSaidaEstoque(isConsumo, selectedQty)
        }

        dialog.show()
    }

    private fun executarSaidaEstoque(isConsumo: Boolean, quantity: Int) {
        val prod = item ?: return
        val id = prod.id ?: return
        if (!NetworkGuard.ensureConnected(this) { executarSaidaEstoque(isConsumo, quantity) }) return

        val totalDisponivel = prod.quantity ?: quantity
        val isTotal = quantity >= totalDisponivel

        lifecycleScope.launch {
            try {
                if (isConsumo) {
                    repository.recordConsumption(id, quantity)
                } else {
                    repository.recordDiscard(id, quantity)
                }

                if (isTotal) {
                    try { repository.deleteStockProduct(id) } catch (_: Exception) {}
                    item?.id?.let {
                        StockQuantityTracker.remove(this@AlimentoDetalhesActivity, it)
                        ProductImageTracker.remove(this@AlimentoDetalhesActivity, it)
                        StockObservationsTracker.remove(this@AlimentoDetalhesActivity, it)
                        ProductNameTracker.remove(this@AlimentoDetalhesActivity, it)
                    }
                    val msg = if (isConsumo) "Alimento consumido! Economia registrada no Money-Saving."
                              else "Descarte registrado! Perda atualizada no Money-Saving."
                    Toast.makeText(this@AlimentoDetalhesActivity, msg, Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val remaining = totalDisponivel - quantity
                    val msg = if (isConsumo) "$quantity consumido(s)! Economia registrada no Money-Saving."
                              else "$quantity descartado(s)! Perda registrada no Money-Saving."
                    Toast.makeText(this@AlimentoDetalhesActivity, msg, Toast.LENGTH_SHORT).show()
                    atualizarDetalhesDaApi()
                }
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(this@AlimentoDetalhesActivity, e.message) {
                    executarSaidaEstoque(isConsumo, quantity)
                }
            }
        }
    }

    private fun confirmarRemocao() {
        val prod = item ?: return
        val dialogView = layoutInflater.inflate(R.layout.activity_stock_removal_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val unit = lotUnitLabel(prod.product?.unitOfMeasure)
        val dialogProdName = prod.id?.let { ProductNameTracker.getName(this, it) } ?: prod.product?.name ?: "Alimento"
        dialogView.findViewById<TextView>(R.id.tvNomeAlimentoRemocao)?.text = dialogProdName
        dialogView.findViewById<TextView>(R.id.tvQuantidadeAlimentoRemocao)?.text = "${prod.quantity ?: 1}$unit"
        dialogView.findViewById<TextView>(R.id.tvValidadeAlimentoRemocao)?.text = getString(
            R.string.lot_detail_expiration_value,
            stockDetailExpiration(prod.expireDate) ?: getString(R.string.lot_detail_not_informed))

        val imgRem = dialogView.findViewById<ImageView>(R.id.imgAlimentoRemocao)
        val imgRes = ProductCategoryHelper.getImagemParaProduto(dialogProdName, prod.category ?: prod.product?.category)
        imgRem?.clearColorFilter()
        imgRem?.setImageResource(imgRes)

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
                item?.id?.let {
                    StockQuantityTracker.remove(this@AlimentoDetalhesActivity, it)
                    ProductImageTracker.remove(this@AlimentoDetalhesActivity, it)
                    StockObservationsTracker.remove(this@AlimentoDetalhesActivity, it)
                    ProductNameTracker.remove(this@AlimentoDetalhesActivity, it)
                }
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
