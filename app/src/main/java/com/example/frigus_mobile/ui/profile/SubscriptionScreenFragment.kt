package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.text.InputFilter
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ActivePlan
import com.example.frigus_mobile.data.model.PlanBenefit
import com.example.frigus_mobile.data.model.PlanLimits
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Shared presentation for the existing G11 state, without adding a second network flow. */
abstract class SubscriptionScreenFragment(layout: Int) : Fragment(layout) {
    protected val model: SubscriptionViewModel by lazy {
        ViewModelProvider(requireActivity(), SubscriptionViewModel.Factory(requireContext()))[SubscriptionViewModel::class.java]
    }
    protected val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))

    protected fun observeSubscription(view: View, render: (SubscriptionState) -> Unit) {
        var previousFeedback: String? = null
        model.state.observe(viewLifecycleOwner) { state ->
            render(state)
            val feedback = when {
                state.loading -> null
                state.uncertainOperation -> getString(R.string.subscription_uncertain)
                state.error != null -> state.error.userMessage(getString(when {
                    state.error.status == 403 -> R.string.subscription_forbidden
                    state.error.code == "NETWORK_ERROR" -> R.string.subscription_network_error
                    state.error.code == "INVALID_RESPONSE" -> R.string.subscription_invalid_response
                    else -> R.string.subscription_load_error
                }))
                state.notice != null -> getString(when (state.notice) {
                    SubscriptionNotice.GROUP_CREATED -> R.string.subscription_group_created
                    SubscriptionNotice.STOCK_CREATED -> R.string.subscription_stock_created
                    SubscriptionNotice.CANCELED -> R.string.subscription_canceled
                    SubscriptionNotice.REACTIVATED -> R.string.subscription_reactivated
                    SubscriptionNotice.CHECKOUT -> R.string.subscription_checkout_received
                    SubscriptionNotice.INVITED -> R.string.subscription_invited
                    SubscriptionNotice.REVOKED -> R.string.subscription_revoked
                })
                else -> null
            }
            if (feedback != null && feedback != previousFeedback) {
                Snackbar.make(view, feedback, if (state.error != null || state.uncertainOperation)
                    Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_LONG)
                    .setAction(R.string.subscription_refresh) { model.load() }.show()
            }
            previousFeedback = feedback
        }
        model.loadIfNeeded()
    }

    protected fun renderCurrentPlan(view: View, state: SubscriptionState, name: Int, price: Int, validity: Int) {
        val subscription = state.subscription
        view.findViewById<TextView>(name).text = value(subscription?.plan?.name)
        view.findViewById<TextView>(price).text = planPrice(subscription?.plan)
        view.findViewById<TextView>(validity).text = when {
            state.loading -> getString(R.string.subscription_loading)
            subscription?.currentPeriodEnd != null -> getString(
                if (subscription.canceledAt != null) R.string.profile_benefits_until else R.string.profile_valid_until,
                date(subscription.currentPeriodEnd))
            else -> getString(R.string.subscription_unknown)
        }
    }

    protected fun planPrice(plan: ActivePlan?): String {
        val price = amount(plan?.price)
        return when (plan?.billingInterval?.uppercase(Locale.ROOT)) {
            "MONTH", "MONTHLY" -> getString(R.string.profile_per_month, price)
            "YEAR", "YEARLY", "ANNUAL" -> getString(R.string.plans_per_year, price)
            else -> price
        }
    }

    protected fun date(source: String?): String {
        if (source.isNullOrBlank()) return getString(R.string.subscription_unknown)
        return runCatching {
            val parsed = runCatching { LocalDate.parse(source.take(10)) }.getOrElse {
                LocalDate.parse(source, DateTimeFormatter.ofPattern("dd/MM/uuuu"))
            }
            parsed.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(resources.configuration.locales[0]))
        }.getOrDefault(getString(R.string.subscription_unknown))
    }

    protected fun showPayment() {
        AlertDialog.Builder(requireContext()).setTitle(R.string.profile_payment_method)
            .setMessage(R.string.subscription_payment_unavailable)
            .setPositiveButton(R.string.profile_billing_history) { _, _ -> abrirTela(HistoricoCobrancasFragment()) }
            .setNegativeButton(R.string.subscription_close, null).show()
    }

    protected fun showFeatures(plan: ActivePlan? = model.state.value?.subscription?.plan) {
        val current = model.state.value
        val isCurrent = plan?.planCode != null && plan.planCode == current?.subscription?.plan?.planCode
        val limits = plan?.limits ?: current?.limits?.takeIf { isCurrent }
        message(R.string.plans_features_details,
            listOfNotNull(benefitsText(plan?.benefits), limits?.let(::limitsText)).joinToString("\n\n"))
    }

    protected fun cancelSubscription() {
        val state = model.state.value ?: return
        val subscription = state.subscription ?: return
        if (state.loading || state.uncertainOperation) return
        val reactivate = subscription.canceledAt != null
        if (!reactivate && (subscription.status != "ACTIVE" || subscription.plan?.price?.let { it > 0 } != true)) {
            message(R.string.profile_cancel_subscription, getString(R.string.plans_no_upcoming_charge))
            return
        }
        confirm(getString(if (reactivate) R.string.subscription_confirm_reactivate else R.string.subscription_confirm_cancel)) {
            if (reactivate) model.reactivate() else model.cancel()
        }
    }

    protected fun showGroups() {
        val groups = model.state.value?.groups.orEmpty()
        if (groups.isEmpty()) { message(R.string.subscription_groups, getString(R.string.subscription_no_groups)); return }
        AlertDialog.Builder(requireContext()).setTitle(R.string.subscription_groups)
            .setItems(groups.map { value(it.name) }.toTypedArray()) { _, index -> groups[index].id?.let(model::selectGroup) }
            .setNegativeButton(R.string.subscription_close, null).show()
    }

    protected fun showName(stock: Boolean) {
        val input = EditText(requireContext()).apply {
            setHint(if (stock) R.string.subscription_stock_name else R.string.subscription_group_name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(120))
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (stock) R.string.subscription_create_stock else R.string.subscription_create_group).setView(input)
            .setPositiveButton(R.string.subscription_confirm, null).setNegativeButton(R.string.subscription_back, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) input.error = getString(R.string.subscription_invalid_name)
                else {
                    dialog.dismiss()
                    confirm(getString(if (stock) R.string.subscription_confirm_stock else R.string.subscription_confirm_group, name)) {
                        if (stock) model.createStock(name) else model.createGroup(name)
                    }
                }
            }
        }
        dialog.show()
    }

    protected fun showInvite() {
        if (model.state.value?.selectedGroup?.isOwner != true) {
            message(R.string.subscription_invite, getString(R.string.subscription_select_owned_group)); return
        }
        val email = EditText(requireContext()).apply {
            setHint(R.string.subscription_email)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val role = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.subscription_editor), getString(R.string.subscription_viewer)))
        }
        val form = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(email)
            addView(role)
        }
        val dialog = AlertDialog.Builder(requireContext()).setTitle(R.string.subscription_invite).setView(form)
            .setPositiveButton(R.string.subscription_confirm, null).setNegativeButton(R.string.subscription_back, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val address = email.text.toString().trim()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) email.error = getString(R.string.subscription_invalid_email)
                else {
                    dialog.dismiss()
                    confirm(getString(R.string.subscription_confirm_invite, address)) {
                        model.invite(address, if (role.selectedItemPosition == 0) "EDITOR" else "VIEWER")
                    }
                }
            }
        }
        dialog.show()
    }

    protected fun benefitsText(benefits: List<PlanBenefit>?): String = benefits?.takeIf { it.isNotEmpty() }?.joinToString("\n") {
        val enabled = getString(if (it.enabled) R.string.subscription_included else R.string.subscription_not_included)
        if (it.limit != null) getString(R.string.subscription_benefit_limit, value(it.label ?: it.code), enabled, it.limit)
        else getString(R.string.subscription_benefit, value(it.label ?: it.code), enabled)
    } ?: getString(R.string.subscription_no_benefits)

    protected fun limitsText(limits: PlanLimits): String = listOfNotNull(
        limits.maxGroupsCreated?.let { getString(R.string.subscription_limit_groups, it) },
        limits.maxGroupMembers?.let { getString(R.string.subscription_limit_members, it) },
        limits.maxStocks?.let { getString(R.string.subscription_limit_stocks, it) },
        limits.maxProductsPerStock?.let { getString(R.string.subscription_limit_products, it) },
        limits.allowOwnProducts?.let { getString(R.string.subscription_limit_own_products, yesNo(it)) },
        limits.allowSavedRecipes?.let { getString(R.string.subscription_limit_recipes, yesNo(it)) },
        limits.allowMoneySaving?.let { getString(R.string.subscription_limit_money_saving, yesNo(it)) },
        limits.hasMonthlyReport?.let { getString(R.string.subscription_limit_report, yesNo(it)) },
        limits.enterprise?.let { getString(R.string.subscription_limit_enterprise, yesNo(it)) },
        limits.costPerPublishedAd?.let { getString(R.string.subscription_limit_ad, amount(it)) }
    ).joinToString("\n")

    protected fun row(label: String, heading: Boolean = false) = TextView(requireContext()).apply {
        text = label
        typeface = resources.getFont(R.font.sarabun)
        setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.account_body_size))
        val gap = resources.getDimensionPixelSize(R.dimen.account_gap)
        setPadding(gap, gap, gap, gap)
        if (heading) { setTypeface(typeface, android.graphics.Typeface.BOLD); isAccessibilityHeading = true }
    }
    protected fun button(label: Int, enabled: Boolean, action: () -> Unit) = MaterialButton(requireContext()).apply {
        setText(label)
        isEnabled = enabled
        setOnClickListener { action() }
    }
    protected fun value(value: String?) = value?.takeIf(String::isNotBlank) ?: getString(R.string.subscription_unknown)
    protected fun amount(value: Double?) = value?.let(currency::format) ?: getString(R.string.subscription_unknown)
    protected fun yesNo(value: Boolean) = getString(if (value) R.string.subscription_yes else R.string.subscription_no)
    protected fun role(value: String?) = when (value) {
        "EDITOR" -> getString(R.string.subscription_editor)
        "VIEWER" -> getString(R.string.subscription_viewer)
        else -> getString(R.string.subscription_unknown)
    }
    protected fun confirm(body: String, action: () -> Unit) {
        AlertDialog.Builder(requireContext()).setMessage(body).setNegativeButton(R.string.subscription_back, null)
            .setPositiveButton(R.string.subscription_confirm) { _, _ -> action() }.show()
    }
    protected fun message(title: Int, body: String) {
        AlertDialog.Builder(requireContext()).setTitle(title).setMessage(body).setPositiveButton(R.string.subscription_close, null).show()
    }
}
