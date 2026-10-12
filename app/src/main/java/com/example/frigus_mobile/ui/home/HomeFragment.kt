package com.example.frigus_mobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.ai.ChatIaActivity
import com.example.frigus_mobile.ui.notifications.NotificacoesActivity
import com.example.frigus_mobile.ui.qrcode.QrCodeScannerActivity
import com.example.frigus_mobile.ui.recipes.ReceitaDetalhesActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Abrir Central de Notificações
        view.findViewById<View>(R.id.btnNotificacoes).setOnClickListener {
            startActivity(Intent(requireContext(), NotificacoesActivity::class.java))
        }

        // Navegar para Estoque
        view.findViewById<View>(R.id.cardAlertaVencendo).setOnClickListener {
            navigateToBottomNav(R.id.nav_estoque)
        }

        view.findViewById<View>(R.id.cardQuickEstoque).setOnClickListener {
            navigateToBottomNav(R.id.nav_estoque)
        }

        // Navegar para Receitas
        view.findViewById<View>(R.id.cardQuickReceitas).setOnClickListener {
            navigateToBottomNav(R.id.nav_receitas)
        }

        // Sugestão de Receita
        view.findViewById<View>(R.id.cardSugestaoReceita).setOnClickListener {
            val intent = Intent(requireContext(), ReceitaDetalhesActivity::class.java).apply {
                putExtra("RECIPE_ID", 1)
                putExtra("RECIPE_TITLE", "Frango ao forno com legumes")
            }
            startActivity(intent)
        }

        // Escanear QR Code
        view.findViewById<View>(R.id.cardEscanearQrCode).setOnClickListener {
            startActivity(Intent(requireContext(), QrCodeScannerActivity::class.java))
        }

        // Fale com nossa IA
        view.findViewById<View>(R.id.cardFaleComIa).setOnClickListener {
            startActivity(Intent(requireContext(), ChatIaActivity::class.java))
        }

        view.findViewById<View>(R.id.cardChats).setOnClickListener {
            startActivity(Intent(requireContext(), ChatIaActivity::class.java))
        }

        // Cards de Local de Estoque
        view.findViewById<View>(R.id.cardEstoqueGeladeira).setOnClickListener {
            navigateToBottomNav(R.id.nav_estoque)
        }

        view.findViewById<View>(R.id.cardEstoqueFreezer).setOnClickListener {
            navigateToBottomNav(R.id.nav_estoque)
        }

        view.findViewById<View>(R.id.cardEstoqueDespensa).setOnClickListener {
            navigateToBottomNav(R.id.nav_estoque)
        }
    }

    private fun navigateToBottomNav(menuItemId: Int) {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.selectedItemId = menuItemId
    }
}
