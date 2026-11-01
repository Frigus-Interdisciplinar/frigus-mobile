package com.example.frigus_mobile.ui.profile

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.*
import com.example.frigus_mobile.data.repository.SubscriptionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class SubscriptionNotice { GROUP_CREATED, STOCK_CREATED, CANCELED, REACTIVATED, CHECKOUT, INVITED, REVOKED }

data class SubscriptionState(
    val plans: List<ActivePlan> = emptyList(),
    val subscription: Subscription? = null,
    val limits: PlanLimits? = null,
    val transactions: List<BillingTransaction> = emptyList(),
    val transactionPage: Int = -1,
    val hasMoreTransactions: Boolean = false,
    val groups: List<FamilyGroup> = emptyList(),
    val groupsLoaded: Boolean = false,
    val selectedGroup: FamilyGroup? = null,
    val groupStocks: List<StockResponse>? = null,
    val invitations: List<GroupInvitation> = emptyList(),
    val loading: Boolean = false,
    val error: ApiError? = null,
    val notice: SubscriptionNotice? = null,
    val uncertainOperation: Boolean = false,
    val checkoutAwaitingRefresh: Boolean = false
)

class SubscriptionViewModel(
    private val repository: SubscriptionRepository,
    private val session: SessionManager,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val mutableState = MutableLiveData(SubscriptionState(
        uncertainOperation = savedState.get<String>(PENDING) != null
    ))
    val state: LiveData<SubscriptionState> = mutableState
    private var busy = false

    var selectedPlanIndex: Int
        get() = savedState[PLAN_INDEX] ?: 0
        set(value) { savedState[PLAN_INDEX] = value.coerceAtLeast(0) }

    fun loadIfNeeded() { if (state.value?.subscription == null && state.value?.uncertainOperation != true) load() }

    fun saveNavigationState() = Bundle().apply {
        putInt(PLAN_INDEX, selectedPlanIndex)
        savedState.get<String>(PENDING)?.let { putString(PENDING, it) }
        savedState.get<String>(SELECTED_GROUP)?.let { putString(SELECTED_GROUP, it) }
        putInt(CONTEXT_REVISION, savedState.get<Int>(CONTEXT_REVISION) ?: 0)
        putInt(HANDLED_CONTEXT, savedState.get<Int>(HANDLED_CONTEXT) ?: 0)
        putBoolean(CHECKOUT_REFRESH, state.value?.checkoutAwaitingRefresh == true)
    }

    fun restoreNavigationState(source: Bundle?) {
        if (source == null) return
        selectedPlanIndex = source.getInt(PLAN_INDEX).coerceIn(0, 100)
        source.getString(SELECTED_GROUP)?.takeIf { runCatching { java.util.UUID.fromString(it) }.isSuccess }
            ?.let { savedState[SELECTED_GROUP] = it }
        if (source.containsKey(CONTEXT_REVISION)) savedState[CONTEXT_REVISION] = source.getInt(CONTEXT_REVISION)
        if (source.containsKey(HANDLED_CONTEXT)) savedState[HANDLED_CONTEXT] = source.getInt(HANDLED_CONTEXT)
        if (source.getString(PENDING) != null) savedState[PENDING] = "mutation"
        update { it.copy(uncertainOperation = savedState.get<String>(PENDING) != null,
            checkoutAwaitingRefresh = source.getBoolean(CHECKOUT_REFRESH)) }
    }

    fun consumeInventoryContextChange(): Boolean {
        val revision = savedState.get<Int>(CONTEXT_REVISION) ?: 0
        if ((savedState.get<Int>(HANDLED_CONTEXT) ?: 0) == revision) return false
        savedState[HANDLED_CONTEXT] = revision
        return true
    }

    /** Only an explicit, complete read clears an uncertain mutation. */
    fun load() = run(false) { generation ->
        val errors = mutableListOf<ApiError>()
        suspend fun <T> read(action: suspend () -> T): T? = try {
            ensureSession(generation)
            action().also { ensureSession(generation) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { errors += failureError(failure); null }
        val plans = read(repository::plans)
        val subscription = read(repository::subscription)
        val limits = read(repository::limits)
        val transactions = read { repository.transactions(0).also { validatePage(it); require(it.number == 0) } }
        val groups = read(repository::groups)
        val selectedId = savedState.get<String>(SELECTED_GROUP)
        val selected = selectedId?.takeIf { id -> groups?.any { it.id == id } == true }
            ?.let { id -> read { repository.group(id).also { require(it.id == id) } } }
        val inventory = selected?.id?.let { id -> read { repository.inventory(id).also {
            require(it.groupId == id && it.stocks != null)
        } } }
        val invitations = if (selected?.isOwner == true && selected.id != null)
            read { repository.invitations(selected.id) } else emptyList()
        ensureSession(generation)
        // A removed group is a definitive reconciliation result after the group list succeeds.
        val reconciled = errors.isEmpty()
        if (reconciled && selected?.id != null && inventory != null) {
            val stocks = inventory.stocks.orEmpty().filterNotNull()
                .filter { it.id > 0 && (it.groupId == null || it.groupId == selected.id) }
            val stockId = session.activeStockId?.takeIf { id -> stocks.any { it.id == id } }
                ?: stocks.firstOrNull()?.id
            publishInventoryContext(selected.id, stockId, generation)
        }
        if (reconciled) savedState.remove<String>(PENDING)
        update { it.copy(
            plans = plans ?: emptyList(), subscription = subscription, limits = limits,
            transactions = transactions?.content.orEmpty(), transactionPage = if (transactions == null) -1 else 0,
            hasMoreTransactions = (transactions?.totalPages ?: 0) > 1,
            groups = groups.orEmpty(), groupsLoaded = groups != null,
            selectedGroup = selected, groupStocks = inventory?.stocks?.filterNotNull(),
            invitations = invitations.orEmpty(), error = errors.firstOrNull(),
            uncertainOperation = !reconciled && savedState.get<String>(PENDING) != null,
            checkoutAwaitingRefresh = !reconciled && it.checkoutAwaitingRefresh
        ) }
    }

    fun loadMoreTransactions() {
        val current = state.value ?: return
        if (!current.hasMoreTransactions) return
        run(false) { generation ->
            val page = repository.transactions(current.transactionPage + 1)
            validatePage(page)
            require(page.number == current.transactionPage + 1)
            ensureSession(generation)
            update { it.copy(transactions = current.transactions + page.content.orEmpty(),
                transactionPage = page.number, hasMoreTransactions = page.number + 1 < page.totalPages) }
        }
    }

    fun selectGroup(id: String) {
        if (state.value?.uncertainOperation == true) return
        run(false) { generation ->
            val group = repository.group(id)
            require(group.id == id)
            ensureSession(generation)
            savedState[SELECTED_GROUP] = id
            update { it.copy(selectedGroup = group, groupStocks = null, invitations = emptyList()) }
            val inventory = repository.inventory(id)
            require(inventory.groupId == id && inventory.stocks != null)
            ensureSession(generation)
            val stocks = inventory.stocks.filterNotNull().filter { it.id > 0 && (it.groupId == null || it.groupId == id) }
            val selectedStock = session.activeStockId?.takeIf { stockId -> stocks.any { it.id == stockId } }
                ?: stocks.firstOrNull()?.id
            publishInventoryContext(id, selectedStock, generation)
            update { it.copy(groupStocks = inventory.stocks.filterNotNull()) }
            if (group.isOwner) {
                val invites = repository.invitations(id)
                ensureSession(generation)
                update { it.copy(invitations = invites) }
            }
        }
    }

    fun createGroup(name: String) {
        val current = state.value ?: return
        val limit = current.limits?.maxGroupsCreated ?: return
        if (name.isBlank() || name.length > 120 || !current.groupsLoaded || current.groups.size >= limit) return
        mutate(SubscriptionNotice.GROUP_CREATED) { generation ->
            val group = repository.createGroup(name)
            require(group.id != null && runCatching { java.util.UUID.fromString(group.id) }.isSuccess)
            ensureSession(generation)
            savedState[SELECTED_GROUP] = group.id
            update { it.copy(groups = it.groups + group, selectedGroup = group,
                groupStocks = null, invitations = emptyList()) }
        }
    }

    fun createStock(name: String) {
        val current = state.value ?: return
        val group = current.selectedGroup ?: return
        val id = group.id ?: return
        if (!group.isOwner || current.groupStocks?.isEmpty() != true || name.isBlank() || name.length > 120) return
        mutate(SubscriptionNotice.STOCK_CREATED) { generation ->
            val stock = repository.createStock(id, name)
            require(stock.id > 0 && stock.groupId == id)
            ensureSession(generation)
            publishInventoryContext(id, stock.id, generation)
            update { it.copy(groupStocks = listOf(stock)) }
        }
    }

    fun cancel() = mutate(SubscriptionNotice.CANCELED) { generation ->
        val result = repository.cancel()
        require(result.id != null)
        ensureSession(generation)
        update { it.copy(subscription = result) }
    }

    fun reactivate() = mutate(SubscriptionNotice.REACTIVATED) { generation ->
        val result = repository.reactivate()
        require(result.id != null)
        ensureSession(generation)
        update { it.copy(subscription = result) }
    }

    fun freeCheckout(plan: ActivePlan) {
        if (plan.price != 0.0 || plan.planCode.isNullOrBlank() || !plan.active || state.value?.checkoutAwaitingRefresh == true) return
        mutate(SubscriptionNotice.CHECKOUT) { generation ->
            val result = repository.freeCheckout(plan.planCode)
            require(result.id != null)
            ensureSession(generation)
            update { it.copy(transactions = listOf(result), transactionPage = -1, hasMoreTransactions = false,
                checkoutAwaitingRefresh = true) }
        }
    }

    fun invite(email: String, role: String) {
        val group = state.value?.selectedGroup ?: return
        val id = group.id ?: return
        if (!group.isOwner || email.isBlank() || role !in listOf("EDITOR", "VIEWER")) return
        mutate(SubscriptionNotice.INVITED) { generation ->
            val result = repository.invite(id, email, role)
            require(result.id != null)
            ensureSession(generation)
            update { it.copy(invitations = it.invitations + result) }
        }
    }

    fun revoke(invitationId: String) {
        val group = state.value?.selectedGroup ?: return
        val id = group.id ?: return
        if (!group.isOwner) return
        mutate(SubscriptionNotice.REVOKED) { generation ->
            repository.revoke(id, invitationId)
            ensureSession(generation)
            update { it.copy(invitations = it.invitations.filterNot { invitation -> invitation.id == invitationId }) }
        }
    }

    private fun mutate(notice: SubscriptionNotice, action: suspend (Long) -> Unit) = run(true) { generation ->
        action(generation)
        ensureSession(generation)
        savedState.remove<String>(PENDING)
        update { it.copy(notice = notice, uncertainOperation = false) }
    }

    private fun run(mutation: Boolean, action: suspend (Long) -> Unit) {
        if (!session.isLoggedIn || busy || mutation && state.value?.uncertainOperation == true) return
        val generation = session.state.generation
        busy = true
        if (mutation) savedState[PENDING] = "mutation"
        update { it.copy(loading = true, error = null, notice = null) }
        viewModelScope.launch {
            try { action(generation) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation) {
                    val error = failureError(failure)
                    val uncertain = mutation && (error.status == null || error.status == 408 || error.status!! >= 500)
                    if (mutation && !uncertain) savedState.remove<String>(PENDING)
                    update { it.copy(error = error, uncertainOperation = it.uncertainOperation || uncertain) }
                }
            } finally {
                busy = false
                if (session.isLoggedIn && session.state.generation == generation) update { it.copy(loading = false) }
            }
        }
    }

    private fun ensureSession(generation: Long) {
        if (!session.isLoggedIn || session.state.generation != generation) throw CancellationException()
    }
    private fun publishInventoryContext(groupId: String, stockId: Int?, generation: Long) {
        ensureSession(generation)
        if (session.activeGroupId == groupId && session.activeStockId == stockId) return
        session.saveActiveGroupAndStock(groupId, stockId)
        savedState[CONTEXT_REVISION] = (savedState.get<Int>(CONTEXT_REVISION) ?: 0) + 1
    }
    private fun update(action: (SubscriptionState) -> SubscriptionState) {
        mutableState.value = action(mutableState.value ?: SubscriptionState())
    }
    private fun validatePage(page: BillingTransactionPage) {
        require(page.content != null && page.number >= 0 && page.totalPages >= 0 && page.totalElements >= 0)
    }
    private fun failureError(failure: Exception): ApiError = when (failure) {
        is BffException -> failure.error
        is InvalidBffResponseException, is IllegalArgumentException -> ApiError(code = "INVALID_RESPONSE")
        else -> ApiError(code = "NETWORK_ERROR")
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(SubscriptionViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SubscriptionViewModel(SubscriptionRepository(), SessionManager(applicationContext), extras.createSavedStateHandle()) as T
        }
    }

    companion object {
        private const val PENDING = "subscription_pending"
        private const val SELECTED_GROUP = "subscription_group"
        private const val CONTEXT_REVISION = "subscription_context_revision"
        private const val HANDLED_CONTEXT = "subscription_handled_context"
        private const val CHECKOUT_REFRESH = "subscription_checkout_refresh"
        private const val PLAN_INDEX = "subscription_plan_index"
    }
}
