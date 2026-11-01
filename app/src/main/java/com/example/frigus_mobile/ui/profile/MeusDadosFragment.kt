package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.ConnectedSession
import com.example.frigus_mobile.data.api.ProfilePatch
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.UserResponse
import com.example.frigus_mobile.ui.home.MainActivity
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Connects account details to the BFF while retaining the restored account screen. */
class MeusDadosFragment : Fragment(R.layout.fragment_meus_dados) {
    private val api get() = RetrofitClient.accountSecurityService
    private var profileCall: Call<UserResponse>? = null
    private var updateCall: Call<UserResponse>? = null
    private var sessionsCall: Call<List<ConnectedSession>>? = null
    private var revokeCall: Call<Void>? = null
    private var profile: UserResponse? = null
    private var sessions = emptyList<ConnectedSession>()
    private var loadingProfile = false
    private var savingProfile = false
    private var profileUncertain = false
    private var loadingSessions = false
    private var statusSnackbar: Snackbar? = null
    private lateinit var nameView: TextView
    private lateinit var emailView: TextView
    private lateinit var birthDateView: TextView
    private lateinit var accountTypeView: TextView
    private lateinit var statusView: TextView
    private lateinit var refreshButton: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nameView = view.findViewById(R.id.tvDadosNome)
        emailView = view.findViewById(R.id.tvDadosEmail)
        birthDateView = view.findViewById(R.id.tvDadosNascimento)
        accountTypeView = view.findViewById(R.id.tvDadosTipoConta)
        statusView = view.findViewById(R.id.tvMeusDadosStatus)
        refreshButton = view.findViewById(R.id.btnAtualizarMeusDados)

        view.findViewById<View>(R.id.btnVoltar).setOnClickListener { parentFragmentManager.popBackStack() }
        refreshButton.setOnClickListener { loadProfile() }
        view.findViewById<View>(R.id.btnPrivacidade).setOnClickListener { open(ConfiguracoesFragment()) }
        view.findViewById<View>(R.id.btnAlterarSenha).setOnClickListener { open(AlterarSenhaFragment()) }
        addConnectedSessionsEntry(view)
        nameView.setOnClickListener { showEditDialog() }
        birthDateView.setOnClickListener { showEditDialog() }
        emailView.apply {
            isClickable = true
            isFocusable = true
            contentDescription = "E-mail. Toque para consultar a verificação."
            setOnClickListener { open(VerificacaoEmailFragment()) }
        }

