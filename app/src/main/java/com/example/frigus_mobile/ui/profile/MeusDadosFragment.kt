package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

class MeusDadosFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_meus_dados, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnPrivacidade).setOnClickListener {
            Toast.makeText(requireContext(), "Políticas de privacidade", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnAlterarSenha).setOnClickListener {
            Toast.makeText(requireContext(), "Alterar senha da conta", Toast.LENGTH_SHORT).show()
        }
    }
}
