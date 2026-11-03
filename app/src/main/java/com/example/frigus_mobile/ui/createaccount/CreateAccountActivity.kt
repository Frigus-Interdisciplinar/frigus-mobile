package com.example.frigus_mobile.ui.createaccount

import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.home.MainActivity
import com.example.frigus_mobile.data.model.AuthResult
import com.example.frigus_mobile.ui.login.LoginActivity
import com.example.frigus_mobile.utils.ValidationUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateAccountActivity : AppCompatActivity() {

    private lateinit var btnVoltar: ImageButton
    private lateinit var etNome: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etDataNascimento: TextInputEditText
    private lateinit var tilDataNascimento: TextInputLayout
    private lateinit var etSenha: TextInputEditText
    private lateinit var etConfirmarSenha: TextInputEditText
    private lateinit var btnCriarConta: MaterialButton
    private lateinit var progressBarCriarConta: ProgressBar
    private lateinit var btnAjudaSenha: ImageView

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
        etDataNascimento = findViewById(R.id.etDataNascimento)
        tilDataNascimento = findViewById(R.id.tilDataNascimento)
        etSenha = findViewById(R.id.etSenha)
        etConfirmarSenha = findViewById(R.id.etConfirmarSenha)
        btnCriarConta = findViewById(R.id.btnCriarConta)
        progressBarCriarConta = findViewById(R.id.progressBarCriarConta)
        btnAjudaSenha = findViewById(R.id.btnAjudaSenha)
    }

    private fun setupListeners() {
        btnVoltar.setOnClickListener { finish() }
        btnCriarConta.setOnClickListener { cadastrarUsuario() }
        btnAjudaSenha.setOnClickListener { exibirPopUpRequisitosSenha() }

        // Data de nascimento: clique no campo ou no TextInputLayout abre o DatePicker
        etDataNascimento.setOnClickListener { abrirDatePicker() }
        tilDataNascimento.setOnClickListener { abrirDatePicker() }

        // Limpa mensagens de erro automaticamente ao digitar
        configurarLimpezaErrosAoDigitar()
    }

    private fun configurarLimpezaErrosAoDigitar() {
        etNome.doAfterTextChanged { if (!it.isNullOrBlank()) etNome.error = null }
        etEmail.doAfterTextChanged { if (!it.isNullOrBlank()) etEmail.error = null }
        etDataNascimento.doAfterTextChanged { if (!it.isNullOrBlank()) etDataNascimento.error = null }
        etSenha.doAfterTextChanged { if (!it.isNullOrBlank()) etSenha.error = null }
        etConfirmarSenha.doAfterTextChanged { if (!it.isNullOrBlank()) etConfirmarSenha.error = null }
    }

    private fun exibirPopUpRequisitosSenha() {
        if (isFinishing || isDestroyed) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_password_requirements, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        val iv8Chars = dialogView.findViewById<ImageView>(R.id.dialogIvReq8Chars)
        val tv8Chars = dialogView.findViewById<TextView>(R.id.dialogTvReq8Chars)
        val ivUpper = dialogView.findViewById<ImageView>(R.id.dialogIvReqUpper)
        val tvUpper = dialogView.findViewById<TextView>(R.id.dialogTvReqUpper)
        val ivLower = dialogView.findViewById<ImageView>(R.id.dialogIvReqLower)
        val tvLower = dialogView.findViewById<TextView>(R.id.dialogTvReqLower)
        val ivNumber = dialogView.findViewById<ImageView>(R.id.dialogIvReqNumber)
        val tvNumber = dialogView.findViewById<TextView>(R.id.dialogTvReqNumber)
        val ivSpecial = dialogView.findViewById<ImageView>(R.id.dialogIvReqSpecial)
        val tvSpecial = dialogView.findViewById<TextView>(R.id.dialogTvReqSpecial)
        val btnEntendi = dialogView.findViewById<View>(R.id.btnEntendiSenha)

        val pass = etSenha.text?.toString() ?: ""
        atualizarItemDialog(iv8Chars, tv8Chars, pass.length in 8..20)
        atualizarItemDialog(ivUpper, tvUpper, pass.any { it.isUpperCase() })
        atualizarItemDialog(ivLower, tvLower, pass.any { it.isLowerCase() })
        atualizarItemDialog(ivNumber, tvNumber, pass.any { it.isDigit() })
        atualizarItemDialog(ivSpecial, tvSpecial, pass.any { !it.isLetterOrDigit() })

        btnEntendi?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun atualizarItemDialog(iv: ImageView?, tv: TextView?, atendido: Boolean) {
        if (iv == null || tv == null) return
        val corVerde = Color.parseColor("#10B981")
        val corCinza = Color.parseColor("#64748B")
        val corIconeCinza = Color.parseColor("#94A3B8")

        if (atendido) {
            iv.setImageResource(R.drawable.ic_circle_check)
            iv.imageTintList = ColorStateList.valueOf(corVerde)
            tv.setTextColor(corVerde)
        } else {
            iv.setImageResource(R.drawable.ic_circle_outline)
            iv.imageTintList = ColorStateList.valueOf(corIconeCinza)
            tv.setTextColor(corCinza)
        }
    }

    private fun setupObservers() {
        viewModel.result.observe(this) { result ->
            val busy = result == AuthResult.Loading
            btnCriarConta.isEnabled = !busy
            btnCriarConta.text = if (busy) "" else getString(R.string.auth_register)
            progressBarCriarConta.visibility = if (busy) View.VISIBLE else View.GONE
            when (result) {
                AuthResult.Authenticated -> {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
                is AuthResult.CreatedNeedsLogin -> openLogin(getString(R.string.auth_account_created_login_pending))
                is AuthResult.Failure -> {
                    val message = result.error.userMessage(getString(R.string.auth_generic_error))
                    if (result.error.code == "REGISTRATION_UNCERTAIN") openLogin(message)
                    else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        result.error.fields.orEmpty().forEach { field ->
                            val input = when (field.fieldName) {
                                "name" -> etNome
                                "email" -> etEmail
                                "birthDate" -> etDataNascimento
                                "rawPassword" -> etSenha
                                else -> null
                            }
                            input?.error = field.displayMessage ?: field.errorMessage
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private fun openLogin(message: String) {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(LoginActivity.EXTRA_NOTICE, message)
        })
        finish()
    }

    private fun abrirDatePicker() {
        val calendar = Calendar.getInstance()

        // Se já houver uma data preenchida, aproveita no DatePicker
        val dataAtualTexto = etDataNascimento.text?.toString()?.trim() ?: ""
        if (dataAtualTexto.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val parsedDate = sdf.parse(dataAtualTexto)
                if (parsedDate != null) {
                    calendar.time = parsedDate
                }
            } catch (_: Exception) {
                calendar.add(Calendar.YEAR, -18)
            }
        } else {
            calendar.add(Calendar.YEAR, -18)
        }

        val ano = calendar.get(Calendar.YEAR)
        val mes = calendar.get(Calendar.MONTH)
        val dia = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val dataFormatada = String.format(Locale.ROOT, "%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
            etDataNascimento.setText(dataFormatada)
            etDataNascimento.error = null
        }, ano, mes, dia)

        // Limita a seleção a datas passadas (evita falha na validação @Past da API)
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun cadastrarUsuario() {
        val nome = etNome.text?.toString()?.trim() ?: ""
        val email = etEmail.text?.toString()?.trim() ?: ""
        val dataNascimento = etDataNascimento.text?.toString()?.trim() ?: ""
        val senha = etSenha.text?.toString() ?: ""
        val confirmarSenha = etConfirmarSenha.text?.toString() ?: ""

        if (nome.isEmpty()) {
            etNome.error = "Informe seu nome completo"
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

        if (dataNascimento.isEmpty()) {
            etDataNascimento.error = "Informe sua data de nascimento"
            etDataNascimento.requestFocus()
            abrirDatePicker()
            return
        }

        if (!ValidationUtils.isBirthDateValida(dataNascimento)) {
            etDataNascimento.error = getString(R.string.auth_invalid_birth_date)
            return
        }

        if (senha.isEmpty()) {
            etSenha.error = "Informe uma senha"
            etSenha.requestFocus()
            exibirPopUpRequisitosSenha()
            return
        }

        if (!ValidationUtils.isSenhaValida(senha)) {
            etSenha.error = "A senha não atende a todos os critérios requeridos"
            etSenha.requestFocus()
            exibirPopUpRequisitosSenha()
            return
        }

        if (senha != confirmarSenha) {
            etConfirmarSenha.error = "As senhas não coincidem"
            etConfirmarSenha.requestFocus()
            return
        }

        viewModel.register(nome, dataNascimento, email, senha)
    }
}
