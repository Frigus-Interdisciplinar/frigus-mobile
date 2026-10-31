package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.utils.NetworkGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MeusDadosFragment : Fragment() {

    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_meus_dados, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        preencherCampos(view)

        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnPrivacidade).setOnClickListener {
            Toast.makeText(requireContext(), "Políticas de privacidade", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnAlterarSenha).setOnClickListener {
            Toast.makeText(requireContext(), "Alterar senha da conta", Toast.LENGTH_SHORT).show()
        }

        carregarDadosDaApi()
    }

    private fun preencherCampos(rootView: View) {
        rootView.findViewById<TextView>(R.id.tvDadosNome)?.text = sessionManager.userName ?: "-"
        rootView.findViewById<TextView>(R.id.tvDadosEmail)?.text = sessionManager.userEmail ?: "-"
        rootView.findViewById<TextView>(R.id.tvDadosNascimento)?.text =
            formatarDataIso(sessionManager.userBirthDate) ?: "-"
        val tipo = when (sessionManager.userAccountType?.uppercase()) {
            "DOMESTIC" -> "Doméstica"
            "COMMERCIAL" -> "Comercial"
            else -> sessionManager.userAccountType ?: "-"
        }
        rootView.findViewById<TextView>(R.id.tvDadosTipoConta)?.text = tipo
    }

    private fun carregarDadosDaApi() {
        if (!NetworkGuard.ensureConnected(activity) { carregarDadosDaApi() }) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    val resp = RetrofitClient.authService.getProfile().execute()
                    if (!resp.isSuccessful) {
                        throw IOException("Não foi possível carregar seus dados na API (Código ${resp.code()}).")
                    }
                    resp.body()
                }
                if (profile != null) {
                    sessionManager.saveUserProfile(profile)
                    view?.let { preencherCampos(it) }
                }
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(activity, e.message) {
                    carregarDadosDaApi()
                }
            }
        }
    }

    private fun formatarDataIso(iso: String?): String? {
        if (iso.isNullOrBlank() || iso.length < 10) return null
        val parts = iso.substring(0, 10).split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
    }
}
