package com.example.frigus_mobile.ui.qrcode

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.frigus_mobile.R

class QrCodeCarregandoActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable {
        startActivity(Intent(this, QrCodeItensActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_qr_code_carregando)

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            handler.removeCallbacks(runnable)
            finish()
        }

        // Simulação de busca na SEFAZ por 2.5 segundos
        handler.postDelayed(runnable, 2500)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}
