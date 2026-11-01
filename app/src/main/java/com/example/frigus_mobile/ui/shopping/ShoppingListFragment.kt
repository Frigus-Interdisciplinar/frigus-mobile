package com.example.frigus_mobile.ui.shopping

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ShoppingListItem
import com.example.frigus_mobile.data.model.ShoppingSyncStatus
import com.example.frigus_mobile.data.model.validShoppingPrice
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** The recovered composition displays an account-scoped local list; only stock transfer uses the network. */
class ShoppingListFragment : Fragment(R.layout.fragment_shopping_list) {
    private val model by lazy { ViewModelProvider(requireActivity())[ShoppingListViewModel::class.java] }
    private lateinit var adapter: ShoppingListAdapter
    private var transferDialog: AlertDialog? = null
    private var stockDialog: AlertDialog? = null
    private var stockSelectionError: com.example.frigus_mobile.data.model.ApiError? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnVoltarListaCompras).setOnClickListener {
            if (requireActivity() is OfflineShoppingActivity) requireActivity().finish()
            else requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_home
        }
        val search = view.findViewById<EditText>(R.id.etAdicionarProdutoLista)
        search.setHint(R.string.shopping_local_search)
        search.setText(model.query)
        search.doAfterTextChanged { model.setQuery(it?.toString().orEmpty()); render(view, model.state.value ?: ShoppingState()) }
        search.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH && search.text.isNotBlank()) {
                openAdd(search.text.toString().trim()); true
            } else false
        }
        adapter = ShoppingListAdapter(
            onItemClick = { item ->
                parentFragmentManager.beginTransaction().replace(R.id.fragment_container,
                    EditShoppingListItemFragment.newInstance(item.id)).addToBackStack(null).commit()
            },
            onCheckChanged = { item, checked ->
                adapter.setItems(model.state.value?.items.orEmpty().filter(::matches))
                if (checked) offerStock(item) else model.checkItem(item, false)
            }
        )
        view.findViewById<RecyclerView>(R.id.rvListaCompras).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ShoppingListFragment.adapter
        }
        view.findViewById<View>(R.id.btnAdicionarItemLista).setOnClickListener { openAdd(search.text.toString().trim().takeIf(String::isNotEmpty)) }
        view.findViewById<View>(R.id.btnTentarNovamenteListaCompras).setOnClickListener { model.reload(true) }
        view.findViewById<View>(R.id.btnShoppingSync).setOnClickListener { model.syncNow() }
        view.findViewById<View>(R.id.headerListaCompras).setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle(R.string.shopping_remote_controls)
                .setMessage(R.string.shopping_local_only)
                .setPositiveButton(android.R.string.ok, null).show()
        }
        model.state.observe(viewLifecycleOwner) { render(view, it) }
        model.loadIfNeeded()
    }

    private fun matches(item: ShoppingListItem) = item.name.contains(model.query.trim(), ignoreCase = true)

    private fun render(view: View, state: ShoppingState) {
        if (!::adapter.isInitialized) return
        adapter.setEnabled(true)
        val items = state.items.filter(::matches)
        adapter.setItems(items)
        view.findViewById<TextView>(R.id.tvShoppingSyncStatus).text = when (state.syncStatus) {
            ShoppingSyncStatus.LOCAL_PENDING -> if (state.syncPendingCount > 0)
                getString(R.string.shopping_sync_pending_count, state.syncPendingCount)
                else getString(R.string.shopping_sync_pending)
            ShoppingSyncStatus.SYNCING -> getString(R.string.shopping_sync_sending)
            ShoppingSyncStatus.SYNCED -> getString(R.string.shopping_sync_done)
            ShoppingSyncStatus.AUTH_REQUIRED -> getString(R.string.shopping_sync_auth)
            ShoppingSyncStatus.ERROR -> getString(R.string.shopping_sync_error)
        }
        view.findViewById<View>(R.id.btnShoppingSync).isEnabled = state.syncStatus != ShoppingSyncStatus.SYNCING
        view.findViewById<View>(R.id.progressListaCompras).visibility = if (state.transferring) View.VISIBLE else View.GONE
        val failed = state.error != null || state.uncertain
        view.findViewById<View>(R.id.estadoErroListaCompras).visibility = if (failed) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.tvErroListaCompras).text = shoppingErrorMessage(requireContext(), state)
        view.findViewById<View>(R.id.rvListaCompras).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        view.findViewById<TextView>(R.id.tvListaComprasVazia).apply {
            visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            setText(R.string.shopping_local_empty)
        }
        view.findViewById<View>(R.id.btnAdicionarItemLista).isEnabled = true
        view.findViewById<View>(R.id.btnTentarNovamenteListaCompras).isEnabled = !state.transferring
        if (state.error?.code != "SHOPPING_CHOOSE_STOCK") stockSelectionError = null
        else if (state.error !== stockSelectionError && state.stock == null && state.stocks.size > 1) {
            stockSelectionError = state.error
            stockDialog = showShoppingStockSelection(requireContext(), state) {
                model.selectStock(it)
                Toast.makeText(requireContext(), R.string.shopping_stock_selected, Toast.LENGTH_LONG).show()
            }
        }
        state.completion?.takeIf { it.kind in setOf(ShoppingCompletionKind.MARKED, ShoppingCompletionKind.TRANSFERRED) }?.let {
            model.consumeCompletion(it.id)
            Toast.makeText(requireContext(), if (it.kind == ShoppingCompletionKind.TRANSFERRED)
                R.string.shopping_remote_transferred else R.string.shopping_checked_local, Toast.LENGTH_LONG).show()
        }
    }

    private fun offerStock(item: ShoppingListItem) {
        AlertDialog.Builder(requireContext()).setTitle(R.string.shopping_remote_mark_title)
            .setMessage(getString(R.string.shopping_transfer_question, item.name))
            .setPositiveButton(R.string.shopping_transfer_yes) { _, _ -> showTransferForm(item) }
            .setNegativeButton(R.string.shopping_transfer_no) { _, _ -> model.checkItem(item, true) }
            .show()
    }

    private fun showTransferForm(item: ShoppingListItem) {
        val state = model.state.value ?: return
        if (state.transferring || state.uncertain) {
            val message = if (state.transferring) getString(R.string.shopping_stock_sending)
                else shoppingErrorMessage(requireContext(), state)
            AlertDialog.Builder(requireContext()).setMessage(message)
                .setPositiveButton(R.string.shopping_remote_refresh) { _, _ -> model.reload(true) }
                .setNegativeButton(android.R.string.cancel, null).show()
            return
        }
        if (transferDialog?.isShowing == true) return
        val form = layoutInflater.inflate(R.layout.dialog_shopping_stock_transfer, null)
        val place = form.findViewById<Spinner>(R.id.spinnerTransferPlace)
        val expiry = form.findViewById<EditText>(R.id.etTransferExpiry)
        val price = form.findViewById<EditText>(R.id.etTransferPrice)
        val allCodes = resources.getStringArray(R.array.shopping_remote_place_codes).toList()
        val allLabels = resources.getStringArray(R.array.shopping_remote_place_labels).toList()
        val codes = if (item.categoryCode == "GRAIN") listOf("PANTRY") else allCodes
        val labels = codes.map { allLabels[allCodes.indexOf(it)] }
        place.adapter = ArrayAdapter(requireContext(), R.layout.item_spinner_selected, labels).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        form.findViewById<View>(R.id.tvTransferGrainHint).visibility =
            if (item.categoryCode == "GRAIN") View.VISIBLE else View.GONE
        expiry.setOnClickListener {
            val today = LocalDate.now()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                expiry.setText(LocalDate.of(year, month + 1, day).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")))
                expiry.error = null
            }, today.year, today.monthValue - 1, today.dayOfMonth).apply {
                datePicker.minDate = System.currentTimeMillis()
            }.show()
        }
        val dialog = AlertDialog.Builder(requireContext()).setTitle(R.string.shopping_remote_transfer)
            .setView(form).setPositiveButton(R.string.shopping_remote_transfer, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        transferDialog = dialog
        dialog.setOnDismissListener { transferDialog = null }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val date = runCatching { LocalDate.parse(expiry.text.toString().trim(),
                    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)) }.getOrNull()
                if (date == null || date.isBefore(LocalDate.now())) {
                    expiry.error = getString(R.string.lista_validade_obrigatoria_erro)
                    return@setOnClickListener
                }
                val unitPrice = price.text.toString().trim().replace(',', '.').toBigDecimalOrNull()
                if (!validShoppingPrice(unitPrice)) {
                    price.error = getString(R.string.shopping_remote_price_invalid)
                    price.requestFocus()
                    return@setOnClickListener
                }
                dialog.dismiss()
                model.transferItem(item, date.toString(), codes[place.selectedItemPosition], requireNotNull(unitPrice))
            }
        }
        dialog.show()
    }

    private fun openAdd(initialName: String?) {
        parentFragmentManager.beginTransaction().replace(R.id.fragment_container,
            AddShoppingListItemFragment.newInstance(initialName, AddShoppingListItemFragment.DESTINATION_SHOPPING_LIST))
            .addToBackStack(null).commit()
    }

    override fun onDestroyView() {
        transferDialog?.dismiss()
        transferDialog = null
        stockDialog?.dismiss()
        stockDialog = null
        super.onDestroyView()
    }
}
