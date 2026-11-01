package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Read-only account screen recovered from a8ed829, using the shared M03 profile state. */
class MeusDadosFragment : Fragment(R.layout.fragment_meus_dados) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = SessionManager(requireContext())
        val model = ViewModelProvider(requireActivity())[ProfileViewModel::class.java]
        configurarVoltar(view)
        view.findViewById<View>(R.id.btnAtualizarMeusDados).setOnClickListener { model.reload() }
        view.findViewById<View>(R.id.btnPrivacidade).setOnClickListener { abrirTela(PrivacidadeFragment()) }
        view.findViewById<View>(R.id.btnAlterarSenha).setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle(R.string.profile_change_password)
                .setMessage(R.string.profile_password_not_integrated)
                .setPositiveButton(android.R.string.ok, null).show()
        }

        fun render(state: ProfileUiState?) {
            if (!session.isLoggedIn) return
            val user = state?.user?.takeIf { it.id == session.userId } ?: session.state.user
            fun value(text: String?) = text?.takeIf(String::isNotBlank) ?: getString(R.string.account_not_informed)
            view.findViewById<TextView>(R.id.tvDadosNome).text = value(user?.name)
            view.findViewById<TextView>(R.id.tvDadosEmail).text = value(user?.email)
            view.findViewById<TextView>(R.id.tvDadosNascimento).text = user?.birthDate?.let { birthDate ->
                runCatching { LocalDate.parse(birthDate).format(DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)) }.getOrNull()
            } ?: getString(R.string.account_not_informed)
            view.findViewById<TextView>(R.id.tvDadosTipoConta).setText(when (user?.accountType?.uppercase(Locale.ROOT)) {
                "DOMESTIC" -> R.string.account_domestic
                "COMMERCIAL" -> R.string.account_commercial
                else -> R.string.account_not_informed
            })
            view.findViewById<View>(R.id.btnAtualizarMeusDados).isEnabled = state?.loading != true
            view.findViewById<TextView>(R.id.tvMeusDadosStatus).apply {
                text = when {
                    state?.loading == true -> getString(R.string.account_loading)
                    state?.error != null -> state.error.userMessage(getString(when {
                        state.error.status == 403 -> R.string.account_forbidden
                        state.error.code == "NETWORK_ERROR" -> R.string.account_network_error
                        state.error.code == "INVALID_RESPONSE" -> R.string.account_invalid_response
                        else -> R.string.account_load_error
                    }))
                    else -> getString(R.string.account_read_only)
                }
                visibility = View.VISIBLE
            }
        }
        render(model.state.value)
        model.state.observe(viewLifecycleOwner, ::render)
        model.loadIfNeeded()
    }
}
