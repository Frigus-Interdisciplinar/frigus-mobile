package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager

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
        configurarVoltar(view)

        view.findViewById<View>(R.id.btnFaqScanner).setOnClickListener {
            exibirRespostaFaq(
                "Scanner de nota fiscal",
                "Leia o QR Code de uma NFC-e da SEFAZ-SP. O aplicativo consulta a nota e mostra os campos extraídos para revisão. Para adicionar um item, confirme a linha, escolha a validade e use um produto já existente no catálogo."
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

        // MoneySaving só existe para contas do tipo Doméstico
        if (SessionManager(requireContext()).isDomestic) {
            view.findViewById<View>(R.id.groupFaqMoneySaving).visibility = View.VISIBLE
            view.findViewById<View>(R.id.btnFaqMoneySaving).setOnClickListener {
                exibirRespostaFaq(
                    "Como funciona o MoneySaving",
                    "O MoneySaving calcula a economia gerada ao consumir alimentos antes do vencimento e aponta o desperdício evitado mês a mês."
                )
            }
        }

        view.findViewById<View>(R.id.btnEntrarContato).setOnClickListener {
            abrirTela(EnviarSolicitacaoFragment())
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
