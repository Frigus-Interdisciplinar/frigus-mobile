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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.model.AuthResult
import com.example.frigus_mobile.ui.createaccount.CreateAccountActivity
import com.example.frigus_mobile.ui.forgotpassword.ForgotPasswordActivity
import com.example.frigus_mobile.ui.home.MainActivity
import com.example.frigus_mobile.utils.CustomTypefaceSpan
import com.example.frigus_mobile.utils.ValidationUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {
    private lateinit var viewModel: LoginViewModel
    private lateinit var etEmail: TextInputEditText
    private lateinit var etSenha: TextInputEditText
    private var tvBanner: TextView? = null
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]
        etEmail = findViewById(R.id.email)
        etSenha = findViewById(R.id.senha)
        tvBanner = findViewById(R.id.tvBannerInfinito)
        setupBannerInfinito()
        findViewById<MaterialButton>(R.id.btnGoogleLogin).apply {
            isEnabled = false
            setText(R.string.auth_google_unavailable)
        }
        findViewById<View>(R.id.btnEntrar).setOnClickListener { fazerLogin() }
        findViewById<View>(R.id.tvCriarConta).setOnClickListener {
            startActivity(Intent(this, CreateAccountActivity::class.java))
        }
        findViewById<View>(R.id.tvEsqueciSenha).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        findViewById<View>(R.id.btnRetrySession).setOnClickListener { viewModel.restore() }
        findViewById<View>(R.id.btnChangeAccount).setOnClickListener { viewModel.signOut(); render(null) }
        intent.getStringExtra(EXTRA_NOTICE)?.let { findViewById<TextView>(R.id.authStatus).text = it }
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

    private fun render(result: AuthResult?) {
        val loading = result == AuthResult.Loading
        val stored = viewModel.hasStoredSession
        findViewById<MaterialButton>(R.id.btnEntrar).apply {
            isEnabled = !loading && !stored
            text = if (loading) "" else getString(R.string.auth_sign_in)
        }
        etEmail.isEnabled = !loading && !stored
        etSenha.isEnabled = !loading && !stored
        findViewById<View>(R.id.tvCriarConta).isEnabled = !loading && !stored
        findViewById<View>(R.id.tvEsqueciSenha).isEnabled = !loading && !stored
        findViewById<View>(R.id.progressBarLogin).visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnRetrySession).visibility = if (stored && !loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnChangeAccount).visibility = if (stored && !loading) View.VISIBLE else View.GONE
        val status = findViewById<TextView>(R.id.authStatus)
        if (result is AuthResult.Failure) {
            status.text = result.error.userMessage(getString(R.string.auth_generic_error))
            result.error.fields.orEmpty().forEach { field ->
                val message = field.displayMessage ?: field.errorMessage
                when (field.fieldName) {
                    "email" -> etEmail.error = message
                    "rawPassword" -> etSenha.error = message
                }
            }
        } else if (loading) status.setText(if (stored) R.string.auth_validating_session else R.string.auth_signing_in)
        else if (result == null && !intent.hasExtra(EXTRA_NOTICE)) status.text = ""
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
