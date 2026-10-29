package com.example.frigus_mobile.ui.stock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.model.StockProductUpdateRequest
import com.example.frigus_mobile.data.repository.StockRepository
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

data class StockLotState(
    val lot: StockProductResponse? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: ApiError? = null,
    val uncertain: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false
)

class StockLotViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = StockRepository()
    private val session = SessionManager(application)
    private val mutableState = MutableLiveData<StockLotState>().apply {
        if (savedState.get<String>("pending_action") != null) value = StockLotState(uncertain = true)
    }
    val state: LiveData<StockLotState> = mutableState
    private var operation: Job? = null
    var changed: Boolean
        get() = savedState["changed"] ?: false
        private set(value) { savedState["changed"] = value }

    fun draft(field: String, fallback: String) = savedState.get<String>("draft.$field") ?: fallback
    fun edit(field: String, value: String) { savedState["draft.$field"] = value }
    fun loadIfNeeded(id: Int) { if (mutableState.value == null) load(id) }

    fun load(id: Int, reconcile: Boolean = false) {
        val current = mutableState.value ?: StockLotState()
        if (!session.isLoggedIn || current.loading || current.busy) return
        if (id <= 0) {
            mutableState.value = StockLotState(error = ApiError(code = "INVALID_RESPONSE"))
            return
        }
        operation?.cancel()
        val generation = session.state.generation
        mutableState.value = current.copy(loading = true, lot = null, error = null)
        operation = viewModelScope.launch {
            try {
                val lot = repository.getStockProductById(id)
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                if (reconcile) {
                    savedState.keys().filter { it.startsWith("draft.") }.forEach { savedState.remove<String>(it) }
                    savedState.remove<String>("pending_action")
                }
                mutableState.value = StockLotState(lot = lot, uncertain = current.uncertain && !reconcile)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation) {
                    val error = failureError(failure)
                    if (error.status == 404) {
                        changed = true
                        savedState.remove<String>("pending_action")
                    }
                    mutableState.value = current.copy(loading = false, lot = null, error = error,
                        uncertain = current.uncertain && error.status != 404)
                }
            }
        }
    }

    fun save(id: Int, request: StockProductUpdateRequest) = mutate(id, request)
    fun delete(id: Int) = mutate(id, null)

    private fun mutate(id: Int, request: StockProductUpdateRequest?) {
        val current = mutableState.value ?: return
        if (!session.isLoggedIn || current.loading || current.busy || current.uncertain || current.lot?.id != id) return
        val generation = session.state.generation
        changed = true
        savedState["pending_action"] = if (request == null) "DELETE" else "PUT"
        mutableState.value = current.copy(busy = true, error = null)
        operation = viewModelScope.launch {
            try {
                val result = if (request == null) { repository.deleteStockProduct(id); null }
                    else repository.updateStockProduct(id, request)
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                changed = true
                savedState.remove<String>("pending_action")
                mutableState.value = current.copy(lot = result ?: current.lot, busy = false,
                    saved = request != null, deleted = request == null, error = null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation) {
                    val error = failureError(failure)
                    val uncertain = error.status == null || error.status == 408 || error.status!! >= 500
                    if (uncertain) changed = true else savedState.remove<String>("pending_action")
                    mutableState.value = current.copy(busy = false, error = error, uncertain = uncertain,
                        lot = if (error.status == 404) null else current.lot)
                }
            }
        }
    }

    private fun failureError(failure: Exception) = when (failure) {
        is BffException -> failure.error
        is JsonParseException, is MalformedJsonException -> ApiError(code = "INVALID_RESPONSE")
        is IOException -> ApiError(code = "NETWORK_ERROR")
        else -> ApiError(code = "INVALID_RESPONSE")
    }
}
