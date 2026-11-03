package com.example.frigus_mobile.ui.conversation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity

abstract class HumanConversationActivity : AppCompatActivity() {
    protected lateinit var session: SessionManager
    private var initialCredential: com.example.frigus_mobile.data.local.SessionCredential? = null
    private var initialOwner: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        initialCredential = session.state.credential()
        initialOwner = session.userId
        if (!currentSession()) { openLogin(); return }
        SessionManager.authenticated.observe(this) { if (!currentSession()) openLogin() }
    }
    protected fun currentSession() = session.isLoggedIn && session.state.credential() == initialCredential && session.userId == initialOwner
    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !currentSession()) openLogin()
    }
    protected fun openLogin() {
        if (isFinishing) return
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
