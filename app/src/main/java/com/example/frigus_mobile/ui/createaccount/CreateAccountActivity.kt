package com.example.frigus_mobile.ui.createaccount

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.home.MainActivity
import com.example.frigus_mobile.utils.ValidationUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar
import java.util.Locale

class CreateAccountActivity : AppCompatActivity() {

    private lateinit var btnVoltar: ImageButton
    private lateinit var etNome: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etTelefone: TextInputEditText
    private lateinit var etDataNascimento: TextInputEditText
    private lateinit var etSenha: TextInputEditText
    private lateinit var etConfirmarSenha: TextInputEditText
    private lateinit var cbTermos: MaterialCheckBox
    private lateinit var tvJaTemConta: TextView
    private lateinit var btnCriarConta: MaterialButton

    private lateinit var viewModel: CreateAccountViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_account)

        viewModel = ViewModelProvider(this)[CreateAccountViewModel::class.java]

        initViews()
        setupListeners()
        setupObservers()
    }

    private fun initViews() {
        btnVoltar = findViewById(R.id.btnVoltar)
        etNome = findViewById(R.id.etNome)
        etEmail = findViewById(R.id.etEmail)
        etTelefone = findViewById(R.id.etTelefone)
        etDataNascimento = findViewById(R.id.etDataNascimento)
        etSenha = findViewById(R.id.etSenha)
        etConfirmarSenha = findViewById(R.id.etConfirmarSenha)
        cbTermos = findViewById(R.id.cbTermos)
        tvJaTemConta = findViewById(R.id.tvJaTemConta)
        btnCriarConta = findViewById(R.id.btnCriarConta)
    }

    private fun setupListeners() {
        btnVoltar.setOnClickListener { finish() }
        tvJaTemConta.setOnClickListener { finish() }
        btnCriarConta.setOnClickListener { cadastrarUsuario() }

        etDataNascimento.setOnClickListener { abrirDatePicker() }
        etDataNascimento.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                abrirDatePicker()
            }
        }
    }

    private fun setupObservers() {
        viewModel.getRegisterResult().observe(this) { resource ->
            if (resource == null) return@observe

            if (resource.isLoading) {
                btnCriarConta.isEnabled = false
            } else if (resource.isSuccess) {
                btnCriarConta.isEnabled = true
                Toast.makeText(this, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else if (resource.isError) {
                btnCriarConta.isEnabled = true
                Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun abrirDatePicker() {
        val calendar = Calendar.getInstance()
        val ano = calendar.get(Calendar.YEAR) - 18
        val mes = calendar.get(Calendar.MONTH)
        val dia = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val dataFormatada = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
            etDataNascimento.setText(dataFormatada)
            etDataNascimento.error = null
        }, ano, mes, dia)

        datePickerDialog.show()
    }

    private fun cadastrarUsuario() {
        val nome = etNome.text?.toString()?.trim() ?: ""
        val email = etEmail.text?.toString()?.trim() ?: ""
        val telefone = etTelefone.text?.toString()?.trim() ?: ""
        val dataNascimento = etDataNascimento.text?.toString()?.trim() ?: ""
        val senha = etSenha.text?.toString()?.trim() ?: ""
        val confirmarSenha = etConfirmarSenha.text?.toString()?.trim() ?: ""

        if (nome.isEmpty()) {
            etNome.error = "Informe seu nome"
            etNome.requestFocus()
            return
        }

        if (email.isEmpty()) {
            etEmail.error = "Informe seu e-mail"
            etEmail.requestFocus()
            return
        }

        if (!ValidationUtils.isEmailValido(email)) {
            etEmail.error = "Informe um e-mail válido (ex: nome@email.com)"
            etEmail.requestFocus()
            return
        }

        if (telefone.isEmpty()) {
            etTelefone.error = "Informe seu telefone"
            etTelefone.requestFocus()
            return
        }

        if (dataNascimento.isEmpty()) {
            etDataNascimento.error = "Informe sua data de nascimento"
            etDataNascimento.requestFocus()
            return
        }

        if (senha.isEmpty()) {
            etSenha.error = "Informe uma senha"
            etSenha.requestFocus()
            return
        }

        if (!ValidationUtils.isSenhaValida(senha)) {
            etSenha.error = "A senha deve ter de 8 a 20 caracteres, com 1 maiúscula, 1 minúscula, 1 número e 1 especial"
            etSenha.requestFocus()
            return
        }

        if (senha != confirmarSenha) {
            etConfirmarSenha.error = "As senhas não coincidem"
            etConfirmarSenha.requestFocus()
            return
        }

        if (!cbTermos.isChecked) {
            Toast.makeText(this, "Por favor, aceite os Termos de Uso para continuar.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.register(nome, dataNascimento, email, senha)
    }
}
