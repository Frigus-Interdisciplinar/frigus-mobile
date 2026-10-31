package com.example.frigus_mobile.ui.profile

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ActivePlan
import com.example.frigus_mobile.data.model.PlanLimits
import com.example.frigus_mobile.ui.home.HomeViewModel
import com.example.frigus_mobile.ui.stock.InventoryViewModel
import java.text.NumberFormat
import java.util.Locale

class AssinaturaPlanosFragment : Fragment() {
    private val model: SubscriptionViewModel by lazy {
        ViewModelProvider(this, SubscriptionViewModel.Factory(requireContext()))[SubscriptionViewModel::class.java]
    }
    private val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private lateinit var details: LinearLayout
    private var lastCreatedStockId: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_assinatura_planos, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener { parentFragmentManager.popBackStack() }
        val content = view.findViewById<LinearLayout>(R.id.subscriptionContent)
        details = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(details)
        view.findViewById<View>(R.id.profilePlanActionsTitle).visibility = View.VISIBLE
        view.findViewById<View>(R.id.profilePlanActionsCard).visibility = View.VISIBLE
        view.findViewById<View>(R.id.btnFormaPagamento).visibility = View.GONE
        view.findViewById<View>(R.id.btnGerenciarPlano).setOnClickListener { showPlans() }
        view.findViewById<View>(R.id.btnHistoricoCobrancas).setOnClickListener { showTransactions() }
        view.findViewById<View>(R.id.btnBeneficiosPlano).setOnClickListener { showBenefits() }
        view.findViewById<View>(R.id.btnMinhaFamilia).setOnClickListener { showGroups() }
        view.findViewById<View>(R.id.btnConvidarMembro).setOnClickListener { showInvite() }
        model.state.observe(viewLifecycleOwner) { render(view, it) }
        if (model.state.value?.subscription == null) model.load()
    }

    private fun render(view: View, state: SubscriptionState) {
        if (state.notice?.startsWith("Estoque criado.") == true) {
            val stockId = state.groupStocks?.firstOrNull()?.id
            if (stockId != null && stockId != lastCreatedStockId) {
                lastCreatedStockId = stockId
                ViewModelProvider(requireActivity())[HomeViewModel::class.java].invalidate()
                ViewModelProvider(requireActivity())[InventoryViewModel::class.java].refresh()
            }
        }
        val subscription = state.subscription
        view.findViewById<TextView>(R.id.tvPlanoAtualNome).text =
            subscription?.plan?.name ?: if (state.loading) "Carregando..." else "Plano não disponível"
        view.findViewById<TextView>(R.id.tvPlanoAtualPreco).apply {
            visibility = if (subscription?.plan?.price == null) View.GONE else View.VISIBLE
            text = subscription?.plan?.price?.let { currency.format(it) }.orEmpty()
        }
        view.findViewById<TextView>(R.id.tvPlanoAtualValidade).text =
            subscription?.let { "Status: ${it.status ?: "não informado"} · até ${it.currentPeriodEnd ?: "data não informada"}${if (it.canceledAt != null) " · renovação cancelada" else ""}" }
                ?: state.error.orEmpty()
        details.removeAllViews()
        if (state.loading) details.addView(line("Carregando dados do BFF..."))
        state.error?.let {
            details.addView(line(if (state.uncertainOperation)
                "Resultado incerto. Consulte a assinatura, os convites e as transações antes de qualquer novo envio. $it"
                else it))
        }
        state.notice?.let { details.addView(line(it)) }
        if (state.error != null || state.uncertainOperation) {
            details.addView(button("Atualizar dados") { model.load() })
        }
        if (subscription != null) {
            val action = when {
                subscription.canceledAt != null -> "Reativar assinatura"
                subscription.status == "ACTIVE" && subscription.plan?.price?.let { it > 0 } == true -> "Cancelar assinatura"
                else -> null
            }
            if (action != null) details.addView(button(action) {
                confirm(action) {
                    if (subscription.canceledAt != null) model.reactivate() else model.cancel()
                }
            })
        }
        state.limits?.let { limits ->
            details.addView(line("Limites do plano\n${limitsText(limits)}"))
        }
        if (!state.loading && !state.uncertainOperation && state.groupsLoaded &&
            state.limits?.maxGroupsCreated?.let { state.groups.size < it } == true) {
            details.addView(button("Criar grupo") { showCreateGroup() })
        }
        details.addView(line("Transações"))
        if (state.transactions.isEmpty()) details.addView(line("Nenhuma transação retornada."))
        state.transactions.forEach {
            details.addView(line("${it.createdAt ?: "Data não informada"} · ${it.planCode ?: "Plano"} · ${it.status ?: "Status não informado"}\n${it.amount?.let { amount -> currency.format(amount) } ?: "Valor não informado"} · ${it.paymentMethod ?: "Método não informado"}"))
        }
        if (state.hasMoreTransactions) details.addView(button("Carregar mais transações") { model.loadMoreTransactions() })
        state.selectedGroup?.let { group ->
            details.addView(line("Grupo: ${group.name ?: "Sem nome"}"))
            when (val stocks = state.groupStocks) {
                null -> details.addView(line("Estoque ainda não consultado. Atualize os dados ou selecione o grupo novamente."))
                else -> {
                    if (stocks.isEmpty()) details.addView(line("Este grupo ainda não tem estoque."))
                    stocks.forEach { details.addView(line("Estoque: ${it.name ?: "Sem nome"} · ID ${it.id}")) }
                    if (stocks.isEmpty() && group.isOwner && !state.loading && !state.uncertainOperation)
                        details.addView(button("Criar estoque") { showCreateStock() })
                }
            }
            group.members.orEmpty().forEach { member ->
                details.addView(line("${member.name ?: member.email ?: "Membro"} · ${member.memberRole ?: "Papel não informado"}"))
            }
            if (group.members.isNullOrEmpty()) details.addView(line("Nenhum membro retornado."))
            details.addView(line("Convites"))
            state.invitations.forEach { invitation ->
                details.addView(line("${invitation.email ?: "E-mail não informado"} · ${invitation.memberRole ?: ""} · ${if (invitation.revokedAt != null) "revogado" else if (invitation.acceptedAt != null) "aceito" else "pendente"}"))
                if (group.isOwner && invitation.id != null && invitation.revokedAt == null && invitation.acceptedAt == null) {
                    details.addView(button("Revogar convite") {
                        confirm("Revogar convite para ${invitation.email}?") { model.revoke(invitation.id) }
                    })
                }
            }
            if (state.invitations.isEmpty()) details.addView(line("Nenhum convite retornado."))
        }
    }

    private fun showPlans() {
        val plans = model.state.value?.plans.orEmpty()
        if (plans.isEmpty()) { message("Planos", "Nenhum plano ativo disponível."); return }
        val labels = plans.map { plan ->
            "${plan.name ?: plan.planCode ?: "Plano"} · ${plan.price?.let { currency.format(it) } ?: "Preço não informado"}"
        }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle("Planos disponíveis")
            .setItems(labels) { _, index -> showPlan(plans[index]) }.setNegativeButton("Fechar", null).show()
    }

    private fun showPlan(plan: ActivePlan) {
        val body = buildString {
            appendLine(plan.description.orEmpty())
            plan.benefits.orEmpty().forEach {
                appendLine("${it.label ?: it.code ?: "Benefício"}: ${if (it.enabled) "incluído" else "não incluído"}${it.limit?.let { limit -> " · limite $limit" } ?: ""}")
            }
            plan.limits?.let { appendLine(limitsText(it)) }
            if (plan.price?.let { it > 0 } == true) appendLine("Contratação paga pendente da integração de pagamento real.")
        }
        val dialog = AlertDialog.Builder(requireContext()).setTitle(plan.name ?: "Plano").setMessage(body)
            .setNegativeButton("Fechar", null)
        if (plan.price == 0.0 && plan.planCode != model.state.value?.subscription?.plan?.planCode) {
            dialog.setPositiveButton("Escolher plano gratuito") { _, _ ->
                confirm("Confirmar contratação do plano gratuito?") { model.freeCheckout(plan) }
            }
        }
        dialog.show()
    }

    private fun showBenefits() {
        val benefits = model.state.value?.subscription?.plan?.benefits.orEmpty()
        message("Benefícios do plano", benefits.takeIf { it.isNotEmpty() }?.joinToString("\n") {
            "${it.label ?: it.code ?: "Benefício"}: ${if (it.enabled) "incluído" else "não incluído"}${it.limit?.let { limit -> " · limite $limit" } ?: ""}"
        } ?: "Nenhum benefício retornado pelo servidor.")
    }

    private fun showTransactions() {
        val state = model.state.value ?: return
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 12)
        }
        if (state.transactions.isEmpty()) container.addView(line("Nenhuma transação retornada."))
        state.transactions.forEach {
            container.addView(line("${it.createdAt ?: "Data não informada"} · ${it.planCode ?: "Plano"}\n${it.status ?: "Status não informado"} · ${it.amount?.let { amount -> currency.format(amount) } ?: "Valor não informado"} · ${it.paymentMethod ?: "Método não informado"}"))
        }
        AlertDialog.Builder(requireContext()).setTitle("Histórico de transações")
            .setView(container)
            .setPositiveButton(if (state.hasMoreTransactions) "Carregar mais" else "Fechar") { _, _ ->
                if (state.hasMoreTransactions) model.loadMoreTransactions()
            }
            .setNegativeButton("Fechar", null).show()
    }

    private fun showGroups() {
        val groups = model.state.value?.groups.orEmpty()
        if (groups.isEmpty()) { message("Meus grupos", "Nenhum grupo retornado. Use Criar grupo abaixo dos limites do plano."); return }
        AlertDialog.Builder(requireContext()).setTitle("Meus grupos")
            .setItems(groups.map { it.name ?: "Grupo sem nome" }.toTypedArray()) { _, index ->
                groups[index].id?.let(model::selectGroup)
            }.setNegativeButton("Fechar", null).show()
    }

    private fun showCreateGroup() {
        val input = EditText(requireContext()).apply { hint = "Nome do grupo"; filters = arrayOf(android.text.InputFilter.LengthFilter(120)) }
        AlertDialog.Builder(requireContext()).setTitle("Criar grupo").setView(input)
            .setPositiveButton("Continuar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) message("Grupo", "Informe o nome do grupo.")
                else confirm("Criar o grupo '$name'?") { model.createGroup(name) }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun showCreateStock() {
        val input = EditText(requireContext()).apply { hint = "Nome do estoque"; filters = arrayOf(android.text.InputFilter.LengthFilter(120)) }
        AlertDialog.Builder(requireContext()).setTitle("Criar estoque").setView(input)
            .setPositiveButton("Continuar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) message("Estoque", "Informe o nome do estoque.")
                else confirm("Criar o estoque '$name' neste grupo?") { model.createStock(name) }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun showInvite() {
        val group = model.state.value?.selectedGroup
        if (group?.isOwner != true) {
            message("Convite", "Selecione um grupo que você administra em Meus grupos.")
            return
        }
        val input = EditText(requireContext()).apply {
            hint = "E-mail do convidado"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val role = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                listOf("Editor", "Visualizador"))
        }
        val form = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
            addView(input)
            addView(role)
        }
        AlertDialog.Builder(requireContext()).setTitle("Convidar membro").setView(form)
            .setPositiveButton("Confirmar") { _, _ ->
                val email = input.text.toString().trim()
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
                    confirm("Enviar convite para $email?") { model.invite(email, if (role.selectedItemPosition == 0) "EDITOR" else "VIEWER") }
                else message("Convite", "Informe um e-mail válido.")
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun limitsText(limits: PlanLimits): String = listOfNotNull(
        limits.maxGroupsCreated?.let { "Grupos: $it" },
        limits.maxGroupMembers?.let { "Membros por grupo: $it" },
        limits.maxStocks?.let { "Estoques: $it" },
        limits.maxProductsPerStock?.let { "Produtos por estoque: $it" },
        limits.allowOwnProducts?.let { "Produtos próprios: ${if (it) "sim" else "não"}" },
        limits.allowSavedRecipes?.let { "Receitas salvas: ${if (it) "sim" else "não"}" },
        limits.allowMoneySaving?.let { "MoneySaving: ${if (it) "sim" else "não"}" },
        limits.hasMonthlyReport?.let { "Relatório mensal: ${if (it) "sim" else "não"}" },
        limits.enterprise?.let { "Plano empresarial: ${if (it) "sim" else "não"}" },
        limits.costPerPublishedAd?.let { "Custo por anúncio publicado: ${currency.format(it)}" }
    ).joinToString("\n")

    private fun confirm(text: String, action: () -> Unit) {
        AlertDialog.Builder(requireContext()).setMessage(text)
            .setNegativeButton("Voltar", null).setPositiveButton("Confirmar") { _, _ -> action() }.show()
    }
    private fun message(title: String, body: String) {
        AlertDialog.Builder(requireContext()).setTitle(title).setMessage(body).setPositiveButton("OK", null).show()
    }
    private fun line(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 14f
        setPadding(8, 12, 8, 12)
    }
    private fun button(value: String, action: () -> Unit) = Button(requireContext()).apply {
        text = value
        setOnClickListener { action() }
    }
}
