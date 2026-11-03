package com.example.frigus_mobile.ui.profile

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.ChangePasswordBody
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.ApiError
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** Alterar senha: validação local e requisitos em tempo real. */
class AlterarSenhaFragment : Fragment() {
    private var changeCall: Call<Void>? = null
    private var changing = false
    private var uncertain = false
    private var feedbackSnackbar: Snackbar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_alterar_senha, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        val etAtual = view.findViewById<TextInputEditText>(R.id.etCurrentPassword)
        val etNova = view.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirma = view.findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val tilAtual = view.findViewById<TextInputLayout>(R.id.tilCurrentPassword)
        val tilNova = view.findViewById<TextInputLayout>(R.id.tilNewPassword)
        val tilConfirma = view.findViewById<TextInputLayout>(R.id.tilConfirmPassword)

        val ivTamanho = view.findViewById<ImageView>(R.id.ivRequirementLength)
        val ivLetrasNumeros = view.findViewById<ImageView>(R.id.ivRequirementLettersNumbers)
        val ivEspecial = view.findViewById<ImageView>(R.id.ivRequirementSpecial)

        fun atualizarRequisitos(senha: String) {
            marcarRequisito(ivTamanho, senha.length >= 8)
            marcarRequisito(ivLetrasNumeros, senha.any { it.isLetter() } && senha.any { it.isDigit() })
            marcarRequisito(ivEspecial, senha.any { !it.isLetterOrDigit() })
        }
        atualizarRequisitos("")

        etNova.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                atualizarRequisitos(s?.toString().orEmpty())
                tilNova.error = null
            }
        })
        etAtual.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { tilAtual.error = null }
        })
        etConfirma.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { tilConfirma.error = null }
        })

        view.findViewById<View>(R.id.btnSaveNewPassword).setOnClickListener {
            val atual = etAtual.text?.toString().orEmpty()
            val nova = etNova.text?.toString().orEmpty()
            val confirma = etConfirma.text?.toString().orEmpty()

            tilAtual.error = if (atual.isEmpty()) getString(R.string.settings_password_current_required) else null
            tilNova.error = when {
                !senhaValida(nova) -> getString(R.string.settings_password_new_invalid)
                nova == atual -> getString(R.string.settings_password_same)
                else -> null
            }
            tilConfirma.error = if (confirma != nova) getString(R.string.settings_password_mismatch) else null

            if (tilAtual.error != null || tilNova.error != null || tilConfirma.error != null) return@setOnClickListener

            changePassword(atual, nova, view.findViewById(R.id.btnSaveNewPassword))
        }
    }

    private fun changePassword(current: String, new: String, save: View) {
        if (changing || uncertain) return
        changing = true
        save.isEnabled = false
        changeCall = RetrofitClient.accountSecurityService.changePassword(ChangePasswordBody(current, new)).also { request ->
            request.enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (call.isCanceled || view == null) return
                    changing = false
                    when {
                        response.code() == 204 || response.code() == 200 -> {
                            showFeedback("Senha alterada pelo servidor.", Snackbar.LENGTH_LONG)
                            parentFragmentManager.popBackStack()
                        }
                        response.code() >= 500 || response.isSuccessful -> {
                            uncertain = true
                            showFeedback("Resultado incerto. O pedido não será repetido; entre novamente para confirmar a nova senha.", Snackbar.LENGTH_INDEFINITE)
                        }
                        else -> {
                            save.isEnabled = true
                            showFeedback(ApiError.parse(response).userMessage("Não foi possível alterar a senha (${response.code()})."), Snackbar.LENGTH_LONG)
                        }
                    }
                }

                override fun onFailure(call: Call<Void>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    changing = false
                    uncertain = true
                    showFeedback("Resultado incerto. O pedido não será repetido; entre novamente para confirmar a nova senha.", Snackbar.LENGTH_INDEFINITE)
                }
            })
        }
    }

    private fun senhaValida(senha: String) =
        senha.length >= 8 && senha.any { it.isLetter() } && senha.any { it.isDigit() } &&
            senha.any { !it.isLetterOrDigit() }

    /** Verde com check quando atendido, cinza com círculo vazio caso contrário. */
    private fun marcarRequisito(icone: ImageView, ok: Boolean) {
        val cor = ContextCompat.getColor(requireContext(), if (ok) R.color.color_green else R.color.gray)
        icone.setImageResource(if (ok) R.drawable.ic_circle_check else R.drawable.ic_circle_outline)
        icone.imageTintList = ColorStateList.valueOf(cor)
        (icone.parent as ViewGroup).let { linha ->
            (linha.getChildAt(1) as? TextView)?.setTextColor(cor)
        }
    }

    private fun showFeedback(message: String, duration: Int) {
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = Snackbar.make(requireView(), message, duration).also { it.show() }
    }

    override fun onDestroyView() {
        changeCall?.cancel()
        feedbackSnackbar?.dismiss()
        changeCall = null
        feedbackSnackbar = null
        changing = false
        super.onDestroyView()
    }
}
