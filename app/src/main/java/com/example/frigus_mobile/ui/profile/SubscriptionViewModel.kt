package com.example.frigus_mobile.ui.profile

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.model.*
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.repository.SubscriptionRepository
import com.example.frigus_mobile.data.local.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    val error: String? = null,
    val notice: String? = null,
    val uncertainOperation: Boolean = false
)

class SubscriptionViewModel(private val repository: SubscriptionRepository, private val session: SessionManager) : ViewModel() {
    val state = MutableLiveData(SubscriptionState())
    private var busy = false
    private var readStage = ""

    fun load() = runRead {
        val errors = mutableListOf<String>()
        suspend fun <T> read(label: String, action: suspend () -> T): T? = try {
            action()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            errors += "$label: ${e.message ?: "falha na consulta"}"
            null
        }
        val plans = read("Planos", repository::plans)
        val subscription = read("Assinatura", repository::subscription)
        val limits = read("Limites", repository::limits)
        val transactions = read("Transações") { repository.transactions(0).also {
            require(it.content != null && it.number == 0)
        } }
        val groups = read("Grupos", repository::groups)
        val selectedId = state.value?.selectedGroup?.id
        val selected = selectedId?.takeIf { id -> groups?.any { it.id == id } == true }
            ?.let { read("Grupo") { repository.group(it) } }
        val inventory = selected?.id?.let { id -> read("Estoque") { repository.inventory(id) } }
        val stocks = inventory?.takeIf { it.groupId == selected?.id }?.stocks?.filterNotNull()
        val invitations = if (selected?.isOwner == true && selected.id != null)
            read("Convites") { repository.invitations(selected.id) } else emptyList()
        state.value = state.value!!.copy(
            plans = plans ?: state.value!!.plans,
            subscription = subscription ?: state.value!!.subscription,
            limits = limits ?: state.value!!.limits,
            transactions = transactions?.content ?: state.value!!.transactions,
            transactionPage = if (transactions != null) 0 else state.value!!.transactionPage,
            hasMoreTransactions = transactions?.totalPages?.let { it > 1 } ?: state.value!!.hasMoreTransactions,
            groups = groups ?: state.value!!.groups, groupsLoaded = groups != null,
            selectedGroup = selected, groupStocks = stocks, invitations = invitations ?: emptyList(),
            error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            uncertainOperation = state.value!!.uncertainOperation && (
                plans == null || subscription == null || limits == null || transactions == null || groups == null ||
                    selectedId != null && (selected == null || stocks == null || selected.isOwner && invitations == null)
            )
        )
    }

    fun loadMoreTransactions() {
        val current = state.value ?: return
        if (!current.hasMoreTransactions) return
        runRead {
            val page = repository.transactions(current.transactionPage + 1)
            require(page.content != null && page.number == current.transactionPage + 1)
            state.value = state.value!!.copy(
                transactions = current.transactions + page.content,
                transactionPage = page.number, hasMoreTransactions = page.number + 1 < page.totalPages
            )
        }
    }

    fun selectGroup(id: String) = runRead {
        val group = repository.group(id)
        val invites = if (group.isOwner) repository.invitations(id) else emptyList()
        state.value = state.value!!.copy(selectedGroup = group, groupStocks = null, invitations = invites)
        val inventory = repository.inventory(id)
        require(inventory.groupId == id && inventory.stocks != null)
        state.value = state.value!!.copy(groupStocks = inventory.stocks.filterNotNull())
    }

    fun createGroup(name: String) {
        val current = state.value ?: return
        val limit = current.limits?.maxGroupsCreated ?: return
        if (name.isBlank() || !current.groupsLoaded || current.groups.size >= limit) return
        runMutation {
            val group = repository.createGroup(name)
            require(group.id != null && runCatching { java.util.UUID.fromString(group.id) }.isSuccess)
            state.value = state.value!!.copy(
                groups = state.value!!.groups + group, selectedGroup = group,
                groupStocks = null, invitations = emptyList(), notice = "Grupo criado. Consulte o estoque antes de criar outro."
            )
            runCatching {
                val inventory = repository.inventory(group.id)
                if (inventory.groupId == group.id && inventory.stocks != null)
                    state.value = state.value!!.copy(groupStocks = inventory.stocks.filterNotNull())
            }
        }
    }

