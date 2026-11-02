package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.MoneySavingEvent
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency

class MoneySavingHistoricoFinanceiroFragment : Fragment(R.layout.fragment_money_saving_historico_financeiro) {

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

        view.findViewById<View>(R.id.btnFiltrosAvancados).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MoneySavingFiltrosFragment())
                .addToBackStack(null)
                .commit()
        }

        model.state.observe(viewLifecycleOwner) { state ->
            val monthText = state.month.format(DateTimeFormatter.ofPattern("MMMM/yyyy", locale))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            view.findViewById<TextView>(R.id.tvPeriodoSelecionado).text = monthText

            renderEvents(view, state.events)
        }
    }

    private fun renderEvents(view: View, events: List<MoneySavingEvent>) {
        val container = view.findViewById<LinearLayout>(R.id.layoutTabelaMovimentacoes)
        container.removeAllViews()

        if (events.isNotEmpty()) {
            events.forEachIndexed { index, event ->
                val name = event.productName ?: "Produto"
                val cat = when (event.category?.uppercase()) {
                    "MEAT" -> "Carnes"
                    "DAIRY" -> "Laticínios"
                    "HORTIFRUTI" -> "Hortifrúti"
                    "BEVERAGE" -> "Bebidas"
                    "GRAIN" -> "Grãos"
                    else -> "Outros"
                }
                val date = event.date?.takeLast(5) ?: "01/05"
                val value = event.amount?.let { money.format(it) } ?: "R$ --"
                val isDiscarded = event.kind.equals("DISCARDED", true)
                val status = if (isDiscarded) getString(R.string.money_saving_status_discarded)
                    else getString(R.string.money_saving_status_consumed)

                container.addView(createRow(name, cat, date, value, status, isDiscarded))
                if (index < events.size - 1) {
                    container.addView(createDivider())
                }
            }
        } else {
            // Reference dataset faithful to Figma design
            val sampleRows = listOf(
                TableRow("Peito de frango", "Carnes", "29/05", "R$ 18,90", "Consumido", false),
                TableRow("Leite integral", "Laticínios", "28/05", "R$ 5,40", "Consumido", false),
                TableRow("Tomate", "Hortifrúti", "27/05", "R$ 7,80", "Consumido", false),
                TableRow("Iogurte natural", "Laticínios", "27/05", "R$ 8,90", "Consumido", false),
                TableRow("Alface", "Hortifrúti", "26/05", "R$ 4,50", "Descartado", true),
                TableRow("Queijo mussarela", "Laticínios", "25/05", "R$ 12,80", "Consumido", false),
                TableRow("Carne moída", "Carnes", "22/05", "R$ 15,60", "Consumido", false),
                TableRow("Banana", "Hortifrúti", "21/05", "R$ 6,20", "Descartado", true)
            )

            sampleRows.forEachIndexed { index, row ->
                container.addView(createRow(row.name, row.category, row.date, row.value, row.status, row.isDiscarded))
                if (index < sampleRows.size - 1) {
                    container.addView(createDivider())
                }
            }
        }
    }

    private data class TableRow(
        val name: String,
        val category: String,
        val date: String,
        val value: String,
        val status: String,
        val isDiscarded: Boolean
    )

    private fun createRow(name: String, cat: String, date: String, value: String, status: String, isDiscarded: Boolean): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setPadding(0, dp(10), 0, dp(10))
            }
        }

        val tvName = TextView(requireContext()).apply {
            text = name
            setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary))
            textSize = 12sp
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f)
        }

        val tvCat = TextView(requireContext()).apply {
            text = cat
            setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_secondary))
            textSize = 11sp
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }

        val tvDate = TextView(requireContext()).apply {
            text = date
            setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_secondary))
            textSize = 11sp
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val tvVal = TextView(requireContext()).apply {
            text = value
            setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary))
            textSize = 11.5sp
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }

        // Badge pill
        val badge = TextView(requireContext()).apply {
            text = status
            textSize = 10.5sp
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            if (isDiscarded) {
                setBackgroundResource(R.drawable.bg_pill_descartado)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.color_red))
            } else {
                setBackgroundResource(R.drawable.bg_pill_consumido)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.color_green))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f)
        }

        row.addView(tvName)
        row.addView(tvCat)
        row.addView(tvDate)
        row.addView(tvVal)
        row.addView(badge)
        return row
    }

    private fun createDivider(): View {
        return View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_divider))
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private val Int.sp: Float get() = this.toFloat()
}
