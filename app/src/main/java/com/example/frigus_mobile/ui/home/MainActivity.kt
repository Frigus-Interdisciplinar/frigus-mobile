package com.example.frigus_mobile.ui.home

import android.content.Intent
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
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.ui.profile.PerfilFragment
import com.example.frigus_mobile.ui.recipes.ReceitasFragment
import com.example.frigus_mobile.ui.shopping.ShoppingListFragment
import com.example.frigus_mobile.ui.stock.EstoqueFragment
import com.example.frigus_mobile.utils.NetworkGuard
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val tabHistory = mutableListOf<Int>()
    private var isNavigatingBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (sessionManager.accessToken.isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        SessionManager.authenticated.observe(this) { isAuthenticated ->
            if (isAuthenticated == false && !isFinishing) {
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }

        setContentView(R.layout.activity_main)

        // Configura ícones escuros na barra de status para combinar com o fundo claro
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)

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
                R.id.nav_lista -> {
                    trocarFragmento(ShoppingListFragment())
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // volta para a tela anterior
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }

                // se o usuário estiver em outra aba e houver histórico de navegação
                if (tabHistory.size > 1) {
                    tabHistory.removeAt(tabHistory.size - 1)
                    val previousTab = tabHistory.last()
                    isNavigatingBack = true
                    bottomNav.selectedItemId = previousTab
                    isNavigatingBack = false
                    return
                }

                // se a aba atual não for a primeira página (Home), volta para a Home
                if (bottomNav.selectedItemId != R.id.nav_home) {
                    tabHistory.clear()
                    tabHistory.add(R.id.nav_home)
                    isNavigatingBack = true
                    bottomNav.selectedItemId = R.id.nav_home
                    isNavigatingBack = false
                    return
                }

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
//            .setCustomAnimations(
//                android.R.anim.fade_in,
//                android.R.anim.fade_out,
//                android.R.anim.fade_in,
//                android.R.anim.fade_out
//            )
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
