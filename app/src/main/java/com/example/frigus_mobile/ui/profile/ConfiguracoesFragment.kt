package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

class ConfiguracoesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_configuracoes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnNotificacoes).setOnClickListener {
            Toast.makeText(requireContext(), "Configuração de notificações", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnPreferenciasAlimentos).setOnClickListener {
            Toast.makeText(requireContext(), "Preferências de alimentos", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnUnidadesMedidas).setOnClickListener {
            Toast.makeText(requireContext(), "Unidades e medidas", Toast.LENGTH_SHORT).show()
        }
    }
}
