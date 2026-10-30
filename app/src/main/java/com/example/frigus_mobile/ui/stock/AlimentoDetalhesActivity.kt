package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity
import java.text.NumberFormat

class AlimentoDetalhesActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var model: StockLotViewModel
    private var leaving = false
    private var lotId = 0
    private val editResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            model.load(lotId, reconcile = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.isLoggedIn) { openLogin(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_alimento_detalhes)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lotDetailRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(findViewById(R.id.lotDetailRoot))
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
        lotId = intent.getIntExtra(EXTRA_LOT_ID, 0)
        model = ViewModelProvider(this)[StockLotViewModel::class.java]
        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<View>(R.id.lotReload).setOnClickListener { model.load(lotId, reconcile = true) }
        findViewById<View>(R.id.btnEditarAlimento).setOnClickListener {
            editResult.launch(Intent(this, EditarAlimentoActivity::class.java).putExtra(EXTRA_LOT_ID, lotId))
        }
        findViewById<View>(R.id.btnRemoverEstoque).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.stock_remove_from_stock).setMessage(R.string.lot_remove_confirmation)
                .setPositiveButton(R.string.stock_remove) { _, _ -> model.delete(lotId) }
                .setNegativeButton(R.string.stock_cancel, null).show()
        }
        model.state.observe(this) { state ->
            if (model.changed) setResult(RESULT_OK)
            if (state.deleted) {
                Toast.makeText(this, R.string.lot_removed, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK); finish(); return@observe
            }
            findViewById<View>(R.id.lotProgress).visibility = if (state.loading || state.busy) View.VISIBLE else View.GONE
            findViewById<View>(R.id.lotContent).visibility = if (state.lot != null) View.VISIBLE else View.GONE
            findViewById<View>(R.id.lotReload).isEnabled = !state.loading && !state.busy
            val canMutate = state.lot != null && !state.loading && !state.busy && !state.uncertain
            findViewById<View>(R.id.btnEditarAlimento).isEnabled = canMutate
            findViewById<View>(R.id.btnRemoverEstoque).isEnabled = canMutate
            findViewById<TextView>(R.id.lotStatus).text = lotStatus(state)
            state.lot?.let { lot ->
                val unknown = getString(R.string.account_not_informed)
                fun text(value: String?) = value?.takeIf(String::isNotBlank) ?: unknown
                fun number(value: Int?) = value?.let { NumberFormat.getIntegerInstance().format(it) } ?: unknown
                fun label(code: String?, codes: Int, labels: Int): String {
                    val index = resources.getStringArray(codes).indexOf(code)
                    return if (index > 0) resources.getStringArray(labels)[index] else text(code)
                }
                val category = label(lot.category ?: lot.product?.category, R.array.inventory_category_codes, R.array.inventory_category_labels)
                val place = label(lot.product?.storagePlace, R.array.inventory_place_codes, R.array.inventory_place_labels)
                findViewById<TextView>(R.id.tvDetalhesNome).text = text(lot.product?.name)
                findViewById<ImageView>(R.id.imgDetalhesAlimento).setImageResource(
                    stockCategoryIcon(lot.category ?: lot.product?.category))
                findViewById<TextView>(R.id.chipCategoria).text = category
                findViewById<TextView>(R.id.chipLocal).text = place
                findViewById<TextView>(R.id.tvInfoCategoria).text = category
                findViewById<TextView>(R.id.tvInfoValidade).text = stockDisplayDate(lot.expireDate).ifBlank { unknown }
                val unit = lotUnitLabel(lot.product?.unitOfMeasure)
                findViewById<TextView>(R.id.tvInfoQuantidadeOriginal).text = getString(
                    R.string.inventory_quantity, number(lot.initialQuantity), unit)
                findViewById<TextView>(R.id.tvFracaoQuantidade).text = getString(
                    R.string.lot_quantity_fraction, number(lot.quantity), number(lot.initialQuantity), unit)
                val currentQuantity = lot.quantity
                val initialQuantity = lot.initialQuantity
                val percent = if (currentQuantity != null && currentQuantity >= 0 && initialQuantity != null && initialQuantity > 0)
                    currentQuantity.toLong() * 100 / initialQuantity else null
                findViewById<TextView>(R.id.tvPercentualRestante).apply {
                    visibility = if (percent != null) View.VISIBLE else View.GONE
                    text = percent?.let { NumberFormat.getPercentInstance().format(it / 100.0) }.orEmpty()
                }
                findViewById<ProgressBar>(R.id.pbQuantidadeRestante).apply {
                    visibility = if (percent != null) View.VISIBLE else View.GONE
                    progress = percent?.coerceIn(0, 100)?.toInt() ?: 0
                }
                findViewById<TextView>(R.id.lotMinimumValue).text = number(lot.minimalQuantity)
                findViewById<TextView>(R.id.lotBatchValue).text = text(lot.batch)
                findViewById<TextView>(R.id.lotUnitValue).text = unit
                findViewById<TextView>(R.id.lotBrandValue).text = text(lot.product?.brand)
                findViewById<TextView>(R.id.lotIdentity).text = getString(R.string.inventory_lot_identity, lot.id, lot.stockId)
                findViewById<TextView>(R.id.lotProductStatus).setText(when (lot.productStatus) {
                    "FRESH" -> R.string.inventory_fresh
                    "NEAR_EXPIRATION" -> R.string.inventory_near_expiration
                    "EXPIRED" -> R.string.inventory_expired
                    else -> R.string.inventory_unclassified
                })
            }
        }
        model.loadIfNeeded(lotId)
    }

    private fun lotStatus(state: StockLotState): String = when {
        state.uncertain -> getString(R.string.lot_uncertain)
        state.busy -> getString(R.string.lot_sending)
        state.loading -> getString(R.string.inventory_loading)
        state.error != null -> state.error.userMessage(getString(when (state.error.status) {
            403 -> R.string.lot_forbidden
            404 -> R.string.lot_not_found
            else -> R.string.lot_error
        }))
        else -> ""
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !session.isLoggedIn) openLogin()
    }
    override fun finish() {
        if (::model.isInitialized && model.requiresRefreshOnExit) setResult(RESULT_OK)
        super.finish()
    }

    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
    companion object { const val EXTRA_LOT_ID = "stock_lot_id" }
}
