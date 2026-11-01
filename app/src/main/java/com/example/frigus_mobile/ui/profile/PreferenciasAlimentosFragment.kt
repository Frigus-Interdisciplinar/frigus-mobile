package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.FoodPreference
import com.google.android.material.snackbar.Snackbar

/** The recovered controls persist per account and feed the assistant's request context. */
class PreferenciasAlimentosFragment : Fragment(R.layout.fragment_preferencias_alimentos) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)
        val model = ViewModelProvider(this)[FoodPreferencesViewModel::class.java]
        val choices = mapOf(
            R.id.checkVegetarian to FoodPreference.VEGETARIAN,
            R.id.checkVegan to FoodPreference.VEGAN,
            R.id.checkNoRedMeat to FoodPreference.NO_RED_MEAT,
            R.id.checkNoSeafood to FoodPreference.NO_SEAFOOD,
            R.id.checkNoLactose to FoodPreference.NO_LACTOSE,
            R.id.checkNoGluten to FoodPreference.NO_GLUTEN,
            R.id.checkNoSugar to FoodPreference.NO_SUGAR,
            R.id.switchQuickRecipes to FoodPreference.QUICK_RECIPES,
            R.id.switchEconomicalRecipes to FoodPreference.ECONOMICAL_RECIPES
        )
        var rendering = false
        choices.forEach { (id, preference) ->
            view.findViewById<CompoundButton>(id).setOnCheckedChangeListener { _, checked ->
                if (!rendering) model.update(preference, checked)
            }
        }
        val rows = mapOf(
            R.id.rowVegetarian to R.id.checkVegetarian,
            R.id.rowVegan to R.id.checkVegan,
            R.id.rowNoRedMeat to R.id.checkNoRedMeat,
            R.id.rowNoSeafood to R.id.checkNoSeafood,
            R.id.rowNoLactose to R.id.checkNoLactose,
            R.id.rowNoGluten to R.id.checkNoGluten,
            R.id.rowNoSugar to R.id.checkNoSugar
        )
        rows.forEach { (row, check) ->
            view.findViewById<View>(row).setOnClickListener {
                view.findViewById<CompoundButton>(check).let { if (it.isEnabled) it.toggle() }
            }
        }
        model.state.observe(viewLifecycleOwner) { state ->
            rendering = true
            choices.forEach { (id, preference) ->
                view.findViewById<CompoundButton>(id).apply {
                    isChecked = state.preferences?.isEnabled(preference) ?: preference.defaultValue
                    isEnabled = state.preferences != null && !state.saving
                }
            }
            rows.keys.forEach { view.findViewById<View>(it).isEnabled = state.preferences != null && !state.saving }
            rendering = false
            if (state.failed) Snackbar.make(view, R.string.profile_food_save_failed, Snackbar.LENGTH_LONG).show()
        }
    }
}
