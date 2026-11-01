package com.example.frigus_mobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.InventoryHomeResponse
import com.example.frigus_mobile.ui.ai.ChatIaActivity
import com.example.frigus_mobile.ui.conversation.ConversationsActivity
import com.example.frigus_mobile.ui.notifications.NotificacoesActivity
import com.example.frigus_mobile.ui.qrcode.QrCodeScannerActivity
import com.example.frigus_mobile.ui.recipes.RecipesViewModel
import com.example.frigus_mobile.utils.NetworkGuard
import java.text.NumberFormat

/** The original 93933ee cards, connected to the current BFF-backed state. */
class HomeFragment : Fragment(R.layout.fragment_home) {
    private var displayedError: com.example.frigus_mobile.data.model.ApiError? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateGreeting(view)
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
        listOf(R.id.cardQuickReceitas, R.id.cardSugestaoReceita).forEach { id ->
            view.findViewById<View>(id).setOnClickListener { main.selectTab(R.id.nav_receitas) }
        }
        view.findViewById<View>(R.id.btnNotificacoes).setOnClickListener {
            startActivity(Intent(requireContext(), NotificacoesActivity::class.java))
        }
        view.findViewById<View>(R.id.cardFaleComIa).setOnClickListener {
            startActivity(Intent(requireContext(), ChatIaActivity::class.java))
        }
        view.findViewById<View>(R.id.cardChats).setOnClickListener {
            startActivity(Intent(requireContext(), ConversationsActivity::class.java))
        }
        view.findViewById<View>(R.id.cardMoneySaving).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, com.example.frigus_mobile.ui.profile.MoneySavingFragment())
                .addToBackStack(null).commit()
        }
        view.findViewById<View>(R.id.cardEscanearQrCode).setOnClickListener {
            startActivity(Intent(requireContext(), QrCodeScannerActivity::class.java))
        }

        val model = ViewModelProvider(requireActivity())[HomeViewModel::class.java]
        model.state.observe(viewLifecycleOwner) { state ->
            if (state.home != null) renderSummary(view, state.home) else clearSummary(view)
            val error = state.error
            if (error == null) displayedError = null
            else if (displayedError !== error) {
                displayedError = error
                val message = error.userMessage(getString(when {
                    error.code == "NETWORK_ERROR" -> R.string.home_network_error
                    error.code == "INVALID_RESPONSE" -> R.string.home_invalid_response
                    error.status == 403 -> R.string.home_forbidden
                    else -> R.string.home_load_error
                }))
                NetworkGuard.showBlockingError(activity, message) { model.reload() }
            }
        }
        model.loadIfNeeded()

        val recipes = ViewModelProvider(requireActivity())[RecipesViewModel::class.java]
        view.findViewById<ImageView>(R.id.homeSuggestedImage).apply {
            setImageResource(R.drawable.ic_restaurant)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        recipes.suggestions.observe(viewLifecycleOwner) { state ->
            val recipe = state.items.firstOrNull()
            view.findViewById<TextView>(R.id.homeSuggestedTitle).text =
                recipe?.recipeName?.takeIf(String::isNotBlank) ?: getString(R.string.home_suggestion_empty)
            view.findViewById<TextView>(R.id.homeSuggestedSubtitle).text = getString(when {
                state.loading -> R.string.recipe_browser_loading
                state.error != null -> R.string.home_suggestion_load_error
                else -> R.string.home_suggestion_description
            })
        }
        if (recipes.suggestions.value == null) recipes.refreshSuggestions()
    }

    override fun onResume() {
        super.onResume()
        view?.let(::updateGreeting)
    }

    private fun updateGreeting(view: View) {
        val name = SessionManager(requireContext()).userName?.trim()?.split(Regex("\\s+"))
            ?.firstOrNull()?.takeIf(String::isNotBlank)
        view.findViewById<TextView>(R.id.tvGreeting).text =
            if (name == null) getString(R.string.home_greeting) else getString(R.string.home_named_greeting, name)
    }

    private fun clearSummary(view: View) {
        listOf(R.id.tvQuickEstoqueQtd, R.id.tvQtdGeladeira, R.id.tvQtdFreezer, R.id.tvQtdDespensa)
            .forEach { view.findViewById<TextView>(it).setText(R.string.home_unknown_count) }
        view.findViewById<View>(R.id.cardAlertaVencendo).visibility = View.GONE
    }

    private fun renderSummary(view: View, home: InventoryHomeResponse) {
        view.findViewById<TextView>(R.id.tvQuickEstoqueQtd).text = itemCount(home.summary?.itemCount)
        val places = home.summary?.itemsByStoragePlace
        mapOf(R.id.tvQtdGeladeira to "FRIDGE", R.id.tvQtdFreezer to "FREEZER", R.id.tvQtdDespensa to "PANTRY")
            .forEach { (id, place) ->
                val count = if (places == null) null else if (places.containsKey(place)) places[place] else 0L
                view.findViewById<TextView>(id).text = itemCount(count)
            }
        val near = home.statusCounts?.nearExpiration?.takeIf { it >= 0 }
        val expired = home.statusCounts?.expired?.takeIf { it >= 0 }
        val total = if (near != null && expired != null) near + expired else null
        view.findViewById<View>(R.id.cardAlertaVencendo).visibility =
            if ((near ?: 0) > 0 || (expired ?: 0) > 0) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.tvAlertaVencendoTitulo).text = if (total == null)
            getString(R.string.home_expiration_counts, number(near), number(expired))
        else resources.getQuantityString(R.plurals.home_expiring_food_count, if (total == 1L) 1 else 2, number(total))
    }

    private fun itemCount(value: Long?): String {
        if (value == null || value < 0) return getString(R.string.home_unknown_count)
        return resources.getQuantityString(R.plurals.home_item_count, if (value == 1L) 1 else 2, number(value))
    }

    private fun number(value: Long?): String = if (value == null || value < 0)
        getString(R.string.home_unknown_count) else NumberFormat.getIntegerInstance().format(value)
}
