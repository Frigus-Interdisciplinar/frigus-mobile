package com.example.frigus_mobile.data.repository

import android.content.Context
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.requireBody
import com.example.frigus_mobile.data.api.requireNoContent
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.RecipeSuggestionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RecipeRepository(@Suppress("UNUSED_PARAMETER") context: Context) {
    private val api = RetrofitClient.recipeService

    suspend fun list(page: Int = 0, size: Int = 20, query: String? = null):
        PageResponse<RecipeResponse> {
        val result = api.getAllRecipes(page, size, query).awaitGet()
        val content: List<RecipeResponse?>? = result.content
        requireResponse(content != null && result.number == page && result.size > 0 &&
            result.totalPages >= 0 && result.totalElements >= (content?.size ?: 0) &&
            (content?.size ?: 0) <= result.size &&
            (if (result.totalPages == 0) content?.isEmpty() == true && result.totalElements == 0L
                else result.number < result.totalPages && (!content.isNullOrEmpty() || result.number == result.totalPages - 1)) &&
            content.all { it != null && (it.recipeId ?: 0) > 0 })
        return result
    }

    /** M14 computes suggestions for the current group; no AI call or catalog fallback. */
    suspend fun suggestions(): List<RecipeResponse> {
        val result: List<RecipeSuggestionResponse?> = api.getSuggestions(limit = 10).awaitGet()
        requireResponse(result.all { it != null && (it.recipe?.recipeId ?: 0) > 0 })
        return result.map { suggestion ->
            suggestion!!.recipe!!.copy(
                matchedIngredients = suggestion.matchedIngredients,
                missingIngredients = suggestion.missingIngredients,
                score = suggestion.score,
                availability = suggestion.availability
            )
        }
    }

    suspend fun detail(id: Int, stockId: Int? = null): RecipeResponse {
        val result = api.getRecipe(id, stockId).awaitGet()
        requireResponse(id > 0 && result.recipeId == id)
        return result
    }

    suspend fun favorites(page: Int = 0, size: Int = 20): PageResponse<RecipeResponse> =
        withContext(Dispatchers.IO) { api.getFavorites(page, size).requireBody() }

    suspend fun favorite(id: Int, enabled: Boolean) = withContext(Dispatchers.IO) {
        if (enabled) api.addFavorite(id).requireNoContent() else api.removeFavorite(id).requireNoContent()
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
}
