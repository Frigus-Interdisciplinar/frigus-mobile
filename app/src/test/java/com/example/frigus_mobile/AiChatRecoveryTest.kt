package com.example.frigus_mobile

import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.model.*
import com.example.frigus_mobile.data.repository.*
import com.example.frigus_mobile.ui.ai.aiChatMutationUncertain
import org.junit.Assert.*
import org.junit.Test

class AiChatRecoveryTest {
    private val group = "123e4567-e89b-12d3-a456-426614174000"
    private val otherGroup = "123e4567-e89b-12d3-a456-426614174001"

    @Test fun infersOnlyTheUnambiguousStockInTheConfirmedGroup() {
        val home = InventoryHomeResponse(groupId = group, stocks = listOf(StockResponse(23, group)))
        assertEquals(23, selectAiChatStock(home, group, null)?.id)
        assertEquals(23, selectAiChatStock(home, group, 23)?.id)
        assertNull(selectAiChatStock(home.copy(stocks = emptyList()), group, null))
    }

    @Test fun neverSilentlyChangesAnExplicitStockOrChoosesBetweenSeveralStocks() {
        val home = InventoryHomeResponse(groupId = group,
            stocks = listOf(StockResponse(23, group), StockResponse(24, group)))
        assertEquals(24, selectAiChatStock(home, group, 24)?.id)
        try {
            selectAiChatStock(home, group, null)
            fail("An ambiguous stock selection must be resolved by the app context")
        } catch (error: AiChatContextException) { assertEquals("AI_STOCK_AMBIGUOUS", error.code) }
        try {
            selectAiChatStock(home, group, 99)
            fail("An invalid active stock must not fall back to another stock")
        } catch (error: AiChatContextException) { assertEquals("AI_STOCK_CHANGED", error.code) }
    }

    @Test fun rejectsInventoryFromAnotherGroup() {
        val home = InventoryHomeResponse(groupId = otherGroup, stocks = listOf(StockResponse(23, otherGroup)))
        try { selectAiChatStock(home, group, null); fail("Wrong group") }
        catch (_: InvalidBffResponseException) { }
        try {
            selectAiChatStock(home.copy(groupId = group), group, null)
            fail("A stock explicitly owned by another group is invalid")
        } catch (_: InvalidBffResponseException) { }
    }

    @Test fun requiresARealReplyAndAConversationIdentifier() {
        assertFalse(validAiChatReply(AiRecipeChatResponse()))
        assertFalse(validAiChatReply(AiRecipeChatResponse(sessionId = "server-session")))
        assertFalse(validAiChatReply(AiRecipeChatResponse(chatMessage = "Resposta sem contexto")))
        assertTrue(validAiChatReply(AiRecipeChatResponse(sessionId = "server-session", chatMessage = "Resposta real")))
        assertTrue(validAiChatReply(AiRecipeChatResponse(sessionId = "server-session", recipeId = 4,
            recipeName = "Receita retornada", recipeInstructions = "Instruções retornadas")))
    }

    @Test fun dietaryContextIsAppendedOnlyToTheRequestAndRespectsTheBffLimit() {
        assertEquals("Pergunta", composeAiChatMessage(" Pergunta ", ""))
        assertEquals("Pergunta\n\nPreferência alimentar", composeAiChatMessage("Pergunta", "Preferência alimentar"))
        try { composeAiChatMessage("x".repeat(3001), ""); fail("UI message limit") }
        catch (_: AiChatInputException) { }
        try { composeAiChatMessage("x".repeat(3000), "y".repeat(1000)); fail("Combined BFF limit") }
        catch (_: AiChatInputException) { }
    }

    @Test fun assistantWireContractCarriesCurrentPreferencesAndExplicitSavedRecipeIds() {
        val gson = com.google.gson.Gson()
        val request = gson.toJsonTree(AiRecipeChatRequest("Pergunta", 23, "conversa", listOf("VEGAN"))).asJsonObject
        assertEquals(23, request.get("stockId").asInt)
        assertEquals("VEGAN", request.getAsJsonArray("foodPreferences")[0].asString)
        val reply = gson.fromJson("""{"sessionId":"conversa","chatMessage":"Receita salva","recipeIds":[4,9]}""",
            AiRecipeChatResponse::class.java)
        assertEquals(listOf(4, 9), reply.recipeIds)
        assertTrue(validAiChatReply(reply))
        assertFalse(validAiChatReply(reply.copy(recipeIds = listOf(-1))))
    }

    @Test fun uncertainRepliesCannotBeRetriedAsDefinitiveRejections() {
        assertTrue(aiChatMutationUncertain(ApiError(code = "NETWORK_ERROR")))
        assertTrue(aiChatMutationUncertain(ApiError(code = "INVALID_RESPONSE")))
        assertTrue(aiChatMutationUncertain(ApiError(status = 408)))
        assertTrue(aiChatMutationUncertain(ApiError(status = 503)))
        assertFalse(aiChatMutationUncertain(ApiError(status = 400)))
        assertFalse(aiChatMutationUncertain(ApiError(status = 403)))
        assertFalse(aiChatMutationUncertain(ApiError(code = "AI_INPUT_TOO_LONG")))
    }
}
