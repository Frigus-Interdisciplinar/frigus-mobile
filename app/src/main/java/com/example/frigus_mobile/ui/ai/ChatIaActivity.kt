package com.example.frigus_mobile.ui.ai

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ChatUiMessage
import com.example.frigus_mobile.data.repository.AiChatRepository
import com.example.frigus_mobile.utils.NetworkGuard
import kotlinx.coroutines.launch

class ChatIaActivity : AppCompatActivity() {

    private lateinit var adapter: ChatIaAdapter
    private lateinit var etInput: EditText
    private lateinit var rvMessages: RecyclerView
    private lateinit var scrollView: NestedScrollView
    private lateinit var sessionManager: SessionManager
    private lateinit var aiChatRepository: AiChatRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_chat_ia)

        sessionManager = SessionManager(this)
        aiChatRepository = AiChatRepository(this)

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        etInput = findViewById(R.id.etChatInput)
        rvMessages = findViewById(R.id.rvChatMessages)
        scrollView = findViewById(R.id.scrollChat)

        rvMessages.layoutManager = LinearLayoutManager(this)
        adapter = ChatIaAdapter(this, mutableListOf())
        rvMessages.adapter = adapter

        val primeiroNome = sessionManager.userName?.split(" ")?.firstOrNull() ?: "usuário"
        adapter.addMessage(
            ChatUiMessage(
                text = "Olá, $primeiroNome! 👋\nComo posso te ajudar hoje com seus alimentos e receitas?",
                isUser = false
            )
        )

        findViewById<View>(R.id.btnEnviarMensagem).setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                enviarMensagem(text)
                etInput.setText("")
            }
        }

        // Sugestões Rápidas
        findViewById<View>(R.id.sugestaoCozinhar).setOnClickListener {
            enviarMensagem("O que posso cozinhar hoje com os alimentos do meu estoque?")
        }

        findViewById<View>(R.id.sugestaoVencimento).setOnClickListener {
            enviarMensagem("Quais alimentos estão próximos de vencer no meu estoque?")
        }

        findViewById<View>(R.id.sugestaoListaCompras).setOnClickListener {
            enviarMensagem("Monte uma lista de compras para a semana com base no meu estoque")
        }

        findViewById<View>(R.id.sugestaoEconomia).setOnClickListener {
            enviarMensagem("Quanto economizei evitando desperdício este mês?")
        }
    }

    private fun enviarMensagem(mensagemUsuario: String) {
        if (!NetworkGuard.ensureConnected(this) { enviarMensagem(mensagemUsuario) }) return

        adapter.addMessage(ChatUiMessage(text = mensagemUsuario, isUser = true))
        adapter.addMessage(ChatUiMessage(text = "Pensando...", isUser = false, isLoading = true))
        scrollDown()

        lifecycleScope.launch {
            try {
                val respostaBot = aiChatRepository.sendChatMessage(mensagemUsuario)
                adapter.updateLastMessage(respostaBot, isLoading = false)
                scrollDown()
            } catch (e: Exception) {
                adapter.removeLastMessage()
                NetworkGuard.showBlockingError(this@ChatIaActivity, e.message) {
                    enviarMensagem(mensagemUsuario)
                }
            }
        }
    }

    private fun scrollDown() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}
