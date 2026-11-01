package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.confirmedMoneySavingAmount
import com.example.frigus_mobile.data.model.isMoneySavingPlanRequired
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency

class MoneySavingFragment : Fragment(R.layout.fragment_money_saving) {
    private val model: MoneySavingViewModel by lazy {
        ViewModelProvider(requireActivity(), MoneySavingViewModel.Factory(requireContext()))[MoneySavingViewModel::class.java]
    }
    private val locale get() = resources.configuration.locales[0]
    private val money get() = NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("BRL") }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener { parentFragmentManager.popBackStack() }
        listOf(R.id.btnInfo, R.id.btnComoValoresCalculados).forEach { id ->
            view.findViewById<View>(id).setOnClickListener {
                AlertDialog.Builder(requireContext()).setTitle(R.string.money_saving_title)
                    .setMessage(R.string.money_saving_explanation).setPositiveButton(R.string.money_saving_close, null).show()
            }
        }
        view.findViewById<View>(R.id.moneyPreviousMonth).setOnClickListener { model.changeMonth(-1) }
        view.findViewById<View>(R.id.moneyNextMonth).setOnClickListener { model.changeMonth(1) }
        view.findViewById<View>(R.id.moneyRefresh).setOnClickListener { model.load() }
        view.findViewById<View>(R.id.moneyMore).setOnClickListener { model.nextPage() }
        view.findViewById<View>(R.id.moneyUpgradePlan).setOnClickListener {
            parentFragmentManager.beginTransaction().replace(R.id.fragment_container, AssinaturaPlanosFragment())
                .addToBackStack(null).commit()
        }
        bindFilter(view.findViewById(R.id.moneyCategoryFilter), R.array.money_saving_categories) { position ->
            model.filter(MoneySavingViewModel.CATEGORIES[position], model.state.value?.kind)
        }
        bindFilter(view.findViewById(R.id.moneyKindFilter), R.array.money_saving_kinds) { position ->
            model.filter(model.state.value?.category, MoneySavingViewModel.KINDS[position])
        }
        view.findViewById<View>(R.id.btnGastosAlimentacao).setOnClickListener {
            model.filter(model.state.value?.category, "PURCHASE")
            scrollToEvents(view)
        }
        view.findViewById<View>(R.id.btnEconomiaDesperdicio).setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle(R.string.money_saving_waste)
                .setItems(R.array.money_saving_effects) { _, selected ->
                    model.filter(model.state.value?.category, if (selected == 0) "CONSUMED" else "DISCARDED")
                    scrollToEvents(view)
                }.setNegativeButton(R.string.money_saving_close, null).show()
        }
        view.findViewById<View>(R.id.btnHistoricoFinanceiro).setOnClickListener {
            model.filter(null, null)
            scrollToEvents(view)
        }
        model.state.observe(viewLifecycleOwner) { render(view, it) }
        model.loadIfNeeded()
    }

    private fun scrollToEvents(view: View) {
        val target = view.findViewById<View>(R.id.moneyEventsCard)
        view.findViewById<ScrollView>(R.id.moneyScroll).post {
            if (this.view === view) view.findViewById<ScrollView>(R.id.moneyScroll).smoothScrollTo(0, target.top)
        }
    }

    private fun bindFilter(spinner: Spinner, labels: Int, changed: (Int) -> Unit) {
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(labels))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, selected: View?, position: Int, id: Long) {
                if (position != spinner.selectedItemPosition) return
                val state = model.state.value ?: return
                val codes = if (spinner.id == R.id.moneyCategoryFilter) MoneySavingViewModel.CATEGORIES else MoneySavingViewModel.KINDS
                val current = if (spinner.id == R.id.moneyCategoryFilter) state.category else state.kind
                if (codes.getOrNull(position) != current) changed(position)
            }
        }
    }

    private fun render(view: View, state: MoneySavingState) {
        listOf(R.id.moneyPreviousMonth, R.id.moneyNextMonth, R.id.moneyRefresh, R.id.moneyCategoryFilter,
            R.id.moneyKindFilter, R.id.btnGastosAlimentacao, R.id.btnEconomiaDesperdicio, R.id.btnHistoricoFinanceiro)
            .forEach { view.findViewById<View>(it).isEnabled = !state.loading }
        view.findViewById<Spinner>(R.id.moneyCategoryFilter).setSelection(MoneySavingViewModel.CATEGORIES.indexOf(state.category).coerceAtLeast(0))
        view.findViewById<Spinner>(R.id.moneyKindFilter).setSelection(MoneySavingViewModel.KINDS.indexOf(state.kind).coerceAtLeast(0))
        view.findViewById<TextView>(R.id.tvMesReferencia).text = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        view.findViewById<View>(R.id.moneyProgress).visibility = if (state.loading) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.moneyUpgradePlan).apply {
            visibility = if (isMoneySavingPlanRequired(state.error)) View.VISIBLE else View.GONE
            isEnabled = !state.loading
        }
        view.findViewById<TextView>(R.id.moneyStatus).apply {
            visibility = if (state.loading || state.error != null || state.summary?.hasFinancialData != true) View.VISIBLE else View.GONE
            text = when {
                state.loading -> getString(R.string.money_saving_loading)
                state.error != null -> state.error.userMessage(getString(when {
                    state.error.status == 403 -> R.string.money_saving_access_denied
                    state.error.code == "NETWORK_ERROR" -> R.string.money_saving_network_error
                    state.error.code == "INVALID_RESPONSE" -> R.string.money_saving_invalid_response
                    else -> R.string.money_saving_load_error
                }))
                else -> getString(R.string.money_saving_no_confirmed_values)
            }
        }
        val summary = state.summary
        val verified = summary?.hasFinancialData == true
        fun amount(value: Double?) = this.amount(verified, value)
        view.findViewById<TextView>(R.id.tvEconomiaObtida).text = amount(summary?.saving?.amount)
        view.findViewById<TextView>(R.id.tvGastoAlimentacao).text = amount(summary?.spending?.amount)
        view.findViewById<TextView>(R.id.tvDesperdicio).text = amount(summary?.loss?.amount)
        view.findViewById<TextView>(R.id.tvComparadoGastos).text = summary?.spendingChangePercent
            ?.takeIf { verified && it.isFinite() }?.let {
                getString(R.string.money_saving_spending_change, NumberFormat.getNumberInstance(locale).format(it))
            } ?: getString(R.string.money_saving_unknown)
        view.findViewById<TextView>(R.id.tvComparadoDesperdicio).text = summary?.loss?.actions?.takeIf { it >= 0 }
            ?.let { getString(R.string.money_saving_loss_actions, NumberFormat.getIntegerInstance(locale).format(it)) }
            ?: getString(R.string.money_saving_not_informed)
        view.findViewById<TextView>(R.id.moneyCategoryTotals).text = summary?.categories
            ?.takeIf { verified }?.joinToString("\n") {
                getString(R.string.money_saving_breakdown, category(it.category), amount(it.spent), amount(it.saved), amount(it.lost))
            }.orEmpty()
        view.findViewById<LinearLayout>(R.id.moneyMonths).apply {
            removeAllViews()
            val months = summary?.months.orEmpty().takeIf { verified }.orEmpty()
            if (months.isEmpty()) addView(row(getString(R.string.money_saving_no_confirmed_values)))
            months.forEach { point ->
                addView(row(getString(R.string.money_saving_breakdown, point.month ?: getString(R.string.money_saving_not_informed),
                    amount(point.spent), amount(point.saved), amount(point.lost))))
            }
        }
        view.findViewById<View>(R.id.moneyMore).apply {
            visibility = if (state.hasMore && state.error == null) View.VISIBLE else View.GONE
            isEnabled = !state.loading
        }
        view.findViewById<LinearLayout>(R.id.moneyEvents).apply {
            removeAllViews()
            if (state.events.isEmpty() && !state.loading && state.error == null) addView(row(getString(R.string.money_saving_empty_events)))
            state.events.forEach { event ->
                val kind = MoneySavingViewModel.KINDS.indexOf(event.kind)
                val label = resources.getStringArray(R.array.money_saving_kinds).getOrNull(kind)
                    ?: getString(R.string.money_saving_event)
                addView(row(getString(R.string.money_saving_event_row,
                    event.date ?: getString(R.string.money_saving_not_informed),
                    event.productName ?: getString(R.string.money_saving_product), label,
                    event.quantity?.takeIf { it > 0 }?.let { NumberFormat.getIntegerInstance(locale).format(it) }
                        ?: getString(R.string.money_saving_not_informed), event.unitOfMeasure.orEmpty(),
                    this@MoneySavingFragment.amount(event.verifiedValue, event.amount),
                    getString(if (event.includedInMetric) R.string.money_saving_included else R.string.money_saving_excluded))))
            }
        }
    }

    private fun category(code: String?): String {
        val index = MoneySavingViewModel.CATEGORIES.indexOf(code)
        return if (index > 0) resources.getStringArray(R.array.money_saving_categories)[index]
            else getString(R.string.money_saving_category)
    }

    private fun amount(verified: Boolean, value: Double?): String = confirmedMoneySavingAmount(verified, value)
        ?.let { money.format(it) } ?: getString(R.string.money_saving_unknown)

    private fun row(value: String) = TextView(requireContext()).apply {
        text = value
        setTextColor(requireContext().getColor(R.color.color_text_primary))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.account_body_size))
        val gap = resources.getDimensionPixelSize(R.dimen.account_gap)
        setPadding(0, gap, 0, gap)
    }
}
