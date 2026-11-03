package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.google.android.material.switchmaterial.SwitchMaterial

class SegurancaFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_seguranca, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val switch2FA = view.findViewById<SwitchMaterial>(R.id.switch2FA)
        val tvStatus2FA = view.findViewById<TextView>(R.id.tvStatus2FA)

        switch2FA.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvStatus2FA.text = "Ativada"
                Toast.makeText(requireContext(), "Autenticação em duas etapas ativada", Toast.LENGTH_SHORT).show()
            } else {
                tvStatus2FA.text = "Desativada"
                Toast.makeText(requireContext(), "Autenticação em duas etapas desativada", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btnDispositivosConectados).setOnClickListener {
            Toast.makeText(requireContext(), "Gerenciamento de dispositivos conectados", Toast.LENGTH_SHORT).show()
        }
    }
}
