package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.SupportRequestBody
import com.example.frigus_mobile.data.api.SupportRequestItem
import com.example.frigus_mobile.data.api.SupportRequestPage
import com.example.frigus_mobile.data.api.SupportRequestValidationIssue
import com.example.frigus_mobile.data.api.SupportRequestValidator
import com.example.frigus_mobile.data.model.ApiError
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** Keeps the restored help screen and sends support requests through the BFF. */
class AjudaSuporteFragment : Fragment(R.layout.fragment_ajuda_suporte) {
    private var listCall: Call<SupportRequestPage>? = null
    private var createCall: Call<SupportRequestItem>? = null
    private var busy = false
    private var uncertainRequest: SupportRequestBody? = null
    private var page = 0
    private var loadingSnackbar: Snackbar? = null
    private var feedbackSnackbar: Snackbar? = null
    private var createDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener { parentFragmentManager.popBackStack() }
        faq(view, R.id.btnFaqScanner, "Scanner de nota fiscal",
            "Leia o QR Code da nota pelo fluxo do Estoque. Revise os campos e os itens retornados pelo servidor antes de confirmar qualquer inclusão.")
        faq(view, R.id.btnFaqManual, "Adicionar alimentos",
            "O estoque exibe os produtos e lotes associados à sua conta. A inclusão depende das opções disponíveis no aplicativo e das permissões retornadas pelo servidor.")
        faq(view, R.id.btnFaqQuantidade, "Atualizar quantidade",
            "Abra o detalhe de um lote existente para consultar o saldo e as movimentações confirmadas pelo servidor.")
        faq(view, R.id.btnFaqMoneySaving, "MoneySaving", getString(R.string.profile_faq_money_saving))
        view.findViewById<View>(R.id.btnEntrarContato).setOnClickListener {
            if (uncertainRequest != null) loadHistory(0) else showContactOptions()
        }
    }

    private fun faq(view: View, id: Int, title: String, body: String) {
        view.findViewById<View>(id).setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle(title).setMessage(body)
                .setPositiveButton("Entendi", null).show()
        }
    }

    private fun showContactOptions() {
        AlertDialog.Builder(requireContext()).setTitle("Suporte")
            .setItems(arrayOf("Enviar solicitação", "Minhas solicitações")) { _, index ->
                if (index == 0) showCreateForm() else loadHistory(0)
            }.setNegativeButton("Fechar", null).show()
    }

    private fun showCreateForm() {
        if (busy || uncertainRequest != null) return
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        val subject = EditText(requireContext()).apply {
            hint = getString(R.string.profile_request_subject_hint)
            maxLines = 1
            filters = arrayOf(InputFilter.LengthFilter(SupportRequestValidator.SUBJECT_MAX_LENGTH))
            content.addView(this)
        }
        val description = EditText(requireContext()).apply {
            hint = getString(R.string.profile_request_description_hint)
            minLines = 3
            filters = arrayOf(InputFilter.LengthFilter(SupportRequestValidator.DESCRIPTION_MAX_LENGTH))
            content.addView(this)
        }
        val message = TextView(requireContext()).apply { content.addView(this) }
        val dialog = AlertDialog.Builder(requireContext()).setTitle(R.string.profile_request_title)
            .setView(content).setNegativeButton("Cancelar", null).setPositiveButton("Enviar", null).create()
        createDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = subject.text.toString().trim()
                val body = description.text.toString().trim()
                when (SupportRequestValidator.validate(title, body)) {
                    SupportRequestValidationIssue.REQUIRED_FIELDS -> {
                        message.text = getString(R.string.profile_request_required_fields)
                        return@setOnClickListener
                    }
                    SupportRequestValidationIssue.SUBJECT_LENGTH -> {
                        message.text = getString(
                            R.string.profile_request_subject_length,
                            SupportRequestValidator.SUBJECT_MIN_LENGTH,
                            SupportRequestValidator.SUBJECT_MAX_LENGTH
                        )
                        return@setOnClickListener
                    }
                    SupportRequestValidationIssue.DESCRIPTION_LENGTH -> {
                        message.text = getString(
                            R.string.profile_request_description_length,
                            SupportRequestValidator.DESCRIPTION_MIN_LENGTH,
                            SupportRequestValidator.DESCRIPTION_MAX_LENGTH
                        )
                        return@setOnClickListener
                    }
                    null -> Unit
                }
                send(SupportRequestBody(title, body), dialog, message)
            }
        }
        dialog.show()
    }

    private fun send(requestBody: SupportRequestBody, dialog: AlertDialog, message: TextView) {
        if (busy || uncertainRequest != null) return
        busy = true
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        message.text = "Enviando solicitação..."
        createCall = RetrofitClient.supportService.create(requestBody).also { request ->
            request.enqueue(object : Callback<SupportRequestItem> {
                override fun onResponse(call: Call<SupportRequestItem>, response: Response<SupportRequestItem>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    val result = response.body()
                    if (response.code() == 201 && valid(result)) {
                        createDialog = null
                        dialog.dismiss()
                        Snackbar.make(requireView(), "Solicitação criada. Protocolo: ${result!!.protocol}", Snackbar.LENGTH_LONG).show()
                    } else if (response.isSuccessful || response.code() >= 500) {
                        uncertainRequest = requestBody
                        createDialog = null
                        dialog.dismiss()
                        loadHistory(0)
                    } else {
                        message.text = ApiError.parse(response).userMessage("Não foi possível enviar (${response.code()}).")
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                }

                override fun onFailure(call: Call<SupportRequestItem>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    uncertainRequest = requestBody
                    createDialog = null
                    dialog.dismiss()
                    loadHistory(0)
                }
            })
        }
    }

    private fun valid(item: SupportRequestItem?): Boolean =
        !item?.id.isNullOrBlank() && !item?.protocol.isNullOrBlank() && !item?.status.isNullOrBlank()

    private fun loadHistory(target: Int) {
        if (busy) return
        busy = true
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = null
        loadingSnackbar?.dismiss()
        loadingSnackbar = Snackbar.make(requireView(), "Carregando solicitações...", Snackbar.LENGTH_INDEFINITE).also { it.show() }
        listCall?.cancel()
        listCall = RetrofitClient.supportService.requests(target, 20).also { request ->
            request.enqueue(object : Callback<SupportRequestPage> {
                override fun onResponse(call: Call<SupportRequestPage>, response: Response<SupportRequestPage>) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    loadingSnackbar?.dismiss()
                    loadingSnackbar = null
                    val data = response.body()
                    if (response.code() != 200 || data?.content == null || data.number != target || data.last == null) {
                        val message = when {
                            response.isSuccessful -> "Resposta inválida ao consultar solicitações."
                            response.code() >= 500 -> "O serviço de suporte está indisponível. Tente novamente."
                            else -> ApiError.parse(response).userMessage("Não foi possível carregar (${response.code()}).")
                        }
                        showFeedback(message) { loadHistory(target) }
                        return
                    }
                    page = target
                    val entries = data.content.orEmpty()
                    val pending = uncertainRequest
                    val matching = pending?.let { expected -> entries.firstOrNull { item ->
                        item.subject?.trim()?.equals(expected.subject.trim(), ignoreCase = true) == true &&
                            item.description?.trim() == expected.description.trim()
                    } }
                    if (matching != null) {
                        uncertainRequest = null
                        AlertDialog.Builder(requireContext()).setTitle("Solicitação encontrada")
                            .setMessage("O histórico contém uma solicitação com os mesmos dados. Protocolo: ${matching.protocol ?: "não informado"}\nStatus: ${matching.status ?: "não informado"}")
                            .setPositiveButton("Fechar", null).show()
                    } else {
                        showHistoryPage(data)
                    }
                }

                override fun onFailure(call: Call<SupportRequestPage>, error: Throwable) {
                    if (call.isCanceled || view == null) return
                    busy = false
                    loadingSnackbar?.dismiss()
                    loadingSnackbar = null
                    showFeedback("Falha de conexão ao consultar solicitações.") { loadHistory(target) }
                }
            })
        }
    }

    private fun showHistoryPage(data: SupportRequestPage) {
        val entries = data.content.orEmpty()
        val message = if (entries.isEmpty()) "Nenhuma solicitação encontrada."
            else entries.joinToString("\n\n") { item ->
                listOfNotNull(
                    item.protocol?.takeIf(String::isNotBlank)?.let { "Protocolo: $it" },
                    item.status?.takeIf(String::isNotBlank)?.let { "Status: $it" },
                    item.subject?.takeIf(String::isNotBlank)?.let { "Assunto: $it" },
                    item.createdAt?.takeIf(String::isNotBlank)
                ).joinToString("\n").ifBlank { "Solicitação sem detalhes disponíveis." }
            }
        val pending = uncertainRequest != null
        val dialog = AlertDialog.Builder(requireContext()).setTitle("Minhas solicitações")
            .setMessage(message).setNegativeButton("Fechar", null)
            .apply {
                if (data.last != true) setNeutralButton("Carregar mais", null)
                if (pending && data.last == true) setPositiveButton("Não encontrei; liberar novo envio", null)
            }.create()
        dialog.setOnShowListener {
            if (data.last != true) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                loadHistory(page + 1)
            }
            if (pending && data.last == true) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                uncertainRequest = null
                dialog.dismiss()
                Snackbar.make(requireView(), "O histórico foi consultado. Revise antes de enviar outra solicitação.", Snackbar.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun showFeedback(message: String, retry: () -> Unit) {
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_INDEFINITE)
            .setAction("Tentar novamente") { retry() }.also { it.show() }
    }

    override fun onDestroyView() {
        listCall?.cancel()
        createCall?.cancel()
        loadingSnackbar?.dismiss()
        feedbackSnackbar?.dismiss()
        listCall = null
        createCall = null
        loadingSnackbar = null
        feedbackSnackbar = null
        createDialog = null
        busy = false
        super.onDestroyView()
    }
}
