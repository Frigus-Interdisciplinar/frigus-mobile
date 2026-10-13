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
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.AiRecipeChatRequest
import com.example.frigus_mobile.data.model.ChatUiMessage
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.utils.NetworkGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class ChatIaActivity : AppCompatActivity() {

    private lateinit var adapter: ChatIaAdapter
    private lateinit var etInput: EditText
    private lateinit var rvMessages: RecyclerView
    private lateinit var scrollView: NestedScrollView
    private lateinit var sessionManager: SessionManager
    private lateinit var stockRepository: StockRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_chat_ia)

        sessionManager = SessionManager(this)
        stockRepository = StockRepository(this)

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
                text = "Olá, $primeiroNome! 👋\nComo posso te ajudar hoje?",
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
            enviarMensagem("O que posso cozinhar hoje?")
        }

        findViewById<View>(R.id.sugestaoVencimento).setOnClickListener {
            enviarMensagem("Quais alimentos estão próximos de vencer?")
        }

        findViewById<View>(R.id.sugestaoListaCompras).setOnClickListener {
            enviarMensagem("Monte uma lista de compras para a semana")
        }

        findViewById<View>(R.id.sugestaoEconomia).setOnClickListener {
            enviarMensagem("Quanto economizei esse mês?")
        }
    }

    private fun enviarMensagem(mensagemUsuario: String) {
        if (!NetworkGuard.ensureConnected(this) { enviarMensagem(mensagemUsuario) }) return

        adapter.addMessage(ChatUiMessage(text = mensagemUsuario, isUser = true))
        scrollDown()

        lifecycleScope.launch {
            try {
                val respostaBot = obterRespostaAi(mensagemUsuario)
                adapter.addMessage(ChatUiMessage(text = respostaBot, isUser = false))
                scrollDown()
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(this@ChatIaActivity, e.message) {
                    enviarMensagem(mensagemUsuario)
                }
            }
        }
    }

    private suspend fun obterRespostaAi(pergunta: String): String = withContext(Dispatchers.IO) {
        val stockId = stockRepository.resolveUserStockId()
            ?: return@withContext "Você ainda não possui um estoque ativo vinculado à sua conta. Adicione produtos ou crie um grupo para usar o assistente com seus alimentos!"

        val req = AiRecipeChatRequest(message = pergunta, stockId = stockId)
        val resp = RetrofitClient.aiRecipeService.chat(req).execute()
        if (resp.isSuccessful && resp.body() != null) {
            val body = resp.body()!!
            if (!body.chatMessage.isNullOrEmpty()) {
                return@withContext body.chatMessage
            }
            if (!body.recipeName.isNullOrEmpty()) {
                return@withContext "Sugestão de receita: ${body.recipeName}!\n${body.recipeDescription ?: ""}"
            }
            return@withContext "Receita processada com sucesso."
        } else if (resp.code() == 403) {
            sessionManager.saveActiveGroupAndStock(null, null)
            return@withContext "Sua conta não possui permissão para acessar este estoque ou recurso de IA no momento."
        } else if (resp.code() == 503 || resp.code() == 502 || resp.code() == 500) {
            return@withContext gerarRespostaComDadosDoEstoque(pergunta)
        } else {
            throw IOException("Não foi possível obter resposta da IA no servidor (Código ${resp.code()}).")
        }
    }

    private suspend fun gerarRespostaComDadosDoEstoque(pergunta: String): String {
        val produtos = stockRepository.getStockProducts()
        if (produtos.isEmpty()) {
            return "Seu estoque está vazio no momento. Adicione alimentos ao estoque para receber sugestões personalizadas!"
        }
        val p = pergunta.lowercase()
        val vencendo = produtos.filter { it.productStatus == "NEAR_EXPIRATION" || it.productStatus == "EXPIRED" }
        return when {
            p.contains("venc") -> {
                if (vencendo.isEmpty()) {
                    "Nenhum alimento do seu estoque está próximo do vencimento! Seus ${produtos.size} itens estão em dia."
                } else {
                    val nomes = vencendo.joinToString(", ") { "${it.product?.name ?: "Item"} (${it.expireDate ?: "-"})" }
                    "Atenção: você tem ${vencendo.size} alimento(s) vencendo ou vencido(s): $nomes."
                }
            }
            else -> {
                val nomes = produtos.take(5).joinToString(", ") { it.product?.name ?: "Item" }
                "No momento seu estoque possui ${produtos.size} item(ns), incluindo: $nomes. Consulte a aba Receitas para ver pratos sugeridos!"
            }
        }
    }

    private fun scrollDown() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}

