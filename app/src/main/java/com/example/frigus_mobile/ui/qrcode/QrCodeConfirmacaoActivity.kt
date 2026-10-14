package com.example.frigus_mobile.ui.qrcode

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.home.MainActivity

class QrCodeConfirmacaoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_confirmacao)

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            irParaInicio()
        }

        findViewById<View>(R.id.btnVoltarInicio).setOnClickListener {
            irParaInicio()
        }
    }

    private fun irParaInicio() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}
