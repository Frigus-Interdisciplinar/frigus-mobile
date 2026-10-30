package com.example.frigus_mobile.ui.stock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.StockMovementRequest
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.ui.stock.adapter.StockMovementAdapter
import java.text.NumberFormat

class AlimentoDetalhesActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var model: StockLotViewModel
    private lateinit var movements: StockMovementsViewModel
    private var leaving = false
    private var lotId = 0
    private val editResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            model.load(lotId, reconcile = true)
            movements.loadFirst(lotId)
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
        movements = ViewModelProvider(this)[StockMovementsViewModel::class.java]
        val movementAdapter = StockMovementAdapter()
        findViewById<RecyclerView>(R.id.movementHistoryList).apply {
            layoutManager = LinearLayoutManager(this@AlimentoDetalhesActivity)
            adapter = movementAdapter
        }
        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<View>(R.id.lotReload).setOnClickListener {
            model.load(lotId, reconcile = true)
            movements.loadFirst(lotId)
        }
        findViewById<View>(R.id.btnEditarAlimento).setOnClickListener {
            editResult.launch(Intent(this, EditarAlimentoActivity::class.java).putExtra(EXTRA_LOT_ID, lotId))
        }
        findViewById<View>(R.id.btnRemoverEstoque).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.stock_remove_from_stock).setMessage(R.string.lot_remove_confirmation)
                .setPositiveButton(R.string.stock_remove) { _, _ -> model.delete(lotId) }
                .setNegativeButton(R.string.stock_cancel, null).show()
        }
        findViewById<View>(R.id.movementHistoryRetry).setOnClickListener { movements.loadFirst(lotId) }
        findViewById<View>(R.id.movementHistoryMore).setOnClickListener { movements.loadMore(lotId) }
        findViewById<View>(R.id.movementVerify).setOnClickListener { movements.verify(lotId) }
        findViewById<View>(R.id.movementGlobalVerify).setOnClickListener { movements.verify(lotId) }
        findViewById<View>(R.id.movementAcknowledge).setOnClickListener { movements.acknowledgeVerification() }
        findViewById<View>(R.id.movementSubmit).setOnClickListener { confirmMovement() }
        model.state.observe(this) { state ->
            if (model.changed) setResult(RESULT_OK)
            if (state.deleted) {
                Toast.makeText(this, R.string.lot_removed, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK); finish(); return@observe
            }
            findViewById<View>(R.id.lotProgress).visibility = if (state.loading || state.busy) View.VISIBLE else View.GONE
            findViewById<View>(R.id.lotContent).visibility = if (state.lot != null) View.VISIBLE else View.GONE
            findViewById<View>(R.id.lotReload).isEnabled = !state.loading && !state.busy
            updateActionAvailability()
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
        movements.state.observe(this) { state ->
            movementAdapter.updateList(state.items)
            findViewById<TextView>(R.id.movementHistoryStatus).text = when {
                state.loading || state.loadingMore -> getString(R.string.movement_loading)
                state.readError != null -> state.readError.userMessage(getString(R.string.movement_read_error))
                !state.loaded -> getString(R.string.movement_loading)
                state.items.isEmpty() -> getString(R.string.movement_empty)
                else -> getString(R.string.movement_loaded,
                    NumberFormat.getIntegerInstance().format(state.items.size),
                    NumberFormat.getIntegerInstance().format(state.totalElements))
            }
            findViewById<View>(R.id.movementHistoryRetry).visibility =
                if (state.readError != null && !state.uncertain) View.VISIBLE else View.GONE
            findViewById<View>(R.id.movementHistoryMore).apply {
                visibility = if (state.loaded && state.nextPage < state.totalPages &&
                    state.readError == null && !state.uncertain) View.VISIBLE else View.GONE
                isEnabled = !state.loading && !state.loadingMore
            }
            findViewById<TextView>(R.id.movementMutationStatus).text = when {
                state.verifying -> getString(R.string.movement_verifying)
                state.verificationReady -> getString(R.string.movement_verification_ready)
                state.uncertain -> getString(R.string.movement_uncertain)
                state.busy -> getString(R.string.movement_sending)
                state.mutationError != null -> state.mutationError.userMessage(getString(when (state.mutationError.status) {
                    403 -> R.string.lot_forbidden
                    404 -> R.string.lot_not_found
                    else -> R.string.movement_error
                }))
                else -> ""
            }
            findViewById<View>(R.id.movementVerify).visibility =
                if (state.uncertain && !state.verifying && !state.verificationReady) View.VISIBLE else View.GONE
            findViewById<View>(R.id.movementAcknowledge).visibility =
                if (state.uncertain && state.verificationReady) View.VISIBLE else View.GONE
            state.verifiedLot?.let { movements.consumeVerifiedLot()?.let(model::showVerifiedLot) }
            if (movements.consumeConfirmation()) {
                Toast.makeText(this, R.string.movement_saved, Toast.LENGTH_SHORT).show()
                findViewById<EditText>(R.id.movementQuantity).text.clear()
                findViewById<EditText>(R.id.movementObservation).text.clear()
                setResult(RESULT_OK)
                model.load(lotId, reconcile = true)
            }
            updateActionAvailability()
        }
        model.loadIfNeeded(lotId)
        movements.loadIfNeeded(lotId)
    }

    private fun updateActionAvailability() {
        if (!::model.isInitialized || !::movements.isInitialized) return
        val lot = model.state.value
        val movement = movements.state.value
        val enabled = lot?.lot != null && !lot.loading && !lot.busy && !lot.uncertain &&
            movement?.busy != true && movement?.loading != true && movement?.loadingMore != true &&
            movement?.uncertain != true && movement?.verifying != true
        findViewById<View>(R.id.btnEditarAlimento).isEnabled = enabled
        findViewById<View>(R.id.btnRemoverEstoque).isEnabled = enabled
        findViewById<View>(R.id.movementSubmit).isEnabled = enabled
        findViewById<View>(R.id.movementType).isEnabled = enabled
        findViewById<View>(R.id.movementQuantity).isEnabled = enabled
        findViewById<View>(R.id.movementObservation).isEnabled = enabled
        val showGlobal = lot?.lot == null && movement?.uncertain == true
        findViewById<TextView>(R.id.movementGlobalStatus).apply {
            visibility = if (showGlobal) View.VISIBLE else View.GONE
            text = when {
                movement?.verifying == true -> getString(R.string.movement_verifying)
                movement?.verificationReady == true -> getString(R.string.movement_verification_ready)
                else -> getString(R.string.movement_uncertain)
            }
        }
        findViewById<View>(R.id.movementGlobalVerify).visibility =
            if (showGlobal && movement?.verifying != true && movement?.verificationReady != true)
                View.VISIBLE else View.GONE
    }

    private fun confirmMovement() {
        val labels = intArrayOf(R.string.movement_type_in, R.string.movement_type_out,
            R.string.movement_type_adjustment)
        val codes = arrayOf("IN", "OUT", "ADJUSTMENT")
        val index = findViewById<Spinner>(R.id.movementType).selectedItemPosition
        val quantityInput = findViewById<EditText>(R.id.movementQuantity)
        val quantity = quantityInput.text.toString().trim().toIntOrNull()
        if (index !in codes.indices || quantity == null || quantity < 0 ||
            (index != 2 && quantity == 0)) {
            quantityInput.error = getString(R.string.movement_invalid_quantity)
            return
        }
        val observationInput = findViewById<EditText>(R.id.movementObservation)
        val observation = observationInput.text.toString().trim().takeIf(String::isNotEmpty)
        if ((observation?.length ?: 0) > 2000) {
            observationInput.error = getString(R.string.movement_invalid_observation)
            return
        }
        val request = StockMovementRequest(codes[index], quantity, observation)
        AlertDialog.Builder(this)
            .setTitle(R.string.movement_confirm_title)
            .setMessage(getString(R.string.movement_confirm_message, getString(labels[index]),
                NumberFormat.getIntegerInstance().format(quantity)))
            .setNegativeButton(R.string.stock_cancel, null)
            .setPositiveButton(R.string.movement_confirm_action) { _, _ -> movements.submit(lotId, request) }
            .show()
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
        if ((::model.isInitialized && model.requiresRefreshOnExit) ||
            (::movements.isInitialized && movements.requiresRefreshOnExit)) setResult(RESULT_OK)
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
