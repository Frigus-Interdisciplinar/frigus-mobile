package com.example.frigus_mobile.ui.stock

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.stock.adapter.EstoqueProdutoAdapter
import java.text.NumberFormat

class EstoqueFragment : Fragment(R.layout.fragment_estoque) {
    private lateinit var model: InventoryViewModel
    private var inventoryScroll: NestedScrollView? = null
    private var restoredRevision: Long? = null
    private var scheduledRevision: Long? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model = ViewModelProvider(requireActivity())[InventoryViewModel::class.java]
        view.isSaveFromParentEnabled = false
        val scroll = view.findViewById<NestedScrollView>(R.id.inventoryScroll)
        inventoryScroll = scroll
        restoredRevision = null
        scheduledRevision = null
        val adapter = EstoqueProdutoAdapter(requireContext(), emptyList())
        view.findViewById<RecyclerView>(R.id.rvProximosVencimento).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        val stock = view.findViewById<Spinner>(R.id.inventoryStock)
        val place = view.findViewById<Spinner>(R.id.inventoryPlace)
        val category = view.findViewById<Spinner>(R.id.inventoryCategory)
        val search = view.findViewById<EditText>(R.id.etBuscarAlimento)
        val places = resources.getStringArray(R.array.inventory_place_codes)
        val categories = resources.getStringArray(R.array.inventory_category_codes)
        place.adapter = choices(resources.getStringArray(R.array.inventory_place_labels).toList())
        category.adapter = choices(resources.getStringArray(R.array.inventory_category_labels).toList())
        search.setText(model.state.value?.filters?.query.orEmpty())
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                model.state.value?.filters?.let { model.changeFilters(it.copy(query = s.toString())) }
            }
        })
        view.findViewById<View>(R.id.inventoryRefresh).setOnClickListener { model.refresh() }
        view.findViewById<View>(R.id.inventoryRetry).setOnClickListener { model.retry() }
        view.findViewById<View>(R.id.inventoryMore).setOnClickListener { model.loadMore() }

        var stockIds = listOf<Int?>(null)
        var stockLabels = emptyList<String>()
        var rendering = false
        stock.onItemSelectedListener = selection { position ->
            if (!rendering) model.state.value?.filters?.let {
                if (stock.isEnabled) model.changeFilters(it.copy(stockId = stockIds.getOrNull(position)))
            }
        }
        place.onItemSelectedListener = selection { position ->
            if (!rendering) model.state.value?.filters?.let {
                model.changeFilters(it.copy(storagePlace = places.getOrNull(position)?.takeIf(String::isNotEmpty)))
            }
        }
        category.onItemSelectedListener = selection { position ->
            if (!rendering) model.state.value?.filters?.let {
                model.changeFilters(it.copy(category = categories.getOrNull(position)?.takeIf(String::isNotEmpty)))
            }
        }

        model.state.observe(viewLifecycleOwner) { state ->
            rendering = true
            if (search.text.toString() != state.filters.query) search.setText(state.filters.query)
            val stocks = state.home?.stocks.orEmpty().filterNotNull()
            val labels = listOf(getString(R.string.inventory_all_stocks)) + stocks.map {
                getString(R.string.inventory_stock_option, it.name?.takeIf(String::isNotBlank)
                    ?: getString(R.string.inventory_unnamed_stock), it.id)
            }
            stockIds = listOf(null) + stocks.map { it.id }
            if (labels != stockLabels) {
                stockLabels = labels
                stock.adapter = choices(labels)
            }
            stock.setSelection(stockIds.indexOf(state.filters.stockId).coerceAtLeast(0))
            place.setSelection(places.indexOf(state.filters.storagePlace.orEmpty()).coerceAtLeast(0))
            category.setSelection(categories.indexOf(state.filters.category.orEmpty()).coerceAtLeast(0))
            stock.isEnabled = stocks.isNotEmpty()
            rendering = false
            adapter.updateList(state.items)
            render(view, state)
            if (!state.loading && restoredRevision != state.scrollRevision &&
                scheduledRevision != state.scrollRevision) {
                val revision = state.scrollRevision
                val position = model.scrollPosition
                scheduledRevision = revision
                scroll.doOnPreDraw {
                    if (inventoryScroll === scroll && model.state.value?.scrollRevision == revision) {
                        scroll.scrollTo(0, position)
                        restoredRevision = revision
                    }
                }
            }
        }
        model.loadIfNeeded()
    }

    override fun onPause() {
        rememberScroll()
        super.onPause()
    }

    override fun onDestroyView() {
        rememberScroll()
        inventoryScroll = null
        super.onDestroyView()
    }

    private fun rememberScroll() {
        val revision = restoredRevision ?: return
        inventoryScroll?.let { model.saveScrollPosition(it.scrollY, revision) }
    }

    private fun choices(labels: List<String>) =
        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun selection(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action(position)
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    private fun render(view: View, state: InventoryUiState) {
        view.findViewById<View>(R.id.inventoryProgress).visibility = if (state.loading) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.inventoryRefresh).isEnabled = !state.loading
        view.findViewById<View>(R.id.inventoryRetry).visibility = if (state.error != null) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.inventoryMore).apply {
            visibility = if (state.hasMore && state.error == null) View.VISIBLE else View.GONE
            isEnabled = !state.loading
        }
        view.findViewById<View>(R.id.inventoryAllStatusesNotice).visibility =
            if (state.showAllStatusesNotice) View.VISIBLE else View.GONE
        val home = state.home
        view.findViewById<View>(R.id.inventorySummary).visibility = if (home != null) View.VISIBLE else View.GONE
        if (home != null) {
            val number = NumberFormat.getIntegerInstance()
            fun count(value: Long?) = value?.takeIf { it >= 0 }?.let(number::format)
                ?: getString(R.string.inventory_unknown)
            view.findViewById<TextView>(R.id.inventorySummaryText).text = getString(
                R.string.inventory_group_counts, count(home.summary?.itemCount),
                count(home.statusCounts?.fresh), count(home.statusCounts?.nearExpiration),
                count(home.statusCounts?.expired)
            )
        }
        view.findViewById<TextView>(R.id.inventoryStatus).apply {
            val error = state.error
            text = when {
                state.loading -> getString(R.string.inventory_loading)
                error?.code == "INVALID_RESPONSE" -> getString(R.string.inventory_invalid_response)
                error?.code == "NETWORK_ERROR" -> getString(R.string.inventory_network_error)
                error != null -> error.userMessage(getString(
                    if (error.status == 403) R.string.inventory_forbidden else R.string.inventory_load_error))
                home?.stocks.isNullOrEmpty() -> getString(R.string.inventory_no_stocks)
                state.items.isEmpty() -> getString(R.string.inventory_no_matches)
                else -> getString(R.string.inventory_loaded_count,
                    NumberFormat.getIntegerInstance().format(state.items.size),
                    NumberFormat.getIntegerInstance().format(state.totalElements))
            }
        }
    }
}
