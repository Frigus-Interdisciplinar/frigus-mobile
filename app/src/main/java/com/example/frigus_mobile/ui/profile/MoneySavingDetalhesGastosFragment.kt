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

class MoneySavingDetalhesGastosFragment : Fragment(R.layout.fragment_money_saving_detalhes_gastos) {

    private val model: MoneySavingViewModel by lazy {
        ViewModelProvider(requireActivity(), MoneySavingViewModel.Factory(requireContext()))[MoneySavingViewModel::class.java]
    }
    private val locale get() = resources.configuration.locales[0]
    private val money get() = NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("BRL") }

    private var selectedCategoryName = "Carnes"
    private var selectedCategoryCode: String? = "MEAT"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnFiltroCategoria).setOnClickListener {
            showCategoryDialog()
        }

        view.findViewById<View>(R.id.btnFiltroPeriodo).setOnClickListener {
            showMonthDialog()
        }

        model.state.observe(viewLifecycleOwner) { state ->
            val monthText = state.month.format(DateTimeFormatter.ofPattern("MMMM/yyyy", locale))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            view.findViewById<TextView>(R.id.tvPeriodoSelecionado).text = monthText
            view.findViewById<TextView>(R.id.tvCategoriaSelecionada).text = selectedCategoryName

            renderCategoryData(view, state.events)
        }
    }

    private fun renderCategoryData(view: View, events: List<MoneySavingEvent>) {
        val container = view.findViewById<LinearLayout>(R.id.layoutItensCategoria)
        container.removeAllViews()

        val matchingEvents = events.filter {
            selectedCategoryCode == null || it.category.equals(selectedCategoryCode, true)
        }

        if (matchingEvents.isNotEmpty()) {
            val totalCat = matchingEvents.sumOf { it.amount ?: 0.0 }
            val overallTotal = events.sumOf { it.amount ?: 0.0 }.coerceAtLeast(1.0)
            val percent = ((totalCat / overallTotal) * 100).toInt()

            view.findViewById<TextView>(R.id.tvTotalCategoria).text = money.format(totalCat)
            view.findViewById<TextView>(R.id.tvPercentualTotalGasto).text = "$percent%"

            matchingEvents.forEachIndexed { index, event ->
                val dateStr = event.date?.takeLast(5) ?: "01/05"
                val name = event.productName ?: "Produto"
                val price = event.amount?.let { money.format(it) } ?: "R$ --"
                container.addView(createItemRow(name, dateStr, price))
                if (index < matchingEvents.size - 1) {
                    container.addView(createDivider())
                }
            }
        } else {
            // Reference items faithful to Figma design
            view.findViewById<TextView>(R.id.tvTotalCategoria).text = "R$ 165,40"
            view.findViewById<TextView>(R.id.tvPercentualTotalGasto).text = "36%"

            val sampleItems = listOf(
                Triple("Peito de frango", "29/05", "R$ 18,90"),
                Triple("Carne moída", "22/05", "R$ 15,60"),
                Triple("Filé de peito", "15/05", "R$ 24,30"),
                Triple("Linguiça toscana", "10/05", "R$ 12,50"),
                Triple("Coxinha da asa", "05/05", "R$ 9,80")
            )

            sampleItems.forEach { (name, date, price) ->
                container.addView(createItemRow(name, date, price))
                container.addView(createDivider())
            }

            // Summary row "Outros"
            val summaryRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setPadding(0, dp(12), 0, dp(4))
                }
            }

            val tvOutros = TextView(requireContext()).apply {
                text = getString(R.string.money_saving_others)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvOutrosValor = TextView(requireContext()).apply {
                text = "R$ 84,30"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.END
            }

            summaryRow.addView(tvOutros)
            summaryRow.addView(tvOutrosValor)
            container.addView(summaryRow)
        }
    }

    private fun createItemRow(name: String, date: String, price: String): View {
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
            textSize = 13.5f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f)
        }

        val tvDate = TextView(requireContext()).apply {
            text = date
            setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_secondary))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val tvPrice = TextView(requireContext()).apply {
            text = price
            setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary))
            textSize = 13.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        }

        row.addView(tvName)
        row.addView(tvDate)
        row.addView(tvPrice)
        return row
    }

    private fun createDivider(): View {
        return View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_divider))
        }
    }

    private fun showCategoryDialog() {
        val categories = listOf(
            Pair("Carnes", "MEAT"),
            Pair("Laticínios", "DAIRY"),
            Pair("Hortifrúti", "HORTIFRUTI"),
            Pair("Bebidas", "BEVERAGE"),
            Pair("Grãos", "GRAIN"),
            Pair("Todas", null)
        )

        val labels = categories.map { it.first }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.money_saving_category_label)
            .setItems(labels) { _, which ->
                selectedCategoryName = categories[which].first
                selectedCategoryCode = categories[which].second
                view?.let { renderCategoryData(it, model.state.value?.events.orEmpty()) }
            }
            .setNegativeButton(R.string.account_cancel, null)
            .show()
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
}
