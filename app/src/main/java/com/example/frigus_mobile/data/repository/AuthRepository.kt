package com.example.frigus_mobile.data.repository

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.api.AuthApiService
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.Resource
import com.example.frigus_mobile.data.model.UserLoginRequest
import com.example.frigus_mobile.data.model.UserLoginResponse
import com.example.frigus_mobile.data.model.UserRegisterRequest
import com.example.frigus_mobile.data.model.UserResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AuthRepository(context: Context) {
    private val apiService: AuthApiService = RetrofitClient.authService
    val sessionManager: SessionManager = SessionManager(context)
    private val gson = Gson()

    fun login(email: String, password: String, liveData: MutableLiveData<Resource<UserLoginResponse>>) {
        liveData.value = Resource.loading()

        val request = UserLoginRequest(email, password)
        apiService.login(request).enqueue(object : Callback<UserLoginResponse> {
            override fun onResponse(call: Call<UserLoginResponse>, response: Response<UserLoginResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    sessionManager.saveTokens(body.accessToken, body.refreshToken)
                    liveData.postValue(Resource.success(body))
                } else {
                    val mensagemErro = extrairMensagemErro(response, "Falha ao autenticar na API. Código: ${response.code()}")
                    liveData.postValue(Resource.error(mensagemErro))
                }
            }

            override fun onFailure(call: Call<UserLoginResponse>, t: Throwable) {
                liveData.postValue(Resource.error("Erro de conexão com o servidor: ${t.message}"))
            }
        })
    }


    fun register(name: String, birthDate: String, email: String, password: String, liveData: MutableLiveData<Resource<UserResponse>>) {
        liveData.value = Resource.loading()

        val request = UserRegisterRequest(name, birthDate, email, password)
        apiService.cadastrar(request).enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    android.util.Log.d("AuthRepository", "Cadastro realizado com sucesso: ${body.email}")
                    liveData.postValue(Resource.success(body))
                } else {
                    val mensagemErro = extrairMensagemErro(response, "Erro no cadastro. Código: ${response.code()}")
                    android.util.Log.e("AuthRepository", "Falha no cadastro [${response.code()}]: $mensagemErro")
                    liveData.postValue(Resource.error(mensagemErro))
                }
            }

            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                android.util.Log.e("AuthRepository", "Falha de conexão com a API no cadastro: ${t.message}", t)
                liveData.postValue(Resource.error("Falha de conexão com a API: ${t.message}"))
            }
        })
    }

    fun sendPasswordReset(email: String, liveData: MutableLiveData<Resource<Void>>) {
        liveData.value = Resource.success(null as Void)
    }

    private fun <T> extrairMensagemErro(response: Response<T>, padrao: String): String {
        try {
            val errorBody = response.errorBody()
            if (errorBody != null) {
                val errorJson = errorBody.string()
                android.util.Log.e("AuthRepository", "Corpo de erro bruto da API [${response.code()}]: $errorJson")
                val obj = gson.fromJson(errorJson, JsonObject::class.java)
                if (obj.has("displayMessage") && !obj.get("displayMessage").isJsonNull) {
                    return obj.get("displayMessage").asString
                }
                if (obj.has("message") && !obj.get("message").isJsonNull) {
                    return obj.get("message").asString
                }
                if (obj.has("errors") && obj.get("errors").isJsonArray) {
                    val arr = obj.getAsJsonArray("errors")
                    if (arr.size() > 0) {
                        val first = arr[0]
                        if (first.isJsonObject && first.asJsonObject.has("defaultMessage")) {
                            return first.asJsonObject.get("defaultMessage").asString
                        }
                    }
                }
                if (obj.has("error") && !obj.get("error").isJsonNull) {
                    return obj.get("error").asString
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Erro ao processar corpo de erro da API: ${e.message}", e)
        }
        return padrao
    }
}