        SessionManager(requireContext()).state.user?.let { cached ->
            profile = cached
            render(cached)
        }
        loadProfile()
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment)
            .addToBackStack(null).commit()
    }

    /** Reuses the restored account screen and mirrors its existing row style without changing its XML. */
    private fun addConnectedSessionsEntry(root: View) {
        val anchor = root.findViewById<View>(R.id.btnAlterarSenha) ?: return
        val card = anchor.parent as? LinearLayout ?: return
        if ((0 until card.childCount).any { card.getChildAt(it).tag == "connected-sessions-entry" }) return

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val divider = View(requireContext()).apply {
            tag = "connected-sessions-divider"
            setBackgroundResource(R.color.color_divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
            }
        }
        val row = LinearLayout(requireContext()).apply {
            id = View.generateViewId()
            tag = "connected-sessions-entry"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            val attributes = intArrayOf(android.R.attr.selectableItemBackground)
            val styled = requireContext().obtainStyledAttributes(attributes)
            background = styled.getDrawable(0)
            styled.recycle()
            contentDescription = getString(R.string.profile_connected_sessions)
            setOnClickListener { loadSessions() }
        }
        row.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_devices)
            imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.color_text_secondary)
            contentDescription = null
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        })
        row.addView(TextView(requireContext()).apply {
            text = getString(R.string.profile_connected_sessions)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary))
            textSize = 15f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.sarabun)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        })
        row.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_chevron_right)
            imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.color_text_secondary)
            contentDescription = null
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        })

        val insertion = card.indexOfChild(anchor) + 1
        card.addView(divider, insertion)
        card.addView(row, insertion + 1)
    }

    private fun loadProfile() {
        if (loadingProfile || savingProfile) return
        loadingProfile = true
        refreshButton.isEnabled = false
        statusView.visibility = View.VISIBLE
        statusView.text = "Carregando dados da conta..."
        profileCall?.cancel()
        profileCall = api.profile().also { request ->
            request.enqueue(object : Callback<UserResponse> {
                override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                    if (call.isCanceled || view == null) return
                    loadingProfile = false
                    refreshButton.isEnabled = true
                    val user = response.body()
                    val currentId = SessionManager(requireContext()).userId
                    if (response.code() == 200 && user != null && user.id == currentId &&
                        !user.name.isNullOrBlank() && !user.email.isNullOrBlank() && !user.birthDate.isNullOrBlank()) {
                        profile = user
                        profileUncertain = false
                        SessionManager(requireContext()).saveUserProfile(user)
                        render(user)
                        statusView.text = "Dados confirmados pelo servidor."
                    } else {
                        val message = when {
                            response.isSuccessful -> "O servidor retornou dados de conta incompletos."
                            response.code() >= 500 -> "O serviço de perfil está indisponível. Tente atualizar novamente."
                            else -> ApiError.parse(response).userMessage("Não foi possível carregar os dados (${response.code()}).")
                        }
                        statusView.text = message
                    }
                }

                override fun onFailure(call: Call<UserResponse>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    loadingProfile = false
                    refreshButton.isEnabled = true
                    statusView.visibility = View.VISIBLE
                    statusView.text = "Falha de conexão ao carregar os dados. Tente atualizar novamente."
                }
            })
        }
    }

    private fun render(user: UserResponse) {
        nameView.text = user.name?.takeIf(String::isNotBlank) ?: getString(R.string.account_not_informed)
        emailView.text = user.email?.takeIf(String::isNotBlank) ?: getString(R.string.account_not_informed)
        birthDateView.text = displayDate(user.birthDate)
        accountTypeView.text = when (user.accountType?.uppercase(Locale.ROOT)) {
            "DOMESTIC" -> getString(R.string.account_domestic)
            "COMMERCIAL" -> getString(R.string.account_commercial)
            else -> getString(R.string.account_not_informed)
        }
        nameView.contentDescription = "${nameView.text}. Toque para editar."
        birthDateView.contentDescription = "${birthDateView.text}. Toque para editar."
    }

    private fun showEditDialog() {
        val current = profile ?: return showStatus("Carregue os dados da conta antes de editar.")
        if (savingProfile || profileUncertain) return
        val context = requireContext()
        val fields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        val nameInput = EditText(context).apply {
            hint = "Nome completo"
            setText(current.name.orEmpty())
            fields.addView(this)
        }
        val dateInput = EditText(context).apply {
            hint = "Nascimento (DD/MM/AAAA)"
            setText(displayDate(current.birthDate))
            fields.addView(this)
        }
        val message = TextView(context).apply { fields.addView(this) }
        val dialog = AlertDialog.Builder(context).setTitle("Editar dados da conta")
            .setView(fields).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val enteredName = nameInput.text.toString().trim()
                val parsedDate = runCatching {
                    LocalDate.parse(dateInput.text.toString().trim(), DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT))
                }.getOrNull()
                if (enteredName.isBlank() || parsedDate == null || !parsedDate.isBefore(LocalDate.now())) {
                    message.text = "Informe o nome e uma data válida no formato DD/MM/AAAA."
                    return@setOnClickListener
                }
                updateProfile(enteredName, parsedDate, dialog, message)
            }
        }
        dialog.show()
    }

    private fun updateProfile(name: String, date: LocalDate, dialog: AlertDialog, message: TextView) {
        if (savingProfile || profileUncertain) return
        savingProfile = true
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        message.text = "Salvando dados..."
        updateCall = api.updateProfile(ProfilePatch(name, date.toString())).also { request ->
            request.enqueue(object : Callback<UserResponse> {
                override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                    if (call.isCanceled || view == null) return
                    savingProfile = false
                    val updated = response.body()
                    if (response.code() == 200 && updated != null && updated.id == profile?.id &&
                        updated.name == name && updated.birthDate?.take(10) == date.toString()) {
                        profile = updated
                        SessionManager(requireContext()).saveUserProfile(updated)
                        ViewModelProvider(requireActivity())[ProfileViewModel::class.java].reload()
                        render(updated)
                        dialog.dismiss()
                        showStatus("Dados confirmados pelo servidor.")
                    } else if (response.isSuccessful || response.code() >= 500) {
                        profileUncertain = true
                        dialog.dismiss()
                        showStatus("Não foi possível confirmar o salvamento. Atualize os dados antes de editar novamente.")
                    } else {
                        message.text = ApiError.parse(response).userMessage("Não foi possível salvar (${response.code()}).")
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                }

                override fun onFailure(call: Call<UserResponse>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    savingProfile = false
                    profileUncertain = true
                    dialog.dismiss()
                    showStatus("Resultado incerto. Atualize os dados antes de editar novamente.")
                }
            })
        }
    }

    private fun loadSessions() {
        if (loadingSessions) return
        loadingSessions = true
        statusSnackbar?.dismiss()
        statusSnackbar = Snackbar.make(requireView(), "Carregando sessões...", Snackbar.LENGTH_INDEFINITE).also { it.show() }
        sessionsCall = api.sessions().also { request ->
            request.enqueue(object : Callback<List<ConnectedSession>> {
                override fun onResponse(call: Call<List<ConnectedSession>>, response: Response<List<ConnectedSession>>) {
                    if (call.isCanceled || view == null) return
                    loadingSessions = false
                    statusSnackbar?.dismiss()
                    statusSnackbar = null
                    val body = response.body()
                    if (response.code() == 200 && body != null) {
                        sessions = body.filter { !it.id.isNullOrBlank() && runCatching { UUID.fromString(it.id) }.isSuccess }
                        showSessions()
                    } else {
                        val message = when {
                            response.isSuccessful -> "Resposta de sessões inválida."
                            response.code() >= 500 -> "O serviço de sessões está indisponível. Tente novamente."
                            else -> ApiError.parse(response).userMessage("Não foi possível carregar sessões (${response.code()}).")
                        }
                        showStatus(message, retrySessions = true)
                    }
                }

                override fun onFailure(call: Call<List<ConnectedSession>>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    loadingSessions = false
                    statusSnackbar?.dismiss()
                    statusSnackbar = null
                    showStatus("Falha de conexão ao carregar sessões.", retrySessions = true)
                }
            })
        }
    }

    private fun showSessions() {
        if (sessions.isEmpty()) {
            AlertDialog.Builder(requireContext()).setTitle("Sessões conectadas")
                .setMessage("Nenhuma sessão ativa foi retornada pelo servidor.")
                .setPositiveButton("Fechar", null).show()
            return
        }
        val labels = sessions.map { item ->
            buildString {
                append(item.deviceName?.takeIf(String::isNotBlank) ?: "Dispositivo")
                if (item.current) append(" · este dispositivo")
                item.lastActiveAt?.takeIf(String::isNotBlank)?.let { append("\nÚltima atividade: $it") }
                item.location?.takeIf(String::isNotBlank)?.let { append("\n$it") }
            }
        }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle("Sessões conectadas")
            .setItems(labels) { _, index -> confirmRevoke(sessions[index]) }
            .setNegativeButton("Fechar", null).show()
    }

    private fun confirmRevoke(session: ConnectedSession) {
        val id = session.id ?: return
        AlertDialog.Builder(requireContext()).setTitle("Encerrar sessão?")
            .setMessage(if (session.current) "Você será desconectado deste dispositivo."
                else "Este dispositivo perderá o acesso à conta.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Encerrar") { _, _ -> revokeSession(id, session.current) }.show()
    }

    private fun revokeSession(id: String, current: Boolean) {
        revokeCall = api.revoke(id).also { request ->
            request.enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (call.isCanceled || view == null) return
                    when {
                        response.code() == 204 && current -> (requireActivity() as MainActivity).signOut()
                        response.code() == 204 -> {
                            showStatus("Sessão encerrada pelo servidor.")
                            loadSessions()
                        }
                        response.code() >= 500 || response.isSuccessful -> {
                            showStatus("Resultado incerto. Atualizando a lista para conferir; o pedido não será repetido.")
                            loadSessions()
                        }
                        else -> showStatus(ApiError.parse(response).userMessage("Não foi possível encerrar a sessão (${response.code()})."))
                    }
                }

                override fun onFailure(call: Call<Void>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    showStatus("Resultado incerto. Atualizando a lista para conferir; o pedido não será repetido.")
                    loadSessions()
                }
            })
        }
    }

    private fun displayDate(value: String?): String = try {
        if (value.isNullOrBlank()) getString(R.string.account_not_informed)
        else LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT))
    } catch (_: Exception) { value?.takeIf(String::isNotBlank) ?: getString(R.string.account_not_informed) }

    private fun showStatus(message: String, retrySessions: Boolean = false) {
        statusView.visibility = View.VISIBLE
        statusView.text = message
        if (retrySessions) {
            statusSnackbar?.dismiss()
            statusSnackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_INDEFINITE)
                .setAction("Atualizar sessões") { loadSessions() }.also { it.show() }
        }
    }

    override fun onDestroyView() {
        profileCall?.cancel()
        updateCall?.cancel()
        sessionsCall?.cancel()
        revokeCall?.cancel()
        statusSnackbar?.dismiss()
        profileCall = null
        updateCall = null
        sessionsCall = null
        revokeCall = null
        loadingProfile = false
        savingProfile = false
        loadingSessions = false
        super.onDestroyView()
    }
}
