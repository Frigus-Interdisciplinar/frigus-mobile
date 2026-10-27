package com.example.frigus_mobile.ui.notifications

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity

class NotificacoesActivity : AppCompatActivity() {

    private lateinit var adapter: NotificacaoAdapter
    private lateinit var viewModel: NotificacoesViewModel
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessionManager(this).isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_notificacoes)
        viewModel = ViewModelProvider(this, NotificacoesViewModel.Factory())[NotificacoesViewModel::class.java]

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<View>(R.id.btnLimparTodas).setOnClickListener { viewModel.clearAll() }
        findViewById<View>(R.id.btnCarregarMais).setOnClickListener { viewModel.loadNext() }
        findViewById<View>(R.id.btnRetryNotifications).setOnClickListener { viewModel.loadFirst() }

        adapter = NotificacaoAdapter(emptyList()) { viewModel.markAsRead(it) }
        findViewById<RecyclerView>(R.id.rvNotificacoes).apply {
            layoutManager = LinearLayoutManager(this@NotificacoesActivity)
            adapter = this@NotificacoesActivity.adapter
        }
        viewModel.state.observe(this, ::render)
        SessionManager.authenticated.observe(this) {
            if (!SessionManager(this).isLoggedIn && !leaving) {
                leaving = true
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }
        if (viewModel.state.value?.page == -1 && viewModel.state.value?.loading == false) viewModel.loadFirst()
    }

    private fun render(state: NotificationUiState) {
        adapter.updateList(state.items)
        findViewById<View>(R.id.progressNotificacoes).visibility =
            if (state.loading && state.items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutEmptyState).visibility =
            if (!state.loading && !state.error && state.items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutErrorState).visibility =
            if (!state.loading && state.error && state.items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvNotificationsError).visibility =
            if (state.error && state.items.isNotEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rvNotificacoes).visibility =
            if (state.items.isNotEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnCarregarMais).apply {
            visibility = if (state.hasMore) View.VISIBLE else View.GONE
            isEnabled = !state.loading
        }
        findViewById<View>(R.id.btnLimparTodas).isEnabled = !state.loading && state.items.isNotEmpty()
    }
}

