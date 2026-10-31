package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.ApiError
import retrofit2.Call
import retrofit2.Response
import java.io.IOException

class BffException(val error: ApiError, fallback: String) : IOException(
    "${error.userMessage(fallback)} (HTTP ${error.status ?: "desconhecido"})"
)
class InvalidBffResponseException : IOException("Resposta vazia ou inválida do servidor.")

/** One attempt only. OkHttp never replays mutations; callers decide whether retry is safe. */
fun <T> Call<T>.requireBody(): T {
    val response = execute()
    if (!response.isSuccessful) throw response.failure()
    return response.body() ?: throw InvalidBffResponseException()
}

fun <T> Call<T>.requireCreatedBody(): T {
    val response = execute()
    if (response.code() != 201) {
        if (!response.isSuccessful) throw response.failure()
        throw InvalidBffResponseException()
    }
    return response.body() ?: throw InvalidBffResponseException()
}

fun Call<Void>.requireNoContent() {
    execute().requireNoContent()
}

fun Response<Void>.requireNoContent() {
    if (!isSuccessful) throw failure()
    if (code() != 204) throw InvalidBffResponseException()
}

private fun Response<*>.failure(): BffException = BffException(
    ApiError.parse(this),
    "Falha na operação (HTTP ${code()})."
)

fun unavailableMobileContract(): Nothing = throw UnsupportedOperationException(
    "Esta função ainda não possui contrato /mobile publicado no BFF."
)
