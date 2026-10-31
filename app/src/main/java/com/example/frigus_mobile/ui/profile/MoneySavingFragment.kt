package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class MoneySavingFragment : Fragment() {
    private val model: MoneySavingViewModel by lazy {
        ViewModelProvider(this, MoneySavingViewModel.Factory(requireContext().applicationContext))[MoneySavingViewModel::class.java]
    }
    private val money = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val categories = listOf(null, "HORTIFRUTI", "MEAT", "DAIRY", "GRAIN", "BEVERAGE")
    private val kinds = listOf(null, "PURCHASE", "CONSUMED", "DISCARDED")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_money_saving, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.previousMonth).setOnClickListener { model.changeMonth(-1) }
        view.findViewById<View>(R.id.nextMonth).setOnClickListener { model.changeMonth(1) }
        view.findViewById<View>(R.id.retry).setOnClickListener { model.load() }
        view.findViewById<View>(R.id.loadMore).setOnClickListener { model.nextPage() }
        bindFilter(view.findViewById(R.id.categoryFilter),
            listOf("Todas as categorias", "Hortifruti", "Carnes", "Laticínios", "Grãos", "Bebidas")) {
            model.filter(categories[it], model.state.value?.kind)
        }
        bindFilter(view.findViewById(R.id.kindFilter),
            listOf("Todos os tipos", "Compras", "Consumo", "Descarte")) {
            model.filter(model.state.value?.category, kinds[it])
        }
        model.state.observe(viewLifecycleOwner) { render(view, it) }
        if (model.state.value?.summary == null) model.load()
    }

    private fun bindFilter(spinner: Spinner, labels: List<String>, changed: (Int) -> Unit) {
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.setSelection(if (spinner.id == R.id.categoryFilter)
            categories.indexOf(model.state.value?.category).coerceAtLeast(0)
            else kinds.indexOf(model.state.value?.kind).coerceAtLeast(0))
        var first = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, selected: View?, position: Int, id: Long) {
                if (first) { first = false; return }
                changed(position)
            }
        }
    }

    private fun render(view: View, state: MoneySavingState) {
        listOf(R.id.categoryFilter, R.id.kindFilter, R.id.previousMonth, R.id.nextMonth, R.id.retry)
            .forEach { view.findViewById<View>(it).isEnabled = !state.loading }
        view.findViewById<TextView>(R.id.periodLabel).text =
            SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")).format(state.month.time)
        val status = view.findViewById<TextView>(R.id.status)
        status.text = when {
            state.loading -> "Carregando dados do grupo..."
            state.error != null -> state.error
            state.summary == null -> "Nenhum dado disponível."
            !state.summary.hasFinancialData -> "Sem valores financeiros confirmados neste período."
            else -> ""
        }
        view.findViewById<Button>(R.id.retry).visibility = if (state.error != null) View.VISIBLE else View.GONE
        view.findViewById<Button>(R.id.loadMore).visibility = if (state.hasMore && !state.loading) View.VISIBLE else View.GONE
        val summary = state.summary
        val confirmed = summary?.hasFinancialData == true
        fun confirmedAmount(value: Double?): String = if (confirmed) amount(value) else "Valor não confirmado"
        view.findViewById<TextView>(R.id.summary).text = if (summary == null) "" else buildString {
            appendLine("Gastos: ${confirmedAmount(summary.spending?.amount)}")
            appendLine("Economia: ${confirmedAmount(summary.saving?.amount)}")
            appendLine("Perdas: ${confirmedAmount(summary.loss?.amount)}")
            if (confirmed) summary.spendingChangePercent?.let { append("Variação de gastos: ${it}%") }
        }
        view.findViewById<TextView>(R.id.categories).text =
            summary?.categories?.takeIf { confirmed && it.isNotEmpty() }?.joinToString("\n") {
                "${it.category ?: "Categoria"}: gastos ${confirmedAmount(it.spent)}, economia ${confirmedAmount(it.saved)}, perdas ${confirmedAmount(it.lost)}"
            }?.let { "Categorias\n$it" } ?: ""
        view.findViewById<TextView>(R.id.months).text =
            summary?.months?.takeIf { confirmed && it.isNotEmpty() }?.joinToString("\n") {
                "${it.month ?: "Mês"}: ${confirmedAmount(it.spent)} gastos · ${confirmedAmount(it.saved)} economia · ${confirmedAmount(it.lost)} perdas"
            }?.let { "Últimos meses\n$it" } ?: ""
        val eventList = view.findViewById<LinearLayout>(R.id.events)
        eventList.removeAllViews()
        if (state.events.isEmpty() && !state.loading && state.error == null) {
            eventList.addView(row("Nenhum evento para os filtros selecionados."))
        }
        state.events.forEach { event ->
            val value = if (event.verifiedValue && event.amount != null) amount(event.amount)
                else "Valor não confirmado"
            eventList.addView(row("${event.date.orEmpty()} · ${event.productName ?: "Produto"}\n${event.kind ?: "Evento"} · ${event.quantity ?: "—"} ${event.unitOfMeasure.orEmpty()} · $value${event.reasonCode?.let { " · $it" } ?: ""}"))
        }
    }

    private fun amount(value: Double?): String = value?.let { money.format(it) } ?: "Valor não confirmado"
    private fun row(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 15f
        setPadding(8, 14, 8, 14)
    }
}
