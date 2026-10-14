package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.utils.NetworkGuard
import kotlinx.coroutines.launch
import java.util.Locale

class MoneySavingFragment : Fragment() {

    private lateinit var stockRepository: StockRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_money_saving, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stockRepository = StockRepository(requireContext())

        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnInfo).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Sobre o MoneySaving")
                .setMessage("O MoneySaving analisa seus hábitos de consumo alimentar, calculando o valor poupado ao evitar o descarte de itens e fornecendo relatórios de economia.")
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
                .setMessage("A economia obtida corresponde ao valor dos produtos mantidos dentro do prazo de validade no seu estoque. O desperdício considera itens vencidos.")
                .setPositiveButton("OK", null)
                .show()
        }

        carregarMetricasReais()
    }

    private fun formatarMoeda(valor: Double): String {
        return "R$ " + String.format(Locale.US, "%.2f", valor).replace(".", ",")
    }

    private fun carregarMetricasReais() {
        if (!NetworkGuard.ensureConnected(activity) { carregarMetricasReais() }) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val produtos = stockRepository.getStockProducts()

                var gastoTotal = 0.0
                var desperdicioTotal = 0.0
                var economiaTotal = 0.0
                var qtdEmDia = 0
                var qtdVencidos = 0

                for (item in produtos) {
                    val unitPrice = item.purchaseUnitPrice ?: item.product?.unitPrice ?: 0.0
                    val subtotal = unitPrice
                    gastoTotal += subtotal
                    if (item.productStatus.equals("EXPIRED", ignoreCase = true)) {
                        desperdicioTotal += subtotal
                        qtdVencidos++
                    } else {
                        economiaTotal += subtotal
                        qtdEmDia++
                    }
                }

                view?.findViewById<TextView>(R.id.tvEconomiaObtida)?.text = formatarMoeda(economiaTotal)
                view?.findViewById<TextView>(R.id.tvGastoAlimentacao)?.text = formatarMoeda(gastoTotal)
                view?.findViewById<TextView>(R.id.tvDesperdicio)?.text = formatarMoeda(desperdicioTotal)
                view?.findViewById<TextView>(R.id.tvComparadoGastos)?.text =
                    if (qtdEmDia == 1) "1 item conservado" else "$qtdEmDia itens conservados"
                view?.findViewById<TextView>(R.id.tvComparadoDesperdicio)?.text =
                    if (qtdVencidos == 1) "1 item vencido" else "$qtdVencidos itens vencidos"
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(activity, e.message) {
                    carregarMetricasReais()
                }
            }
        }
    }
}
