package com.example.frigus_mobile.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.push.PushNotifications
import com.example.frigus_mobile.push.PushRegistration
import com.example.frigus_mobile.ui.stock.EstoqueFragment
import com.example.frigus_mobile.ui.recipes.ReceitasFragment
import com.example.frigus_mobile.ui.stock.InventoryViewModel
import com.example.frigus_mobile.ui.profile.PerfilFragment
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.ui.notifications.NotificacoesActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var navigation: BottomNavigationView
    private var leaving = false
    private var signingOut = false
    private var unavailableTitle = 0
    private var pendingNavigation: Bundle? = null
    private val backToHome = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { showHome() }
    }
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            updatePermissionAction()
            if (granted) PushRegistration.sync(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore only the safe navigation state, never legacy feature fragments.
        super.onCreate(null)
        session = SessionManager(this)
        val navigationState = safeNavigationState(savedInstanceState ?: intent.getBundleExtra(EXTRA_RESTORE_NAVIGATION))
        intent.removeExtra(EXTRA_RESTORE_NAVIGATION)
        pendingNavigation = navigationState
        if (!session.isLoggedIn) { openLogin(); return }
        pendingNavigation = null
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
        findViewById<View>(R.id.btnSessionNotifications).setOnClickListener { openNotifications() }
        findViewById<View>(R.id.btnEnablePush).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        findViewById<View>(R.id.btnSessionLogout).setOnClickListener { signOut() }
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
        onBackPressedDispatcher.addCallback(this, backToHome)

        navigation = findViewById(R.id.bottom_navigation)
        navigation.setOnItemSelectedListener { openTab(it.itemId); true }
        navigation.setOnItemReselectedListener { openTab(it.itemId) }
        ViewModelProvider(this)[InventoryViewModel::class.java].restoreNavigationState(navigationState?.getBundle(INVENTORY_NAVIGATION))
        val selected = navigationState?.getInt(SELECTED_TAB) ?: R.id.nav_home
        val validSelected = if (navigation.menu.findItem(selected) != null) selected else R.id.nav_home
        navigation.menu.findItem(validSelected).isChecked = true
        val pendingTitle = navigationState?.getInt(UNAVAILABLE_TITLE) ?: 0
        if (pendingTitle != 0) showUnavailable(pendingTitle) else openTab(validSelected)

        updatePermissionAction()
        if (PushNotifications.shouldOpenCenter(intent)) {
            intent.removeExtra(PushNotifications.EXTRA_OPEN_NOTIFICATIONS)
            openNotifications()
        }
    }

    fun selectTab(id: Int) { navigation.selectedItemId = id }
    fun showHome() { selectTab(R.id.nav_home) }

    fun openInventory(storagePlace: String? = null, showAllStatusesNotice: Boolean = false) {
        if (!session.isLoggedIn) return
        ViewModelProvider(this)[InventoryViewModel::class.java].openShortcut(storagePlace, showAllStatusesNotice)
        selectTab(R.id.nav_estoque)
    }

    private fun openTab(id: Int) {
        val title = when (id) {

            R.id.nav_lista -> R.string.home_shopping_list

            else -> 0
        }
        if (title != 0) showUnavailable(title)
        else {
            unavailableTitle = 0
            backToHome.isEnabled = id == R.id.nav_estoque || id == R.id.nav_receitas || id == R.id.nav_perfil
            val fragment = when (id) {
                R.id.nav_estoque -> EstoqueFragment()
                R.id.nav_receitas -> ReceitasFragment()
                R.id.nav_perfil -> PerfilFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commit()
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
        if (::navigation.isInitialized) outState.putBundle(INVENTORY_NAVIGATION, ViewModelProvider(this)[InventoryViewModel::class.java].saveNavigationState())
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !session.isLoggedIn) openLogin()
        else if (::session.isInitialized) {
            PushRegistration.sync(this)
            updatePermissionAction()
        }
    }

    fun refreshSessionIdentity() {
        if (!session.isLoggedIn || leaving) return
        findViewById<TextView>(R.id.sessionIdentity).text = session.userName ?: session.userEmail.orEmpty()
    }

    fun signOut() {
        if (signingOut || leaving || !session.isLoggedIn) return
        signingOut = true
        findViewById<View>(R.id.btnSessionLogout).isEnabled = false
        lifecycleScope.launch {
            try {
                PushRegistration.unregister(this@MainActivity)
            } finally {
                session.clearSession()
                openLogin()
            }
        }
    }

    private fun openNotifications() {
        startActivity(Intent(this, NotificacoesActivity::class.java))
    }

    private fun updatePermissionAction() {
        if (!::session.isInitialized || !session.isLoggedIn) return
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        findViewById<View>(R.id.pushPermissionBanner).visibility =
            if (needsPermission) View.VISIBLE else View.GONE
    }

    private fun safeNavigationState(source: Bundle?): Bundle? {
        if (source == null) return null
        return Bundle().apply {
            val tab = source.getInt(SELECTED_TAB, R.id.nav_home)
            val safeTabs = setOf(R.id.nav_home, R.id.nav_estoque, R.id.nav_receitas, R.id.nav_lista, R.id.nav_perfil)
            putInt(SELECTED_TAB, tab.takeIf { it in safeTabs } ?: R.id.nav_home)
            val title = source.getInt(UNAVAILABLE_TITLE)
            val safeTitles = setOf(R.string.home_recipes, R.string.home_shopping_list, R.string.home_profile,
                R.string.home_conversations, R.string.home_qr, R.string.home_ai)
            putInt(UNAVAILABLE_TITLE, title.takeIf { it in safeTitles } ?: 0)
            putBundle(INVENTORY_NAVIGATION, source.getBundle(INVENTORY_NAVIGATION))
        }
    }

    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java).apply {
            if (PushNotifications.shouldOpenCenter(this@MainActivity.intent)) {
                putExtra(PushNotifications.EXTRA_OPEN_NOTIFICATIONS, true)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (session.accessToken != null && !session.state.validated) {
                pendingNavigation?.let { putExtra(EXTRA_RESTORE_NAVIGATION, it) }
            }
        })
        finish()
    }

    companion object {
        const val EXTRA_RESTORE_NAVIGATION = "restore_navigation"
        private const val INVENTORY_NAVIGATION = "inventory_navigation"
        private const val SELECTED_TAB = "selected_tab"
        private const val UNAVAILABLE_TITLE = "unavailable_title"
    }
}
