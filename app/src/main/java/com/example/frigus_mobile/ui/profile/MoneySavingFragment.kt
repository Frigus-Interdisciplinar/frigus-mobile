package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

class MoneySavingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_money_saving, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnInfo).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Sobre o MoneySaving")
                .setMessage("O MoneySaving analisa seus hábitos de consumo alimentar, calculando o valor poupado ao evitar o descarte de itens e fornecendo relatórios mensais de economia.")
                .setPositiveButton("Entendi", null)
                .show()
        }

        view.findViewById<View>(R.id.btnGastosAlimentacao).setOnClickListener {
            Toast.makeText(requireContext(), "Relatório detalhado de gastos com alimentação", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnEconomiaDesperdicio).setOnClickListener {
            Toast.makeText(requireContext(), "Análise de economia e desperdício evitado", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnHistoricoFinanceiro).setOnClickListener {
            Toast.makeText(requireContext(), "Histórico financeiro completo", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnComoValoresCalculados).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Cálculo dos Valores")
                .setMessage("A economia obtida é a soma do valor dos produtos consumidos dentro do prazo de validade. O desperdício considera itens descartados ou vencidos.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
