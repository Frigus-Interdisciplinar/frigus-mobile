package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.confirmedMoneySavingAmount
import com.example.frigus_mobile.ui.profile.chart.MoneySavingBarChartView
import com.example.frigus_mobile.ui.profile.chart.MoneySavingDonutChartView
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency

class MoneySavingGastosAlimentacaoFragment : Fragment(R.layout.fragment_money_saving_gastos_alimentacao) {

    private val model: MoneySavingViewModel by lazy {
        ViewModelProvider(requireActivity(), MoneySavingViewModel.Factory(requireContext()))[MoneySavingViewModel::class.java]
    }
    private val locale get() = resources.configuration.locales[0]
    private val money get() = NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("BRL") }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnPeriodo).setOnClickListener {
            showMonthDialog()
        }

        view.findViewById<View>(R.id.btnDetalhesGastos).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MoneySavingDetalhesGastosFragment())
                .addToBackStack(null)
                .commit()
        }

        model.state.observe(viewLifecycleOwner) { state ->
            val monthText = state.month.format(DateTimeFormatter.ofPattern("MMMM/yyyy", locale))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            view.findViewById<TextView>(R.id.tvPeriodoSelecionado).text = monthText

            val summary = state.summary
            val verified = summary?.hasFinancialData == true

            val spentAmount = confirmedMoneySavingAmount(verified, summary?.spending?.amount)
            view.findViewById<TextView>(R.id.tvTotalGasto).text =
                spentAmount?.let { money.format(it) } ?: "R$ 458,70"

            val prevSpent = summary?.previousSpending?.takeIf { verified && it.isFinite() && it > 0 }
            view.findViewById<TextView>(R.id.tvMesAnterior).text =
                prevSpent?.let { money.format(it) } ?: "R$ 502,10"

            val variation = summary?.spendingChangePercent
            if (verified && variation != null && variation.isFinite()) {
                val sign = if (variation <= 0) "↓" else "↑"
                val absVal = kotlin.math.abs(variation)
                view.findViewById<TextView>(R.id.tvVariacaoMesAnterior).text =
                    "$sign ${NumberFormat.getNumberInstance(locale).format(absVal)}%"
            } else {
                view.findViewById<TextView>(R.id.tvVariacaoMesAnterior).text = "↓ 8,6%"
            }

            val categories = summary?.categories.orEmpty()
            if (verified && categories.isNotEmpty()) {
                val totalSpent = (summary?.spending?.amount ?: 1.0).coerceAtLeast(1.0)
                val slices = categories.map {
                    val catColor = when (it.category?.uppercase()) {
                        "MEAT" -> requireContext().getColor(R.color.color_category_meat)
                        "DAIRY" -> requireContext().getColor(R.color.color_category_dairy)
                        "HORTIFRUTI" -> requireContext().getColor(R.color.color_category_produce)
                        "BEVERAGE" -> requireContext().getColor(R.color.color_category_beverage)
                        else -> requireContext().getColor(R.color.color_category_other)
                    }
                    val pct = (((it.spent ?: 0.0) / totalSpent) * 100).toFloat()
                    MoneySavingDonutChartView.DonutSlice(catColor, pct)
                }
                if (slices.isNotEmpty()) {
                    view.findViewById<MoneySavingDonutChartView>(R.id.chartGastosCategoria).slices = slices
                }
            }

            val months = summary?.months.orEmpty()
            if (verified && months.size >= 2) {
                val maxSpent = months.mapNotNull { it.spent }.maxOrNull()?.coerceAtLeast(1.0) ?: 600.0
                val barVals = months.takeLast(6).map { (it.spent ?: 0.0).toFloat() / maxSpent.toFloat() }
                if (barVals.size >= 2) {
                    view.findViewById<MoneySavingBarChartView>(R.id.chartEvolucaoGastos).barValues = barVals
                }
            }
        }
    }

    private fun showMonthDialog() {
        val months = (0..5).map { model.state.value?.month?.minusMonths(it.toLong()) ?: java.time.YearMonth.now() }
        val labels = months.map {
            it.format(DateTimeFormatter.ofPattern("MMMM/yyyy", locale))
                .replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(locale) else c.toString() }
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.money_saving_period)
            .setItems(labels) { _, which ->
                val selected = months[which]
                val current = model.state.value?.month ?: java.time.YearMonth.now()
                val delta = (selected.year * 12 + selected.monthValue) - (current.year * 12 + current.monthValue)
                if (delta != 0) model.changeMonth(delta)
            }
            .setNegativeButton(R.string.account_cancel, null)
            .show()
    }
}
