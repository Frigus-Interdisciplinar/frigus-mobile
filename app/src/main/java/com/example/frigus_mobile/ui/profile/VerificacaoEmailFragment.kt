package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager

/** Mostra para qual e-mail o link de redefinição foi enviado. */
class VerificacaoEmailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_verificacao_email, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarVoltar(view)
        view.findViewById<TextView>(R.id.tvVerificationEmail).text =
            SessionManager(requireContext()).userEmail ?: "-"
    }
}
