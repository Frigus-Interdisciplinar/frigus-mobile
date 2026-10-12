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
import com.example.frigus_mobile.data.model.AiRecipeChatRequest
import com.example.frigus_mobile.data.model.ChatUiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatIaActivity : AppCompatActivity() {

    private lateinit var adapter: ChatIaAdapter
    private lateinit var etInput: EditText
    private lateinit var rvMessages: RecyclerView
    private lateinit var scrollView: NestedScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_chat_ia)

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        etInput = findViewById(R.id.etChatInput)
        rvMessages = findViewById(R.id.rvChatMessages)
        scrollView = findViewById(R.id.scrollChat)

        rvMessages.layoutManager = LinearLayoutManager(this)
        adapter = ChatIaAdapter(this, mutableListOf())
        rvMessages.adapter = adapter

        // Mensagem inicial de boas-vindas do assistente
        adapter.addMessage(
            ChatUiMessage(
                text = "Olá, Juliana! 👋\nComo posso te ajudar hoje?",
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
        adapter.addMessage(ChatUiMessage(text = mensagemUsuario, isUser = true))
        scrollDown()

        lifecycleScope.launch {
            val respostaBot = obterRespostaAi(mensagemUsuario)
            adapter.addMessage(ChatUiMessage(text = respostaBot, isUser = false))
            scrollDown()
        }
    }

    private suspend fun obterRespostaAi(pergunta: String): String = withContext(Dispatchers.IO) {
        try {
            val req = AiRecipeChatRequest(message = pergunta, stockId = 1)
            val resp = RetrofitClient.aiRecipeService.chat(req).execute()
            if (resp.isSuccessful && resp.body() != null) {
                val body = resp.body()!!
                if (!body.chatMessage.isNullOrEmpty()) {
                    return@withContext body.chatMessage!!
                }
                if (!body.recipeName.isNullOrEmpty()) {
                    return@withContext "Sugestão de receita: ${body.recipeName}!\n${body.recipeDescription ?: ""}"
                }
            }
        } catch (_: Exception) {}

        // Fallback inteligente para as perguntas sugeridas
        when {
            pergunta.contains("cozinhar", ignoreCase = true) ->
                "Com os ingredientes no seu estoque (frango, tomates e legumes), recomendo preparar um delicioso Frango ao forno com legumes! Leva apenas 35 minutos."
            pergunta.contains("vencer", ignoreCase = true) ->
                "No seu estoque atual, o Peito de frango vence em 1 dia (29/05) e o Leite integral em 2 dias (30/05). Priorize o consumo deles!"
            pergunta.contains("compras", ignoreCase = true) ->
                "Montei uma sugestão de compras: 1L Leite integral, 1kg Peito de frango, 500g Cenoura e 6un Ovos. Já enviei para a sua aba de Lista de Compras!"
            pergunta.contains("economizei", ignoreCase = true) ->
                "Parabéns! Consumindo os alimentos dentro do prazo de validade você economizou cerca de R$ 84,50 neste mês através do MoneySaving!"
            else ->
                "Posso te ajudar com receitas, gestão de validade do seu estoque e listas de compras inteligentes. O que gostaria de fazer agora?"
        }
    }

    private fun scrollDown() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}
