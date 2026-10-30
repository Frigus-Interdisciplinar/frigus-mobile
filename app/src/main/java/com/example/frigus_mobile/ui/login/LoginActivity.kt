package com.example.frigus_mobile.ui.login

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.BuildConfig
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionCredential
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.AuthResult
import com.example.frigus_mobile.ui.createaccount.CreateAccountActivity
import com.example.frigus_mobile.ui.forgotpassword.ForgotPasswordActivity
import com.example.frigus_mobile.ui.home.MainActivity
import com.example.frigus_mobile.push.PushNotifications
import com.example.frigus_mobile.utils.CustomTypefaceSpan
import com.example.frigus_mobile.utils.ValidationUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {
    private lateinit var viewModel: LoginViewModel
    private lateinit var etEmail: TextInputEditText
    private lateinit var etSenha: TextInputEditText
    private var tvBanner: TextView? = null
    private var navigated = false
    private lateinit var session: SessionManager
    private var restoreCredential: SessionCredential? = null
    private var pendingNavigation: Bundle? = null
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                val photoUrl = account?.photoUrl?.toString()
                if (!photoUrl.isNullOrBlank()) {
                    session.saveGooglePhotoUrl(photoUrl)
                }
                if (!idToken.isNullOrBlank()) {
                    viewModel.loginWithGoogle(idToken)
                } else {
                    Toast.makeText(this, "Não foi possível obter o token do Google", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                val msg = when (e.statusCode) {
                    10 -> "Erro 10 (Developer Error): SHA-1 ou Client ID não configurados no console."
                    12501 -> "Login com Google cancelado pelo usuário."
                    12500 -> "Falha ao autenticar com Google. Tente novamente."
                    7 -> "Sem conexão com a internet para autenticar com o Google."
                    else -> "Falha ao entrar com Google (código ${e.statusCode})"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        session = SessionManager(this)
        if (session.isLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        restoreCredential = session.state.credential()
        pendingNavigation = if (restoreCredential != null)
            intent.getBundleExtra(MainActivity.EXTRA_RESTORE_NAVIGATION) else null
        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]
        etEmail = findViewById(R.id.email)
        etSenha = findViewById(R.id.senha)
        tvBanner = findViewById(R.id.tvBannerInfinito)
        setupBannerInfinito()
        setupGoogleSignIn()

        findViewById<MaterialButton>(R.id.btnGoogleLogin).setOnClickListener {
            iniciarLoginGoogle()
        }

        findViewById<View>(R.id.btnEntrar).setOnClickListener { fazerLogin() }
        findViewById<View>(R.id.tvCriarConta).setOnClickListener {
            discardPendingNavigation()
            startActivity(Intent(this, CreateAccountActivity::class.java))
        }
        findViewById<View>(R.id.tvEsqueciSenha).setOnClickListener {
            discardPendingNavigation()
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        findViewById<View>(R.id.btnRetrySession)?.setOnClickListener { viewModel.restore() }
        findViewById<View>(R.id.btnChangeAccount)?.setOnClickListener {
            discardPendingNavigation()
            viewModel.signOut()
            render(null)
        }
        intent.getStringExtra(EXTRA_NOTICE)?.let { findViewById<TextView>(R.id.authStatus)?.text = it }
        viewModel.result.observe(this) { result ->
            if (restoreCredential == null || session.state.credential() != restoreCredential) {
                discardPendingNavigation()
            }
            if (result == AuthResult.Authenticated && viewModel.isAuthenticated) {
                if (!navigated) {
                    navigated = true
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        if (restoreCredential != null && session.state.credential() == restoreCredential) {
                            pendingNavigation?.let { putExtra(MainActivity.EXTRA_RESTORE_NAVIGATION, it) }
                        }
                        if (PushNotifications.shouldOpenCenter(this@LoginActivity.intent)) {
                            putExtra(PushNotifications.EXTRA_OPEN_NOTIFICATIONS, true)
                        }
                    })
                    finish()
                }
            } else render(result)
        }
        render(viewModel.result.value)
    }

    private fun setupGoogleSignIn() {
        val webClientId = BuildConfig.GOOGLE_CLIENT_ID.ifBlank {
            getString(R.string.default_web_client_id)
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun iniciarLoginGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun render(result: AuthResult?) {
        val loading = result == AuthResult.Loading
        val stored = viewModel.hasStoredSession
        findViewById<MaterialButton>(R.id.btnEntrar).apply {
            isEnabled = !loading && !stored
            text = if (loading) "" else getString(R.string.auth_sign_in)
        }
        findViewById<MaterialButton>(R.id.btnGoogleLogin).isEnabled = !loading
        etEmail.isEnabled = !loading && !stored
        etSenha.isEnabled = !loading && !stored
        findViewById<View>(R.id.tvCriarConta).isEnabled = !loading && !stored
        findViewById<View>(R.id.tvEsqueciSenha).isEnabled = !loading && !stored
        findViewById<View>(R.id.progressBarLogin).visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnRetrySession)?.visibility = if (stored && !loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnChangeAccount)?.visibility = if (stored && !loading) View.VISIBLE else View.GONE
        val status = findViewById<TextView>(R.id.authStatus)
        if (result is AuthResult.Failure) {
            status?.text = result.error.userMessage(getString(R.string.auth_generic_error))
            Toast.makeText(this, result.error.userMessage(getString(R.string.auth_generic_error)), Toast.LENGTH_LONG).show()
            result.error.fields.orEmpty().forEach { field ->
                val message = field.displayMessage ?: field.errorMessage
                when (field.fieldName) {
                    "email" -> etEmail.error = message
                    "rawPassword" -> etSenha.error = message
                }
            }
        } else if (loading) status?.setText(if (stored) R.string.auth_validating_session else R.string.auth_signing_in)
        else if (result == null && !intent.hasExtra(EXTRA_NOTICE)) status?.text = ""
    }

    private fun discardPendingNavigation() {
        restoreCredential = null
        pendingNavigation = null
        intent.removeExtra(MainActivity.EXTRA_RESTORE_NAVIGATION)
    }

    private fun fazerLogin() {
        discardPendingNavigation()
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val password = etSenha.text?.toString().orEmpty()
        if (!ValidationUtils.isEmailValido(email)) {
            etEmail.error = getString(R.string.auth_invalid_email)
            return
        }
        if (password.isEmpty()) {
            etSenha.error = getString(R.string.auth_enter_password)
            return
        }
        viewModel.login(email, password)
    }

    companion object { const val EXTRA_NOTICE = "auth_notice" }

    private fun setupBannerInfinito() {
        val banner = tvBanner ?: return

        var fonte1: Typeface?
        var fonte2: Typeface?
        try {
            fonte1 = ResourcesCompat.getFont(this, R.font.audiowide)
        } catch (_: Exception) {
            fonte1 = Typeface.DEFAULT_BOLD
        }
        try {
            fonte2 = ResourcesCompat.getFont(this, R.font.montserrat)
        } catch (_: Exception) {
            fonte2 = Typeface.DEFAULT
        }

        val corEscura = Color.parseColor("#1B49B6")
        val corClara = Color.parseColor("#6A92E2")

        val builder = SpannableStringBuilder()

        for (i in 0 until 12) {
            val ehPar = i % 2 == 0
            val corAtual = if (ehPar) corEscura else corClara
            val fonteAtual = if (ehPar) fonte1 else fonte2

            val inicio = builder.length
            builder.append("FRIGUS")
            val fim = builder.length

            builder.setSpan(ForegroundColorSpan(corAtual), inicio, fim, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (fonteAtual != null) {
                builder.setSpan(CustomTypefaceSpan("", fonteAtual), inicio, fim, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (ehPar) {
                builder.setSpan(StyleSpan(Typeface.BOLD), inicio, fim, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            builder.append("   ")
        }

        banner.text = builder
        banner.isSelected = true
    }
}
