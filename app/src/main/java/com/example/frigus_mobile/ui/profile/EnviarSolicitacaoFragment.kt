package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.SupportRequestBody
import com.example.frigus_mobile.data.api.SupportRequestItem
import com.example.frigus_mobile.data.model.ApiError
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EnviarSolicitacaoFragment : Fragment(R.layout.fragment_enviar_solicitacao) {
    private var requestCall: Call<SupportRequestItem>? = null
    private var sending = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)
        view.findViewById<View>(R.id.btnEnviarSolicitacao).setOnClickListener { submit(view) }
        val pendingSubject = savedInstanceState?.getString(STATE_PENDING_SUBJECT)
        val pendingDescription = savedInstanceState?.getString(STATE_PENDING_DESCRIPTION)
        if (!pendingSubject.isNullOrBlank() && !pendingDescription.isNullOrBlank()) {
            reconcileUncertain(SupportRequestBody(pendingSubject, pendingDescription))
        }
    }

    private fun submit(view: View) {
        if (sending) return
        val subject = view.findViewById<EditText>(R.id.etAssunto).text.toString().trim()
        val description = view.findViewById<EditText>(R.id.etDescricao).text.toString().trim()
        val status = view.findViewById<TextView>(R.id.tvStatusSolicitacao)
        val button = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEnviarSolicitacao)
        val message = when {
            subject.isBlank() -> getString(R.string.profile_request_subject_required)
            description.length < 10 -> getString(R.string.profile_request_description_invalid)
            else -> null
        }
        if (message != null) {
            status.text = message
            status.visibility = View.VISIBLE
            return
        }

        status.visibility = View.GONE
        sending = true
        button.isEnabled = false
        button.text = getString(R.string.profile_request_sending)
        val body = SupportRequestBody(subject, description)
        requestCall = RetrofitClient.supportService.create(body).also { call ->
            call.enqueue(object : Callback<SupportRequestItem> {
                override fun onResponse(call: Call<SupportRequestItem>, response: Response<SupportRequestItem>) {
                    if (call.isCanceled || this@EnviarSolicitacaoFragment.view == null) return
                    sending = false
                    val item = response.body()
                    if (response.code() == 201 && !item?.id.isNullOrBlank() && !item?.protocol.isNullOrBlank()) {
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.profile_request_sent_title)
                            .setMessage(getString(R.string.profile_request_sent_message, item!!.protocol))
                            .setPositiveButton(R.string.account_cancel) { _, _ -> parentFragmentManager.popBackStack() }
                            .setOnCancelListener { parentFragmentManager.popBackStack() }
                            .show()
                    } else if (response.isSuccessful || response.code() >= 500) {
                        reconcileUncertain(body)
                    } else {
                        showError(status, ApiError.parse(response).userMessage(getString(R.string.profile_request_failed)))
                        restoreButton(button)
                    }
                }

                override fun onFailure(call: Call<SupportRequestItem>, error: Throwable) {
                    if (call.isCanceled || this@EnviarSolicitacaoFragment.view == null) return
                    sending = false
                    reconcileUncertain(body)
                }
            })
        }
    }

    private fun reconcileUncertain(body: SupportRequestBody) {
        parentFragmentManager.popBackStack(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AjudaSuporteFragment.newInstance(body))
            .commit()
    }

    private fun showError(status: TextView, message: String) {
        status.text = message
        status.visibility = View.VISIBLE
    }

    private fun restoreButton(button: com.google.android.material.button.MaterialButton) {
        button.isEnabled = true
        button.text = getString(R.string.profile_request_send)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (sending) {
            outState.putString(STATE_PENDING_SUBJECT, view?.findViewById<EditText>(R.id.etAssunto)?.text?.toString())
            outState.putString(STATE_PENDING_DESCRIPTION, view?.findViewById<EditText>(R.id.etDescricao)?.text?.toString())
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    private companion object {
        const val STATE_PENDING_SUBJECT = "pending_request_subject"
        const val STATE_PENDING_DESCRIPTION = "pending_request_description"
    }
}
