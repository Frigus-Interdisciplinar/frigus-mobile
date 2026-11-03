package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.frigus_mobile.R

class AssinaturaPlanosFragment : SubscriptionScreenFragment(R.layout.fragment_assinatura_planos) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)
        view.findViewById<View>(R.id.subscriptionPlanCard).setOnClickListener { showFeatures() }
        view.findViewById<View>(R.id.btnGerenciarPlano).setOnClickListener { abrirTela(GerenciarPlanoFragment()) }
        view.findViewById<View>(R.id.btnFormaPagamento).setOnClickListener { showPayment() }
        view.findViewById<View>(R.id.btnConvidarMembro).setOnClickListener { showInvite() }
        view.findViewById<View>(R.id.btnMinhaFamilia).setOnClickListener { abrirTela(MeuGrupoFragment()) }
        view.findViewById<View>(R.id.btnCancelarAssinatura).setOnClickListener { cancelSubscription() }
        observeSubscription(view) { state ->
            renderCurrentPlan(view, state, R.id.tvPlanoAtualNome, R.id.tvPlanoAtualPreco, R.id.tvPlanoAtualValidade)
            val reactivate = state.subscription?.canceledAt != null
            view.findViewById<TextView>(R.id.tvCancelSubscription).setText(
                if (reactivate) R.string.subscription_reactivate else R.string.profile_cancel_subscription)
            view.findViewById<View>(R.id.btnCancelarAssinatura).isEnabled =
                state.subscription != null && !state.loading && !state.uncertainOperation
        }
    }
}
