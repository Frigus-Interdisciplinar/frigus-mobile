package com.example.frigus_mobile.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity

class PerfilFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_perfil, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnMeusDados).setOnClickListener {
            abrirFragmento(MeusDadosFragment())
        }

        view.findViewById<View>(R.id.btnAssinaturaPlanos).setOnClickListener {
            abrirFragmento(AssinaturaPlanosFragment())
        }

        view.findViewById<View>(R.id.btnSeguranca).setOnClickListener {
            abrirFragmento(SegurancaFragment())
        }

        view.findViewById<View>(R.id.btnConfiguracoes).setOnClickListener {
            abrirFragmento(ConfiguracoesFragment())
        }

        view.findViewById<View>(R.id.btnAjudaSuporte).setOnClickListener {
            abrirFragmento(AjudaSuporteFragment())
        }

        view.findViewById<View>(R.id.btnMoneySaving).setOnClickListener {
            abrirFragmento(MoneySavingFragment())
        }

        view.findViewById<View>(R.id.cardPlanoPremium).setOnClickListener {
            abrirFragmento(AssinaturaPlanosFragment())
        }

        view.findViewById<View>(R.id.btnSair).setOnClickListener {
            confirmarSaida()
        }
    }

    private fun abrirFragmento(fragmento: Fragment) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragment_container, fragmento)
            .addToBackStack(null)
            .commit()
    }

    private fun confirmarSaida() {
        AlertDialog.Builder(requireContext())
            .setTitle("Sair da conta")
            .setMessage("Tem certeza de que deseja encerrar a sua sessão?")
            .setPositiveButton("Sair") { _, _ ->
                SessionManager(requireContext()).clearSession()
                val intent = Intent(requireActivity(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
