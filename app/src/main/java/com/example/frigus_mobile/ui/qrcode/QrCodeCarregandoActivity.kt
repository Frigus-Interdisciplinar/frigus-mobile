package com.example.frigus_mobile.ui.qrcode

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R

class QrCodeCarregandoActivity : AppCompatActivity() {
    private lateinit var model: QrReceiptScanViewModel
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var retry: View
    private var stockId = 0
    private var payload: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_qr_code_carregando)
        model = ViewModelProvider(this)[QrReceiptScanViewModel::class.java]
        status = findViewById(R.id.receiptScanStatus)
        progress = findViewById(R.id.receiptScanProgress)
        retry = findViewById(R.id.btnRetryReceiptScan)
        stockId = intent.getIntExtra(QrReceiptExtras.STOCK_ID, 0)
        payload = intent.getStringExtra(QrReceiptExtras.QR_PAYLOAD)

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        retry.setOnClickListener {
            val qr = payload ?: return@setOnClickListener
            model.retry(stockId, qr)
        }
        model.state.observe(this, ::render)
        if (stockId <= 0 || payload.isNullOrBlank()) {
            status.setText(R.string.receipt_invalid_qr)
            progress.visibility = View.GONE
            return
        }
        model.scan(stockId, payload!!)
    }

    private fun render(state: ReceiptScanState) {
        progress.visibility = if (state.loading) View.VISIBLE else View.GONE
        retry.visibility = if (state.error != null && state.receiptId == null) View.VISIBLE else View.GONE
        if (state.loading) status.setText(R.string.receipt_scan_loading)
        else if (state.error != null) {
            status.text = when {
                state.uncertain -> getString(R.string.receipt_scan_uncertain)
                state.error.status == 409 -> getString(R.string.receipt_already_imported)
                else -> state.error.userMessage(getString(R.string.receipt_scan_error))
            }
        }
        state.receiptId?.let(::openReview)
    }

    private fun openReview(receiptId: String) {
        startActivity(Intent(this, QrCodeItensActivity::class.java)
            .putExtra(QrReceiptExtras.RECEIPT_ID, receiptId))
        finish()
    }
}
