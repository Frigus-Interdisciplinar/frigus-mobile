package com.example.frigus_mobile.ui.notifications

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.repository.NotificationRepository
import com.example.frigus_mobile.utils.NetworkGuard
import kotlinx.coroutines.launch

class NotificacoesActivity : AppCompatActivity() {

    private val repository = NotificationRepository()
    private lateinit var adapter: NotificacaoAdapter
    private lateinit var layoutEmptyState: View
    private lateinit var rvNotificacoes: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notificacoes)

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        rvNotificacoes = findViewById(R.id.rvNotificacoes)
        rvNotificacoes.layoutManager = LinearLayoutManager(this)

        adapter = NotificacaoAdapter(this, emptyList())
        rvNotificacoes.adapter = adapter

        findViewById<View>(R.id.btnLimparTodas).setOnClickListener {
            adapter.updateList(emptyList())
            layoutEmptyState.visibility = View.VISIBLE
            rvNotificacoes.visibility = View.GONE
        }

        carregarNotificacoes()
    }

    private fun carregarNotificacoes() {
        if (!NetworkGuard.ensureConnected(this) { carregarNotificacoes() }) return

        lifecycleScope.launch {
            try {
                val list = repository.getNotifications()
                if (list.isEmpty()) {
                    layoutEmptyState.visibility = View.VISIBLE
                    rvNotificacoes.visibility = View.GONE
                } else {
                    layoutEmptyState.visibility = View.GONE
                    rvNotificacoes.visibility = View.VISIBLE
                    adapter.updateList(list)
                }
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(this@NotificacoesActivity, e.message) {
                    carregarNotificacoes()
                }
            }
        }
    }
}

