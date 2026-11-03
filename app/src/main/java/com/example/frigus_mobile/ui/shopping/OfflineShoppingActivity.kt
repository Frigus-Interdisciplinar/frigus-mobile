package com.example.frigus_mobile.ui.shopping

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager

/** Local shopping is the only destination exposed without a server-validated session. */
class OfflineShoppingActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var model: OfflineShoppingViewModel
    private lateinit var shopping: ShoppingListViewModel
    private lateinit var connectivity: ConnectivityManager
    private var cachedOwner: String? = null
    private var callbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = requestRevalidation()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = requestRevalidation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        cachedOwner = savedInstanceState?.getString(CACHED_OWNER) ?: session.userId
        if (!session.canReadCachedShopping || session.userId != cachedOwner) { finish(); return }
        model = ViewModelProvider(this)[OfflineShoppingViewModel::class.java]
        if (!model.hasCurrentCachedOwner()) { finish(); return }
        shopping = ViewModelProvider(this)[ShoppingListViewModel::class.java]
        connectivity = getSystemService(ConnectivityManager::class.java)
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
        findViewById<View>(R.id.bottom_navigation).visibility = View.GONE
        findViewById<View>(R.id.pushPermissionBanner).visibility = View.GONE
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, ShoppingListFragment()).commit()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
                else finish()
            }
        })
        SessionManager.authenticated.observe(this) {
            if (!model.hasCurrentCachedOwner()) finish()
        }
        model.revalidated.observe(this) { revision ->
            if (revision > 0L && model.hasCurrentCachedOwner()) {
                shopping.reload(false)
                shopping.syncNow()
            }
        }
        callbackRegistered = runCatching {
            connectivity.registerDefaultNetworkCallback(networkCallback)
        }.isSuccess
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) {
            if (!model.hasCurrentCachedOwner()) finish() else requestRevalidation()
        }
    }

    private fun requestRevalidation() {
        runOnUiThread {
            if (isFinishing || isDestroyed || !::model.isInitialized || !::connectivity.isInitialized) return@runOnUiThread
            if (!model.hasCurrentCachedOwner()) { finish(); return@runOnUiThread }
            val online = runCatching {
                connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.let {
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                } == true
            }.getOrDefault(false)
            model.restoreWhenOnline(online)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(CACHED_OWNER, cachedOwner)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (callbackRegistered) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        if (::model.isInitialized) model.cancelRestore()
        super.onDestroy()
    }

    private companion object { const val CACHED_OWNER = "cached_shopping_owner" }
}
