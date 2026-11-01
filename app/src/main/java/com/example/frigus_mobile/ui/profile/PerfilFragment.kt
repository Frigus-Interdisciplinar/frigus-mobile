package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.home.MainActivity
import com.google.android.material.snackbar.Snackbar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Original profile menu, connected to the existing authenticated BFF state. */
class PerfilFragment : Fragment(R.layout.fragment_perfil) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = SessionManager(requireContext())
        val model = ViewModelProvider(requireActivity())[ProfileViewModel::class.java]
        val avatar = ViewModelProvider(requireActivity())[ProfileAvatarViewModel::class.java]
        val subscription = ViewModelProvider(requireActivity(),
            SubscriptionViewModel.Factory(requireContext()))[SubscriptionViewModel::class.java]
        val host = requireActivity() as MainActivity

        view.findViewById<View>(R.id.cardPlanoPremium).setOnClickListener {
            abrirTela(AssinaturaPlanosFragment())
        }
        view.findViewById<View>(R.id.btnAssinaturaPlanos).setOnClickListener {
            abrirTela(AssinaturaPlanosFragment())
        }
        view.findViewById<View>(R.id.btnConfiguracoes).setOnClickListener {
            abrirTela(ConfiguracoesFragment())
        }
        view.findViewById<View>(R.id.btnAjudaSuporte).setOnClickListener {
            abrirTela(AjudaSuporteFragment())
        }
        view.findViewById<View>(R.id.btnMoneySaving).setOnClickListener {
            abrirTela(MoneySavingFragment())
        }
        // The BFF determines entitlement; the menu is shared by domestic and commercial accounts.
        view.findViewById<View>(R.id.groupMoneySaving).visibility = View.VISIBLE
        view.findViewById<View>(R.id.btnSair).setOnClickListener { button ->
            button.isEnabled = false
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.account_logout_title)
                .setMessage(R.string.account_logout_message)
                .setPositiveButton(R.string.account_logout) { _, _ -> host.signOut() }
                .setNegativeButton(R.string.account_cancel, null)
                .setOnDismissListener { button.isEnabled = true }
                .show()
        }

        fun showIdentity() {
            val user = model.state.value?.user ?: session.state.user
            fun value(text: String?) = text?.takeIf(String::isNotBlank) ?: getString(R.string.account_not_informed)
            view.findViewById<TextView>(R.id.tvNomeUsuario).text = value(user?.name)
            view.findViewById<TextView>(R.id.tvEmailUsuario).text = value(user?.email)
        }
        showIdentity()
        val picture = view.findViewById<ImageView>(R.id.ivAvatar)
        avatar.state.observe(viewLifecycleOwner) { state ->
            val bitmap = state.bitmap?.takeIf { session.isLoggedIn && state.userId == session.userId &&
                state.generation == session.state.generation }
            if (bitmap == null) {
                picture.scaleType = ImageView.ScaleType.FIT_CENTER
                val padding = resources.getDimensionPixelSize(R.dimen.account_avatar_padding)
                picture.setPadding(padding, padding, padding, padding)
                picture.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.profile_primary)
                picture.setImageResource(R.drawable.ic_person)
            } else {
                picture.scaleType = ImageView.ScaleType.CENTER_CROP
                picture.setPadding(0, 0, 0, 0)
                picture.imageTintList = null
                picture.setImageDrawable(RoundedBitmapDrawableFactory.create(resources, bitmap).apply { isCircular = true })
            }
        }
        model.state.observe(viewLifecycleOwner) { state ->
            if (!session.isLoggedIn) return@observe
            showIdentity()
            if (state.user != null) avatar.load()
            state.error?.let { error ->
                val message = error.userMessage(getString(when {
                    error.code == "NETWORK_ERROR" -> R.string.account_network_error
                    error.code == "INVALID_RESPONSE" -> R.string.account_invalid_response
                    error.status == 403 -> R.string.account_forbidden
                    else -> R.string.account_load_error
                }))
                Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                    .setAction(R.string.account_retry) { model.reload() }.show()
            }
        }
        subscription.state.observe(viewLifecycleOwner) { state ->
            if (!session.isLoggedIn) return@observe
            val current = state.subscription
            view.findViewById<TextView>(R.id.tvNomePlanoPerfil).text =
                current?.plan?.name?.takeIf(String::isNotBlank)
                    ?.let { getString(R.string.profile_recovered_plan_name, it) }
                    ?: getString(R.string.profile_recovered_plan_pending)
            view.findViewById<TextView>(R.id.tvValidadePlanoPerfil).text = when {
                state.loading -> getString(R.string.subscription_loading)
                state.uncertainOperation -> getString(R.string.profile_recovered_plan_reconcile)
                current?.currentPeriodEnd != null -> getString(
                    if (current.canceledAt != null) R.string.profile_benefits_until else R.string.profile_valid_until,
                    date(current.currentPeriodEnd))
                else -> getString(R.string.profile_recovered_plan_hint)
            }
        }
        model.loadIfNeeded()
        subscription.loadIfNeeded()
        avatar.load()
    }

    private fun date(text: String?): String {
        if (text.isNullOrBlank()) return getString(R.string.account_not_informed)
        return runCatching {
            val value = runCatching { LocalDate.parse(text.take(10)) }.getOrElse {
                LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/uuuu"))
            }
            value.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(resources.configuration.locales[0]))
        }.getOrDefault(getString(R.string.account_not_informed))
    }
}
