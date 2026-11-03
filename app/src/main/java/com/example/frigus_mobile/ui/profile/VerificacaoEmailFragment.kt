package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.EmailCodeBody
import com.example.frigus_mobile.data.api.EmailVerificationState
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.ApiError
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** Email status and verification use the existing screen and the published BFF flow. */
class VerificacaoEmailFragment : Fragment(R.layout.fragment_verificacao_email) {
    private var statusCall: Call<EmailVerificationState>? = null
    private var requestCall: Call<Void>? = null
    private var verifyCall: Call<EmailVerificationState>? = null
    private var busy = false
    private var requestUncertain = false
    private var verified = false
    private var codeRequested = false
    private var feedbackSnackbar: Snackbar? = null
    private lateinit var emailView: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emailView = view.findViewById(R.id.tvVerificationEmail)
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener { parentFragmentManager.popBackStack() }
        emailView.isClickable = true
        emailView.isFocusable = true
        emailView.setOnClickListener { onEmailTapped() }
        loadStatus()
    }

    private fun onEmailTapped() {
        if (busy || verified) return
        when {
            emailView.text.isNullOrBlank() -> loadStatus()
            requestUncertain -> showStatusRetry("O envio do código ainda não foi confirmado. Atualize o status antes de tentar novamente.")
            codeRequested -> showCodeDialog()
            else -> AlertDialog.Builder(requireContext())
                .setTitle("Verificar e-mail")
                .setMessage("Enviar um código de verificação para ${emailView.text}?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Enviar código") { _, _ -> requestCode() }
                .show()
        }
    }

    private fun loadStatus() {
        if (busy) return
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = null
        busy = true
        updateEmailAction()
        statusCall?.cancel()
        statusCall = RetrofitClient.accountSecurityService.emailStatus().also { request ->
            request.enqueue(object : Callback<EmailVerificationState> {
                override fun onResponse(call: Call<EmailVerificationState>, response: Response<EmailVerificationState>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    val data = response.body()
                    if (response.code() == 200 && !data?.email.isNullOrBlank()) {
                        verified = data!!.verified
                        requestUncertain = false
                        codeRequested = false
                        emailView.text = data.email
                        showMessage(getString(if (verified) R.string.settings_verification_status_verified
                            else R.string.settings_verification_status_pending))
                    } else {
                        val message = when {
                            response.isSuccessful -> "Resposta inválida ao consultar o e-mail."
                            response.code() >= 500 -> "O serviço de verificação está indisponível. Tente novamente."
                            else -> ApiError.parse(response).userMessage("Não foi possível consultar o e-mail (${response.code()}).")
                        }
                        showStatusRetry(message)
                    }
                    updateEmailAction()
                }

                override fun onFailure(call: Call<EmailVerificationState>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    updateEmailAction()
                    showStatusRetry("Falha de conexão ao consultar o e-mail.")
                }
            })
        }
    }

    private fun requestCode() {
        if (busy || verified || requestUncertain) return
        busy = true
        updateEmailAction()
        requestCall = RetrofitClient.accountSecurityService.requestEmailCode().also { request ->
            request.enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    when {
                        response.code() == 204 -> {
                            codeRequested = true
                            updateEmailAction()
                            Snackbar.make(requireView(), "Código enviado. Toque no e-mail para digitá-lo.", Snackbar.LENGTH_LONG)
                                .setAction("Digitar código") { showCodeDialog() }.show()
                        }
                        response.code() >= 500 || response.isSuccessful -> {
                            requestUncertain = true
                            showStatusRetry("Não foi possível confirmar o envio. Verifique sua caixa de entrada e atualize o status.")
                        }
                        else -> {
                            showMessage(ApiError.parse(response).userMessage("Não foi possível solicitar o código (${response.code()})."))
                        }
                    }
                    updateEmailAction()
                }

                override fun onFailure(call: Call<Void>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    requestUncertain = true
                    updateEmailAction()
                    showStatusRetry("Resultado incerto ao solicitar o código. Verifique sua caixa de entrada e atualize o status.")
                }
            })
        }
    }

    private fun showCodeDialog() {
        if (busy || verified) return
        val input = EditText(requireContext()).apply {
            hint = "Código de seis dígitos"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val dialog = AlertDialog.Builder(requireContext()).setTitle("Verificar e-mail")
            .setMessage("Digite o código enviado para ${emailView.text}.")
            .setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Verificar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = input.text.toString().trim()
                if (code.length != 6 || !code.all(Char::isDigit)) {
                    input.error = "Informe seis dígitos"
                    return@setOnClickListener
                }
                verifyCode(code, dialog)
            }
        }
        dialog.show()
    }

    private fun verifyCode(code: String, dialog: AlertDialog) {
        if (busy) return
        busy = true
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        updateEmailAction()
        verifyCall = RetrofitClient.accountSecurityService.verifyEmail(EmailCodeBody(code)).also { request ->
            request.enqueue(object : Callback<EmailVerificationState> {
                override fun onResponse(call: Call<EmailVerificationState>, response: Response<EmailVerificationState>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    val data = response.body()
                    if (response.code() == 200 && data?.verified == true) {
                        verified = true
                        requestUncertain = false
                        codeRequested = false
                        emailView.text = data.email ?: emailView.text
                        updateEmailAction()
                        dialog.dismiss()
                        showMessage(getString(R.string.settings_verification_status_verified))
                    } else if (response.code() >= 500 || response.isSuccessful) {
                        dialog.dismiss()
                        showStatusRetry("Resultado incerto. Atualize o status para confirmar.")
                    } else {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        showMessage(ApiError.parse(response).userMessage("Código inválido ou expirado (${response.code()})."))
                    }
                    updateEmailAction()
                }

                override fun onFailure(call: Call<EmailVerificationState>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    dialog.dismiss()
                    updateEmailAction()
                    showStatusRetry("Resultado incerto. Atualize o status para confirmar.")
                }
            })
        }
    }

    private fun updateEmailAction() {
        if (!::emailView.isInitialized) return
        emailView.isEnabled = !busy && !verified
        emailView.contentDescription = when {
            verified -> "${emailView.text}. E-mail verificado."
            codeRequested -> "${emailView.text}. Toque para digitar o código de verificação."
            requestUncertain -> "${emailView.text}. Resultado do envio incerto; atualize o status."
            else -> "${emailView.text}. Toque para solicitar um código de verificação."
        }
    }

    private fun showMessage(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    private fun showStatusRetry(message: String) {
        if (!::emailView.isInitialized) return
        view?.let {
            feedbackSnackbar?.dismiss()
            feedbackSnackbar = Snackbar.make(it, message, Snackbar.LENGTH_INDEFINITE)
                .setAction("Atualizar status") { loadStatus() }.also { snackbar -> snackbar.show() }
        }
    }

    override fun onDestroyView() {
        statusCall?.cancel()
        requestCall?.cancel()
        verifyCall?.cancel()
        feedbackSnackbar?.dismiss()
        statusCall = null
        requestCall = null
        verifyCall = null
        feedbackSnackbar = null
        busy = false
        super.onDestroyView()
    }
}
