package com.example.frigus_mobile.ui.profile

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.ActivePlan
import com.example.frigus_mobile.data.model.PlanLimits
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

class AlterarPlanoFragment : SubscriptionScreenFragment(R.layout.fragment_alterar_plano) {
    private data class Feature(val label: String, val enabled: Boolean?)
    private var displayedPlans = emptyList<ActivePlan>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)
        val scroll = view.findViewById<HorizontalScrollView>(R.id.planCarouselScroll)
        val cards = view.findViewById<LinearLayout>(R.id.containerPlanos)
        val dots = view.findViewById<LinearLayout>(R.id.indicadoresPlanos)
        fun indicators(index: Int) {
            dots.removeAllViews()
            repeat(displayedPlans.size) { position ->
                dots.addView(ImageView(requireContext()).apply {
                    setImageResource(if (position == index) R.drawable.plan_figma_dot_active else R.drawable.plan_figma_dot)
                    contentDescription = getString(R.string.plans_page_position, position + 1, displayedPlans.size)
                    layoutParams = LinearLayout.LayoutParams(dp(if (position == index) 18 else 8), dp(8)).apply {
                        marginStart = dp(4); marginEnd = dp(4)
                    }
                    setOnClickListener { scroll.smoothScrollTo(position * dp(216), 0) }
                })
            }
        }
        scroll.setOnScrollChangeListener { _, x, _, _, _ ->
            val position = (x.toDouble() / dp(216)).roundToInt().coerceIn(0, (displayedPlans.size - 1).coerceAtLeast(0))
            model.selectedPlanIndex = position
            indicators(position)
        }
        observeSubscription(view) { state ->
            displayedPlans = state.plans.sortedWith(compareBy<ActivePlan> { it.price ?: Double.MAX_VALUE }.thenBy { it.name })
            cards.removeAllViews()
            displayedPlans.forEach { plan ->
                val card = layoutInflater.inflate(R.layout.item_profile_plan_option, cards, false)
                card.findViewById<TextView>(R.id.tvPlanoOpcaoNome).text = value(plan.name)
                card.findViewById<TextView>(R.id.tvPlanoOpcaoPreco).text = planPrice(plan)
                val current = plan.planCode != null && plan.planCode == state.subscription?.plan?.planCode
                val free = plan.price == 0.0
                val family = plan.planCode?.contains("FAMILY", true) == true || plan.name?.contains("Fam", true) == true
                card.findViewById<ImageView>(R.id.ivPlanoIcone).setImageResource(when {
                    free -> R.drawable.plan_figma_free
                    family -> R.drawable.plan_figma_family
                    else -> R.drawable.plan_figma_premium
                })
                card.findViewById<ImageView>(R.id.ivPlanoFundo).setImageResource(
                    if (!free && !family) R.drawable.plan_figma_premium_bg else R.drawable.plan_figma_free_bg)
                val features = card.findViewById<LinearLayout>(R.id.containerBeneficios)
                val limits = plan.limits ?: state.limits.takeIf { current }
                planFeatures(plan, limits).forEach { feature ->
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        minimumHeight = resources.getDimensionPixelSize(R.dimen.plans_card_feature_row)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    row.addView(TextView(requireContext()).apply {
                        setText(when (feature.enabled) {
                            true -> R.string.plans_check_yes
                            false -> R.string.plans_check_no
                            null -> R.string.plans_check_unknown
                        })
                        typeface = resources.getFont(R.font.sarabun)
                        setTextColor(ContextCompat.getColor(requireContext(), if (feature.enabled == false)
                            R.color.plans_cancel_color else R.color.blue1))
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT)
                    })
                    row.addView(TextView(requireContext()).apply {
                        text = feature.label
                        typeface = resources.getFont(R.font.sarabun)
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
                        textSize = 12f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    features.addView(row)
                }
                card.setOnClickListener { showFeatures(plan) }
                card.findViewById<MaterialButton>(R.id.btnEscolherPlano).apply {
                    text = if (current) getString(R.string.plans_current) else
                        getString(R.string.plans_choose_named, value(plan.name))
                    isEnabled = !current && !state.loading && !state.uncertainOperation && !state.checkoutAwaitingRefresh
                    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                        if (current) R.color.pure_white else R.color.blue1))
                    setTextColor(ContextCompat.getColor(requireContext(), if (current) R.color.blue1 else R.color.pure_white))
                    strokeWidth = if (current) dp(1) else 0
                    strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.profile_divider))
                    setOnClickListener {
                        if (free && plan.active) confirm(getString(R.string.subscription_confirm_free, value(plan.name))) {
                            model.freeCheckout(plan)
                        } else message(R.string.profile_choose_plan, getString(R.string.subscription_paid_unavailable))
                    }
                }
                cards.addView(card)
            }
            view.findViewById<View>(R.id.progressPlanos).visibility = if (state.loading) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.tvPlanosVazio).visibility =
                if (!state.loading && displayedPlans.isEmpty()) View.VISIBLE else View.GONE
            val position = model.selectedPlanIndex.coerceIn(0, (displayedPlans.size - 1).coerceAtLeast(0))
            indicators(position)
            scroll.post { if (isAdded && this.view === view) scroll.scrollTo(position * dp(216), 0) }
        }
    }

    private fun planFeatures(plan: ActivePlan, limits: PlanLimits?): List<Feature> {
        val benefits = plan.benefits.orEmpty()
        if (benefits.isNotEmpty()) return benefits.map {
            Feature(if (it.limit != null) getString(R.string.subscription_benefit_limit,
                value(it.label ?: it.code), getString(if (it.enabled) R.string.subscription_included else R.string.subscription_not_included),
                it.limit) else value(it.label ?: it.code), it.enabled)
        }
        if (limits == null) return listOf(Feature(getString(R.string.subscription_unknown), null))
        return listOf(
            Feature(limits.maxGroupMembers?.let { getString(R.string.plans_limits_members, it) }
                ?: getString(R.string.subscription_unknown), limits.maxGroupMembers?.let { it > 0 }),
            Feature(limits.maxProductsPerStock?.let { getString(R.string.plans_limits_products, it) }
                ?: getString(R.string.subscription_unknown), limits.maxProductsPerStock?.let { it > 0 }),
            Feature(limits.maxStocks?.let { getString(R.string.plans_limits_stocks, it) }
                ?: getString(R.string.subscription_unknown), limits.maxStocks?.let { it > 0 }),
            Feature(getString(R.string.subscription_limit_own_products,
                limits.allowOwnProducts?.let(::yesNo) ?: getString(R.string.subscription_unknown)), limits.allowOwnProducts),
            Feature(getString(R.string.subscription_limit_recipes,
                limits.allowSavedRecipes?.let(::yesNo) ?: getString(R.string.subscription_unknown)), limits.allowSavedRecipes),
            Feature(getString(R.string.subscription_limit_money_saving,
                limits.allowMoneySaving?.let(::yesNo) ?: getString(R.string.subscription_unknown)), limits.allowMoneySaving)
        )
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
