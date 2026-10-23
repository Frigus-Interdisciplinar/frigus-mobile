package com.example.frigus_mobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity

/** G01 authenticated landing. Feature fragments are enabled by their own PRs. */
class MainActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Do not restore legacy fragments that could load Core-backed features.
        super.onCreate(null)
        session = SessionManager(this)
        if (!session.isLoggedIn) { openLogin(); return }
        setContentView(R.layout.activity_session)
        findViewById<TextView>(R.id.sessionIdentity).text = session.userName ?: session.userEmail.orEmpty()
        findViewById<View>(R.id.btnSessionLogout).setOnClickListener { session.clearSession(); openLogin() }
        SessionManager.authenticated.observe(this) { if (!session.isLoggedIn) openLogin() }
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
}
