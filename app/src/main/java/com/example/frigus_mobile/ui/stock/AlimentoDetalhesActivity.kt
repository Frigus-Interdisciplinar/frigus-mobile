package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
        findViewById<View>(R.id.lotBack).setOnClickListener { finish() }
        findViewById<View>(R.id.lotReload).setOnClickListener { model.load(lotId, reconcile = true) }
        findViewById<View>(R.id.btnEditarAlimento).setOnClickListener {
            editResult.launch(Intent(this, EditarAlimentoActivity::class.java).putExtra(EXTRA_LOT_ID, lotId))
        }
        findViewById<View>(R.id.btnRemoverEstoque).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.lot_remove_title).setMessage(R.string.lot_remove_confirmation)
                .setPositiveButton(R.string.lot_remove) { _, _ -> model.delete(lotId) }
                .setNegativeButton(R.string.account_cancel, null).show()
        }
        model.state.observe(this) { state ->
            if (model.changed) setResult(RESULT_OK)
            if (state.deleted) { setResult(RESULT_OK); finish(); return@observe }
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
                findViewById<TextView>(R.id.lotInformation).text = getString(R.string.lot_information,
                    lot.id, lot.stockId, number(lot.quantity), number(lot.initialQuantity),
                    number(lot.minimalQuantity), text(lot.expireDate), text(lot.batch),
                    category, place, lotUnitLabel(lot.product?.unitOfMeasure), text(lot.product?.brand),
                    getString(when (lot.productStatus) {
                        "FRESH" -> R.string.inventory_fresh
                        "NEAR_EXPIRATION" -> R.string.inventory_near_expiration
                        "EXPIRED" -> R.string.inventory_expired
                        else -> R.string.inventory_unclassified
                    }))
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
    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
    companion object { const val EXTRA_LOT_ID = "stock_lot_id" }
}

internal fun android.content.Context.lotUnitLabel(code: String?): String = getString(when (code) {
    "GRAM" -> R.string.inventory_unit_g
    "KILOGRAM" -> R.string.inventory_unit_kg
    "LITER" -> R.string.inventory_unit_l
    "MILLILITER" -> R.string.inventory_unit_ml
    "UNIT" -> R.string.inventory_unit_each
    "DOZEN" -> R.string.inventory_unit_dozen
    "PACKAGE" -> R.string.inventory_unit_package
    else -> R.string.inventory_unit_unknown
})
