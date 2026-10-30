package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

/** Preferências de alimentos: tudo persistido localmente via [ProfileStore]. */
class PreferenciasAlimentosFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_preferencias_alimentos, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        val store = ProfileStore(requireContext())
        // id do botão -> (chave, valor padrão)
        val opcoes = listOf(
            Triple(R.id.checkVegetarian, "food_vegetarian", false),
            Triple(R.id.checkVegan, "food_vegan", false),
            Triple(R.id.checkNoRedMeat, "food_no_red_meat", false),
            Triple(R.id.checkNoSeafood, "food_no_seafood", false),
            Triple(R.id.checkNoLactose, "food_no_lactose", false),
            Triple(R.id.checkNoGluten, "food_no_gluten", false),
            Triple(R.id.checkNoSugar, "food_no_sugar", false),
            Triple(R.id.switchQuickRecipes, "food_quick_recipes", true),
            Triple(R.id.switchEconomicalRecipes, "food_economical_recipes", true)
        )
        for ((id, chave, padrao) in opcoes) {
            val botao = view.findViewById<CompoundButton>(id)
            botao.isChecked = store.getBoolean(chave, padrao)
            botao.setOnCheckedChangeListener { _, marcado -> store.putBoolean(chave, marcado) }
        }

        // Clicar na linha inteira alterna o checkbox.
        listOf(
            R.id.rowVegetarian to R.id.checkVegetarian,
            R.id.rowVegan to R.id.checkVegan,
            R.id.rowNoRedMeat to R.id.checkNoRedMeat,
            R.id.rowNoSeafood to R.id.checkNoSeafood,
            R.id.rowNoLactose to R.id.checkNoLactose,
            R.id.rowNoGluten to R.id.checkNoGluten,
            R.id.rowNoSugar to R.id.checkNoSugar
        ).forEach { (linha, check) ->
            view.findViewById<View>(linha).setOnClickListener {
                view.findViewById<CompoundButton>(check).toggle()
            }
        }
    }
}
