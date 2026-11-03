package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.frigus_mobile.R

class GerenciarPlanoFragment : SubscriptionScreenFragment(R.layout.fragment_gerenciar_plano) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)
        view.findViewById<View>(R.id.subscriptionManageCard).setOnClickListener { showFeatures() }
        view.findViewById<View>(R.id.btnAlterarPlano).setOnClickListener { abrirTela(AlterarPlanoFragment()) }
        view.findViewById<View>(R.id.btnCancelarAssinatura).setOnClickListener { cancelSubscription() }
        view.findViewById<View>(R.id.tvFormaPagamento).setOnClickListener { showPayment() }
        observeSubscription(view) { state ->
            renderCurrentPlan(view, state, R.id.tvPlanoNome, R.id.tvPlanoPreco, R.id.tvPlanoValidade)
            val subscription = state.subscription
            view.findViewById<TextView>(R.id.tvRenovacao).text = when (subscription?.plan?.billingInterval?.uppercase()) {
                "MONTH", "MONTHLY" -> getString(R.string.profile_monthly)
                "YEAR", "YEARLY", "ANNUAL" -> getString(R.string.plans_yearly)
                else -> getString(R.string.subscription_unknown)
            }
            view.findViewById<TextView>(R.id.tvProximaCobranca).text =
                if (subscription?.canceledAt != null || subscription?.autoRenew == false)
                    getString(R.string.plans_no_upcoming_charge) else date(subscription?.currentPeriodEnd)
            // The subscription contract does not confirm a future charge amount or a saved card.
            view.findViewById<TextView>(R.id.tvValorProximaCobranca).setText(R.string.plans_charge_unconfirmed)
            view.findViewById<TextView>(R.id.tvFormaPagamento).setText(R.string.subscription_unknown)
            view.findViewById<TextView>(R.id.tvCancelManage).setText(
                if (subscription?.canceledAt != null) R.string.subscription_reactivate else R.string.profile_cancel_subscription)
            view.findViewById<View>(R.id.btnCancelarAssinatura).isEnabled =
                subscription != null && !state.loading && !state.uncertainOperation
        }
    }
}
