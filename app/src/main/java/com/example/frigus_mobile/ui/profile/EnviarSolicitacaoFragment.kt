package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class EnviarSolicitacaoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_enviar_solicitacao, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        val etAssunto = view.findViewById<EditText>(R.id.etAssunto)
        val etDescricao = view.findViewById<EditText>(R.id.etDescricao)

        view.findViewById<View>(R.id.btnEnviarSolicitacao).setOnClickListener {
            val assunto = etAssunto.text.toString().trim()
            val descricao = etDescricao.text.toString().trim()

            etAssunto.error = if (assunto.isEmpty()) "Informe o assunto" else null
            etDescricao.error = if (descricao.length < 10) "Descreva melhor (mín. 10 caracteres)" else null
            if (etAssunto.error != null || etDescricao.error != null) return@setOnClickListener

            // TODO: o BFF mobile ainda não publica endpoint de suporte; protocolo gerado localmente.
            val protocolo = "FRG-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) +
                "-" + Random.nextInt(1000, 10000)

            AlertDialog.Builder(requireContext())
                .setTitle("Solicitação enviada")
                .setMessage("Recebemos sua solicitação. Seu número de protocolo é $protocolo. Nosso time responde em até 72h.")
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ -> voltarTela() }
                .show()
        }
    }
}
