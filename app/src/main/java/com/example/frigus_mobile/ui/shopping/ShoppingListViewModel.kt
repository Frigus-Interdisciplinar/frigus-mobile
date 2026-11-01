package com.example.frigus_mobile.ui.shopping

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.*
import com.example.frigus_mobile.data.repository.ShoppingListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

enum class ShoppingCompletionKind { ADDED, UPDATED, REMOVED, MARKED, TRANSFERRED }
data class ShoppingCompletion(val id: Long, val kind: ShoppingCompletionKind)
data class ShoppingPricePrompt(val draft: ShoppingDraft, val toStock: Boolean)

data class ShoppingState(
    val groupId: String? = null,
    val stocks: List<StockResponse> = emptyList(), val stock: StockResponse? = null,
    val items: List<ShoppingListItem> = emptyList(), val catalog: List<ShoppingCatalogProduct> = emptyList(),
    val loading: Boolean = false, val transferring: Boolean = false,
    val uncertain: Boolean = false, val error: ApiError? = null,
    val pendingPrice: ShoppingPricePrompt? = null, val completion: ShoppingCompletion? = null,
    val reconciledLots: List<StockProductResponse> = emptyList(), val inventoryRevision: Long = 0,
    val syncStatus: ShoppingSyncStatus = ShoppingSyncStatus.LOCAL_PENDING,
    val syncPendingCount: Int = 0, val syncError: ApiError? = null
)

