package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.RecipeResponse
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

/** M18 returns the suggestions already saved for one stock; it does not create a conversation. */
class AiSuggestionsRepository {
    fun load(stockId: Int, result: (List<RecipeResponse>?, ApiError?) -> Unit): Call<List<RecipeResponse>> {
        require(stockId > 0)
        val call = RetrofitClient.recipeService.getSuggestionsByStock(stockId)
        call.enqueue(object : Callback<List<RecipeResponse>> {
            override fun onResponse(call: Call<List<RecipeResponse>>, response: Response<List<RecipeResponse>>) {
                if (call.isCanceled) return
                val items: List<RecipeResponse?>? = response.body()
                when {
                    response.code() != 200 -> result(null, ApiError.parse(response))
                    items == null || items.any { it == null || (it.recipeId ?: 0) <= 0 } ->
                        result(null, ApiError(code = "INVALID_RESPONSE"))
                    else -> result(items.filterNotNull(), null)
                }
            }

            override fun onFailure(call: Call<List<RecipeResponse>>, failure: Throwable) {
                if (call.isCanceled) return
                val code = when (failure) {
                    is JsonParseException, is MalformedJsonException -> "INVALID_RESPONSE"
                    is IOException -> "NETWORK_ERROR"
                    else -> "INVALID_RESPONSE"
                }
                result(null, ApiError(code = code))
            }
        })
        return call
    }
}
