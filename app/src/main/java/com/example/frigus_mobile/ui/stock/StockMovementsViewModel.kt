package com.example.frigus_mobile.ui.stock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.StockMovementRequest
import com.example.frigus_mobile.data.model.StockMovementResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

data class StockMovementsState(
    val items: List<StockMovementResponse> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val readError: ApiError? = null,
    val busy: Boolean = false,
    val mutationError: ApiError? = null,
    val uncertain: Boolean = false,
    val verifying: Boolean = false,
    val verificationReady: Boolean = false,
    val verifiedLot: StockProductResponse? = null,
    val confirmed: Boolean = false,
    val nextPage: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Long = 0
)

class StockMovementsViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = StockRepository()
    private val session = SessionManager(application)
    private val mutableState = MutableLiveData(StockMovementsState(uncertain = savedState.get<Boolean>(PENDING) == true))
    val state: LiveData<StockMovementsState> = mutableState
    private var readJob: Job? = null
    private var writeJob: Job? = null

    val requiresRefreshOnExit: Boolean get() = savedState.get<Boolean>(CHANGED) == true || savedState.get<Boolean>(PENDING) == true

    fun loadIfNeeded(id: Int) { if (mutableState.value?.loaded != true) loadFirst(id) }
    fun loadFirst(id: Int) = loadPage(id, 0)
    fun loadMore(id: Int) {
        val current = mutableState.value ?: return
        if (current.loaded && current.nextPage < current.totalPages) loadPage(id, current.nextPage)
    }

    private fun loadPage(id: Int, page: Int) {
        val current = mutableState.value ?: return
        if (!session.isLoggedIn || id <= 0 || current.loading || current.loadingMore || current.busy || current.verifying) return
        readJob?.cancel()
        val generation = session.state.generation
        mutableState.value = current.copy(loading = page == 0, loadingMore = page > 0, readError = null)
        readJob = viewModelScope.launch {
            try {
                val result = repository.getMovements(id, page, PAGE_SIZE)
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                mutableState.value = (mutableState.value ?: current).copy(
                    items = if (page == 0) result.content else current.items + result.content,
                    loaded = true, loading = false, loadingMore = false, readError = null,
                    nextPage = page + 1, totalPages = result.totalPages, totalElements = result.totalElements)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation)
                    mutableState.value = (mutableState.value ?: current).copy(
                        loading = false, loadingMore = false, readError = failureError(failure))
            }
        }
    }

    fun submit(id: Int, request: StockMovementRequest) {
        val current = mutableState.value ?: return
        if (!session.isLoggedIn || id <= 0 || current.busy || current.loading || current.loadingMore ||
            current.uncertain || current.verifying ||
            request.movementType !in TYPES || request.quantity < 0 ||
            (request.movementType != "ADJUSTMENT" && request.quantity == 0) ||
            (request.observation?.length ?: 0) > 2000) return
        savedState[PENDING] = true
        savedState[CHANGED] = true
        mutableState.value = current.copy(busy = true, mutationError = null, verificationReady = false)
        val generation = session.state.generation
        writeJob = viewModelScope.launch {
            try {
                repository.createMovement(id, request)
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                savedState.remove<Boolean>(PENDING)
                mutableState.value = (mutableState.value ?: current).copy(busy = false, confirmed = true,
                    mutationError = null, uncertain = false)
                loadFirst(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                val error = failureError(failure)
                val uncertain = error.status == null || error.status == 408 || error.status!! >= 500 ||
                    error.code == "INVALID_RESPONSE"
                if (!uncertain) savedState.remove<Boolean>(PENDING)
                mutableState.value = (mutableState.value ?: current).copy(busy = false,
                    mutationError = error, uncertain = uncertain, verificationReady = false)
            }
        }
    }

    fun verify(id: Int) {
        val current = mutableState.value ?: return
        if (!session.isLoggedIn || id <= 0 || !current.uncertain || current.verifying || current.busy) return
        readJob?.cancel()
        val generation = session.state.generation
        mutableState.value = current.copy(verifying = true, verificationReady = false, readError = null)
        readJob = viewModelScope.launch {
            try {
                val lot = repository.getStockProductById(id)
                val page: PageResponse<StockMovementResponse> = repository.getMovements(id, 0, PAGE_SIZE)
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                mutableState.value = (mutableState.value ?: current).copy(items = page.content,
                    loaded = true, loading = false, loadingMore = false, readError = null,
                    verifying = false, verificationReady = true, verifiedLot = lot,
                    nextPage = 1, totalPages = page.totalPages, totalElements = page.totalElements)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation)
                    mutableState.value = (mutableState.value ?: current).copy(verifying = false,
                        verificationReady = false, readError = failureError(failure))
            }
        }
    }

    fun consumeVerifiedLot(): StockProductResponse? {
        val current = mutableState.value ?: return null
        mutableState.value = current.copy(verifiedLot = null)
        return current.verifiedLot
    }

    fun acknowledgeVerification() {
        val current = mutableState.value ?: return
        if (!current.uncertain || !current.verificationReady) return
        savedState.remove<Boolean>(PENDING)
        mutableState.value = current.copy(uncertain = false, verificationReady = false, mutationError = null)
    }

    fun consumeConfirmation(): Boolean {
        val current = mutableState.value ?: return false
        if (!current.confirmed) return false
        mutableState.value = current.copy(confirmed = false)
        return true
    }

    private fun failureError(failure: Exception) = when (failure) {
        is BffException -> failure.error
        is InvalidBffResponseException, is JsonParseException, is MalformedJsonException -> ApiError(code = "INVALID_RESPONSE")
        is IOException -> ApiError(code = "NETWORK_ERROR")
        else -> ApiError(code = "INVALID_RESPONSE")
    }

    companion object {
        private const val PENDING = "pending_movement"
        private const val CHANGED = "movement_changed"
        private const val PAGE_SIZE = 20
        private val TYPES = setOf("IN", "OUT", "ADJUSTMENT")
    }
}
