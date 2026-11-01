package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

class EnviarSolicitacaoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_enviar_solicitacao, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)

        view.findViewById<View>(R.id.btnEnviarSolicitacao).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.home_feature_pending)
                .setMessage(R.string.profile_support_not_integrated)
                .setPositiveButton(R.string.account_cancel, null)
                .show()
        }
    }
}
