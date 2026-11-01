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
import com.google.android.material.button.MaterialButton
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
    private var feedbackSnackbar: Snackbar? = null
    private lateinit var emailView: TextView
    private lateinit var statusView: TextView
    private lateinit var requestButton: MaterialButton
    private lateinit var verifyButton: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emailView = view.findViewById(R.id.tvVerificationEmail)
        statusView = view.findViewById(R.id.tvVerificationStatus)
        requestButton = view.findViewById(R.id.btnRequestVerificationCode)
        verifyButton = view.findViewById(R.id.btnVerifyEmailCode)
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener { parentFragmentManager.popBackStack() }
        requestButton.setOnClickListener { requestCode() }
        verifyButton.setOnClickListener { showCodeDialog() }
        loadStatus()
    }

    private fun loadStatus() {
        if (busy) return
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = null
        busy = true
        statusView.text = getString(R.string.settings_verification_status_loading)
        updateButtons()
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
                        emailView.text = data.email
                        statusView.text = getString(if (verified) R.string.settings_verification_status_verified
                            else R.string.settings_verification_status_pending)
                    } else {
                        statusView.text = when {
                            response.isSuccessful -> "Resposta inválida ao consultar o e-mail."
                            response.code() >= 500 -> "O serviço de verificação está indisponível. Tente novamente."
                            else -> ApiError.parse(response).userMessage("Não foi possível consultar o e-mail (${response.code()}).")
                        }
                        showStatusRetry()
                    }
                    updateButtons()
                }

                override fun onFailure(call: Call<EmailVerificationState>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    statusView.text = "Falha de conexão ao consultar o e-mail."
                    updateButtons()
                    showStatusRetry()
                }
            })
        }
    }

    private fun requestCode() {
        if (busy || verified || requestUncertain) return
        busy = true
        updateButtons()
        requestCall = RetrofitClient.accountSecurityService.requestEmailCode().also { request ->
            request.enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    when {
                        response.code() == 204 -> {
                            statusView.text = getString(R.string.settings_verification_code_sent)
                            Snackbar.make(requireView(), R.string.settings_verification_code_sent, Snackbar.LENGTH_LONG).show()
                        }
                        response.code() >= 500 || response.isSuccessful -> {
                            requestUncertain = true
                            statusView.text = "Não foi possível confirmar o envio. Verifique sua caixa de entrada e atualize o status."
                            showStatusRetry()
                        }
                        else -> {
                            statusView.text = ApiError.parse(response).userMessage("Não foi possível solicitar o código (${response.code()}).")
                        }
                    }
                    updateButtons()
                }

                override fun onFailure(call: Call<Void>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    requestUncertain = true
                    statusView.text = "Resultado incerto ao solicitar o código. Verifique sua caixa de entrada e atualize o status."
                    updateButtons()
                    showStatusRetry()
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
        updateButtons()
        verifyCall = RetrofitClient.accountSecurityService.verifyEmail(EmailCodeBody(code)).also { request ->
            request.enqueue(object : Callback<EmailVerificationState> {
                override fun onResponse(call: Call<EmailVerificationState>, response: Response<EmailVerificationState>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    val data = response.body()
                    if (response.code() == 200 && data?.verified == true) {
                        verified = true
                        requestUncertain = false
                        emailView.text = data.email ?: emailView.text
                        statusView.text = getString(R.string.settings_verification_status_verified)
                        dialog.dismiss()
                        Snackbar.make(requireView(), R.string.settings_verification_status_verified, Snackbar.LENGTH_LONG).show()
                    } else if (response.code() >= 500 || response.isSuccessful) {
                        dialog.dismiss()
                        statusView.text = "Resultado incerto. Atualize o status para confirmar."
                        showStatusRetry()
                    } else {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        Snackbar.make(dialog.window!!.decorView,
                            ApiError.parse(response).userMessage("Código inválido ou expirado (${response.code()})."),
                            Snackbar.LENGTH_LONG).show()
                    }
                    updateButtons()
                }

                override fun onFailure(call: Call<EmailVerificationState>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    dialog.dismiss()
                    statusView.text = "Resultado incerto. Atualize o status para confirmar."
                    updateButtons()
                    showStatusRetry()
                }
            })
        }
    }

    private fun updateButtons() {
        if (!::requestButton.isInitialized) return
        requestButton.visibility = if (verified) View.GONE else View.VISIBLE
        verifyButton.visibility = if (verified) View.GONE else View.VISIBLE
        requestButton.isEnabled = !busy && !requestUncertain && !verified
        verifyButton.isEnabled = !busy && !verified
    }

    private fun showStatusRetry() {
        view?.let {
            feedbackSnackbar?.dismiss()
            feedbackSnackbar = Snackbar.make(it, statusView.text, Snackbar.LENGTH_INDEFINITE)
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