/** Account-local offline CRUD; only an explicit stock confirmation can start BFF mutations. */
class ShoppingListViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    private val repository = ShoppingListRepository(application)
    private val mutableState = MutableLiveData(ShoppingState())
    val state: LiveData<ShoppingState> = mutableState
    private var loadedGeneration: Long? = null
    private var loadedOwner: String? = null
    private var loadedGroup: String? = null
    private var loadedStock: Int? = null
    private var loadedValidated: Boolean? = null
    private var job: Job? = null
    private var requestId = 0L
    private var completionId = 0L
    private var mutationOwner: String? = null
    private var pendingMutation: ShoppingPendingMutation? = null
    var query: String = ""
        private set

    fun setQuery(value: String) { query = value }
    fun item(id: String): ShoppingListItem? = state.value?.items?.firstOrNull { it.id == id }

    fun loadIfNeeded() {
        if (loadedGeneration != session.state.generation || loadedOwner != owner() ||
            loadedGroup != session.activeGroupId || loadedStock != session.activeStockId ||
            loadedValidated != session.isLoggedIn) reload(false)
    }

    /** Loading/reloading the shopping list itself never requires a network connection. */
    fun reload(explicit: Boolean = true) {
        loadLocal()
        if (explicit && state.value?.uncertain == true) remoteRead { guard ->
            val user = owner() ?: throw CancellationException()
            val pending = repository.pending(user) ?: return@remoteRead
            reconcile(user, pending, guard)
            guard()
            mutableState.value = mutableState.value!!.copy(uncertain = false,
                inventoryRevision = mutableState.value!!.inventoryRevision + 1)
        }
    }

    private fun loadLocal() {
        val user = owner()
        val changed = loadedGeneration != session.state.generation || loadedOwner != user ||
            loadedGroup != session.activeGroupId || loadedStock != session.activeStockId
        if (changed) { job?.cancel(); requestId++ }
        val old = state.value ?: ShoppingState()
        val sameAccount = loadedOwner == user && loadedGeneration == session.state.generation
        if (!sameAccount) query = ""
        val sameGroup = sameAccount && loadedGroup == session.activeGroupId
        loadedGeneration = session.state.generation; loadedOwner = user
        loadedGroup = session.activeGroupId; loadedStock = session.activeStockId
        loadedValidated = session.isLoggedIn
        if (user == null) { repository.stopSync(); mutableState.value = ShoppingState(); return }
        try {
            val stocks = if (sameGroup) old.stocks else emptyList()
            mutableState.value = (if (sameAccount) old else ShoppingState()).copy(
                groupId = if (sameGroup) old.groupId else null, stocks = stocks,
                stock = inferredShoppingStock(stocks, session.activeStockId),
                items = repository.localItems(user), loading = false,
                transferring = if (changed) false else old.transferring,
                uncertain = repository.hasPending(user), error = null, completion = null,
                syncPendingCount = repository.localPendingCount(user))
            if (!session.isLoggedIn) {
                repository.stopSync()
                mutableState.value = mutableState.value!!.copy(syncStatus = ShoppingSyncStatus.AUTH_REQUIRED)
                return
            }
            val generation = session.state.generation
            repository.startSync(user, viewModelScope, { session.isLoggedIn && owner() == user && session.state.generation == generation }) { status, count, error ->
                if (session.isLoggedIn && owner() == user && session.state.generation == generation) {
                    val items = runCatching { repository.localItems(user) }.getOrElse { mutableState.value!!.items }
                    mutableState.value = mutableState.value!!.copy(items = items,
                        syncStatus = status, syncPendingCount = count, syncError = error)
                }
            }
        } catch (_: Exception) {
            mutableState.value = (if (sameAccount) old else ShoppingState()).copy(
                loading = false, transferring = false, error = ApiError(code = "SHOPPING_LOCAL_SAVE_FAILED"))
        }
    }

    /** Optional read after the user chooses stock; local shopping never calls this implicitly. */
    fun prepareStock() = remoteRead { guard -> context(guard) }
    fun syncNow() { loadIfNeeded(); repository.syncNow() }

    fun selectStock(stockId: Int) {
        val current = state.value ?: return
        if (current.transferring || current.stocks.none { it.id == stockId }) return
        session.saveActiveGroupAndStock(current.groupId, stockId)
        loadLocal()
    }

    fun cancelPricePrompt() { mutableState.value = state.value!!.copy(pendingPrice = null) }
    fun consumeCompletion(id: Long) {
        if (state.value?.completion?.id == id) mutableState.value = state.value!!.copy(completion = null)
    }

    fun submitDraft(draft: ShoppingDraft, toStock: Boolean) {
        if (!validDraft(draft)) return
        if (!toStock) {
            localAction(ShoppingCompletionKind.ADDED) { user ->
                repository.saveLocalItem(user, localItem(draft))
            }
            return
        }
        if (!validTransfer(draft)) return
        val key = "offline-draft/" + listOf(draft.name.trim(), draft.categoryCode, draft.unitCode,
            draft.quantity, draft.expireDate, draft.unitPrice!!.stripTrailingZeros().toPlainString()).joinToString("/")
        transfer(draft, key, null)
    }

    fun updateItem(item: ShoppingListItem, draft: ShoppingDraft) {
        if (!validDraft(draft)) return
        localAction(ShoppingCompletionKind.UPDATED) { user ->
            val actual = repository.localItems(user).firstOrNull { it.id == item.id }
                ?: throw IllegalStateException()
            repository.saveLocalItem(user, localItem(draft).copy(id = actual.id, isChecked = actual.isChecked))
        }
    }

    fun updateItem(item: ShoppingListItem, quantity: Int, observation: String, imageUri: String?) =
        updateItem(item, ShoppingDraft(item.name, quantity, item.unitCode ?: ShoppingListItem.mapUnitToCode(item.unit),
            item.categoryCode, observation = observation, imageUri = imageUri))

    fun deleteItem(item: ShoppingListItem) = localAction(ShoppingCompletionKind.REMOVED) { user ->
        repository.deleteLocalItem(user, item.id)
    }

    fun checkItem(item: ShoppingListItem, checked: Boolean) = localAction(ShoppingCompletionKind.MARKED) { user ->
        val actual = repository.localItems(user).firstOrNull { it.id == item.id } ?: throw IllegalStateException()
        val result = repository.saveLocalItem(user, actual.copy(isChecked = checked))
        // Unchecking is an explicit new shopping cycle. An unresolved transfer is never discarded.
        if (!checked && !repository.hasPending(user)) runCatching { repository.clearLotCheckpoint(user, transferKey(item.id)) }
        result
    }

    fun transferItem(item: ShoppingListItem, expiry: String, place: String, unitPrice: BigDecimal) {
        val actual = item(item.id) ?: return
        val draft = ShoppingDraft(actual.name, actual.quantity,
            actual.unitCode ?: ShoppingListItem.mapUnitToCode(actual.unit), actual.categoryCode,
            place, expiry, actual.observation, actual.imageUrl, unitPrice = unitPrice)
        if (!validDraft(draft) || !validTransfer(draft)) return
        transfer(draft, transferKey(actual.id), actual.id)
    }

    private fun localItem(draft: ShoppingDraft) = ShoppingListItem(
        name = draft.name.trim(), quantity = draft.quantity, unit = draft.unitCode,
        category = ShoppingListItem.mapCodeToCategory(draft.categoryCode), categoryCode = draft.categoryCode,
        observation = draft.observation, addedBy = "", imageUrl = draft.imageUri, unitCode = draft.unitCode)

    private fun localAction(kind: ShoppingCompletionKind, update: (String) -> List<ShoppingListItem>) {
        val user = owner() ?: return
        if (loadedOwner != user || loadedGeneration != session.state.generation) { loadLocal(); return }
        try {
            val items = update(user)
            mutableState.value = mutableState.value!!.copy(items = items, error = null, pendingPrice = null,
                completion = ShoppingCompletion(++completionId, kind), syncPendingCount = repository.localPendingCount(user),
                syncStatus = if (session.isLoggedIn) ShoppingSyncStatus.LOCAL_PENDING else ShoppingSyncStatus.AUTH_REQUIRED)
            repository.syncNow()
        } catch (_: Exception) { fail("SHOPPING_LOCAL_SAVE_FAILED") }
    }

    private fun validDraft(draft: ShoppingDraft): Boolean {
        if (owner() == null) return false
        if (draft.name.isBlank() || draft.quantity <= 0 || draft.unitCode.isBlank() || draft.categoryCode.isBlank()) {
            fail("SHOPPING_INVALID_ITEM"); return false
        }
        return true
    }

    private fun validTransfer(draft: ShoppingDraft): Boolean {
        if (!validShoppingPrice(draft.unitPrice)) { fail("SHOPPING_PRICE_REQUIRED"); return false }
        if (runCatching { LocalDate.parse(draft.expireDate) }.getOrNull() == null) {
            fail("SHOPPING_EXPIRY_REQUIRED"); return false
        }
        if (!validShoppingPlace(draft.categoryCode, draft.storagePlace)) {
            fail(if (draft.categoryCode == "GRAIN") "SHOPPING_GRAIN_PANTRY" else "SHOPPING_EXPIRY_REQUIRED")
            return false
        }
        return true
    }

    private suspend fun context(guard: () -> Unit): ShoppingState {
        val context = repository.context(session.activeGroupId)
        guard()
        val stock = inferredShoppingStock(context.stocks, session.activeStockId)
        mutableState.value = mutableState.value!!.copy(groupId = context.groupId, stocks = context.stocks, stock = stock)
        if (stock == null) throw BffException(ApiError(status = 422,
            code = if (context.stocks.isEmpty()) "SHOPPING_NO_STOCK" else "SHOPPING_CHOOSE_STOCK"), "")
        return mutableState.value!!
    }

    private fun transfer(draft: ShoppingDraft, key: String, localId: String?) = remoteRead { guard ->
        val user = owner() ?: throw CancellationException()
        if (repository.hasPending(user)) {
            mutableState.value = mutableState.value!!.copy(uncertain = true)
            return@remoteRead
        }
        mutationOwner = user
        pendingMutation = null
        val current = context(guard)
        val price = draft.unitPrice!!
        val saved = repository.lotCheckpoint(user, key)
        val lot: StockProductResponse
        if (saved != null) {
            val lots = repository.lots(current.stock!!.id, guard)
            guard()
            lot = lots.singleOrNull { it.id == saved.id && it.stockId == current.stock.id &&
                it.quantity == draft.quantity && it.expireDate?.take(10) == draft.expireDate &&
                it.hasShoppingPrice(price) && it.product?.name.equals(draft.name.trim(), true) }
                ?: throw BffException(ApiError(status = 409, code = "SHOPPING_RECONCILIATION_REQUIRED"), "")
            checkpointPending(ShoppingPendingMutation(current.groupId!!, current.stock.id, confirmedWrite = true,
                lotKey = key, productId = lot.productId, quantity = draft.quantity, expiry = draft.expireDate,
                confirmedLotId = lot.id, purchaseUnitPrice = price))
        } else {
            val catalog = repository.catalog(current.groupId!!, guard)
            guard()
            mutableState.value = mutableState.value!!.copy(catalog = catalog)
            val matching = catalog.filter { it.name.equals(draft.name.trim(), true) &&
                it.category == draft.categoryCode && it.unitOfMeasure == draft.unitCode }
            val named = catalog.filter { it.name.equals(draft.name.trim(), true) }
            val selected = draft.productId?.let { id -> named.firstOrNull { it.id == id } }
                ?: named.singleOrNull() ?: matching.singleOrNull()
                ?: matching.filter { it.ownerGroupId == current.groupId }.singleOrNull()
            if (selected == null && named.isNotEmpty())
                throw BffException(ApiError(status = 422, code = "SHOPPING_AMBIGUOUS_PRODUCT"), "")
            // Both the local choice and the authoritative selected product must obey the grain rule.
            if (!validShoppingPlace(selected?.category ?: draft.categoryCode, draft.storagePlace))
                throw BffException(ApiError(status = 422, code = "SHOPPING_GRAIN_PANTRY"), "")
            if (selected != null && selected.unitOfMeasure != draft.unitCode)
                throw BffException(ApiError(status = 422, code = "SHOPPING_UNIT_MISMATCH"), "")
            checkpointPending(ShoppingPendingMutation(current.groupId, current.stock!!.id,
                quantity = draft.quantity, expiry = draft.expireDate, purchaseUnitPrice = price))
            val product = selected ?: repository.createProduct(ShoppingCreateProduct(draft.name.trim(),
                draft.categoryCode, draft.storagePlace, price, draft.unitCode, current.groupId)).also {
                checkpointPending(pendingMutation!!.copy(confirmedWrite = true, productId = it.id))
                guard()
            }
            guard()
            val lots = repository.lots(current.stock.id, guard)
            guard()
            checkpointPending(pendingMutation!!.copy(lotKey = key, productId = product.id, baselineLotIds = lots.mapNotNull { it.id }))
            guard()
            lot = repository.createLot(current.stock.id, current.groupId, product.id!!, draft.quantity, draft.expireDate!!, price)
            checkpointPending(pendingMutation!!.copy(confirmedWrite = true, confirmedLotId = lot.id))
            repository.saveLotCheckpoint(user, key, lot)
            guard()
            mutableState.value = mutableState.value!!.copy(inventoryRevision = mutableState.value!!.inventoryRevision + 1)
        }
        if (!validShoppingPlace(lot.product?.category ?: draft.categoryCode, draft.storagePlace))
            throw BffException(ApiError(status = 422, code = "SHOPPING_GRAIN_PANTRY"), "")
        if (lot.shoppingPlace() != draft.storagePlace) {
            val changed = repository.setLotPlace(lot.id!!, draft.storagePlace, price)
            checkpointPending(pendingMutation!!.copy(confirmedWrite = true))
            repository.saveLotCheckpoint(user, key, changed)
            guard()
        }
        guard()
        if (localId != null) {
            val item = repository.localItems(user).firstOrNull { it.id == localId }
            // Offline edits remain available while the request runs; do not mark a changed item as purchased.
            if (item != null && item.name.trim() == draft.name.trim() && item.quantity == draft.quantity &&
                item.categoryCode == draft.categoryCode &&
                (item.unitCode ?: ShoppingListItem.mapUnitToCode(item.unit)) == draft.unitCode)
                repository.saveLocalItem(user, item.copy(isChecked = true))
        } else repository.clearLotCheckpoint(user, key)
        repository.clearPending(user)
        pendingMutation = null
        guard()
        mutableState.value = mutableState.value!!.copy(items = repository.localItems(user), uncertain = false,
            completion = ShoppingCompletion(++completionId, ShoppingCompletionKind.TRANSFERRED))
        repository.syncNow()
    }

    private suspend fun reconcile(user: String, pending: ShoppingPendingMutation, guard: () -> Unit) {
        val context = repository.context(pending.groupId)
        guard()
        if (context.stocks.none { it.id == pending.stockId })
            throw BffException(ApiError(code = "SHOPPING_RECONCILIATION_REQUIRED"), "")
        repository.catalog(pending.groupId, guard)
        val lots = repository.lots(pending.stockId, guard)
        guard()
        if (pending.lotKey != null) {
            val recovered = recoveredShoppingLot(pending, lots)
            val candidates = lots.filter { it.id !in pending.baselineLotIds.orEmpty() &&
                it.productId == pending.productId && it.expireDate?.take(10) == pending.expiry }
            if (recovered == null && (pending.confirmedLotId != null || pending.baselineLotIds == null || candidates.isNotEmpty()))
                throw BffException(ApiError(code = "SHOPPING_RECONCILIATION_REQUIRED"), "")
            if (recovered != null && pending.purchaseUnitPrice != null && !recovered.hasShoppingPrice(pending.purchaseUnitPrice))
                throw BffException(ApiError(code = "SHOPPING_RECONCILIATION_REQUIRED"), "")
            if (recovered != null) repository.saveLotCheckpoint(user, pending.lotKey, recovered)
        }
        guard()
        repository.clearPending(user)
        mutableState.value = mutableState.value!!.copy(reconciledLots = lots)
    }

    private fun checkpointPending(value: ShoppingPendingMutation) {
        repository.savePending(mutationOwner!!, value)
        pendingMutation = value
    }

    private fun remoteRead(block: suspend (guard: () -> Unit) -> Unit) {
        val user = owner() ?: return
        if (!session.isLoggedIn) { fail("SHOPPING_AUTH_REQUIRED"); return }
        if (loadedOwner != user || loadedGeneration != session.state.generation) { loadLocal(); return }
        if (state.value?.transferring == true) return
        val generation = session.state.generation
        val group = session.activeGroupId
        val stock = session.activeStockId
        val request = ++requestId
        fun active() = session.isLoggedIn && owner() == user && session.state.generation == generation &&
            session.activeGroupId == group && session.activeStockId == stock && request == requestId
        fun guard() { if (!active()) throw CancellationException() }
        mutableState.value = mutableState.value!!.copy(transferring = true, error = null, completion = null)
        job = viewModelScope.launch {
            try { guard(); block(::guard) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (active()) {
                    val failure = failure(error)
                    val pending = runCatching { repository.pending(user) }.getOrNull()
                    val uncertain = pending != null && (pending.confirmedWrite || failure.code == "INVALID_RESPONSE" ||
                        failure.code == "SHOPPING_RECONCILIATION_REQUIRED" || shoppingMutationUncertain(failure.status))
                    if (pending != null && !uncertain) runCatching { repository.clearPending(user) }
                    mutableState.value = mutableState.value!!.copy(error = failure, completion = null,
                        uncertain = repository.hasPending(user))
                }
            } finally { if (active()) mutableState.value = mutableState.value!!.copy(transferring = false) }
        }
    }

    private fun owner(): String? = session.userId?.takeIf {
        (session.isLoggedIn || session.canReadCachedShopping) && it.isNotBlank() && it != "local_user"
    }
    private fun fail(code: String) { mutableState.value = state.value!!.copy(error = ApiError(code = code)) }
    private fun failure(error: Exception): ApiError = when (error) {
        is BffException -> error.error
        is IOException -> ApiError(code = "NETWORK_ERROR")
        else -> ApiError(code = "INVALID_RESPONSE")
    }
    private fun transferKey(id: String) = "offline-item/$id"

    override fun onCleared() { repository.close(); super.onCleared() }
}
