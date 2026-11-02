package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.confirmedMoneySavingAmount
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency

class MoneySavingDetalhesEconomiaFragment : Fragment(R.layout.fragment_money_saving_detalhes_economia) {

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

            val categories = summary?.categories.orEmpty()
            if (verified && categories.isNotEmpty()) {
                val carnes = categories.firstOrNull { it.category.equals("MEAT", true) }
                val laticinios = categories.firstOrNull { it.category.equals("DAIRY", true) }
                val hortifruti = categories.firstOrNull { it.category.equals("HORTIFRUTI", true) }
                val bebidas = categories.firstOrNull { it.category.equals("BEVERAGE", true) }
                val outros = categories.firstOrNull { it.category.equals("GRAIN", true) || it.category.equals("OTHER", true) }

                carnes?.saved?.let { view.findViewById<TextView>(R.id.tvEconomiaCarnes).text = money.format(it) }
                laticinios?.saved?.let { view.findViewById<TextView>(R.id.tvEconomiaLaticinios).text = money.format(it) }
                hortifruti?.saved?.let { view.findViewById<TextView>(R.id.tvEconomiaHortifruti).text = money.format(it) }
                bebidas?.saved?.let { view.findViewById<TextView>(R.id.tvEconomiaBebidas).text = money.format(it) }
                outros?.saved?.let { view.findViewById<TextView>(R.id.tvEconomiaOutros).text = money.format(it) }
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
                model.setMonth(months[which])
            }
            .setNegativeButton(R.string.account_cancel, null)
            .show()
    }
}