    fun createStock(name: String) {
        val group = state.value?.selectedGroup ?: return
        val groupId = group.id ?: return
        if (!group.isOwner || state.value?.groupStocks?.isNotEmpty() != false || name.isBlank()) return
        runMutation {
            val stock = repository.createStock(groupId, name)
            require(stock.id > 0 && stock.groupId == groupId)
            session.saveActiveGroupAndStock(groupId, stock.id)
            state.value = state.value!!.copy(
                groupStocks = listOf(stock), notice = "Estoque criado. Volte ao início para carregar os produtos."
            )
            runCatching {
                val inventory = repository.inventory(groupId)
                if (inventory.groupId == groupId && inventory.stocks != null)
                    state.value = state.value!!.copy(groupStocks = inventory.stocks.filterNotNull())
            }
        }
    }

    fun cancel() = runMutation {
        val result = repository.cancel()
        require(result.id != null)
        state.value = state.value!!.copy(subscription = result, notice = "Renovação automática cancelada. Consulte o fim do período vigente.")
    }

    fun reactivate() = runMutation {
        val result = repository.reactivate()
        require(result.id != null)
        state.value = state.value!!.copy(subscription = result, notice = "Assinatura reativada.")
    }

    fun freeCheckout(plan: ActivePlan) {
        if (plan.price != 0.0 || plan.planCode.isNullOrBlank()) return
        runMutation {
            val result = repository.freeCheckout(plan.planCode)
            require(result.id != null)
            state.value = state.value!!.copy(
                notice = "Solicitação do plano gratuito recebida: ${result.status ?: "em processamento"}. Consulte o histórico.",
                transactions = emptyList(), transactionPage = -1, hasMoreTransactions = false
            )
            // Reconcile through reads only; never replay checkout.
            runCatching {
                val page = repository.transactions(0)
                if (page.content != null) state.value = state.value!!.copy(
                    transactions = page.content, transactionPage = page.number,
                    hasMoreTransactions = page.number + 1 < page.totalPages
                )
                val subscription = repository.subscription()
                val limits = repository.limits()
                state.value = state.value!!.copy(subscription = subscription, limits = limits)
            }
        }
    }

    fun invite(email: String, role: String) {
        val group = state.value?.selectedGroup ?: return
        val id = group.id ?: return
        if (!group.isOwner || role !in listOf("EDITOR", "VIEWER")) return
        runMutation {
            val result = repository.invite(id, email, role)
            require(result.id != null)
            state.value = state.value!!.copy(
                invitations = state.value!!.invitations + result, notice = "Convite criado para $email."
            )
            runCatching { state.value = state.value!!.copy(invitations = repository.invitations(id)) }
        }
    }

    fun revoke(invitationId: String) {
        val group = state.value?.selectedGroup ?: return
        val id = group.id ?: return
        if (!group.isOwner) return
        runMutation {
            repository.revoke(id, invitationId)
            state.value = state.value!!.copy(
                invitations = state.value!!.invitations.filterNot { it.id == invitationId },
                notice = "Convite revogado."
            )
            runCatching { state.value = state.value!!.copy(invitations = repository.invitations(id)) }
        }
    }

    private fun runRead(action: suspend () -> Unit) = run(false, action)
    private fun runMutation(action: suspend () -> Unit) = run(true, action)
    private fun run(mutation: Boolean, action: suspend () -> Unit) {
        if (busy || (mutation && state.value?.uncertainOperation == true)) return
        busy = true
        state.value = state.value!!.copy(loading = true, error = null, notice = null)
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                state.value = state.value!!.copy(
                    error = "${if (mutation) "Operação" else readStage}: ${e.message ?: "Falha na operação."}",
                    uncertainOperation = mutation && (e !is BffException || (e.error.status ?: 500) >= 500)
                )
            } finally {
                busy = false
                state.value = state.value!!.copy(loading = false)
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SubscriptionViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SubscriptionViewModel(SubscriptionRepository(), SessionManager(context)) as T
        }
    }
}
