package com.example.frigus_mobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.InventoryHomeResponse
import com.example.frigus_mobile.ui.notifications.NotificacoesActivity
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat

class HomeFragment : Fragment(R.layout.fragment_home) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = SessionManager(requireContext())
        val name = session.userName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() }
        view.findViewById<TextView>(R.id.tvGreeting).text = if (name == null)
            getString(R.string.home_greeting) else getString(R.string.home_named_greeting, name)

        val main = requireActivity() as MainActivity
        view.findViewById<View>(R.id.cardQuickEstoque).setOnClickListener { main.openInventory() }
        mapOf(
            R.id.cardEstoqueGeladeira to "FRIDGE",
            R.id.cardEstoqueFreezer to "FREEZER",
            R.id.cardEstoqueDespensa to "PANTRY"
        ).forEach { (id, place) ->
            view.findViewById<View>(id).setOnClickListener { main.openInventory(storagePlace = place) }
        }
        view.findViewById<View>(R.id.cardAlertaVencendo).setOnClickListener {
            main.openInventory(showAllStatusesNotice = true)
        }
        view.findViewById<View>(R.id.cardQuickReceitas).setOnClickListener { main.selectTab(R.id.nav_receitas) }
        view.findViewById<View>(R.id.btnNotificacoes).setOnClickListener {
            startActivity(Intent(requireContext(), NotificacoesActivity::class.java))
        }
        mapOf(R.id.cardChats to R.string.home_conversations,
            R.id.cardEscanearQrCode to R.string.home_qr,
            R.id.cardFaleComIa to R.string.home_ai).forEach { (id, title) ->
            view.findViewById<View>(id).setOnClickListener { main.showUnavailable(title) }
        }

        val model = ViewModelProvider(requireActivity())[HomeViewModel::class.java]
        view.findViewById<View>(R.id.homeReloadButton).setOnClickListener { model.reload() }
        model.state.observe(viewLifecycleOwner) { state -> render(view, state) }
        model.loadIfNeeded()
    }

    private fun render(view: View, state: HomeUiState) {
        view.findViewById<View>(R.id.homeProgress).visibility = if (state.loading) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.homeSummary).visibility = if (state.home != null) View.VISIBLE else View.GONE
        view.findViewById<MaterialButton>(R.id.homeReloadButton).apply {
            isEnabled = !state.loading
            setText(if (state.error == null) R.string.home_refresh else R.string.home_retry)
        }
        view.findViewById<TextView>(R.id.homeStatus).apply {
            visibility = if (state.loading || state.error != null) View.VISIBLE else View.GONE
            text = when {
                state.loading -> getString(R.string.home_loading)
                state.error?.code == "NETWORK_ERROR" -> getString(R.string.home_network_error)
                state.error?.code == "INVALID_RESPONSE" -> getString(R.string.home_invalid_response)
                state.error != null -> state.error.userMessage(getString(
                    if (state.error.status == 403) R.string.home_forbidden else R.string.home_load_error))
                else -> ""
            }
        }
        state.home?.let { renderSummary(view, it) }
    }

    private fun renderSummary(view: View, home: InventoryHomeResponse) {
        val total = home.summary?.itemCount
        view.findViewById<TextView>(R.id.tvQuickEstoqueQtd).text = itemCount(total)
        val places = home.summary?.itemsByStoragePlace
        mapOf(R.id.tvQtdGeladeira to "FRIDGE", R.id.tvQtdFreezer to "FREEZER", R.id.tvQtdDespensa to "PANTRY")
            .forEach { (id, place) ->
                val count = if (places == null) null else if (places.containsKey(place)) places[place] else 0L
                view.findViewById<TextView>(id).text = itemCount(count)
            }
        view.findViewById<TextView>(R.id.homeEmptyState).apply {
            visibility = if (home.stocks.isNullOrEmpty() || total == 0L) View.VISIBLE else View.GONE
            setText(if (home.stocks.isNullOrEmpty()) R.string.home_no_stocks else R.string.home_empty_stock)
        }
        val near = home.statusCounts?.nearExpiration?.takeIf { it >= 0 }
        val expired = home.statusCounts?.expired?.takeIf { it >= 0 }
        val hasAlerts = (near != null && near > 0) || (expired != null && expired > 0)
        view.findViewById<View>(R.id.cardAlertaVencendo).visibility = if (hasAlerts) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.tvAlertaVencendoTitulo).text =
            getString(R.string.home_expiration_counts, number(near), number(expired))
        view.findViewById<TextView>(R.id.homeStatusNote).apply {
            visibility = if (near == null || expired == null) View.VISIBLE else View.GONE
            setText(R.string.home_expiration_unknown)
        }
    }

    private fun itemCount(value: Long?): String {
        if (value == null || value < 0) return getString(R.string.home_unknown_count)
        return resources.getQuantityString(R.plurals.home_item_count, if (value == 1L) 1 else 2, number(value))
    }

    private fun number(value: Long?): String = if (value == null || value < 0)
        getString(R.string.home_unknown_count) else NumberFormat.getIntegerInstance().format(value)
}
