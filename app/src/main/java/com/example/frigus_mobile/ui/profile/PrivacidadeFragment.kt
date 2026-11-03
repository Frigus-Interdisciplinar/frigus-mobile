package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

/** Privacidade: dois switches persistidos localmente. */
class PrivacidadeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_privacidade, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        val store = ProfileStore(requireContext())
        mapOf(
            R.id.switchUsageData to "privacy_usage_data",
            R.id.switchPersonalized to "privacy_personalized"
        ).forEach { (id, chave) ->
            val botao = view.findViewById<CompoundButton>(id)
            botao.isChecked = store.getBoolean(chave, true)
            botao.setOnCheckedChangeListener { _, marcado -> store.putBoolean(chave, marcado) }
        }
    }
}
