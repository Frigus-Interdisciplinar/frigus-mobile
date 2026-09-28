package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

class AjudaSuporteFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ajuda_suporte, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btnFaqScanner).setOnClickListener {
            exibirRespostaFaq(
                "Scanner de nota fiscal",
                "Aponte a câmera para o QR Code da sua nota fiscal para que os itens sejam cadastrados automaticamente no seu estoque."
            )
        }

        view.findViewById<View>(R.id.btnFaqManual).setOnClickListener {
            exibirRespostaFaq(
                "Adicionar alimentos manualmente",
                "Acesse a aba Estoque e clique no botão '+' para informar o nome, categoria, quantidade e data de validade."
            )
        }

        view.findViewById<View>(R.id.btnFaqQuantidade).setOnClickListener {
            exibirRespostaFaq(
                "Atualizar quantidade",
                "Selecione o item desejado no Estoque e utilize os botões '+' ou '-' para ajustar as unidades disponíveis."
            )
        }

        view.findViewById<View>(R.id.btnFaqMoneySaving).setOnClickListener {
            exibirRespostaFaq(
                "Como funciona o MoneySaving",
                "O MoneySaving calcula a economia gerada ao consumir alimentos antes do vencimento e aponta o desperdício evitado mês a mês."
            )
        }

        view.findViewById<View>(R.id.btnEntrarContato).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Fale Conosco")
                .setMessage("Envie sua mensagem para nosso suporte em suporte@frigus.com.br. Nosso time responderá em até 24h.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun exibirRespostaFaq(titulo: String, conteudo: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(titulo)
            .setMessage(conteudo)
            .setPositiveButton("Entendi", null)
            .show()
    }
}
