package com.example.frigus_mobile.ui.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

class MoneySavingComoCalculadoFragment : Fragment(R.layout.fragment_money_saving_como_calculado) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnVoltar).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
}
