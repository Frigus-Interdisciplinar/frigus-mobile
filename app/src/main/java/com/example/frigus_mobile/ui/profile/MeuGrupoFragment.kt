package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import com.example.frigus_mobile.R

/** Original group screen, now backed only by the confirmed G11 state. */
class MeuGrupoFragment : SubscriptionScreenFragment(R.layout.fragment_meu_grupo) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)
        view.findViewById<View>(R.id.btnConvidarMembro).setOnClickListener { showInvite() }
        observeSubscription(view) { state ->
            val enabled = !state.loading && !state.uncertainOperation
            view.findViewById<View>(R.id.btnConvidarMembro).isEnabled = enabled && state.selectedGroup?.isOwner == true
            val card = view.findViewById<LinearLayout>(R.id.cardMembrosGrupo)
            card.removeAllViews()
            card.addView(button(R.string.plans_select_group, enabled) { showGroups() })
            if (state.groupsLoaded && state.limits?.maxGroupsCreated?.let { state.groups.size < it } == true)
                card.addView(button(R.string.subscription_create_group, enabled) { showName(false) })
            val group = state.selectedGroup
            if (group == null) {
                card.addView(row(getString(if (state.groups.isEmpty()) R.string.subscription_no_groups else R.string.plans_no_group_selected)))
            } else {
                card.addView(row(getString(R.string.subscription_selected_group, value(group.name)), true))
                when (val stocks = state.groupStocks) {
                    null -> {
                        card.addView(row(getString(R.string.subscription_stocks_unknown)))
                        card.addView(button(R.string.subscription_refresh, !state.loading) { model.load() })
                    }
                    else -> {
                        if (stocks.isEmpty()) {
                            card.addView(row(getString(R.string.subscription_no_stocks)))
                            if (group.isOwner) card.addView(button(R.string.subscription_create_stock, enabled) { showName(true) })
                        }
                        stocks.forEach { card.addView(row(getString(R.string.subscription_stock, value(it.name), it.id))) }
                    }
                }
                card.addView(row(getString(R.string.subscription_members), true))
                if (group.members.isNullOrEmpty()) card.addView(row(getString(R.string.subscription_no_members)))
                group.members.orEmpty().forEach { member ->
                    card.addView(row(getString(R.string.subscription_member, value(member.name ?: member.email),
                        if (member.isOwner) getString(R.string.subscription_owner) else role(member.memberRole))))
                }
                if (group.isOwner) {
                    card.addView(row(getString(R.string.subscription_invitations), true))
                    if (state.invitations.isEmpty()) card.addView(row(getString(R.string.subscription_no_invitations)))
                    state.invitations.forEach { invitation ->
                        card.addView(row(getString(R.string.subscription_invitation, value(invitation.email), role(invitation.memberRole),
                            getString(when {
                                invitation.revokedAt != null -> R.string.subscription_invitation_revoked
                                invitation.acceptedAt != null -> R.string.subscription_accepted
                                else -> R.string.subscription_pending
                            }))))
                        if (invitation.id != null && invitation.revokedAt == null && invitation.acceptedAt == null)
                            card.addView(button(R.string.subscription_revoke, enabled) {
                                confirm(getString(R.string.subscription_confirm_revoke, value(invitation.email))) {
                                    model.revoke(invitation.id)
                                }
                            })
                    }
                }
            }
        }
    }
}
