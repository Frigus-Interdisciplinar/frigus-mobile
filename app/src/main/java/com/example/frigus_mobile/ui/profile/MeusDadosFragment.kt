package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.ProfilePatch
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.UserResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MeusDadosFragment : Fragment(R.layout.fragment_meus_dados) {
    private val api get() = RetrofitClient.accountSecurityService
    private var profileCall: Call<UserResponse>? = null
    private var updateCall: Call<UserResponse>? = null
    private var profile: UserResponse? = null
    private var loadingProfile = false
    private var savingProfile = false
    private var profileUncertain = false
    private lateinit var nameView: TextView
    private lateinit var emailView: TextView
    private lateinit var birthDateView: TextView
    private lateinit var accountTypeView: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nameView = view.findViewById(R.id.tvDadosNome)
        emailView = view.findViewById(R.id.tvDadosEmail)
        birthDateView = view.findViewById(R.id.tvDadosNascimento)
        accountTypeView = view.findViewById(R.id.tvDadosTipoConta)

        view.findViewById<View>(R.id.btnVoltar).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.btnPrivacidade).setOnClickListener { open(ConfiguracoesFragment()) }
        view.findViewById<View>(R.id.btnAlterarSenha).setOnClickListener { open(AlterarSenhaFragment()) }
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

    private fun loadProfile() {
        if (loadingProfile || savingProfile) return
        loadingProfile = true
        profileCall?.cancel()
        profileCall = api.profile().also { request ->
            request.enqueue(object : Callback<UserResponse> {
                override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                    if (call.isCanceled || view == null) return
                    loadingProfile = false
                    val user = response.body()
                    val currentId = SessionManager(requireContext()).userId
                    if (response.code() == 200 && user != null && user.id == currentId &&
                        !user.name.isNullOrBlank() && !user.email.isNullOrBlank() && !user.birthDate.isNullOrBlank()) {
                        profile = user
                        profileUncertain = false
                        SessionManager(requireContext()).saveUserProfile(user)
                        render(user)
                    }
                }

                override fun onFailure(call: Call<UserResponse>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    loadingProfile = false
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
        val current = profile ?: return
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
                        Toast.makeText(requireContext(), "Dados atualizados com sucesso.", Toast.LENGTH_SHORT).show()
                    } else if (response.isSuccessful || response.code() >= 500) {
                        profileUncertain = true
                        dialog.dismiss()
                        Toast.makeText(requireContext(), "Não foi possível confirmar o salvamento.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "Falha de conexão ao salvar os dados.", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun displayDate(value: String?): String = try {
        if (value.isNullOrBlank()) getString(R.string.account_not_informed)
        else LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT))
    } catch (_: Exception) { value?.takeIf(String::isNotBlank) ?: getString(R.string.account_not_informed) }

    override fun onDestroyView() {
        profileCall?.cancel()
        updateCall?.cancel()
        profileCall = null
        updateCall = null
        loadingProfile = false
        savingProfile = false
        super.onDestroyView()
    }
}
