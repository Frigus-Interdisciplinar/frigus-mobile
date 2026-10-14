package com.example.frigus_mobile.ui.home

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.profile.PerfilFragment
import com.example.frigus_mobile.ui.recipes.ReceitasFragment
import com.example.frigus_mobile.ui.stock.EstoqueFragment
import com.example.frigus_mobile.utils.NetworkGuard
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val tabHistory = mutableListOf<Int>()
    private var isNavigatingBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configura ícones escuros na barra de status para combinar com o fundo claro
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)

        // Ajusta o padding do topo para respeitar a barra de status (horário, bateria, Wi-Fi),
        // impedindo que os elementos da página passem por trás dela durante o scroll.
        val mainView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }
        mainView.requestApplyInsets()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            tabHistory.clear()
            tabHistory.add(R.id.nav_home)
            trocarFragmento(HomeFragment())
        } else {
            if (tabHistory.isEmpty()) {
                tabHistory.add(bottomNav.selectedItemId)
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            if (!isNavigatingBack) {
                tabHistory.remove(item.itemId)
                tabHistory.add(item.itemId)
            }

            // Limpa sub-telas ao trocar de aba principal
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

            when (item.itemId) {
                R.id.nav_home -> {
                    trocarFragmento(HomeFragment())
                    true
                }
                R.id.nav_estoque -> {
                    trocarFragmento(EstoqueFragment())
                    true
                }
                R.id.nav_receitas -> {
                    trocarFragmento(ReceitasFragment())
                    true
                }
                R.id.nav_perfil -> {
                    trocarFragmento(PerfilFragment())
                    true
                }
                else -> {
                    trocarFragmento(HomeFragment())
                    true
                }
            }
        }

        // Controle do botão Voltar do celular
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 1. Se houver sub-páginas abertas na pilha (ex: Meus Dados no Perfil), volta para a tela anterior
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }

                // 2. Se o usuário estiver em outra aba e houver histórico de navegação
                if (tabHistory.size > 1) {
                    tabHistory.removeAt(tabHistory.size - 1)
                    val previousTab = tabHistory.last()
                    isNavigatingBack = true
                    bottomNav.selectedItemId = previousTab
                    isNavigatingBack = false
                    return
                }

                // 3. Se a aba atual não for a primeira página (Home), volta para a Home
                if (bottomNav.selectedItemId != R.id.nav_home) {
                    tabHistory.clear()
                    tabHistory.add(R.id.nav_home)
                    isNavigatingBack = true
                    bottomNav.selectedItemId = R.id.nav_home
                    isNavigatingBack = false
                    return
                }

                // 4. Se já está na primeira página (Home), pergunta se deseja realmente sair
                mostrarDialogoSaida()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        NetworkGuard.ensureConnected(this) {
            recreate()
        }
    }

    private fun trocarFragmento(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun mostrarDialogoSaida() {
        AlertDialog.Builder(this)
            .setTitle("Sair do aplicativo")
            .setMessage("Deseja realmente sair do aplicativo?")
            .setPositiveButton("Sim") { _, _ ->
                finish()
            }
            .setNegativeButton("Não", null)
            .show()
    }
}
