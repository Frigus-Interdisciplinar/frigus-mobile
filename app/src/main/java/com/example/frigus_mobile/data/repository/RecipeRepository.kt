package com.example.frigus_mobile.data.repository

import android.content.Context
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.RecipeSuggestionResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RecipeRepository(context: Context) {
    private val api = RetrofitClient.recipeService
    private val session = com.example.frigus_mobile.data.local.SessionManager(context.applicationContext)

    suspend fun list(page: Int = 0, size: Int = 20, query: String? = null):
        PageResponse<RecipeResponse> {
        val result = api.getAllRecipes(page, size, query).awaitGet()
        validatePage(result, page)
        return result
    }

    private fun validatePage(result: PageResponse<RecipeResponse>, page: Int) {
        val content: List<RecipeResponse?>? = result.content
        requireResponse(content != null && result.number == page && result.size > 0 &&
            result.totalPages >= 0 && result.totalElements >= (content?.size ?: 0) &&
            (content?.size ?: 0) <= result.size &&
            (if (result.totalPages == 0) content?.isEmpty() == true && result.totalElements == 0L
                else result.number < result.totalPages && (!content.isNullOrEmpty() || result.number == result.totalPages - 1)) &&
            content.all { it != null && (it.recipeId ?: 0) > 0 })
    }

    /** M18 supplies saved assistant recommendations; M14 adds current stock matches. Both are reads. */
    suspend fun suggestions(stockId: Int? = null): List<RecipeResponse> {
        val resolvedStock = stockId ?: run {
            val home = RetrofitClient.stockService.getInventoryHome(session.activeGroupId).awaitGet()
            requireResponse(home.groupId != null && (session.activeGroupId == null || home.groupId == session.activeGroupId))
            home.stocks?.singleOrNull()?.id
        }
        val saved = resolvedStock?.let { api.getSuggestionsByStock(it).awaitGet() }.orEmpty()
        requireResponse(saved.all { (it.recipeId ?: 0) > 0 })
        val result: List<RecipeSuggestionResponse?> = api.getSuggestions(stockId = resolvedStock, limit = 10).awaitGet()
        requireResponse(result.all { it != null && (it.recipe?.recipeId ?: 0) > 0 })
        val computed = result.map { suggestion ->
            suggestion!!.recipe!!.copy(
                matchedIngredients = suggestion.matchedIngredients,
                missingIngredients = suggestion.missingIngredients,
                score = suggestion.score,
                availability = suggestion.availability
            )
        }
        val computedById = computed.associateBy { it.recipeId }
        val enrichedSaved = saved.map { recipe ->
            val current = computedById[recipe.recipeId]
            recipe.copy(favorite = current?.favorite ?: recipe.favorite,
                availability = current?.availability ?: recipe.availability)
        }
        return (enrichedSaved + computed).distinctBy { it.recipeId }
    }

    suspend fun detail(id: Int, stockId: Int? = null): RecipeResponse {
        val result = api.getRecipe(id, stockId).awaitGet()
        requireResponse(id > 0 && result.recipeId == id)
        return result
    }

    suspend fun favorites(page: Int = 0, size: Int = 20): PageResponse<RecipeResponse> {
        val result = api.getFavorites(page, size).awaitGet()
        validatePage(result, page)
        return result
    }

    suspend fun favorite(id: Int, enabled: Boolean) {
        requireResponse(id > 0)
        if (enabled) api.addFavorite(id).awaitNoContent() else api.removeFavorite(id).awaitNoContent()
    }

    private fun requireResponse(valid: Boolean) {
        if (!valid) throw BffException(ApiError(code = "INVALID_RESPONSE"), "")
    }

    private suspend fun <T : Any> Call<T>.awaitGet(): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (!continuation.isActive) return
                val body = response.body()
                when {
                    response.code() != 200 -> continuation.resumeWithException(BffException(ApiError.parse(response), ""))
                    body == null -> continuation.resumeWithException(BffException(ApiError(code = "INVALID_RESPONSE"), ""))
                    else -> continuation.resume(body)
                }
            }
            override fun onFailure(call: Call<T>, failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        })
    }

    private suspend fun Call<Void>.awaitNoContent(): Unit = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (!continuation.isActive) return
                when {
                    response.code() == 204 -> continuation.resume(Unit)
                    response.isSuccessful -> continuation.resumeWithException(BffException(ApiError(code = "INVALID_RESPONSE"), ""))
                    else -> continuation.resumeWithException(BffException(ApiError.parse(response), ""))
                }
            }
            override fun onFailure(call: Call<Void>, failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        })
    }
}
