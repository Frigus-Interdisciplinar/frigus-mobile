package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
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
            abrirTela(InformacoesContaFragment())
        }
        // Tela de dispositivos conectados descartada por enquanto.
        view.findViewById<View>(R.id.btnDispositivosConectados).setOnClickListener {
            mostrarToast(getString(R.string.settings_coming_soon))
        }

        // Verificação em duas etapas: só persiste o estado localmente.
        val store = ProfileStore(requireContext())
        val checkTwoFactor = view.findViewById<CheckBox>(R.id.checkTwoFactor)
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus2FA)
        fun atualizarStatus(ativa: Boolean) {
            tvStatus.setText(if (ativa) R.string.settings_two_factor_on else R.string.settings_two_factor_off)
        }
        val ativa = store.getBoolean(CHAVE_DUAS_ETAPAS, false)
        checkTwoFactor.isChecked = ativa
        atualizarStatus(ativa)
        checkTwoFactor.setOnCheckedChangeListener { _, marcado ->
            store.putBoolean(CHAVE_DUAS_ETAPAS, marcado)
            atualizarStatus(marcado)
        }
    }

    private companion object {
        const val CHAVE_DUAS_ETAPAS = "two_factor_enabled"
    }
}
