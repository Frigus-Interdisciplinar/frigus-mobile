package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager

/** Informações da conta (somente leitura), vindas da sessão. */
class InformacoesContaFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_informacoes_conta, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        val session = SessionManager(requireContext())
        view.findViewById<TextView>(R.id.tvAccountName).text = session.userName?.ifBlank { null } ?: "-"
        view.findViewById<TextView>(R.id.tvAccountEmail).text = session.userEmail?.ifBlank { null } ?: "-"
        view.findViewById<TextView>(R.id.tvAccountBirthdate).text = formatarDataIso(session.userBirthDate) ?: "-"
        view.findViewById<TextView>(R.id.tvAccountType).text = when (session.userAccountType?.uppercase()) {
            "DOMESTIC" -> "Doméstico"
            "COMMERCIAL" -> "Comercial"
            else -> session.userAccountType?.ifBlank { null } ?: "-"
        }
    }
}
