package com.example.frigus_mobile.ui.qrcode

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.repository.ReceiptRepository
import com.example.frigus_mobile.ui.home.MainActivity
import com.google.android.material.button.MaterialButton
import java.time.LocalDate

class QrCodeItensActivity : AppCompatActivity() {
    private lateinit var model: ReceiptReviewViewModel
    private lateinit var adapter: QrCodeProdutoAdapter
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var summary: View
    private lateinit var note: TextView
    private lateinit var confirm: MaterialButton
    private lateinit var verify: MaterialButton
    private var receiptId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_qr_code_itens)
        model = ViewModelProvider(this)[ReceiptReviewViewModel::class.java]
        receiptId = intent.getStringExtra(QrReceiptExtras.RECEIPT_ID)
        progress = findViewById(R.id.receiptReviewProgress)
        status = findViewById(R.id.receiptReviewStatus)
        summary = findViewById(R.id.receiptInvoiceSummary)
        note = findViewById(R.id.receiptReviewNote)
        confirm = findViewById(R.id.btnConfirmReceipt)
        verify = findViewById(R.id.btnVerifyReceipt)

        adapter = QrCodeProdutoAdapter(
            onIncludedChanged = { id, included -> model.setIncluded(id, included) },
            onQuantityChanged = { id, delta -> model.adjustQuantity(id, delta) },
            onExpirationRequested = ::chooseExpiration
        )
        findViewById<RecyclerView>(R.id.rvItensNotaFiscal).apply {
            layoutManager = LinearLayoutManager(this@QrCodeItensActivity)
            adapter = this@QrCodeItensActivity.adapter
        }

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        confirm.setOnClickListener { confirmImport() }
        verify.setOnClickListener {
            val id = receiptId ?: return@setOnClickListener
            if (model.state.value?.uncertain == true) model.verify(id) else model.load(id)
        }
        model.state.observe(this, ::render)
        val id = receiptId
        if (id.isNullOrBlank()) {
            showStatus(getString(R.string.receipt_review_error))
            return
        }
        model.loadIfNeeded(id)
    }

    private fun render(state: ReceiptReviewState) {
        progress.visibility = if (state.loading || state.verifying || state.submitting) View.VISIBLE else View.GONE
        val receipt = state.receipt
        bindInvoice(receipt)
        adapter.updateList(state.lines)

        val isReviewable = receipt?.status == ReceiptRepository.REVIEW_REQUIRED
        val imported = receipt?.status == ReceiptRepository.IMPORTED
        val failed = receipt?.status == ReceiptRepository.FAILED
        confirm.visibility = if (isReviewable && !state.uncertain) View.VISIBLE else View.GONE
        confirm.isEnabled = isReviewable && !state.uncertain && !state.submitting && !state.loading
        verify.visibility = if (state.uncertain || state.readError != null) View.VISIBLE else View.GONE
        verify.isEnabled = !state.loading && !state.verifying && !state.submitting
        if (state.uncertain) verify.setText(R.string.receipt_verify)
        else verify.setText(R.string.receipt_review_retry)

        val stateMessage = when {
            state.loading -> getString(R.string.receipt_review_loading)
            state.verifying -> getString(R.string.receipt_verifying)
            state.submitting -> getString(R.string.receipt_confirm_sending)
            state.readError != null -> state.readError.userMessage(getString(R.string.receipt_review_error))
            imported -> getString(R.string.receipt_review_imported)
            failed -> getString(R.string.receipt_review_failed)
            state.uncertain && isReviewable -> getString(R.string.receipt_still_pending)
            state.uncertain -> getString(R.string.receipt_confirm_uncertain)
            state.confirmError != null -> state.confirmError.userMessage(getString(R.string.receipt_confirm_error))
            isReviewable && state.lines.isEmpty() -> getString(R.string.receipt_review_empty)
            else -> null
        }
        if (stateMessage.isNullOrBlank()) status.visibility = View.GONE else showStatus(stateMessage)
        bindNote(state, isReviewable)

        state.completion?.let { completion ->
            model.consumeCompletion()
            startActivity(Intent(this, QrCodeConfirmacaoActivity::class.java)
                .putExtra(QrReceiptExtras.RECEIPT_ID, completion.receiptId)
                .putExtra(QrReceiptExtras.IMPORTED_COUNT, completion.importedCount ?: -1)
                .putExtra(QrReceiptExtras.IGNORED_COUNT, completion.ignoredCount ?: -1))
            finish()
        }
    }

    private fun bindInvoice(state: com.example.frigus_mobile.data.model.ReceiptResponse?) {
        val invoice = state?.invoice
        summary.visibility = if (invoice == null) View.GONE else View.VISIBLE
        if (invoice == null) return
        findViewById<TextView>(R.id.receiptIssuer).text = getString(R.string.receipt_issuer,
            invoice.issuerName?.takeIf(String::isNotBlank) ?: getString(R.string.receipt_value_unavailable))
        findViewById<TextView>(R.id.receiptIssuedAt).text = getString(R.string.receipt_issue_date,
            invoice.issuedAt?.takeIf(String::isNotBlank) ?: getString(R.string.receipt_value_unavailable))
        findViewById<TextView>(R.id.receiptTotal).text = getString(R.string.receipt_total,
            invoice.totalAmount?.let { java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR")).format(it) }
                ?: getString(R.string.receipt_value_unavailable))
        findViewById<TextView>(R.id.receiptAccessKey).text = getString(R.string.receipt_access_key,
            invoice.accessKey?.takeIf(String::isNotBlank) ?: getString(R.string.receipt_value_unavailable))
        val warnings = state.warnings.orEmpty().filter(String::isNotBlank)
        findViewById<TextView>(R.id.receiptWarnings).apply {
            text = warnings.joinToString("\n")
            visibility = if (warnings.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun bindNote(state: ReceiptReviewState, isReviewable: Boolean) {
        if (!isReviewable || state.uncertain || state.lines.isEmpty()) {
            note.visibility = View.GONE
            return
        }
        val selected = state.lines.filter { it.include }
        val missingExpiration = selected.any { it.expireDate == null }
        val badQuantity = state.lines.any { !it.include && it.quantity == null &&
            (it.item.matchedProduct?.productId ?: 0) > 0 }
        val message = when {
            missingExpiration -> getString(R.string.receipt_expiration_required)
            badQuantity -> getString(R.string.receipt_quantity_not_importable)
            else -> getString(R.string.receipt_import_summary, selected.size, state.lines.size - selected.size)
        }
        note.text = message
        note.visibility = View.VISIBLE
    }

    private fun confirmImport() {
        val id = receiptId ?: return
        val state = model.state.value ?: return
        val request = model.request()
        if (request == null) {
            val needsExpiration = state.lines.any { it.include && it.expireDate == null }
            Toast.makeText(this,
                if (needsExpiration) R.string.receipt_expiration_required else R.string.receipt_quantity_not_importable,
                Toast.LENGTH_LONG).show()
            return
        }
        val importCount = request.count { it.action == "IMPORT" }
        val ignoredCount = request.size - importCount
        AlertDialog.Builder(this)
            .setTitle(R.string.receipt_confirm_title)
            .setMessage(getString(R.string.receipt_confirm_message, importCount, ignoredCount))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.receipt_confirm_action) { _, _ -> model.submit(id, request) }
            .show()
    }

    private fun chooseExpiration(lineId: String) {
        val line = model.state.value?.lines?.firstOrNull { it.item.lineId == lineId } ?: return
        val initial = line.expireDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        DatePickerDialog(this, { _, year, month, day ->
            model.setExpireDate(lineId, "%04d-%02d-%02d".format(year, month + 1, day))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    private fun showStatus(message: String) {
        status.text = message
        status.visibility = View.VISIBLE
    }
}
