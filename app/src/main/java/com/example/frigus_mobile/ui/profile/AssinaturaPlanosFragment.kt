package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

class AssinaturaPlanosFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_assinatura_planos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnGerenciarPlano).setOnClickListener {
            Toast.makeText(requireContext(), "Gerenciamento de plano", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnFormaPagamento).setOnClickListener {
            Toast.makeText(requireContext(), "Formas de pagamento cadastradas", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnHistoricoCobrancas).setOnClickListener {
            Toast.makeText(requireContext(), "Histórico de faturas e cobranças", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnBeneficiosPlano).setOnClickListener {
            Toast.makeText(requireContext(), "Benefícios exclusivos do Plano Premium", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnConvidarMembro).setOnClickListener {
            Toast.makeText(requireContext(), "Convidar novo membro para o plano", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnMinhaFamilia).setOnClickListener {
            Toast.makeText(requireContext(), "Gerenciamento do grupo familiar", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnCancelarAssinatura).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Cancelar assinatura")
                .setMessage("Deseja realmente cancelar seu Plano Premium? Seus benefícios expirarão em 25/12/2026.")
                .setPositiveButton("Confirmar cancelamento") { _, _ ->
                    Toast.makeText(requireContext(), "Solicitação de cancelamento processada", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Manter plano", null)
                .show()
        }
    }
}
