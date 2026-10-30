package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import kotlinx.coroutines.launch

class GerenciarPlanoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_gerenciar_plano, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarVoltar(view)

        // Fluxo de troca de plano ainda não existe.
        view.findViewById<View>(R.id.btnAlterarPlano).setOnClickListener {
            mostrarToast(getString(R.string.profile_coming_soon))
        }
        view.findViewById<View>(R.id.btnCancelarAssinatura).setOnClickListener {
            abrirTela(CancelarAssinaturaFragment())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val plano = PlanoAtual.carregar(requireContext())
            view.preencherCardPlano(plano)
            view.findViewById<TextView>(R.id.tvRenovacao).text = plano.renovacao
            view.findViewById<TextView>(R.id.tvProximaCobranca).text = plano.proximaCobranca
            view.findViewById<TextView>(R.id.tvValorProximaCobranca).text = formatarMoeda(plano.preco)
            view.findViewById<TextView>(R.id.tvFormaPagamento).text =
                plano.cartao?.let { "•••• ${it.ultimos4}" } ?: getString(R.string.profile_not_informed)
            view.findViewById<View>(R.id.btnCancelarAssinatura).visibility =
                if (plano.cancelada) View.GONE else View.VISIBLE
        }
    }
}
