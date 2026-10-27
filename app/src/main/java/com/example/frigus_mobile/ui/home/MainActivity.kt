package com.example.frigus_mobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var navigation: BottomNavigationView
    private var leaving = false
    private var unavailableTitle = 0
    private val backToHome = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { showHome() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore only the safe navigation state, never legacy feature fragments.
        super.onCreate(null)
        session = SessionManager(this)
        if (!session.isLoggedIn) { openLogin(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(findViewById(R.id.main))
        findViewById<TextView>(R.id.sessionIdentity).text = session.userName ?: session.userEmail.orEmpty()
        findViewById<View>(R.id.btnSessionLogout).setOnClickListener { session.clearSession(); openLogin() }
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
        onBackPressedDispatcher.addCallback(this, backToHome)

        navigation = findViewById(R.id.bottom_navigation)
        navigation.setOnItemSelectedListener { openTab(it.itemId); true }
        navigation.setOnItemReselectedListener { openTab(it.itemId) }
        val selected = savedInstanceState?.getInt(SELECTED_TAB) ?: R.id.nav_home
        val validSelected = if (navigation.menu.findItem(selected) != null) selected else R.id.nav_home
        navigation.menu.findItem(validSelected).isChecked = true
        val pendingTitle = savedInstanceState?.getInt(UNAVAILABLE_TITLE) ?: 0
        if (pendingTitle != 0) showUnavailable(pendingTitle) else openTab(validSelected)
    }

    fun selectTab(id: Int) { navigation.selectedItemId = id }
    fun showHome() { selectTab(R.id.nav_home) }

    private fun openTab(id: Int) {
        val title = when (id) {
            R.id.nav_estoque -> R.string.home_stock
            R.id.nav_receitas -> R.string.home_recipes
            R.id.nav_lista -> R.string.home_shopping_list
            R.id.nav_perfil -> R.string.home_profile
            else -> 0
        }
        if (title != 0) showUnavailable(title)
        else {
            unavailableTitle = 0
            backToHome.isEnabled = false
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, HomeFragment()).commit()
        }
    }

    fun showUnavailable(title: Int) {
        unavailableTitle = title
        backToHome.isEnabled = true
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, FeatureUnavailableFragment.newInstance(title)).commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::navigation.isInitialized) outState.putInt(SELECTED_TAB, navigation.selectedItemId)
        outState.putInt(UNAVAILABLE_TITLE, unavailableTitle)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !session.isLoggedIn) openLogin()
    }

    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    companion object {
        private const val SELECTED_TAB = "selected_tab"
        private const val UNAVAILABLE_TITLE = "unavailable_title"
    }
}
