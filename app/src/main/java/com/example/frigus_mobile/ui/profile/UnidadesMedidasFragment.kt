package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

/** Unidades e medidas: seleção única por grupo, persistida localmente. */
class UnidadesMedidasFragment : Fragment() {

    // Um grupo: chave de persistência, unidade padrão e (chave da unidade -> linha, check).
    private class Grupo(val chave: String, val padrao: String, val itens: Map<String, Pair<Int, Int>>)

    private val grupos = listOf(
        Grupo("unit_weight", "kg", mapOf(
            "kg" to (R.id.rowUnitKg to R.id.checkUnitKg),
            "g" to (R.id.rowUnitG to R.id.checkUnitG),
            "lb" to (R.id.rowUnitLb to R.id.checkUnitLb),
            "oz" to (R.id.rowUnitOz to R.id.checkUnitOz)
        )),
        Grupo("unit_volume", "l", mapOf(
            "l" to (R.id.rowUnitL to R.id.checkUnitL),
            "ml" to (R.id.rowUnitMl to R.id.checkUnitMl),
            "cups" to (R.id.rowUnitCups to R.id.checkUnitCups),
            "spoons" to (R.id.rowUnitSpoons to R.id.checkUnitSpoons)
        )),
        Grupo("unit_temperature", "c", mapOf(
            "c" to (R.id.rowUnitCelsius to R.id.checkUnitCelsius),
            "f" to (R.id.rowUnitFahrenheit to R.id.checkUnitFahrenheit)
        ))
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_unidades_medidas, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        val store = ProfileStore(requireContext())
        for (grupo in grupos) {
            fun selecionar(unidade: String) {
                for ((chave, ids) in grupo.itens) {
                    val marcado = chave == unidade
                    view.findViewById<View>(ids.first).isActivated = marcado
                    view.findViewById<View>(ids.second).visibility = if (marcado) View.VISIBLE else View.GONE
                }
            }
            val salva = store.getString(grupo.chave, grupo.padrao)
            selecionar(if (salva in grupo.itens) salva!! else grupo.padrao)
            for ((chave, ids) in grupo.itens) {
                view.findViewById<View>(ids.first).setOnClickListener {
                    store.putString(grupo.chave, chave)
                    selecionar(chave)
                }
            }
        }
    }
}
