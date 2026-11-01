package com.example.frigus_mobile.data.api

import retrofit2.Call
import retrofit2.http.POST

data class ShoppingFirebaseCredential(val uid: String?, val projectId: String?, val customToken: String?)

interface FirebaseSessionApiService {
    @POST("mobile/firebase/session")
    fun create(): Call<ShoppingFirebaseCredential>
}
