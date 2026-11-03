package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import kotlinx.coroutines.launch

class CancelarAssinaturaFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_cancelar_assinatura, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        view.findViewById<View>(R.id.btnManterAssinatura).setOnClickListener {
            voltarTela()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val plano = PlanoAtual.carregar(requireContext())
            view.preencherCardPlano(plano, beneficios = true)

            view.findViewById<View>(R.id.btnConfirmarCancelamento).setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.profile_cancel_subscription)
                    .setMessage(getString(R.string.profile_cancel_confirm_detail))
                    .setNegativeButton(R.string.profile_keep_subscription, null)
                    .setPositiveButton(R.string.profile_cancel_subscription) { _, _ ->
                        PlanoAtual.cancelar(requireContext(), plano.proximaCobranca)
                        mostrarToast(getString(R.string.profile_subscription_canceled, plano.proximaCobranca))
                        voltarTela()
                    }
                    .show()
            }
        }
    }
}
