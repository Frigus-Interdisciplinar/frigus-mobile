package com.example.frigus_mobile.data.repository

import android.content.Context
import android.util.Log
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.AiRecipeChatRequest
import com.example.frigus_mobile.data.model.AiRecipeChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiChatRepository(context: Context) {

    private val sessionManager = SessionManager(context)
    private val stockRepository = StockRepository(context)

    // Mantém o ID da sessão da conversa para continuidade do contexto no frigus-ai
    var currentSessionId: String? = null
        private set

    fun resetSession() {
        currentSessionId = null
    }

    /**
     * Envia mensagem para o Chat de IA.
     * Fluxo:
     * 1. Tenta via BFF (POST /mobile/recipes/ai/chat)
     * 2. Se o BFF falhar ou estiver indisponível, tenta via Core API (POST /ai/recipes/chat)
     * 3. Se ambos falharem (502/503/timeout), gera resposta local contextualizada com o estoque
     */
    suspend fun sendChatMessage(userMessage: String): String = withContext(Dispatchers.IO) {
        val stockId = stockRepository.resolveUserStockId()
            ?: return@withContext "Você ainda não possui um estoque ativo vinculado à sua conta. Adicione produtos ou crie um grupo para que a IA possa analisar seus alimentos!"

        val request = AiRecipeChatRequest(
            message = userMessage,
            stockId = stockId,
            sessionId = currentSessionId
        )

        // 1. Tenta enviar prioritariamente pelo BFF
        try {
            Log.d("AiChatRepository", "Enviando mensagem ao BFF (stockId=$stockId, sessionId=$currentSessionId)...")
            val bffResponse = RetrofitClient.bffAiService.chatBff(request)
            if (bffResponse.isSuccessful && bffResponse.body() != null) {
                val body = bffResponse.body()!!
                Log.d("AiChatRepository", "Resposta recebida do BFF com sucesso. sessionId=${body.sessionId}")
                if (!body.sessionId.isNullOrBlank()) {
                    currentSessionId = body.sessionId
                }
                return@withContext formatResponse(body)
            } else {
                val err = bffResponse.errorBody()?.string()
                Log.w("AiChatRepository", "BFF retornou código ${bffResponse.code()}: $err")
                if (bffResponse.code() == 403) {
                    sessionManager.saveActiveGroupAndStock(null, null)
                    return@withContext "Sua conta não possui permissão para acessar este estoque no momento."
                }
            }
        } catch (e: Exception) {
            Log.w("AiChatRepository", "Falha na chamada ao BFF: ${e.message}. Tentando Core API...")
        }

        // 2. Fallback: Tenta enviar diretamente pelo Core API
        try {
            Log.d("AiChatRepository", "Tentando fallback pelo Core API...")
            val coreResponse = RetrofitClient.coreAiService.chatCore(request)
            if (coreResponse.isSuccessful && coreResponse.body() != null) {
                val body = coreResponse.body()!!
                Log.d("AiChatRepository", "Resposta recebida do Core API com sucesso. sessionId=${body.sessionId}")
                if (!body.sessionId.isNullOrBlank()) {
                    currentSessionId = body.sessionId
                }
                return@withContext formatResponse(body)
            } else {
                val err = coreResponse.errorBody()?.string()
                Log.w("AiChatRepository", "Core API retornou código ${coreResponse.code()}: $err")
                if (coreResponse.code() == 403) {
                    sessionManager.saveActiveGroupAndStock(null, null)
                    return@withContext "Sua conta não possui permissão para acessar este estoque no momento."
                }
            }
        } catch (e: Exception) {
            Log.w("AiChatRepository", "Falha no fallback Core API: ${e.message}")
        }

        // 3. Fallback inteligente: gera resposta local com os dados reais do estoque
        Log.i("AiChatRepository", "Serviços remotos de IA indisponíveis no momento. Gerando resposta local baseada no estoque...")
        return@withContext generateLocalStockResponse(userMessage)
    }

    private fun formatResponse(body: AiRecipeChatResponse): String {
        val chatMsg = body.chatMessage?.trim()
        val recipeName = body.recipeName?.trim()
        val instructions = body.recipeInstructions?.trim()

        val sb = StringBuilder()
        if (!chatMsg.isNullOrEmpty()) {
            sb.append(chatMsg)
        }

        // Se uma receita foi gerada ou associada
        if (!recipeName.isNullOrEmpty()) {
            val alreadyMentioned = chatMsg?.contains(recipeName, ignoreCase = true) == true
            if (!alreadyMentioned) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append("🍽️ **Receita sugerida:** $recipeName")
            }
            if (!body.recipeDescription.isNullOrBlank() && !alreadyMentioned) {
                sb.append("\n${body.recipeDescription.trim()}")
            }
            if (!instructions.isNullOrBlank() && !sb.contains(instructions)) {
                sb.append("\n\n📝 **Modo de preparo:**\n$instructions")
            }
        } else if (sb.isEmpty()) {
            sb.append("Mensagem processada pelo assistente.")
        }

        return sb.toString()
    }

    private suspend fun generateLocalStockResponse(pergunta: String): String {
        val produtos = stockRepository.getStockProducts()
        if (produtos.isEmpty()) {
            return "Seu estoque está vazio no momento. Adicione alimentos ao estoque para receber sugestões personalizadas da IA!"
        }
        val p = pergunta.lowercase()
        val vencendo = produtos.filter { it.productStatus == "NEAR_EXPIRATION" || it.productStatus == "EXPIRED" }

        return when {
            p.contains("venc") || p.contains("validade") -> {
                if (vencendo.isEmpty()) {
                    "Nenhum alimento do seu estoque está próximo do vencimento! Seus ${produtos.size} itens cadastrados estão em dia. 👍"
                } else {
                    val nomes = vencendo.joinToString("\n• ") {
                        "${it.product?.name ?: "Item"} - Vencimento: ${it.expireDate ?: "não informado"}"
                    }
                    "⚠️ Atenção! Você tem ${vencendo.size} alimento(s) vencendo ou vencido(s):\n• $nomes"
                }
            }
            p.contains("cozinh") || p.contains("prepar") || p.contains("receit") || p.contains("almoço") || p.contains("jantar") -> {
                val nomes = produtos.take(5).joinToString(", ") { it.product?.name ?: "Item" }
                "Com base nos itens do seu estoque ($nomes), você pode preparar combinações rápidas ou consultar a aba Receitas para sugestões completas!"
            }
            p.contains("lista") || p.contains("compr") -> {
                val emFalta = produtos.filter { (it.quantity ?: 0) <= (it.minimalQuantity ?: 1) }
                if (emFalta.isEmpty()) {
                    "Seus itens de estoque estão em quantidade suficiente. Acesse a Lista de Compras para planejar suas próximas compras!"
                } else {
                    val nomes = emFalta.joinToString(", ") { it.product?.name ?: "Item" }
                    "Estes itens estão com estoque baixo e você pode adicionar à sua lista de compras: $nomes."
                }
            }
            p.contains("economi") || p.contains("gasto") || p.contains("dinheiro") -> {
                val valorTotal = produtos.sumOf { (it.quantity ?: 1) * (it.purchaseUnitPrice ?: 0.0) }
                "Você possui atualmente ${produtos.size} produtos gerenciados, totalizando cerca de R$ ${String.format(java.util.Locale.US, "%.2f", valorTotal)} em alimentos preservados contra desperdício!"
            }
            else -> {
                val nomes = produtos.take(5).joinToString(", ") { it.product?.name ?: "Item" }
                "No momento seu estoque possui ${produtos.size} item(ns), incluindo: $nomes.\nVocê pode me perguntar sobre receitas, validades ou dicas de aproveitamento!"
            }
        }
    }
}
