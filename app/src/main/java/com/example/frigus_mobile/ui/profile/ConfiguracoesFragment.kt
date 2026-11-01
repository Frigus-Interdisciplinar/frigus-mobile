package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

class ConfiguracoesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_configuracoes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarVoltar(view)

        view.findViewById<View>(R.id.btnPreferenciasAlimentos).setOnClickListener {
            abrirTela(PreferenciasAlimentosFragment())
        }
        view.findViewById<View>(R.id.btnUnidadesMedidas).setOnClickListener {
            abrirTela(UnidadesMedidasFragment())
        }
        view.findViewById<View>(R.id.btnPrivacidade).setOnClickListener {
            abrirTela(PrivacidadeFragment())
        }
        view.findViewById<View>(R.id.btnAlterarSenha).setOnClickListener {
            abrirTela(AlterarSenhaFragment())
        }
        view.findViewById<View>(R.id.btnInformacoesConta).setOnClickListener {
            abrirTela(MeusDadosFragment())
        }
    }
}
