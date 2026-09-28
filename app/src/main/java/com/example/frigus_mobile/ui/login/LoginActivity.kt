package com.example.frigus_mobile.ui.login

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.createaccount.CreateAccountActivity
import com.example.frigus_mobile.ui.forgotpassword.ForgotPasswordActivity
import com.example.frigus_mobile.ui.home.MainActivity
import com.example.frigus_mobile.utils.CustomTypefaceSpan
import com.example.frigus_mobile.utils.ValidationUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etSenha: TextInputEditText
    private lateinit var btnEntrar: MaterialButton
    private lateinit var btnCriarConta: MaterialButton
    private lateinit var tvEsqueciSenha: TextView
    private var tvBanner: TextView? = null

    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        initViews()
        setupBannerInfinito()
        setupListeners()
        setupObservers()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.email)
        etSenha = findViewById(R.id.senha)
        btnEntrar = findViewById(R.id.btnEntrar)
        btnCriarConta = findViewById(R.id.btnCriarConta)
        tvEsqueciSenha = findViewById(R.id.tvEsqueciSenha)
        tvBanner = findViewById(R.id.tvBannerInfinito)
    }

    private fun setupListeners() {
        btnEntrar.setOnClickListener { fazerLogin() }

        btnCriarConta.setOnClickListener {
            val intent = Intent(this, CreateAccountActivity::class.java)
            startActivity(intent)
        }

        tvEsqueciSenha.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupObservers() {
        viewModel.getLoginResult().observe(this) { resource ->
            if (resource == null) return@observe

            if (resource.isLoading) {
                btnEntrar.isEnabled = false
            } else if (resource.isSuccess) {
                btnEntrar.isEnabled = true
                Toast.makeText(this, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else if (resource.isError) {
                btnEntrar.isEnabled = true
                Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fazerLogin() {
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
