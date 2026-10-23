package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.StockApiService
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.InventoryHomeResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID
import java.io.IOException
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException

class InventoryHomeRepository(private val api: StockApiService = RetrofitClient.stockService) {
    fun load(result: (InventoryHomeResponse?, ApiError?) -> Unit): Call<InventoryHomeResponse> {
        val call = api.getInventoryHome()
        call.enqueue(object : Callback<InventoryHomeResponse> {
            override fun onResponse(call: Call<InventoryHomeResponse>, response: Response<InventoryHomeResponse>) {
                if (call.isCanceled) return
                val body = response.body()
                when {
                    response.code() != 200 -> result(null, ApiError.parse(response))
                    body == null || !valid(body) -> result(null, ApiError(code = "INVALID_RESPONSE"))
                    else -> result(body, null)
                }
            }

            override fun onFailure(call: Call<InventoryHomeResponse>, t: Throwable) {
                if (call.isCanceled) return
                val code = when (t) {
                    is JsonParseException, is MalformedJsonException -> "INVALID_RESPONSE"
                    is IOException -> "NETWORK_ERROR"
                    else -> "INVALID_RESPONSE"
                }
                result(null, ApiError(code = code))
            }
        })
        return call
    }

    private fun valid(home: InventoryHomeResponse): Boolean {
        val groupId = home.groupId ?: return false
        val validGroup = try { UUID.fromString(groupId).toString().equals(groupId, ignoreCase = true) }
            catch (_: IllegalArgumentException) { false }
        val stocks = home.stocks ?: return false
        val count = home.summary?.itemCount ?: return false
        return validGroup && count >= 0 && stocks.all { it != null && it.id > 0 } && (stocks.isNotEmpty() || count == 0L)
    }
}
