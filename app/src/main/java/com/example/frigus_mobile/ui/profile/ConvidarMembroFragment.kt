package com.example.frigus_mobile.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.google.android.material.switchmaterial.SwitchMaterial

class ConvidarMembroFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_convidar_membro, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        val etDestinatario = view.findViewById<EditText>(R.id.etConviteDestinatario)
        val actvFuncao = view.findViewById<AutoCompleteTextView>(R.id.actvFuncao)
        val switchEstoque = view.findViewById<View>(R.id.switchGerenciarEstoque)

        val funcoes = arrayOf("Membro", "Administrador")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, funcoes)
        actvFuncao.setAdapter(adapter)
        actvFuncao.setText(funcoes[0], false)
        actvFuncao.setOnClickListener { actvFuncao.showDropDown() }

        view.findViewById<View>(R.id.btnEnviarConvite).setOnClickListener {
            val destinatario = etDestinatario.text.toString().trim()
            if (destinatario.isEmpty()) {
                etDestinatario.error = "Informe o nome ou e-mail"
                return@setOnClickListener
            }

            val funcao = actvFuncao.text.toString()
            val isAdmin = funcao.equals("Administrador", ignoreCase = true)
            val permiteEstoque = when (switchEstoque) {
                is SwitchMaterial -> switchEstoque.isChecked
                is SwitchCompat -> switchEstoque.isChecked
                else -> false
            }

            val novoMembro = MembroGrupo(
                nome = destinatario,
                administrador = isAdmin,
                pendente = true,
                gerenciaEstoque = permiteEstoque
            )
            GrupoLocal(requireContext()).adicionar(novoMembro)

            mostrarToast("Convite enviado com sucesso!")
            voltarTela()
        }

        view.findViewById<View>(R.id.btnCompartilharLink).setOnClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Venha fazer parte do meu grupo no aplicativo Frigus! Acesse: https://frigus.app/convite")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Compartilhar convite"))
        }
    }
}
