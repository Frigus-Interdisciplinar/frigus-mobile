package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.ApiError
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** Uses the app's existing notification settings layout and persists only documented fields. */
class NotificationSettingsFragment : Fragment(R.layout.fragment_configuracoes_notificacoes) {
    private val keys = linkedMapOf(
        R.id.switchExpiring to "expirationAlertsEnabled",
        R.id.switchLowStock to "lowStockAlertsEnabled",
        R.id.switchShopping to "shoppingRemindersEnabled",
        R.id.switchDaily to "weeklySummaryEnabled"
    )
    private val unsupportedIds = listOf(R.id.switchRecipes, R.id.switchPromotions)
    private val controls = linkedMapOf<Int, CompoundButton>()
    private var source: JsonObject? = null
    private var call: Call<JsonObject>? = null
    private var busy = false
    private var uncertain = false
    private var binding = false
    private lateinit var root: View
    private var progressSnackbar: Snackbar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        root = view
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener { parentFragmentManager.popBackStack() }
        keys.keys.forEach { id -> controls[id] = view.findViewById(id) }
        keys.keys.forEach { id ->
            controls[id]?.setOnCheckedChangeListener { _, _ ->
                if (!binding && !busy && !uncertain && source != null) save()
            }
        }
        unsupportedIds.forEach { id -> view.findViewById<CompoundButton>(id).apply { isEnabled = false } }
        updateRowCopy(R.id.switchDaily, mapOf(
            getString(R.string.profile_alert_daily) to "Resumo semanal",
            getString(R.string.profile_alert_daily_detail) to "Receba um resumo semanal do seu estoque"
        ))
        showUnavailableRow(R.id.switchRecipes, mapOf(
            getString(R.string.profile_alert_recipes) to "Receitas sugeridas · indisponível",
            getString(R.string.profile_alert_recipes_detail) to "O BFF ainda não permite salvar esta preferência"
        ))
        showUnavailableRow(R.id.switchPromotions, mapOf(
            getString(R.string.profile_alert_promotions) to "Promoções e ofertas · indisponível",
            getString(R.string.profile_alert_promotions_detail) to "O BFF ainda não permite salvar esta preferência"
        ))
        load()
    }

    private fun updateRowCopy(switchId: Int, replacements: Map<String, String>) {
        val control = root.findViewById<View>(switchId)
        var parent = control.parent as? ViewGroup
        while (parent != null) {
            val texts = findTextViews(parent)
            if (texts.any { it.text.toString() in replacements }) {
                texts.forEach { text -> replacements[text.text.toString()]?.let { text.text = it } }
                return
            }
            parent = parent.parent as? ViewGroup
        }
    }

    private fun showUnavailableRow(switchId: Int, replacements: Map<String, String>) {
        updateRowCopy(switchId, replacements)
        var parent = root.findViewById<View>(switchId).parent as? ViewGroup
        parent = parent?.parent as? ViewGroup
        parent?.isClickable = true
        parent?.setOnClickListener {
            Snackbar.make(root, "Essa opção ainda não pode ser salva pelo servidor.", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun findTextViews(root: View): List<TextView> {
        if (root is TextView) return listOf(root)
        if (root !is ViewGroup) return emptyList()
        return (0 until root.childCount).flatMap { findTextViews(root.getChildAt(it)) }
    }

    private fun valid(value: JsonObject?): Boolean = try {
        value != null && keys.values.all { value.get(it)?.asJsonPrimitive?.isBoolean == true } && value.get("theme") != null
    } catch (_: Exception) { false }

    private fun updateControls() {
        val enabled = source != null && !busy && !uncertain
        controls.values.forEach { it.isEnabled = enabled }
    }

    private fun render() {
        val current = source ?: return
        binding = true
        try {
            keys.forEach { (id, key) -> controls[id]?.isChecked = current.get(key).asBoolean }
        } finally { binding = false }
    }

    private fun load() {
        if (busy) return
        busy = true
        updateControls()
        showProgress("Carregando preferências...")
        call = RetrofitClient.notificationSettingsService.load().also { request ->
            request.enqueue(object : Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    progressSnackbar?.dismiss()
                    progressSnackbar = null
                    if (response.code() == 200 && valid(response.body())) {
                        source = response.body()
                        uncertain = false
                        render()
                        updateControls()
                    } else {
                        source = null
                        updateControls()
                        val message = if (response.isSuccessful) "Resposta inválida ao carregar preferências."
                            else ApiError.parse(response).userMessage("Falha ao carregar (${response.code()}).")
                        Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE).setAction("Tentar novamente") { load() }.show()
                    }
                }

                override fun onFailure(call: Call<JsonObject>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    progressSnackbar?.dismiss()
                    progressSnackbar = null
                    source = null
                    updateControls()
                    Snackbar.make(root, "Falha de conexão ao carregar preferências.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Tentar novamente") { load() }.show()
                }
            })
        }
    }

    private fun save() {
        val current = source ?: return
        if (busy || uncertain) return
        val body = current.deepCopy()
        keys.forEach { (id, key) -> body.addProperty(key, controls[id]?.isChecked == true) }
        busy = true
        updateControls()
        showProgress("Salvando preferência...")
        call = RetrofitClient.notificationSettingsService.save(body).also { request ->
            request.enqueue(object : Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    progressSnackbar?.dismiss()
                    progressSnackbar = null
                    if (response.code() == 200 && valid(response.body())) {
                        source = response.body()
                        uncertain = false
                        render()
                        Snackbar.make(root, "Preferência confirmada pelo servidor.", Snackbar.LENGTH_SHORT).show()
                    } else if (response.isSuccessful || response.code() >= 500) {
                        uncertain = true
                        render()
                        Snackbar.make(root, "Resultado incerto. Recarregue antes de alterar novamente.", Snackbar.LENGTH_INDEFINITE)
                            .setAction("Recarregar") { load() }.show()
                    } else {
                        render()
                        Snackbar.make(root, ApiError.parse(response).userMessage("Não foi possível salvar (${response.code()})."), Snackbar.LENGTH_LONG).show()
                    }
                    updateControls()
                }

                override fun onFailure(call: Call<JsonObject>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    uncertain = true
                    progressSnackbar?.dismiss()
                    progressSnackbar = null
                    render()
                    Snackbar.make(root, "Resultado incerto. Recarregue para conferir; o envio não será repetido.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Recarregar") { load() }.show()
                    updateControls()
                }
            })
        }
    }

    private fun showProgress(message: String) {
        progressSnackbar?.dismiss()
        progressSnackbar = Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE).also { it.show() }
    }

    override fun onDestroyView() {
        call?.cancel()
        call = null
        progressSnackbar?.dismiss()
        progressSnackbar = null
        busy = false
        super.onDestroyView()
    }
}
