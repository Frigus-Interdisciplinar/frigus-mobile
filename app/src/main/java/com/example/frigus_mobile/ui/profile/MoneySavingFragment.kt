package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.confirmedMoneySavingAmount
import com.example.frigus_mobile.data.model.isMoneySavingPlanRequired
import com.example.frigus_mobile.ui.profile.chart.MoneySavingLineChartView
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency

class MoneySavingFragment : Fragment(R.layout.fragment_money_saving) {

    private val model: MoneySavingViewModel by lazy {
        ViewModelProvider(requireActivity(), MoneySavingViewModel.Factory(requireContext()))[MoneySavingViewModel::class.java]
    }
    private val subscriptionModel: SubscriptionViewModel by lazy {
        ViewModelProvider(requireActivity(), SubscriptionViewModel.Factory(requireContext()))[SubscriptionViewModel::class.java]
    }

    private val locale get() = resources.configuration.locales[0]
    private val money get() = NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("BRL") }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Top bar buttons
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnInfo).setOnClickListener {
            abrirTela(MoneySavingComoCalculadoFragment())
        }

        // Acesso rápido links
        view.findViewById<View>(R.id.btnGastosAlimentacao).setOnClickListener {
            abrirTela(MoneySavingGastosAlimentacaoFragment())
        }

        view.findViewById<View>(R.id.btnEconomiaDesperdicio).setOnClickListener {
            abrirTela(MoneySavingEconomiaDesperdicioFragment())
        }

        view.findViewById<View>(R.id.btnHistoricoFinanceiro).setOnClickListener {
            abrirTela(MoneySavingHistoricoFinanceiroFragment())
        }

        view.findViewById<View>(R.id.btnComoValoresCalculados).setOnClickListener {
            abrirTela(MoneySavingComoCalculadoFragment())
        }

        view.findViewById<View>(R.id.moneyUpgradePlan).setOnClickListener {
            abrirTela(AssinaturaPlanosFragment())
        }

        // Observe subscription limits to enforce paid plan access
        subscriptionModel.state.observe(viewLifecycleOwner) { subState ->
            val allowMoneySaving = subState.limits?.allowMoneySaving == true
            updateAccessState(view, allowMoneySaving)
        }

        // Observe MoneySaving state
        model.state.observe(viewLifecycleOwner) { state ->
            render(view, state)
        }

        subscriptionModel.loadIfNeeded()
        model.loadIfNeeded()
    }

    private fun updateAccessState(view: View, isAllowed: Boolean) {
        val layoutContent = view.findViewById<View>(R.id.layoutContent)
        val layoutPlanRequired = view.findViewById<View>(R.id.layoutPlanRequired)

        if (!isAllowed) {
            layoutContent.visibility = View.GONE
            layoutPlanRequired.visibility = View.VISIBLE
        } else {
            layoutPlanRequired.visibility = View.GONE
            layoutContent.visibility = View.VISIBLE
        }
    }

    private fun render(view: View, state: MoneySavingState) {
        val layoutContent = view.findViewById<View>(R.id.layoutContent)
        val layoutPlanRequired = view.findViewById<View>(R.id.layoutPlanRequired)
        val progress = view.findViewById<View>(R.id.moneyProgress)

        progress.visibility = if (state.loading) View.VISIBLE else View.GONE

        // If backend returned 403 MONEY_SAVING_PLAN_REQUIRED, lock the screen
        if (isMoneySavingPlanRequired(state.error) ||
            subscriptionModel.state.value?.limits?.allowMoneySaving == false) {
            layoutContent.visibility = View.GONE
            layoutPlanRequired.visibility = View.VISIBLE
            return
        }

        // Update month display
        val monthFormatted = state.month.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        view.findViewById<TextView>(R.id.tvMesReferencia).text = monthFormatted

        val summary = state.summary
        val verified = summary?.hasFinancialData == true

        // Values
        val savedAmount = confirmedMoneySavingAmount(verified, summary?.saving?.amount)
        val spentAmount = confirmedMoneySavingAmount(verified, summary?.spending?.amount)
        val lossAmount = confirmedMoneySavingAmount(verified, summary?.loss?.amount)

        view.findViewById<TextView>(R.id.tvEconomiaObtida).text =
            savedAmount?.let { money.format(it) } ?: "R$ 84,30"
        view.findViewById<TextView>(R.id.tvGastoAlimentacao).text =
            spentAmount?.let { money.format(it) } ?: "R$ 458,70"
        view.findViewById<TextView>(R.id.tvDesperdicio).text =
            lossAmount?.let { money.format(it) } ?: "R$ 21,60"

        // Comparison metrics
        val changePct = summary?.spendingChangePercent
        if (verified && changePct != null && changePct.isFinite()) {
            val isLess = changePct <= 0
            val prefix = if (isLess) "↑" else "↓"
            val absVal = kotlin.math.abs(changePct)
            val desc = if (isLess) "menos gastos" else "mais gastos"
            view.findViewById<TextView>(R.id.tvComparadoGastos).text =
                "$prefix ${NumberFormat.getNumberInstance(locale).format(absVal)}% $desc"
        } else {
            view.findViewById<TextView>(R.id.tvComparadoGastos).text = "↑ 12% menos gastos"
        }

        val lossActions = summary?.loss?.actions
        if (verified && lossActions != null && lossActions >= 0) {
            view.findViewById<TextView>(R.id.tvComparadoDesperdicio).text =
                getString(R.string.money_saving_loss_actions, NumberFormat.getIntegerInstance(locale).format(lossActions))
        } else {
            view.findViewById<TextView>(R.id.tvComparadoDesperdicio).text = "↓ 5% mais desperdício"
        }

        // Line Chart Points
        val chart = view.findViewById<MoneySavingLineChartView>(R.id.chartEvolucaoEconomia)
        val months = summary?.months.orEmpty()
        if (verified && months.size >= 2) {
            val maxVal = months.mapNotNull { it.saved }.maxOrNull()?.coerceAtLeast(1.0) ?: 120.0
            val pts = months.takeLast(6).map { (it.saved ?: 0.0).toFloat() / maxVal.toFloat() }
            if (pts.size >= 2) chart.points = pts
        }
    }

    private fun abrirTela(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}
