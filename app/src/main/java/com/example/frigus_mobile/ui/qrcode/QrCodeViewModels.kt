package com.example.frigus_mobile.ui.qrcode

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.ReceiptConfirmItem
import com.example.frigus_mobile.data.model.ReceiptItem
import com.example.frigus_mobile.data.model.ReceiptResponse
import com.example.frigus_mobile.data.repository.ReceiptRepository
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

data class ReceiptScanState(
    val loading: Boolean = false,
    val receiptId: String? = null,
    val error: ApiError? = null,
    val uncertain: Boolean = false
)

data class ReceiptLineReview(
    val item: ReceiptItem,
    val include: Boolean,
    val quantity: Int?,
    val expireDate: String?
)

data class ReceiptCompletion(val receiptId: String, val importedCount: Int?, val ignoredCount: Int?)

data class ReceiptReviewState(
    val receipt: ReceiptResponse? = null,
    val lines: List<ReceiptLineReview> = emptyList(),
    val loading: Boolean = false,
    val readError: ApiError? = null,
    val submitting: Boolean = false,
    val confirmError: ApiError? = null,
    val uncertain: Boolean = false,
    val verifying: Boolean = false,
    val completion: ReceiptCompletion? = null
)

object ReceiptConfirmationGuard {
    private const val PREFERENCES = "mobile_receipt_confirmation_guard"

    fun pendingReceiptId(context: Context): String? {
        val owner = ownerKey(context) ?: return null
        return context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(owner, null)
    }

    fun setPending(context: Context, receiptId: String) {
        val owner = ownerKey(context) ?: return
        check(context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(owner, receiptId).commit())
    }

    fun clear(context: Context, receiptId: String) {
        val owner = ownerKey(context) ?: return
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getString(owner, null) == receiptId) preferences.edit().remove(owner).commit()
    }

    private fun ownerKey(context: Context): String? {
        val session = SessionManager(context)
        val owner = session.state.user?.id?.takeIf(String::isNotBlank)
            ?: session.userEmail?.trim()?.lowercase()?.takeIf(String::isNotBlank)
        return owner?.let { "owner:$it" }
    }
}

class QrReceiptScanViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = ReceiptRepository()
    private val session = SessionManager(application)
    private val mutableState = MutableLiveData(ReceiptScanState(
        receiptId = savedState.get<String>(RECEIPT_ID), uncertain = savedState.get<Boolean>(PENDING) == true))
    val state: LiveData<ReceiptScanState> = mutableState

    fun scan(stockId: Int, payload: String, explicitRetry: Boolean = false) {
        val current = mutableState.value ?: ReceiptScanState()
        if (!session.isLoggedIn || stockId <= 0 || payload.isBlank() || current.loading || current.receiptId != null) return
        if (savedState.get<Boolean>(PENDING) == true && !explicitRetry) {
            mutableState.value = current.copy(uncertain = true)
            return
        }
        savedState[PENDING] = true
        val generation = session.state.generation
        mutableState.value = current.copy(loading = true, error = null, uncertain = false)
        viewModelScope.launch {
            try {
                val result = repository.scan(stockId, payload)
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                val receiptId = result.receiptId ?: throw InvalidBffResponseException()
                savedState.remove<Boolean>(PENDING)
                savedState[RECEIPT_ID] = receiptId
                mutableState.value = (mutableState.value ?: current).copy(
                    loading = false, receiptId = receiptId, error = null, uncertain = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                val error = failureError(failure)
                val uncertain = isUncertain(error)
                if (!uncertain) savedState.remove<Boolean>(PENDING)
                mutableState.value = (mutableState.value ?: current).copy(
                    loading = false, error = error, uncertain = uncertain)
            }
        }
    }

    fun retry(stockId: Int, payload: String) {
        if (mutableState.value?.loading == true || mutableState.value?.receiptId != null) return
        savedState.remove<Boolean>(PENDING)
        scan(stockId, payload, explicitRetry = true)
    }

    private fun failureError(failure: Exception) = when (failure) {
        is BffException -> failure.error
        is InvalidBffResponseException, is JsonParseException, is MalformedJsonException -> ApiError(code = "INVALID_RESPONSE")
        is IOException -> ApiError(code = "NETWORK_ERROR")
        else -> ApiError(code = "INVALID_RESPONSE")
    }

    private fun isUncertain(error: ApiError) = error.status == null || error.status == 408 ||
        error.status?.let { it >= 500 } == true || error.code == "INVALID_RESPONSE"

    companion object {
        private const val PENDING = "receipt_scan_pending"
        private const val RECEIPT_ID = "receipt_scan_id"
    }
}

class ReceiptReviewViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = ReceiptRepository()
    private val session = SessionManager(application)
    private val mutableState = MutableLiveData(ReceiptReviewState(
        uncertain = savedState.get<Boolean>(PENDING_CONFIRM) == true ||
            ReceiptConfirmationGuard.pendingReceiptId(application) != null))
    val state: LiveData<ReceiptReviewState> = mutableState

    fun loadIfNeeded(receiptId: String) {
        if (mutableState.value?.receipt == null && mutableState.value?.loading != true) load(receiptId)
    }

    fun load(receiptId: String) = read(receiptId, verifying = false)

    fun verify(receiptId: String) {
        val current = mutableState.value ?: return
        if (!current.uncertain || current.verifying || current.submitting) return
        read(receiptId, verifying = true)
    }

    private fun read(receiptId: String, verifying: Boolean) {
        val current = mutableState.value ?: ReceiptReviewState()
        if (!session.isLoggedIn || current.loading || current.verifying || receiptId.isBlank()) return
        savedState[RECEIPT_ID] = receiptId
        val generation = session.state.generation
        mutableState.value = current.copy(
            loading = !verifying, verifying = verifying, readError = null)
        viewModelScope.launch {
            try {
                val receipt = repository.get(receiptId)
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                val pending = savedState.get<Boolean>(PENDING_CONFIRM) == true ||
                    ReceiptConfirmationGuard.pendingReceiptId(getApplication()) == receiptId
                if (pending && receipt.status == ReceiptRepository.IMPORTED) {
                    val importedCount = savedState.get<Int>(EXPECTED_IMPORTED)
                    val ignoredCount = savedState.get<Int>(EXPECTED_IGNORED)
                    val completion = ReceiptCompletion(receiptId,
                        importedCount.takeIf { ignoredCount != null },
                        ignoredCount.takeIf { importedCount != null })
                    clearPendingConfirmation()
                    mutableState.value = (mutableState.value ?: current).copy(
                        receipt = receipt, loading = false, verifying = false,
                        readError = null, uncertain = false, completion = completion)
                    return@launch
                }
                val terminalFailure = pending && receipt.status == ReceiptRepository.FAILED
                if (terminalFailure) clearPendingConfirmation()
                val lines = if (receipt.status == ReceiptRepository.REVIEW_REQUIRED)
                    receipt.items.orEmpty().filterNotNull().map(::initialLineReview) else emptyList()
                mutableState.value = (mutableState.value ?: current).copy(
                    receipt = receipt, lines = lines, loading = false, verifying = false,
                    readError = null, uncertain = pending && !terminalFailure, confirmError = if (pending && !terminalFailure)
                        ApiError(code = "CONFIRMATION_UNCERTAIN") else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation)
                    mutableState.value = (mutableState.value ?: current).copy(
                        loading = false, verifying = false, readError = failureError(failure))
            }
        }
    }

    fun setIncluded(lineId: String, included: Boolean): Boolean {
        val current = mutableState.value ?: return false
        if (current.uncertain || current.submitting) return false
        val index = current.lines.indexOfFirst { it.item.lineId == lineId }
        if (index < 0) return false
        val line = current.lines[index]
        if (included && ((line.item.matchedProduct?.productId ?: 0) <= 0 || (line.quantity ?: 0) <= 0)) return false
        val updated = current.lines.toMutableList()
        updated[index] = line.copy(include = included)
        mutableState.value = current.copy(lines = updated, confirmError = null)
        return true
    }

    fun adjustQuantity(lineId: String, delta: Int): Boolean {
        val current = mutableState.value ?: return false
        if (current.uncertain || current.submitting) return false
        val index = current.lines.indexOfFirst { it.item.lineId == lineId }
        if (index < 0) return false
        val line = current.lines[index]
        val quantity = line.quantity ?: return false
        val changed = quantity.toLong() + delta
        if (changed !in 1..Int.MAX_VALUE.toLong()) return false
        val updated = current.lines.toMutableList()
        updated[index] = line.copy(quantity = changed.toInt())
        mutableState.value = current.copy(lines = updated, confirmError = null)
        return true
    }

    fun setExpireDate(lineId: String, value: String): Boolean {
        val current = mutableState.value ?: return false
        if (current.uncertain || current.submitting || !isIsoDate(value)) return false
        val index = current.lines.indexOfFirst { it.item.lineId == lineId }
        if (index < 0) return false
        val updated = current.lines.toMutableList()
        updated[index] = updated[index].copy(expireDate = value)
        mutableState.value = current.copy(lines = updated, confirmError = null)
        return true
    }

    fun request(): List<ReceiptConfirmItem>? {
        val lines = mutableState.value?.lines ?: return null
        if (lines.isEmpty()) return null
        val result = ArrayList<ReceiptConfirmItem>(lines.size)
        for (line in lines) {
            val lineId = line.item.lineId ?: return null
            if (!line.include) {
                result += ReceiptConfirmItem(lineId, IGNORE)
                continue
            }
            val productId = line.item.matchedProduct?.productId?.takeIf { it > 0 } ?: return null
            val quantity = line.quantity?.takeIf { it > 0 } ?: return null
            val expireDate = line.expireDate?.takeIf(::isIsoDate) ?: return null
            result += ReceiptConfirmItem(lineId, IMPORT, productId, quantity, expireDate)
        }
        return result
    }

    fun submit(receiptId: String, request: List<ReceiptConfirmItem>) {
        val current = mutableState.value ?: return
        if (!session.isLoggedIn || receiptId.isBlank() || current.submitting || current.uncertain ||
            current.receipt?.status != ReceiptRepository.REVIEW_REQUIRED || request.isEmpty()) return
        val importCount = request.count { it.action == IMPORT }
        val ignoredCount = request.count { it.action == IGNORE }
        savedState[PENDING_CONFIRM] = true
        savedState[EXPECTED_IMPORTED] = importCount
        savedState[EXPECTED_IGNORED] = ignoredCount
        ReceiptConfirmationGuard.setPending(getApplication(), receiptId)
        val generation = session.state.generation
        mutableState.value = current.copy(submitting = true, confirmError = null, uncertain = false)
        viewModelScope.launch {
            try {
                val result = repository.confirm(receiptId, request)
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                clearPendingConfirmation()
                mutableState.value = (mutableState.value ?: current).copy(
                    submitting = false, uncertain = false, confirmError = null,
                    completion = ReceiptCompletion(receiptId,
                        result.importedItems.orEmpty().size, result.ignoredLineIds.orEmpty().size))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                val error = failureError(failure)
                val uncertain = isUncertain(error) || error.code == "ALREADY_IMPORTED"
                if (!uncertain) clearPendingConfirmation()
                mutableState.value = (mutableState.value ?: current).copy(
                    submitting = false, uncertain = uncertain, confirmError = error)
            }
        }
    }

    fun consumeCompletion(): ReceiptCompletion? {
        val current = mutableState.value ?: return null
        val completion = current.completion ?: return null
        mutableState.value = current.copy(completion = null)
        return completion
    }

    private fun clearPendingConfirmation() {
        savedState.get<String>(RECEIPT_ID)?.let { ReceiptConfirmationGuard.clear(getApplication(), it) }
        savedState.remove<Boolean>(PENDING_CONFIRM)
        savedState.remove<Int>(EXPECTED_IMPORTED)
        savedState.remove<Int>(EXPECTED_IGNORED)
    }

    private fun initialLineReview(item: ReceiptItem): ReceiptLineReview {
        val quantityValue = item.quantity
        val quantity = quantityValue?.takeIf {
            it >= 1 && it <= Int.MAX_VALUE.toDouble() && it % 1.0 == 0.0
        }?.toInt()
        val matched = (item.matchedProduct?.productId ?: 0) > 0
        return ReceiptLineReview(item, include = matched && quantity != null, quantity = quantity,
            expireDate = item.expireDate?.takeIf(::isIsoDate))
    }

    private fun isIsoDate(value: String): Boolean = try {
        LocalDate.parse(value).toString() == value
    } catch (_: Exception) { false }

    private fun failureError(failure: Exception) = when (failure) {
        is BffException -> failure.error
        is InvalidBffResponseException, is JsonParseException, is MalformedJsonException -> ApiError(code = "INVALID_RESPONSE")
        is IOException -> ApiError(code = "NETWORK_ERROR")
        else -> ApiError(code = "INVALID_RESPONSE")
    }

    private fun isUncertain(error: ApiError) = error.status == null || error.status == 408 ||
        error.status?.let { it >= 500 } == true || error.code == "INVALID_RESPONSE"

    companion object {
        private const val RECEIPT_ID = "review_receipt_id"
        private const val PENDING_CONFIRM = "receipt_confirmation_pending"
        private const val EXPECTED_IMPORTED = "receipt_expected_imported"
        private const val EXPECTED_IGNORED = "receipt_expected_ignored"
        private const val IMPORT = "IMPORT"
        private const val IGNORE = "IGNORE"
    }
}
