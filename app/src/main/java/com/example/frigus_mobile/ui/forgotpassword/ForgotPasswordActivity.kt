package com.example.frigus_mobile.ui.forgotpassword

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.utils.ValidationUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var btnVoltar: ImageButton
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnEnviarInstrucoes: MaterialButton
    private lateinit var tvVoltarLogin: TextView

    private lateinit var viewModel: ForgotPasswordViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        viewModel = ViewModelProvider(this)[ForgotPasswordViewModel::class.java]

        initViews()
        setupListeners()
        setupObservers()
    }

    private fun initViews() {
        btnVoltar = findViewById(R.id.btnVoltar)
        etEmail = findViewById(R.id.etEmail)
        btnEnviarInstrucoes = findViewById(R.id.btnEnviarInstrucoes)
        tvVoltarLogin = findViewById(R.id.tvVoltarLogin)
    }

    private fun setupListeners() {
        btnVoltar.setOnClickListener { finish() }
        tvVoltarLogin.setOnClickListener { finish() }

        btnEnviarInstrucoes.setOnClickListener {
            val email = etEmail.text?.toString()?.trim() ?: ""

            if (email.isEmpty()) {
                etEmail.error = "Informe seu e-mail"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (!ValidationUtils.isEmailValido(email)) {
                etEmail.error = "Informe um e-mail válido"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            viewModel.sendPasswordReset(email)
        }
    }

    private fun setupObservers() {
        viewModel.getResetResult().observe(this) { resource ->
            if (resource == null) return@observe

            if (resource.isLoading) {
                btnEnviarInstrucoes.isEnabled = false
            } else if (resource.isSuccess) {
                btnEnviarInstrucoes.isEnabled = true
                Toast.makeText(this, "Instruções de redefinição enviadas para o seu e-mail!", Toast.LENGTH_LONG).show()
                finish()
            } else if (resource.isError) {
                btnEnviarInstrucoes.isEnabled = true
                Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
