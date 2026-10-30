package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.home.MainActivity
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

class PerfilFragment : Fragment(R.layout.fragment_perfil) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val model = ViewModelProvider(requireActivity())[ProfileViewModel::class.java]
        val host = requireActivity() as MainActivity
        view.findViewById<View>(R.id.accountReload).setOnClickListener { model.reload() }
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
        model.state.observe(viewLifecycleOwner) { state ->
            render(view, state)
            if (state.user != null) host.refreshSessionIdentity()
        }
        model.loadIfNeeded()
    }

    private fun render(view: View, state: ProfileUiState) {
        view.findViewById<View>(R.id.accountProgress).visibility = if (state.loading) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.accountFields).visibility = if (state.user != null) View.VISIBLE else View.GONE
        view.findViewById<MaterialButton>(R.id.accountReload).apply {
            isEnabled = !state.loading
            setText(if (state.error == null) R.string.account_refresh else R.string.account_retry)
        }
        view.findViewById<TextView>(R.id.accountStatus).apply {
            visibility = if (state.loading || state.error != null) View.VISIBLE else View.GONE
            val error = state.error
            text = when {
                state.loading -> getString(R.string.account_loading)
                error?.code == "NETWORK_ERROR" -> getString(R.string.account_network_error)
                error?.code == "INVALID_RESPONSE" -> getString(R.string.account_invalid_response)
                error != null -> error.userMessage(getString(
                    if (error.status == 403) R.string.account_forbidden else R.string.account_load_error
                ))
                else -> ""
            }
        }
        state.user?.let { user ->
            fun value(text: String?) = text?.takeIf(String::isNotBlank) ?: getString(R.string.account_not_informed)
            view.findViewById<TextView>(R.id.tvNomeUsuario).text = value(user.name)
            view.findViewById<TextView>(R.id.tvEmailUsuario).text = value(user.email)
            view.findViewById<TextView>(R.id.accountBirthDate).text = birthDate(user.birthDate)
            view.findViewById<TextView>(R.id.accountType).text = when (user.accountType) {
                "DOMESTIC" -> getString(R.string.account_domestic)
                "COMMERCIAL" -> getString(R.string.account_commercial)
                else -> value(user.accountType)
            }
        }
    }

    private fun birthDate(value: String?): String {
        if (value.isNullOrBlank()) return getString(R.string.account_not_informed)
        return try {
            LocalDate.parse(value).format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(resources.configuration.locales[0])
            )
        } catch (_: DateTimeParseException) {
            getString(R.string.account_not_informed)
        }
    }
}
