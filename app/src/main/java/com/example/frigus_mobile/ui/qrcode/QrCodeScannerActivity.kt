package com.example.frigus_mobile.ui.qrcode

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.StockResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch

class QrCodeScannerActivity : AppCompatActivity() {
    private lateinit var stockSelector: Spinner
    private lateinit var status: TextView
    private lateinit var retry: View
    private lateinit var scanButton: View
    private val stocks = mutableListOf<StockResponse>()
    private var loadingStocks = false
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        if (!SessionManager(this).isLoggedIn) { finish(); return }

        ReceiptConfirmationGuard.pendingReceiptId(this)?.let { receiptId ->
            startActivity(Intent(this, QrCodeItensActivity::class.java)
                .putExtra(QrReceiptExtras.RECEIPT_ID, receiptId))
            finish()
            return
        }

        setContentView(R.layout.activity_qr_code_scanner)
        stockSelector = findViewById(R.id.receiptStockSelector)
        status = findViewById(R.id.receiptScannerStatus)
        retry = findViewById(R.id.receiptStockRetry)
        scanButton = findViewById(R.id.btnScanReceipt)
        stockSelector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            mutableListOf(getString(R.string.receipt_select_stock)))

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        retry.setOnClickListener { loadStocks() }
        scanButton.setOnClickListener { startQrScan() }
        loadStocks()
    }

    private fun loadStocks() {
        if (loadingStocks) return
        loadingStocks = true
        stockSelector.isEnabled = false
        scanButton.isEnabled = false
        status.visibility = View.VISIBLE
        status.setText(R.string.receipt_loading_stocks)
        retry.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val home = StockRepository(this@QrCodeScannerActivity).getInventoryContext()
                stocks.clear()
                stocks.addAll(home.stocks.orEmpty().filterNotNull().filter { it.id > 0 })
                val labels = mutableListOf(getString(R.string.receipt_select_stock))
                labels.addAll(stocks.map { stock -> stock.name?.takeIf(String::isNotBlank)
                    ?: getString(R.string.receipt_stock_fallback, stock.id) })
                stockSelector.adapter = ArrayAdapter(this@QrCodeScannerActivity,
                    android.R.layout.simple_spinner_dropdown_item, labels)
                stockSelector.setSelection(0)
                if (stocks.isEmpty()) {
                    status.setText(R.string.receipt_no_stocks)
                    status.visibility = View.VISIBLE
                    stockSelector.isEnabled = false
                    scanButton.isEnabled = false
                } else {
                    status.visibility = View.GONE
                    stockSelector.isEnabled = true
                    scanButton.isEnabled = true
                }
            } catch (_: Exception) {
                stocks.clear()
                status.setText(R.string.receipt_stocks_error)
                status.visibility = View.VISIBLE
                stockSelector.isEnabled = false
                scanButton.isEnabled = false
                retry.visibility = View.VISIBLE
            } finally {
                loadingStocks = false
            }
        }
    }

    private fun startQrScan() {
        if (scanning || loadingStocks) return
        val selected = stockSelector.selectedItemPosition - 1
        val stockId = stocks.getOrNull(selected)?.id
        if (stockId == null) {
            status.setText(R.string.receipt_select_stock)
            status.visibility = View.VISIBLE
            return
        }
        scanning = true
        scanButton.isEnabled = false
        status.visibility = View.GONE
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            val task = GmsBarcodeScanning.getClient(this, options).startScan()
            task.addOnSuccessListener(this) { barcode ->
                scanning = false
                scanButton.isEnabled = true
                val payload = barcode.rawValue?.trim()
                if (payload.isNullOrBlank()) {
                    showStatus(R.string.receipt_invalid_qr)
                    return@addOnSuccessListener
                }
                startActivity(Intent(this, QrCodeCarregandoActivity::class.java)
                    .putExtra(QrReceiptExtras.STOCK_ID, stockId)
                    .putExtra(QrReceiptExtras.QR_PAYLOAD, payload))
            }.addOnFailureListener(this) {
                scanning = false
                scanButton.isEnabled = true
                showStatus(R.string.receipt_scan_unavailable)
            }.addOnCanceledListener(this) {
                scanning = false
                scanButton.isEnabled = true
                showStatus(R.string.receipt_scan_cancelled)
            }
        } catch (_: Exception) {
            scanning = false
            scanButton.isEnabled = true
            showStatus(R.string.receipt_scan_unavailable)
        }
    }

    private fun showStatus(message: Int) {
        status.setText(message)
        status.visibility = View.VISIBLE
    }
}

object QrReceiptExtras {
    const val STOCK_ID = "qr_receipt_stock_id"
    const val QR_PAYLOAD = "qr_receipt_payload"
    const val RECEIPT_ID = "qr_receipt_id"
    const val IMPORTED_COUNT = "qr_receipt_imported_count"
    const val IGNORED_COUNT = "qr_receipt_ignored_count"
}
