package com.example.frigus_mobile.ui.qrcode

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.home.MainActivity

class QrCodeConfirmacaoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_confirmacao)

        findViewById<TextView>(R.id.tvQrConfirmacaoTitulo).setText(R.string.receipt_result_title)
        findViewById<TextView>(R.id.tvQrConfirmacaoDescricao).setText(R.string.receipt_result_subtitle)
        val imported = intent.getIntExtra(QrReceiptExtras.IMPORTED_COUNT, -1)
        val ignored = intent.getIntExtra(QrReceiptExtras.IGNORED_COUNT, -1)
        findViewById<TextView>(R.id.tvQrConfirmacaoContagem).apply {
            if (imported >= 0 && ignored >= 0) {
                text = getString(R.string.receipt_result_counts, imported, ignored)
            } else {
                text = getString(R.string.receipt_result_counts_unknown)
            }
        }

        findViewById<View>(R.id.btnVoltar).setOnClickListener { returnToInventory() }
        findViewById<View>(R.id.btnVoltarInicio).setOnClickListener { returnToInventory() }
    }

    private fun returnToInventory() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_RECEIPT_IMPORTED, true)
        }
        startActivity(intent)
        finish()
    }
}
