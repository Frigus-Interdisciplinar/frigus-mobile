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
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.utils.NetworkGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
            Toast.makeText(requireContext(), "Benefícios do seu plano", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnConvidarMembro).setOnClickListener {
            Toast.makeText(requireContext(), "Convidar novo membro para o grupo", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnMinhaFamilia).setOnClickListener {
            Toast.makeText(requireContext(), "Gerenciamento do grupo", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnCancelarAssinatura).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Cancelar assinatura")
                .setMessage("Deseja realmente cancelar sua assinatura ativa?")
                .setPositiveButton("Confirmar cancelamento") { _, _ ->
                    Toast.makeText(requireContext(), "Solicitação de cancelamento processada", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Manter plano", null)
                .show()
        }

        carregarAssinaturaDaApi()
    }

    private fun carregarAssinaturaDaApi() {
        if (!NetworkGuard.ensureConnected(activity) { carregarAssinaturaDaApi() }) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val sub = withContext(Dispatchers.IO) {
                    val resp = RetrofitClient.authService.getMySubscription().execute()
                    if (resp.isSuccessful) resp.body() else null
                }

                val tvNome = view?.findViewById<TextView>(R.id.tvPlanoAtualNome)
                val tvPreco = view?.findViewById<TextView>(R.id.tvPlanoAtualPreco)
                val tvValidade = view?.findViewById<TextView>(R.id.tvPlanoAtualValidade)
                val btnCancelar = view?.findViewById<View>(R.id.btnCancelarAssinatura)

                if (sub?.plan != null && sub.status == "ACTIVE") {
                    tvNome?.text = sub.plan.name ?: "Plano Ativo"
                    val precoVal = sub.plan.price ?: 0.0
                    val precoStr = String.format(Locale.US, "%.2f", precoVal).replace(".", ",")
                    tvPreco?.text = "R$ $precoStr/mês"
                    val validade = formatarDataIso(sub.currentPeriodEnd)
                    tvValidade?.text = if (validade != null) "Válido até $validade" else "Assinatura ativa"
                    btnCancelar?.visibility = View.VISIBLE
                } else {
                    tvNome?.text = "Plano Gratuito"
                    tvPreco?.text = "R$ 0,00/mês"
                    tvValidade?.text = "Sem assinatura paga ativa"
                    btnCancelar?.visibility = View.GONE
                }
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(activity, e.message) {
                    carregarAssinaturaDaApi()
                }
            }
        }
    }

    private fun formatarDataIso(iso: String?): String? {
        if (iso.isNullOrBlank() || iso.length < 10) return null
        val parts = iso.substring(0, 10).split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else null
    }
}
