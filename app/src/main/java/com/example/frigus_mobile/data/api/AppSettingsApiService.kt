package com.example.frigus_mobile.data.api

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface AppSettingsApiService {
    @GET("mobile/core/profile/app-settings") fun load(): Call<JsonObject>
    @PUT("mobile/core/profile/app-settings") fun save(@Body settings: JsonObject): Call<JsonObject>
}
