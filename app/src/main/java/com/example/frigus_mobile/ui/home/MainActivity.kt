package com.example.frigus_mobile.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.push.PushNotifications
import com.example.frigus_mobile.push.PushRegistration
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.ui.notifications.NotificacoesActivity
import kotlinx.coroutines.launch

/** G01 authenticated landing. Feature fragments are enabled by their own PRs. */
class MainActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private var leaving = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updatePermissionAction()
        if (it) PushRegistration.sync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Do not restore legacy fragments that could load Core-backed features.
        super.onCreate(null)
        session = SessionManager(this)
        if (!session.isLoggedIn) { openLogin(); return }
        setContentView(R.layout.activity_session)
        findViewById<TextView>(R.id.sessionIdentity).text = session.userName ?: session.userEmail.orEmpty()
        findViewById<View>(R.id.btnSessionNotifications).setOnClickListener {
            startActivity(Intent(this, NotificacoesActivity::class.java))
        }
        findViewById<View>(R.id.btnEnablePush).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        findViewById<View>(R.id.btnSessionLogout).setOnClickListener { button ->
            button.isEnabled = false
            lifecycleScope.launch {
                try {
                    PushRegistration.unregister(this@MainActivity)
                } finally {
                    session.clearSession()
                    openLogin()
                }
            }
        }
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
        updatePermissionAction()
        if (PushNotifications.shouldOpenCenter(intent)) {
            intent.removeExtra(PushNotifications.EXTRA_OPEN_NOTIFICATIONS)
            startActivity(Intent(this, NotificacoesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !session.isLoggedIn) openLogin()
        else if (::session.isInitialized) {
            PushRegistration.sync(this)
            updatePermissionAction()
        }
    }

    private fun updatePermissionAction() {
        if (!::session.isInitialized || !session.isLoggedIn) return
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        findViewById<View>(R.id.tvPushPermissionExplanation).visibility =
            if (needsPermission) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnEnablePush).visibility =
            if (needsPermission) View.VISIBLE else View.GONE
    }

    private fun openLogin() {
        if (leaving) return
        leaving = true
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
