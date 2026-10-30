package com.example.frigus_mobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.repository.StockRepository
import com.example.frigus_mobile.ui.ai.ChatIaActivity
import com.example.frigus_mobile.ui.notifications.NotificacoesActivity
import com.example.frigus_mobile.ui.qrcode.QrCodeScannerActivity
import com.example.frigus_mobile.utils.NetworkGuard
import com.example.frigus_mobile.utils.ProductCategoryHelper
import com.example.frigus_mobile.utils.launchSafeNetwork
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var stockRepository: StockRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        stockRepository = StockRepository(requireContext())

        atualizarSaudacao(view)

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
            navigateToBottomNav(R.id.nav_receitas)
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

        carregarDadosEstoque()
    }

    override fun onResume() {
        super.onResume()
        view?.let { atualizarSaudacao(it) }
        carregarDadosEstoque()
    }

    private fun atualizarSaudacao(rootView: View) {
        val tvGreeting = rootView.findViewById<TextView>(R.id.tvGreeting)
        val nomeUsuario = sessionManager.userName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
        tvGreeting?.text = if (nomeUsuario != null) "Olá, $nomeUsuario!" else "Olá!"
    }


    private fun carregarDadosEstoque() {
        launchSafeNetwork(::carregarDadosEstoque) {
                // Sincroniza o perfil do usuário autenticado caso ainda não tenha nome/email salvos
                if (sessionManager.userName.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val profileResp = RetrofitClient.authService.getProfile().execute()
                            if (profileResp.isSuccessful && profileResp.body() != null) {
                                sessionManager.saveUserProfile(profileResp.body())
                            }
                        } catch (_: Exception) {
                        }
                    }
                    view?.let { atualizarSaudacao(it) }
                }

                val produtos = stockRepository.getStockProducts()

                val qtdVencendo = produtos.count {
                    it.productStatus.equals("NEAR_EXPIRATION", ignoreCase = true) ||
                        it.productStatus.equals("EXPIRED", ignoreCase = true)
                }
                val cardAlerta = view?.findViewById<View>(R.id.cardAlertaVencendo)
                val tvAlertaTitulo = view?.findViewById<TextView>(R.id.tvAlertaVencendoTitulo)
                if (qtdVencendo > 0) {
                    cardAlerta?.visibility = View.VISIBLE
                    tvAlertaTitulo?.text = if (qtdVencendo == 1) {
                        "1 alimento vencendo"
                    } else {
                        "$qtdVencendo alimentos vencendo"
                    }
                } else {
                    cardAlerta?.visibility = View.GONE
                }

                view?.findViewById<TextView>(R.id.tvQuickEstoqueQtd)?.text = ProductCategoryHelper.formatarQtdItens(produtos.size)

                val qtdGeladeira = produtos.count { (it.product?.storagePlace ?: "FRIDGE").equals("FRIDGE", ignoreCase = true) }
                val qtdFreezer = produtos.count { (it.product?.storagePlace ?: "").equals("FREEZER", ignoreCase = true) }
                val qtdDespensa = produtos.count { (it.product?.storagePlace ?: "").equals("PANTRY", ignoreCase = true) }

                view?.findViewById<TextView>(R.id.tvQtdGeladeira)?.text = ProductCategoryHelper.formatarQtdItens(qtdGeladeira)
                view?.findViewById<TextView>(R.id.tvQtdFreezer)?.text = ProductCategoryHelper.formatarQtdItens(qtdFreezer)
                view?.findViewById<TextView>(R.id.tvQtdDespensa)?.text = ProductCategoryHelper.formatarQtdItens(qtdDespensa)
            }
    }

    private fun navigateToBottomNav(menuItemId: Int) {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.selectedItemId = menuItemId
    }
}

