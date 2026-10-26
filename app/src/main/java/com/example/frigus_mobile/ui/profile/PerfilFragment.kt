package com.example.frigus_mobile.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.push.PushRegistration
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.utils.NetworkGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class PerfilFragment : Fragment() {

    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_perfil, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        preencherDadosSessao(view)

        view.findViewById<View>(R.id.btnMeusDados).setOnClickListener {
            abrirFragmento(MeusDadosFragment())
        }

        view.findViewById<View>(R.id.btnAssinaturaPlanos).setOnClickListener {
            abrirFragmento(AssinaturaPlanosFragment())
        }

        view.findViewById<View>(R.id.btnSeguranca).setOnClickListener {
            abrirFragmento(SegurancaFragment())
        }

        view.findViewById<View>(R.id.btnConfiguracoes).setOnClickListener {
            abrirFragmento(ConfiguracoesFragment())
        }

        view.findViewById<View>(R.id.btnAjudaSuporte).setOnClickListener {
            abrirFragmento(AjudaSuporteFragment())
        }

        view.findViewById<View>(R.id.btnMoneySaving).setOnClickListener {
            abrirFragmento(MoneySavingFragment())
        }

        view.findViewById<View>(R.id.cardPlanoPremium).setOnClickListener {
            abrirFragmento(AssinaturaPlanosFragment())
        }

        view.findViewById<View>(R.id.btnSair).setOnClickListener {
            confirmarSaida()
        }

        carregarDadosPerfilDaApi()
    }

    override fun onResume() {
        super.onResume()
        view?.let { preencherDadosSessao(it) }
        carregarDadosPerfilDaApi()
    }

    private fun preencherDadosSessao(rootView: View) {
        val tvNome = rootView.findViewById<TextView>(R.id.tvNomeUsuario)
        val tvEmail = rootView.findViewById<TextView>(R.id.tvEmailUsuario)
        tvNome?.text = sessionManager.userName ?: "Usuário"
        tvEmail?.text = sessionManager.userEmail ?: ""
    }

    private fun carregarDadosPerfilDaApi() {
        if (!NetworkGuard.ensureConnected(activity) { carregarDadosPerfilDaApi() }) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (profile, subscription) = withContext(Dispatchers.IO) {
                    val profResp = RetrofitClient.authService.getProfile().execute()
                    if (!profResp.isSuccessful) {
                        throw IOException("Não foi possível carregar os dados do perfil (Código ${profResp.code()}).")
                    }
                    val subResp = try {
                        RetrofitClient.authService.getMySubscription().execute()
                    } catch (_: Exception) {
                        null
                    }
                    Pair(profResp.body(), if (subResp?.isSuccessful == true) subResp.body() else null)
                }

                if (profile != null) {
                    sessionManager.saveUserProfile(profile)
                }
                view?.let { rootView ->
                    preencherDadosSessao(rootView)
                    val tvNomePlano = rootView.findViewById<TextView>(R.id.tvNomePlanoPerfil)
                    val tvValidadePlano = rootView.findViewById<TextView>(R.id.tvValidadePlanoPerfil)
                    if (subscription?.plan != null && subscription.status == "ACTIVE") {
                        tvNomePlano?.text = "Plano ${subscription.plan.name ?: "Ativo"}"
                        val validade = formatarDataIso(subscription.currentPeriodEnd)
                        tvValidadePlano?.text = if (validade != null) "Válido até $validade" else "Assinatura ativa"
                    } else {
                        tvNomePlano?.text = "Plano Gratuito"
                        tvValidadePlano?.text = "Sem assinatura paga ativa"
                    }
                }
            } catch (e: Exception) {
                NetworkGuard.showBlockingError(activity, e.message) {
                    carregarDadosPerfilDaApi()
                }
            }
        }
    }

    private fun formatarDataIso(iso: String?): String? {
        if (iso.isNullOrBlank() || iso.length < 10) return null
        val parts = iso.substring(0, 10).split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else null
    }

    private fun abrirFragmento(fragmento: Fragment) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragment_container, fragmento)
            .addToBackStack(null)
            .commit()
    }

    private fun confirmarSaida() {
        AlertDialog.Builder(requireContext())
            .setTitle("Sair da conta")
            .setMessage("Tem certeza de que deseja encerrar a sua sessão?")
            .setPositiveButton("Sair") { _, _ ->
                val appContext = requireContext().applicationContext
                val host = requireActivity()
                lifecycleScope.launch {
                    try {
                        PushRegistration.unregister(appContext)
                    } finally {
                        SessionManager(appContext).clearSession()
                        val intent = Intent(host, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        host.startActivity(intent)
                        host.finish()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
