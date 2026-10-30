package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import kotlinx.coroutines.launch

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

        configurarVoltar(view)

        view.findViewById<View>(R.id.btnGerenciarPlano).setOnClickListener { abrirTela(GerenciarPlanoFragment()) }
        view.findViewById<View>(R.id.btnFormaPagamento).setOnClickListener { abrirTela(FormaPagamentoFragment()) }
        view.findViewById<View>(R.id.btnConvidarMembro).setOnClickListener { abrirTela(ConvidarMembroFragment()) }
        view.findViewById<View>(R.id.btnMeuGrupo).setOnClickListener { abrirTela(MeuGrupoFragment()) }
        view.findViewById<View>(R.id.btnCancelarAssinatura).setOnClickListener { abrirTela(CancelarAssinaturaFragment()) }

        viewLifecycleOwner.lifecycleScope.launch {
            val plano = PlanoAtual.carregar(requireContext())
            view.preencherCardPlano(plano)
            // Assinatura já cancelada: não faz sentido oferecer o cancelamento de novo.
            view.findViewById<View>(R.id.btnCancelarAssinatura).visibility =
                if (plano.cancelada) View.GONE else View.VISIBLE
        }
    }
}
