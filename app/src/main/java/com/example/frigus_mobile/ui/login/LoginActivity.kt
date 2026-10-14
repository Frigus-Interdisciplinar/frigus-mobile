package com.example.frigus_mobile.ui.login

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.BuildConfig
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.createaccount.CreateAccountActivity
import com.example.frigus_mobile.ui.forgotpassword.ForgotPasswordActivity
import com.example.frigus_mobile.ui.home.MainActivity
import com.example.frigus_mobile.utils.CustomTypefaceSpan
import com.example.frigus_mobile.utils.NetworkGuard
import com.example.frigus_mobile.utils.ValidationUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etSenha: TextInputEditText
    private lateinit var btnEntrar: MaterialButton
    private lateinit var btnGoogleLogin: MaterialButton
    private lateinit var progressBarLogin: android.widget.ProgressBar
    private lateinit var progressBarGoogle: android.widget.ProgressBar
    private lateinit var tvCriarConta: TextView
    private lateinit var tvEsqueciSenha: TextView
    private var tvBanner: TextView? = null
    private var isGoogleLoginInProgress = false

    private lateinit var viewModel: LoginViewModel
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            tratarResultadoGoogle(task)
        } else {
            Log.d("LoginActivity", "Google Sign-In cancelado sem dados (resultCode: ${result.resultCode})")
            resetGoogleLoadingState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        initViews()
        setupGoogleSignIn()
        setupBannerInfinito()
        setupListeners()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        NetworkGuard.ensureConnected(this) {}
    }

    private fun initViews() {
        etEmail = findViewById(R.id.email)
        etSenha = findViewById(R.id.senha)
        btnEntrar = findViewById(R.id.btnEntrar)
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin)
        progressBarLogin = findViewById(R.id.progressBarLogin)
        progressBarGoogle = findViewById(R.id.progressBarGoogle)
        tvCriarConta = findViewById(R.id.tvCriarConta)
        tvEsqueciSenha = findViewById(R.id.tvEsqueciSenha)
        tvBanner = findViewById(R.id.tvBannerInfinito)

        val textoCriarConta = "Não tem uma conta? <font color=\"#2559D6\"><b>Criar conta</b></font>"
        tvCriarConta.text = Html.fromHtml(textoCriarConta, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun setupGoogleSignIn() {
        val webClientId = if (BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) {
            BuildConfig.GOOGLE_CLIENT_ID
        } else {
            getString(R.string.default_web_client_id)
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupListeners() {
        btnEntrar.setOnClickListener { fazerLogin() }

        btnGoogleLogin.setOnClickListener {
            iniciarLoginGoogle()
        }

        tvCriarConta.setOnClickListener {
            val intent = Intent(this, CreateAccountActivity::class.java)
            startActivity(intent)
        }

        tvEsqueciSenha.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }
    }

    private fun iniciarLoginGoogle() {
        isGoogleLoginInProgress = true
        btnEntrar.isEnabled = false
        btnGoogleLogin.isEnabled = false
        btnGoogleLogin.text = ""
        btnGoogleLogin.icon = null
        progressBarGoogle.visibility = android.view.View.VISIBLE

        // Desconecta a sessão em cache para sempre exibir o seletor de contas para o usuário escolher
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun resetGoogleLoadingState() {
        isGoogleLoginInProgress = false
        btnEntrar.isEnabled = true
        btnGoogleLogin.isEnabled = true
        btnGoogleLogin.text = "Entrar com Google"
        btnGoogleLogin.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_google_logo)
        progressBarGoogle.visibility = android.view.View.GONE
    }

    private fun tratarResultadoGoogle(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val idToken = account?.idToken

            if (!idToken.isNullOrBlank()) {
                Log.d("LoginActivity", "Google Sign-In obteve token para ${account.email}")
                viewModel.loginWithGoogle(idToken)
            } else {
                resetGoogleLoadingState()
                Toast.makeText(this, "Não foi possível obter o token do Google", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            resetGoogleLoadingState()
            Log.e("LoginActivity", "Falha no Google Sign-In (status code: ${e.statusCode}): ${e.message}", e)
            when (e.statusCode) {
                10 -> { // CommonStatusCodes.DEVELOPER_ERROR
                    Toast.makeText(
                        this,
                        "Erro 10 (Developer Error): SHA-1 ou Client ID não configurados no Google Cloud / Firebase.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                12501 -> { // GoogleSignInStatusCodes.SIGN_IN_CANCELLED
                    Log.d("LoginActivity", "Login com Google cancelado pelo usuário.")
                }
                12500 -> { // GoogleSignInStatusCodes.SIGN_IN_FAILED
                    Toast.makeText(
                        this,
                        "Falha ao autenticar com Google (12500). Verifique a conta ou tente novamente.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                7 -> { // CommonStatusCodes.NETWORK_ERROR
                    Toast.makeText(this, "Sem conexão para autenticar com o Google.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "Falha ao entrar com Google: código ${e.statusCode}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.getLoginResult().observe(this) { resource ->
            if (resource == null) return@observe

            if (resource.isLoading) {
                btnEntrar.isEnabled = false
                btnGoogleLogin.isEnabled = false
                if (isGoogleLoginInProgress) {
                    btnGoogleLogin.text = ""
                    btnGoogleLogin.icon = null
                    progressBarGoogle.visibility = android.view.View.VISIBLE
                } else {
                    btnEntrar.text = ""
                    progressBarLogin.visibility = android.view.View.VISIBLE
                }
            } else if (resource.isSuccess) {
                btnEntrar.isEnabled = true
                btnGoogleLogin.isEnabled = true
                btnEntrar.text = "Entrar"
                progressBarLogin.visibility = android.view.View.GONE
                resetGoogleLoadingState()

                Toast.makeText(this, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else if (resource.isError) {
                btnEntrar.isEnabled = true
                btnGoogleLogin.isEnabled = true
                btnEntrar.text = "Entrar"
                progressBarLogin.visibility = android.view.View.GONE
                resetGoogleLoadingState()

                val msg = resource.message ?: "Erro ao fazer login"
                if (msg.startsWith("Erro de conexão com o servidor")) {
                    NetworkGuard.showBlockingError(this) { fazerLogin() }
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fazerLogin() {
        isGoogleLoginInProgress = false
        val email = etEmail.text?.toString()?.trim() ?: ""
        val senha = etSenha.text?.toString()?.trim() ?: ""

        if (email.isEmpty()) {
            etEmail.error = "Informe seu e-mail"
            etEmail.requestFocus()
            return
        }

        if (!ValidationUtils.isEmailValido(email)) {
            etEmail.error = "Informe um e-mail válido"
            etEmail.requestFocus()
            return
        }

        if (senha.isEmpty()) {
            etSenha.error = "Informe sua senha"
            etSenha.requestFocus()
            return
        }

        viewModel.login(email, senha)
    }

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
