package com.example.frigus_mobile.ui.profile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.utils.NetworkGuard
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class PerfilFragment : Fragment() {

    private lateinit var sessionManager: SessionManager

    companion object {
        private var cachedAvatarBitmap: Bitmap? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_perfil, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        preencherDadosSessao(view)
        atualizarVisibilidadeMoneySaving(view)

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

        view.findViewById<View>(R.id.btnSair).setOnClickListener {
            confirmarSaida()
        }

        carregarDadosPerfilDaApi()
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            preencherDadosSessao(it)
            atualizarVisibilidadeMoneySaving(it)
        }
        carregarDadosPerfilDaApi()
    }

    private fun atualizarVisibilidadeMoneySaving(rootView: View) {
        // A aba de MoneySaving só pode estar visível para usuários do tipo Doméstico
        val groupMoneySaving = rootView.findViewById<View>(R.id.groupMoneySaving)
        groupMoneySaving?.visibility = if (sessionManager.isDomestic) View.VISIBLE else View.GONE
    }

    private fun preencherDadosSessao(rootView: View) {
        val tvNome = rootView.findViewById<TextView>(R.id.tvNomeUsuario)
        val tvEmail = rootView.findViewById<TextView>(R.id.tvEmailUsuario)
        tvNome?.text = sessionManager.userName?.ifBlank { null } ?: "Juliana Martins"
        tvEmail?.text = sessionManager.userEmail?.ifBlank { null } ?: "juliana.martins@email.com"

        carregarFotoPerfil(rootView)
    }

    private fun carregarFotoPerfil(rootView: View) {
        val ivAvatar = rootView.findViewById<ImageView>(R.id.ivAvatar) ?: return

        if (cachedAvatarBitmap != null) {
            aplicarBitmapCircular(ivAvatar, cachedAvatarBitmap!!)
            return
        }

        val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
        val photoUrl = googleAccount?.photoUrl?.toString()
            ?: sessionManager.googlePhotoUrl
            ?: sessionManager.state.user?.profilePictureUrl

        if (!photoUrl.isNullOrBlank()) {
            sessionManager.saveGooglePhotoUrl(photoUrl)
            baixarEExibirFoto(photoUrl, ivAvatar)
        } else {
            exibirAvatarPadrao(ivAvatar)
        }
    }

    private fun baixarEExibirFoto(url: String, ivAvatar: ImageView) {
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.doInput = true
                    conn.connect()
                    val input = conn.inputStream
                    val bmp = BitmapFactory.decodeStream(input)
                    input.close()
                    conn.disconnect()
                    bmp
                } catch (_: Exception) {
                    null
                }
            }

            if (!isAdded || view == null) return@launch

            if (bitmap != null) {
                cachedAvatarBitmap = bitmap
                aplicarBitmapCircular(ivAvatar, bitmap)
            } else {
                exibirAvatarPadrao(ivAvatar)
            }
        }
    }

    private fun aplicarBitmapCircular(ivAvatar: ImageView, bitmap: Bitmap) {
        val circularDrawable = RoundedBitmapDrawableFactory.create(resources, bitmap).apply {
            isCircular = true
        }
        ivAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
        val borderPad = (2 * resources.displayMetrics.density).toInt()
        ivAvatar.setPadding(borderPad, borderPad, borderPad, borderPad)
        ivAvatar.imageTintList = null
        ivAvatar.setImageDrawable(circularDrawable)
    }

    private fun exibirAvatarPadrao(ivAvatar: ImageView) {
        ivAvatar.scaleType = ImageView.ScaleType.FIT_CENTER
        ivAvatar.setImageResource(R.drawable.ic_person)
        val pad = (20 * resources.displayMetrics.density).toInt()
        ivAvatar.setPadding(pad, pad, pad, pad)
        ivAvatar.imageTintList = ContextCompat.getColorStateList(
            requireContext(),
            R.color.profile_primary
        )
    }

    private fun carregarDadosPerfilDaApi() {
        if (!NetworkGuard.ensureConnected(activity) { carregarDadosPerfilDaApi() }) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    try {
                        val profResp = RetrofitClient.authService.getProfile().execute()
                        if (profResp.isSuccessful) profResp.body() else null
                    } catch (_: Exception) {
                        null
                    }
                }

                if (profile != null) {
                    sessionManager.saveUserProfile(profile)
                }

                view?.let { rootView ->
                    preencherDadosSessao(rootView)
                    atualizarVisibilidadeMoneySaving(rootView)

                    val plano = PlanoAtual.carregar(requireContext())
                    val tvNomePlano = rootView.findViewById<TextView>(R.id.tvNomePlanoPerfil)
                    val tvValidadePlano = rootView.findViewById<TextView>(R.id.tvValidadePlanoPerfil)
                    tvNomePlano?.text = "Plano ${plano.nome}"
                    val textoValidade = if (plano.cancelada) "Benefícios até ${plano.proximaCobranca}" else "Válido até ${plano.proximaCobranca}"
                    tvValidadePlano?.text = textoValidade
                }
            } catch (_: Exception) {
                // Silenciosamente mantém os dados preenchidos localmente
            }
        }
    }

    private fun confirmarSaida() {
        AlertDialog.Builder(requireContext())
            .setTitle("Sair da conta")
            .setMessage("Tem certeza de que deseja encerrar a sua sessão?")
            .setPositiveButton("Sair") { _, _ ->
                cachedAvatarBitmap = null
                SessionManager(requireContext()).clearSession()
                val intent = Intent(requireActivity(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
