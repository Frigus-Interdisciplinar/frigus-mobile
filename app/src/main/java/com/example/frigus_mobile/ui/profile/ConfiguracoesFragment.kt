package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

class ConfiguracoesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_configuracoes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarVoltar(view)
        addNotificationsEntry(view)

        view.findViewById<View>(R.id.btnPreferenciasAlimentos).setOnClickListener {
            abrirTela(AppSettingsSectionFragment.newInstance(AppSettingsSectionFragment.FOOD))
        }
        view.findViewById<View>(R.id.btnUnidadesMedidas).setOnClickListener {
            abrirTela(AppSettingsSectionFragment.newInstance(AppSettingsSectionFragment.UNITS))
        }
        view.findViewById<View>(R.id.btnPrivacidade).setOnClickListener {
            abrirTela(AppSettingsSectionFragment.newInstance(AppSettingsSectionFragment.PRIVACY))
        }
        view.findViewById<View>(R.id.btnAlterarSenha).setOnClickListener {
            abrirTela(AlterarSenhaFragment())
        }
        view.findViewById<View>(R.id.btnInformacoesConta).setOnClickListener {
            abrirTela(MeusDadosFragment())
        }
    }

    /** Adds the missing route without changing the restored PR #31 layout XML. */
    private fun addNotificationsEntry(root: View) {
        val unitsRow = root.findViewById<View>(R.id.btnUnidadesMedidas) ?: return
        val card = unitsRow.parent as? LinearLayout ?: return
        if ((0 until card.childCount).any { card.getChildAt(it).tag == "notification-settings-entry" }) return

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val divider = View(requireContext()).apply {
            tag = "notification-settings-divider"
            setBackgroundResource(R.color.profile_divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            }
        }
        val row = LinearLayout(requireContext()).apply {
            id = View.generateViewId()
            tag = "notification-settings-entry"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            minimumHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
            isClickable = true
            isFocusable = true
            val attributes = intArrayOf(android.R.attr.selectableItemBackground)
            val styled = requireContext().obtainStyledAttributes(attributes)
            background = styled.getDrawable(0)
            styled.recycle()
            contentDescription = getString(R.string.profile_notifications)
            setOnClickListener { abrirTela(NotificationSettingsFragment()) }
        }
        row.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_notifications)
            imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.darkBlue)
            contentDescription = null
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(6) }
        })
        row.addView(TextView(requireContext()).apply {
            text = getString(R.string.profile_notifications)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
            textSize = 13f
            typeface = ResourcesCompat.getFont(requireContext(), R.font.sarabun)
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
            }
        })
        row.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_chevron_right)
            imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.darkBlue)
            contentDescription = null
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(4) }
        })

        val insertion = card.indexOfChild(unitsRow) + 1
        card.addView(divider, insertion)
        card.addView(row, insertion + 1)
    }
}
