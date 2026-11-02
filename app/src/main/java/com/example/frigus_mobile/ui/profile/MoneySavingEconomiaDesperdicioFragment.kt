package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.confirmedMoneySavingAmount
import com.example.frigus_mobile.ui.profile.chart.MoneySavingLineChartView
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency

class MoneySavingEconomiaDesperdicioFragment : Fragment(R.layout.fragment_money_saving_economia_desperdicio) {

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

        view.findViewById<View>(R.id.btnDetalhesEconomia).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MoneySavingDetalhesEconomiaFragment())
                .addToBackStack(null)
                .commit()
        }

        val chart = view.findViewById<MoneySavingLineChartView>(R.id.chartEvolucaoDesperdicio)
        chart.chartColor = ContextCompat.getColor(requireContext(), R.color.color_red)
        chart.yLabels = listOf("R$ 80", "R$ 60", "R$ 40", "R$ 20", "R$ 0")
        chart.points = listOf(0.85f, 0.65f, 0.78f, 0.50f, 0.38f, 0.30f)

        model.state.observe(viewLifecycleOwner) { state ->
            val monthText = state.month.format(DateTimeFormatter.ofPattern("MMMM/yyyy", locale))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            view.findViewById<TextView>(R.id.tvPeriodoSelecionado).text = monthText

            val summary = state.summary
            val verified = summary?.hasFinancialData == true

            val savedAmount = confirmedMoneySavingAmount(verified, summary?.saving?.amount)
            val lossAmount = confirmedMoneySavingAmount(verified, summary?.loss?.amount)

            view.findViewById<TextView>(R.id.tvEconomiaObtida).text =
                savedAmount?.let { money.format(it) } ?: "R$ 84,30"
            view.findViewById<TextView>(R.id.tvPerdasVencimento).text =
                lossAmount?.let { money.format(it) } ?: "R$ 21,60"

            val usedCount = summary?.saving?.actions ?: 32L
            val lossCount = summary?.loss?.actions ?: 4L
            val total = (usedCount + lossCount).coerceAtLeast(1L)

            view.findViewById<TextView>(R.id.tvCountAproveitados).text = usedCount.toString()
            view.findViewById<ProgressBar>(R.id.progressAproveitados).progress =
                ((usedCount.toDouble() / total) * 100).toInt()

            view.findViewById<TextView>(R.id.tvCountDescartados).text = lossCount.toString()
            view.findViewById<ProgressBar>(R.id.progressDescartados).progress =
                ((lossCount.toDouble() / total) * 100).toInt()

            val months = summary?.months.orEmpty()
            if (verified && months.size >= 2) {
                val maxLoss = months.mapNotNull { it.lost }.maxOrNull()?.coerceAtLeast(1.0) ?: 80.0
                val pts = months.takeLast(6).map { (it.lost ?: 0.0).toFloat() / maxLoss.toFloat() }
                if (pts.size >= 2) chart.points = pts
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
