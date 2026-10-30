package com.example.frigus_mobile.ui.login

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.BuildConfig
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.AuthResult
import com.example.frigus_mobile.ui.createaccount.CreateAccountActivity
import com.example.frigus_mobile.ui.forgotpassword.ForgotPasswordActivity
import com.example.frigus_mobile.ui.home.MainActivity
import com.example.frigus_mobile.utils.CustomTypefaceSpan
import com.example.frigus_mobile.utils.ValidationUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
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
                    SessionManager(this).saveGooglePhotoUrl(photoUrl)
                }
                if (!idToken.isNullOrBlank()) {
                    viewModel.loginWithGoogle(idToken)
                } else {
                    Toast.makeText(this, "Não foi possível obter o token do Google", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.e("LoginActivity", "Falha no Google Sign-In: ${e.statusCode}", e)
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
        val sessionManager = com.example.frigus_mobile.data.local.SessionManager(this)
        if (sessionManager.isLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
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
            startActivity(Intent(this, CreateAccountActivity::class.java))
        }
        findViewById<View>(R.id.tvEsqueciSenha).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        viewModel.result.observe(this) { result ->
            if (result == AuthResult.Authenticated && viewModel.isAuthenticated) {
                if (!navigated) {
                    navigated = true
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            } else render(result)
        }
        render(viewModel.result.value)
    }

    private fun setupGoogleSignIn() {
        val webClientId = if (BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) {
            BuildConfig.GOOGLE_CLIENT_ID
        } else {
            "414213788789-62jm64drbgc7v1s7trlu7dathe0vsb2d.apps.googleusercontent.com"
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
        findViewById<MaterialButton>(R.id.btnEntrar).apply {
            isEnabled = !loading
            text = if (loading) "" else getString(R.string.auth_sign_in)
        }
        findViewById<MaterialButton>(R.id.btnGoogleLogin).isEnabled = !loading
        etEmail.isEnabled = !loading
        etSenha.isEnabled = !loading
        findViewById<View>(R.id.tvCriarConta).isEnabled = !loading
        findViewById<View>(R.id.tvEsqueciSenha).isEnabled = !loading
        findViewById<View>(R.id.progressBarLogin).visibility = if (loading) View.VISIBLE else View.GONE

        if (result is AuthResult.Failure) {
            val errorMsg = result.error.userMessage(getString(R.string.auth_generic_error))
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            result.error.fields.orEmpty().forEach { field ->
                val message = field.displayMessage ?: field.errorMessage
                when (field.fieldName) {
                    "email" -> etEmail.error = message
                    "rawPassword" -> etSenha.error = message
                }
            }
        }
    }

    private fun fazerLogin() {
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
