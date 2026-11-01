package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.FoodPreference
import com.example.frigus_mobile.data.repository.FoodPreferencesRepository
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** Reuses the existing designed settings layouts and only binds them to the persisted BFF document. */
class AppSettingsSectionFragment : Fragment() {
    companion object {
        const val FOOD = "food"
        const val UNITS = "units"
        const val PRIVACY = "privacy"
        private const val ARG_SECTION = "section"

        fun newInstance(section: String) = AppSettingsSectionFragment().apply {
            arguments = Bundle().apply { putString(ARG_SECTION, section) }
        }
    }

    private data class UnitGroup(val key: String, val rows: List<Pair<Int, Int>>)

    private val section get() = requireArguments().getString(ARG_SECTION).orEmpty()
    private var settings: JsonObject? = null
    private var call: Call<JsonObject>? = null
    private var busy = false
    private var uncertain = false
    private var binding = false
    private var progressSnackbar: Snackbar? = null
    private var feedbackSnackbar: Snackbar? = null
    private lateinit var root: View
    private val toggles = linkedMapOf<String, CompoundButton>()
    private val unitChecks = linkedMapOf<String, List<Pair<String, View>>>()
    private val unitRows = mutableListOf<View>()
    private val selectedUnits = mutableMapOf<String, String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val layout = when (section) {
            FOOD -> R.layout.fragment_preferencias_alimentos
            UNITS -> R.layout.fragment_unidades_medidas
            PRIVACY -> R.layout.fragment_privacidade
            else -> R.layout.fragment_configuracoes
        }
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        root = view
        view.findViewById<View>(R.id.btnVoltar)?.setOnClickListener { parentFragmentManager.popBackStack() }
        bindControls(view)
        attachListeners()
        load()
    }

    private fun bindControls(view: View) {
        fun toggle(id: Int, key: String) { toggles[key] = view.findViewById(id) }
        when (section) {
            FOOD -> {
                toggle(R.id.checkVegetarian, "VEGETARIAN")
                toggle(R.id.checkVegan, "VEGAN")
                toggle(R.id.checkNoRedMeat, "AVOID_RED_MEAT")
                toggle(R.id.checkNoSeafood, "AVOID_SEAFOOD")
                toggle(R.id.checkNoLactose, "LACTOSE_FREE")
                toggle(R.id.checkNoGluten, "GLUTEN_FREE")
                toggle(R.id.checkNoSugar, "SUGAR_FREE")
                toggle(R.id.switchQuickRecipes, "quickRecipesEnabled")
                toggle(R.id.switchEconomicalRecipes, "budgetRecipesEnabled")
            }
            UNITS -> {
                unitGroup("weight", listOf(
                    "KG" to R.id.checkUnitKg, "G" to R.id.checkUnitG, "LB" to R.id.checkUnitLb, "OZ" to R.id.checkUnitOz
                ), listOf(R.id.rowUnitKg, R.id.rowUnitG, R.id.rowUnitLb, R.id.rowUnitOz), view)
                unitGroup("volume", listOf(
                    "L" to R.id.checkUnitL, "ML" to R.id.checkUnitMl, "CUP" to R.id.checkUnitCups, "SPOON" to R.id.checkUnitSpoons
                ), listOf(R.id.rowUnitL, R.id.rowUnitMl, R.id.rowUnitCups, R.id.rowUnitSpoons), view)
                unitGroup("temperature", listOf(
                    "CELSIUS" to R.id.checkUnitCelsius, "FAHRENHEIT" to R.id.checkUnitFahrenheit
                ), listOf(R.id.rowUnitCelsius, R.id.rowUnitFahrenheit), view)
            }
            PRIVACY -> {
                toggle(R.id.switchUsageData, "usageDataCollectionAllowed")
                toggle(R.id.switchPersonalized, "personalizedSuggestionsAllowed")
            }
        }
    }

    private fun unitGroup(key: String, items: List<Pair<String, Int>>, rowIds: List<Int>, view: View) {
        val controls = items.map { (value, id) -> value to view.findViewById<View>(id) }
        unitChecks[key] = controls
        rowIds.forEachIndexed { index, rowId ->
            val row = view.findViewById<View>(rowId)
            row.setOnClickListener {
                if (!busy && !uncertain && settings != null) {
                    selectedUnits[key] = controls[index].first
                    renderSettings()
                    save()
                }
            }
            unitRows += row
        }
    }

    private fun attachListeners() {
        toggles.values.forEach { control ->
            control.setOnCheckedChangeListener { _, _ ->
                if (!binding && !busy && !uncertain && settings != null) save()
            }
        }
    }

    private fun valid(value: JsonObject?): Boolean {
        return try {
        val food = value?.getAsJsonObject("foodPreferences") ?: return false
        val units = value.getAsJsonObject("units") ?: return false
        val privacy = value.getAsJsonObject("privacy") ?: return false
        val preferences = food.getAsJsonArray("preferences") ?: return false
        val restrictions = food.getAsJsonArray("restrictions") ?: return false
        val unitValues = mapOf(
            "weight" to setOf("KG", "G", "LB", "OZ"),
            "volume" to setOf("L", "ML", "CUP", "SPOON"),
            "temperature" to setOf("CELSIUS", "FAHRENHEIT")
        )
        preferences.all { it.isJsonPrimitive && it.asJsonPrimitive.isString } &&
            restrictions.all { it.isJsonPrimitive && it.asJsonPrimitive.isString } &&
            food.get("quickRecipesEnabled")?.asJsonPrimitive?.isBoolean == true &&
            food.get("budgetRecipesEnabled")?.asJsonPrimitive?.isBoolean == true &&
            unitValues.all { (key, allowed) ->
                val selected = units.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                selected != null && selected in allowed
            } &&
            privacy.get("usageDataCollectionAllowed")?.asJsonPrimitive?.isBoolean == true &&
            privacy.get("personalizedSuggestionsAllowed")?.asJsonPrimitive?.isBoolean == true
        } catch (_: Exception) { false }
    }

    private fun load() {
        if (busy) return
        busy = true
        updateControls()
        showProgress("Carregando configurações...")
        call = RetrofitClient.appSettingsService.load().also { request ->
            request.enqueue(object : Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    progressSnackbar?.dismiss()
                    progressSnackbar = null
                    if (response.code() == 200 && valid(response.body())) {
                        settings = response.body()
                        syncSelectedUnits(settings!!)
                        uncertain = false
                        renderSettings()
                        updateControls()
                        if (section == FOOD && !syncAssistantPreferences(settings!!)) {
                            Snackbar.make(root, "Preferências confirmadas, mas as sugestões podem não atualizar neste aparelho.", Snackbar.LENGTH_LONG).show()
                        }
                    } else {
                        settings = null
                        updateControls()
                        val message = when {
                            response.isSuccessful -> "Resposta inválida ao carregar configurações."
                            response.code() >= 500 -> "O serviço de configurações está indisponível no momento. Tente novamente mais tarde."
                            else -> ApiError.parse(response).userMessage("Falha ao carregar (${response.code()}).")
                        }
                        showFeedback(message, "Tentar novamente") { load() }
                    }
                }

                override fun onFailure(call: Call<JsonObject>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    progressSnackbar?.dismiss()
                    progressSnackbar = null
                    settings = null
                    updateControls()
                    showFeedback("Falha de conexão ao carregar configurações.", "Tentar novamente") { load() }
                }
            })
        }
    }

    private fun renderSettings() {
        val current = settings ?: return
        binding = true
        try {
            if (section == FOOD) {
                val food = current.getAsJsonObject("foodPreferences")
                val preferences = food.getAsJsonArray("preferences").map { it.asString }.toSet()
                val restrictions = food.getAsJsonArray("restrictions").map { it.asString }.toSet()
                val selected = preferences + restrictions
                toggles.forEach { (key, control) ->
                    control.isChecked = if (key == "quickRecipesEnabled" || key == "budgetRecipesEnabled")
                        food.get(key).asBoolean else key in selected
                }
            } else if (section == UNITS) {
                val units = current.getAsJsonObject("units")
                unitChecks.forEach { (key, controls) ->
                    val selected = selectedUnits[key] ?: units.get(key).asString
                    selectedUnits[key] = selected
                    controls.forEach { (value, control) ->
                        control.visibility = if (value == selected) View.VISIBLE else View.GONE
                    }
                }
            } else if (section == PRIVACY) {
                val privacy = current.getAsJsonObject("privacy")
                toggles.forEach { (key, control) -> control.isChecked = privacy.get(key).asBoolean }
            }
        } finally { binding = false }
    }

    private fun updateControls() {
        val enabled = settings != null && !busy && !uncertain
        toggles.values.forEach { it.isEnabled = enabled }
        unitRows.forEach { it.isEnabled = enabled }
    }

    private fun save() {
        val current = settings ?: return
        if (busy || uncertain) return
        val updated = current.deepCopy()
        if (section == FOOD) {
            val preferences = listOf("VEGETARIAN", "VEGAN", "AVOID_RED_MEAT", "AVOID_SEAFOOD")
            val restrictions = listOf("LACTOSE_FREE", "GLUTEN_FREE", "SUGAR_FREE")
            updated.getAsJsonObject("foodPreferences").apply {
                add("preferences", updatedCodes(getAsJsonArray("preferences"), preferences))
                add("restrictions", updatedCodes(getAsJsonArray("restrictions"), restrictions))
                addProperty("quickRecipesEnabled", toggles["quickRecipesEnabled"]?.isChecked == true)
                addProperty("budgetRecipesEnabled", toggles["budgetRecipesEnabled"]?.isChecked == true)
            }
        } else if (section == UNITS) {
            unitChecks.forEach { (key, _) -> updated.getAsJsonObject("units").addProperty(key, selectedUnits[key]) }
        } else if (section == PRIVACY) {
            updated.getAsJsonObject("privacy").apply {
                addProperty("usageDataCollectionAllowed", toggles["usageDataCollectionAllowed"]?.isChecked == true)
                addProperty("personalizedSuggestionsAllowed", toggles["personalizedSuggestionsAllowed"]?.isChecked == true)
            }
        }

        busy = true
        updateControls()
        showProgress("Salvando configurações...")
        call = RetrofitClient.appSettingsService.save(updated).also { request ->
            request.enqueue(object : Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    progressSnackbar?.dismiss()
                    progressSnackbar = null
                    if (response.code() == 200 && valid(response.body())) {
                        settings = response.body()
                        syncSelectedUnits(settings!!)
                        uncertain = false
                        renderSettings()
                        val localSynced = section != FOOD || syncAssistantPreferences(settings!!)
                        Snackbar.make(
                            root,
                            if (localSynced) "Alteração confirmada pelo servidor."
                            else "Salvo no servidor; as sugestões podem não atualizar neste aparelho.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else if (response.isSuccessful || response.code() >= 500) {
                        uncertain = true
                        settings?.let(::syncSelectedUnits)
                        renderSettings()
                        showFeedback("Resultado incerto. Recarregue antes de alterar novamente.", "Recarregar") { load() }
                    } else {
                        settings?.let(::syncSelectedUnits)
                        renderSettings()
                        Snackbar.make(root, ApiError.parse(response).userMessage("Não foi possível salvar (${response.code()})."), Snackbar.LENGTH_LONG).show()
                    }
                    updateControls()
                }

                override fun onFailure(call: Call<JsonObject>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    uncertain = true
                    settings?.let(::syncSelectedUnits)
                    renderSettings()
                    progressSnackbar?.dismiss()
                    progressSnackbar = null
                    showFeedback("Resultado incerto. Recarregue para conferir; o envio não será repetido.", "Recarregar") { load() }
                    updateControls()
                }
            })
        }
    }

    private fun updatedCodes(current: JsonArray, supported: List<String>): JsonArray {
        val preserved = current.map { it.asString }.filterNot(supported::contains)
        val selected = supported.filter { toggles[it]?.isChecked == true }
        return JsonArray().apply { (preserved + selected).distinct().forEach(::add) }
    }

    private fun syncSelectedUnits(value: JsonObject) {
        val units = value.getAsJsonObject("units") ?: return
        selectedUnits.clear()
        listOf("weight", "volume", "temperature").forEach { key ->
            units.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.let { selectedUnits[key] = it }
        }
    }

    private fun syncAssistantPreferences(value: JsonObject): Boolean {
        val food = value.getAsJsonObject("foodPreferences") ?: return false
        val preferences = runCatching { food.getAsJsonArray("preferences").map { it.asString }.toSet() }.getOrNull() ?: return false
        val restrictions = runCatching { food.getAsJsonArray("restrictions").map { it.asString }.toSet() }.getOrNull() ?: return false
        val repository = FoodPreferencesRepository(requireContext())
        val enabled = mapOf(
            FoodPreference.VEGETARIAN to ("VEGETARIAN" in preferences),
            FoodPreference.VEGAN to ("VEGAN" in preferences),
            FoodPreference.NO_RED_MEAT to ("AVOID_RED_MEAT" in preferences),
            FoodPreference.NO_SEAFOOD to ("AVOID_SEAFOOD" in preferences),
            FoodPreference.NO_LACTOSE to ("LACTOSE_FREE" in restrictions),
            FoodPreference.NO_GLUTEN to ("GLUTEN_FREE" in restrictions),
            FoodPreference.NO_SUGAR to ("SUGAR_FREE" in restrictions),
            FoodPreference.QUICK_RECIPES to food.get("quickRecipesEnabled").asBoolean,
            FoodPreference.ECONOMICAL_RECIPES to food.get("budgetRecipesEnabled").asBoolean
        )
        return enabled.map { (preference, isEnabled) -> repository.update(preference, isEnabled) }.all { it }
    }

    private fun showProgress(message: String) {
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = null
        progressSnackbar?.dismiss()
        progressSnackbar = Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE).also { it.show() }
    }

    private fun showFeedback(message: String, action: String, retry: () -> Unit) {
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE)
            .setAction(action) { retry() }.also { it.show() }
    }

    override fun onDestroyView() {
        call?.cancel()
        call = null
        progressSnackbar?.dismiss()
        progressSnackbar = null
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = null
        busy = false
        super.onDestroyView()
    }
}
