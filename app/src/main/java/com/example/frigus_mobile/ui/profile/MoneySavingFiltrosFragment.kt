package com.example.frigus_mobile.ui.profile

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class MoneySavingFiltrosFragment : Fragment(R.layout.fragment_money_saving_filtros) {

    private val model: MoneySavingViewModel by lazy {
        ViewModelProvider(requireActivity(), MoneySavingViewModel.Factory(requireContext()))[MoneySavingViewModel::class.java]
    }
    private val locale get() = resources.configuration.locales[0]

    private var selectedCategoryName = "Carnes"
    private var selectedCategoryCode: String? = "MEAT"
    private var selectedStatusName = "Descartado"
    private var selectedStatusCode: String? = "DISCARDED"

    private var fromDate: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var toDate: LocalDate = LocalDate.now()

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

        view.findViewById<View>(R.id.btnDataDe).setOnClickListener {
            showDatePicker(fromDate) { date ->
                fromDate = date
                view.findViewById<TextView>(R.id.tvDataDe).text =
                    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }
        }

        view.findViewById<View>(R.id.btnDataAte).setOnClickListener {
            showDatePicker(toDate) { date ->
                toDate = date
                view.findViewById<TextView>(R.id.tvDataAte).text =
                    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }
        }

        view.findViewById<View>(R.id.btnFiltroStatus).setOnClickListener {
            showStatusDialog()
        }

        view.findViewById<View>(R.id.btnAplicarFiltros).setOnClickListener {
            model.filter(selectedCategoryCode, selectedStatusCode)
            parentFragmentManager.popBackStack()
        }

        model.state.observe(viewLifecycleOwner) { state ->
            val monthText = state.month.format(DateTimeFormatter.ofPattern("MMMM/yyyy", locale))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            view.findViewById<TextView>(R.id.tvPeriodoSelecionado).text = monthText
        }
    }

    private fun showCategoryDialog() {
        val categories = listOf(
            Pair("Carnes", "MEAT"),
            Pair("Laticínios", "DAIRY"),
            Pair("Hortifrúti", "HORTIFRUTI"),
            Pair("Grãos", "GRAIN"),
            Pair("Bebidas", "BEVERAGE"),
            Pair("Todas as categorias", null)
        )

        val labels = categories.map { it.first }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.money_saving_category_label)
            .setItems(labels) { _, which ->
                selectedCategoryName = categories[which].first
                selectedCategoryCode = categories[which].second
                view?.findViewById<TextView>(R.id.tvCategoriaSelecionada)?.text = selectedCategoryName
            }
            .setNegativeButton(R.string.account_cancel, null)
            .show()
    }

    private fun showStatusDialog() {
        val statuses = listOf(
            Pair("Descartado", "DISCARDED"),
            Pair("Consumido", "CONSUMED"),
            Pair("Compras", "PURCHASE"),
            Pair("Todos", null)
        )

        val labels = statuses.map { it.first }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.money_saving_th_status)
            .setItems(labels) { _, which ->
                selectedStatusName = statuses[which].first
                selectedStatusCode = statuses[which].second
                view?.findViewById<TextView>(R.id.tvStatusSelecionado)?.apply {
                    text = selectedStatusName
                    setTextColor(requireContext().getColor(
                        when (selectedStatusCode) {
                            "DISCARDED" -> R.color.color_red
                            "CONSUMED" -> R.color.color_green
                            else -> R.color.color_text_primary
                        }
                    ))
                }
            }
            .setNegativeButton(R.string.account_cancel, null)
            .show()
    }

    private fun showDatePicker(initial: LocalDate, onSelected: (LocalDate) -> Unit) {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                onSelected(LocalDate.of(year, month + 1, dayOfMonth))
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
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
