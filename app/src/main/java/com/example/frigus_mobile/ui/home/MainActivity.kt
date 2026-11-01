package com.example.frigus_mobile.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
import com.example.frigus_mobile.ui.stock.AlimentoDetalhesActivity
import com.example.frigus_mobile.ui.recipes.ReceitasFragment
import com.example.frigus_mobile.ui.stock.InventoryViewModel
import com.example.frigus_mobile.ui.profile.PerfilFragment
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.ui.notifications.NotificacoesActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val subscriptionModel by lazy {
        ViewModelProvider(this, com.example.frigus_mobile.ui.profile.SubscriptionViewModel.Factory(this))[
            com.example.frigus_mobile.ui.profile.SubscriptionViewModel::class.java]
    }
    private lateinit var session: SessionManager
    private lateinit var navigation: BottomNavigationView
    private var leaving = false
    private var signingOut = false
    private var unavailableTitle = 0
    private var pendingNavigation: Bundle? = null
    private val tabHistory = mutableListOf<Int>()
    private var isNavigatingBack = false
    private var shoppingInventoryRevision = 0L
    private val backToHome = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
            else if (tabHistory.size > 1) {
                tabHistory.removeAt(tabHistory.lastIndex)
                isNavigatingBack = true
                navigation.selectedItemId = tabHistory.last()
                isNavigatingBack = false
            } else {
                tabHistory.clear()
                tabHistory.add(R.id.nav_home)
                isNavigatingBack = true
                showHome()
                isNavigatingBack = false
            }
        }
    }
    private val stockLotResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (::session.isInitialized && session.isLoggedIn && result.resultCode == RESULT_OK) {
            ViewModelProvider(this)[InventoryViewModel::class.java].refresh()
            ViewModelProvider(this)[HomeViewModel::class.java].invalidate()
        }
    }
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            updatePermissionAction()
            if (granted) PushRegistration.sync(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore only the safe navigation state, never legacy feature fragments.
        super.onCreate(null)
        savedInstanceState?.getBundle(ACTIVITY_RESULTS)?.let(activityResultRegistry::onRestoreInstanceState)
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
        findViewById<View>(R.id.btnEnablePush).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
        onBackPressedDispatcher.addCallback(this, backToHome)

        navigation = findViewById(R.id.bottom_navigation)
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(manager: androidx.fragment.app.FragmentManager, fragment: androidx.fragment.app.Fragment) {
                navigation.visibility = if (fragment is com.example.frigus_mobile.ui.profile.GerenciarPlanoFragment ||
                    fragment is com.example.frigus_mobile.ui.profile.AlterarPlanoFragment) View.GONE else View.VISIBLE
            }
        }, false)
        navigation.setOnItemSelectedListener {
            if (!isNavigatingBack) {
                tabHistory.remove(it.itemId)
                tabHistory.add(it.itemId)
            }
            openTab(it.itemId)
            true
        }
        navigation.setOnItemReselectedListener { openTab(it.itemId) }
        ViewModelProvider(this)[InventoryViewModel::class.java].restoreNavigationState(navigationState?.getBundle(INVENTORY_NAVIGATION))
        subscriptionModel.restoreNavigationState(navigationState?.getBundle(SUBSCRIPTION_NAVIGATION))
        subscriptionModel.state.observe(this) {
            if (session.isLoggedIn && subscriptionModel.consumeInventoryContextChange()) {
                ViewModelProvider(this)[HomeViewModel::class.java].apply {
                    invalidate()
                    if (supportFragmentManager.findFragmentById(R.id.fragment_container) is HomeFragment)
                        loadIfNeeded()
                }
                ViewModelProvider(this)[InventoryViewModel::class.java].refresh()
                ViewModelProvider(this)[com.example.frigus_mobile.ui.recipes.RecipesViewModel::class.java].refreshSuggestions()
            }
        }
        ViewModelProvider(this)[com.example.frigus_mobile.ui.shopping.ShoppingListViewModel::class.java].state.observe(this) { state ->
            if (session.isLoggedIn && state.inventoryRevision > shoppingInventoryRevision) {
                shoppingInventoryRevision = state.inventoryRevision
                ViewModelProvider(this)[HomeViewModel::class.java].invalidate()
                ViewModelProvider(this)[InventoryViewModel::class.java].refresh()
                ViewModelProvider(this)[com.example.frigus_mobile.ui.recipes.RecipesViewModel::class.java].refreshSuggestions()
            }
        }
        val selected = navigationState?.getInt(SELECTED_TAB) ?: R.id.nav_home
        tabHistory.addAll(navigationState?.getIntArray(TAB_HISTORY)?.toList().orEmpty())
        if (tabHistory.isEmpty()) tabHistory.add(R.id.nav_home)
        if (tabHistory.last() != selected) { tabHistory.remove(selected); tabHistory.add(selected) }
        val unavailable = navigationState?.getInt(UNAVAILABLE_TITLE) ?: 0
        navigation.menu.findItem(selected)?.isChecked = true
        if (unavailable != 0) showUnavailable(unavailable)
        else openTab(selected)
        if (unavailable == 0 && (selected == R.id.nav_perfil ||
                navigationState?.getString(PROFILE_SECTION) == "money_saving")) {
            val section = when (navigationState?.getString(PROFILE_SECTION)) {
                "subscription" -> com.example.frigus_mobile.ui.profile.AssinaturaPlanosFragment()
                "subscription_manage" -> com.example.frigus_mobile.ui.profile.GerenciarPlanoFragment()
                "subscription_plans" -> com.example.frigus_mobile.ui.profile.AlterarPlanoFragment()
                "subscription_group" -> com.example.frigus_mobile.ui.profile.MeuGrupoFragment()
                "subscription_history" -> com.example.frigus_mobile.ui.profile.HistoricoCobrancasFragment()
                "money_saving" -> com.example.frigus_mobile.ui.profile.MoneySavingFragment()
                "profile_data" -> com.example.frigus_mobile.ui.profile.MeusDadosFragment()
                else -> null
            }
            section?.let { supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, it).addToBackStack(null).commit() }
        }
        consumeReceiptImport(intent)
        if (PushNotifications.shouldOpenCenter(intent)) openNotifications()
        updatePermissionAction()
    }

    fun openInventory(storagePlace: String? = null, showAllStatusesNotice: Boolean = false) {
        if (!session.isLoggedIn) return
        ViewModelProvider(this)[InventoryViewModel::class.java].openShortcut(storagePlace, showAllStatusesNotice)
        selectTab(R.id.nav_estoque)
    }

    fun openStockLot(id: Int) {
        if (!session.isLoggedIn || id <= 0) return
        stockLotResult.launch(Intent(this, AlimentoDetalhesActivity::class.java)
            .putExtra(AlimentoDetalhesActivity.EXTRA_LOT_ID, id))
    }

    fun selectTab(tabId: Int) {
        if (!::navigation.isInitialized) return
        navigation.selectedItemId = tabId
    }

    fun showHome() {
        unavailableTitle = 0
        backToHome.isEnabled = false
        if (::navigation.isInitialized && navigation.selectedItemId != R.id.nav_home) {
            navigation.selectedItemId = R.id.nav_home
        } else {
            openTab(R.id.nav_home)
        }
    }

    private fun openTab(tabId: Int) {
        if (supportFragmentManager.backStackEntryCount > 0)
            supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        unavailableTitle = 0
        backToHome.isEnabled = tabId != R.id.nav_home || tabHistory.size > 1
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
        val shouldReplace = when (tabId) {
            R.id.nav_home -> current !is HomeFragment
            R.id.nav_estoque -> current !is EstoqueFragment
            R.id.nav_receitas -> current !is ReceitasFragment
            R.id.nav_perfil -> current !is PerfilFragment
            R.id.nav_lista -> current !is com.example.frigus_mobile.ui.shopping.ShoppingListFragment
            else -> true
        }
        if (shouldReplace) {
            val fragment = when (tabId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_estoque -> EstoqueFragment()
                R.id.nav_receitas -> ReceitasFragment()
                R.id.nav_perfil -> PerfilFragment()
                R.id.nav_lista -> com.example.frigus_mobile.ui.shopping.ShoppingListFragment()
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
        outState.putIntArray(TAB_HISTORY, tabHistory.toIntArray())
        when (supportFragmentManager.findFragmentById(R.id.fragment_container)) {
            is com.example.frigus_mobile.ui.profile.AssinaturaPlanosFragment -> outState.putString(PROFILE_SECTION, "subscription")
            is com.example.frigus_mobile.ui.profile.GerenciarPlanoFragment -> outState.putString(PROFILE_SECTION, "subscription_manage")
            is com.example.frigus_mobile.ui.profile.AlterarPlanoFragment -> outState.putString(PROFILE_SECTION, "subscription_plans")
            is com.example.frigus_mobile.ui.profile.MeuGrupoFragment -> outState.putString(PROFILE_SECTION, "subscription_group")
            is com.example.frigus_mobile.ui.profile.HistoricoCobrancasFragment -> outState.putString(PROFILE_SECTION, "subscription_history")
            is com.example.frigus_mobile.ui.profile.MoneySavingFragment -> outState.putString(PROFILE_SECTION, "money_saving")
            is com.example.frigus_mobile.ui.profile.MeusDadosFragment -> outState.putString(PROFILE_SECTION, "profile_data")
        }
        if (::navigation.isInitialized) outState.putBundle(INVENTORY_NAVIGATION, ViewModelProvider(this)[InventoryViewModel::class.java].saveNavigationState())
        if (::navigation.isInitialized) outState.putBundle(SUBSCRIPTION_NAVIGATION, subscriptionModel.saveNavigationState())
        outState.putBundle(ACTIVITY_RESULTS, Bundle().also(activityResultRegistry::onSaveInstanceState))
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeReceiptImport(intent)
    }

    private fun consumeReceiptImport(source: Intent?) {
        if (source?.getBooleanExtra(EXTRA_RECEIPT_IMPORTED, false) != true || !::navigation.isInitialized) return
        source.removeExtra(EXTRA_RECEIPT_IMPORTED)
        ViewModelProvider(this)[InventoryViewModel::class.java].refresh()
        ViewModelProvider(this)[HomeViewModel::class.java].invalidate()
        navigation.selectedItemId = R.id.nav_estoque
    }

    fun signOut() {
        if (signingOut || leaving || !session.isLoggedIn) return
        signingOut = true
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
            putIntArray(TAB_HISTORY, source.getIntArray(TAB_HISTORY)?.filter { it in safeTabs }?.distinct()?.toIntArray())
            val title = source.getInt(UNAVAILABLE_TITLE)
            val safeTitles = setOf(R.string.home_recipes, R.string.home_shopping_list, R.string.home_profile,
                R.string.home_conversations, R.string.home_qr, R.string.home_ai)
            putInt(UNAVAILABLE_TITLE, title.takeIf { it in safeTitles } ?: 0)
            putBundle(INVENTORY_NAVIGATION, source.getBundle(INVENTORY_NAVIGATION))
            putBundle(SUBSCRIPTION_NAVIGATION, source.getBundle(SUBSCRIPTION_NAVIGATION))
            source.getString(PROFILE_SECTION)?.takeIf { it in setOf("subscription", "subscription_manage",
                "subscription_plans", "subscription_group", "subscription_history", "money_saving", "profile_data") }
                ?.let { putString(PROFILE_SECTION, it) }
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
        const val EXTRA_RECEIPT_IMPORTED = "receipt_imported"
        private const val ACTIVITY_RESULTS = "safe_activity_results"
        private const val INVENTORY_NAVIGATION = "inventory_navigation"
        private const val SELECTED_TAB = "selected_tab"
        private const val UNAVAILABLE_TITLE = "unavailable_title"
        private const val PROFILE_SECTION = "profile_section"
        private const val SUBSCRIPTION_NAVIGATION = "subscription_navigation"
        private const val TAB_HISTORY = "tab_history"
    }
}
