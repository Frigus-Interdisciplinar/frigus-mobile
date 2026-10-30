package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.AdvertisementApiService
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.requireBody
import com.example.frigus_mobile.data.api.requireNoContent
import com.example.frigus_mobile.data.model.AdvertisementResponse
import com.example.frigus_mobile.data.model.PageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdvertisementRepository(private val api: AdvertisementApiService = RetrofitClient.advertisementService) {
    suspend fun active(page: Int? = null, size: Int? = null): PageResponse<AdvertisementResponse> =
        withContext(Dispatchers.IO) { api.getActive(page, size).requireBody() }

    suspend fun recordImpression(id: String) = withContext(Dispatchers.IO) {
        api.recordImpression(id).requireNoContent()
    }

    suspend fun recordClick(id: String) = withContext(Dispatchers.IO) {
        api.recordClick(id).requireNoContent()
    }
}
