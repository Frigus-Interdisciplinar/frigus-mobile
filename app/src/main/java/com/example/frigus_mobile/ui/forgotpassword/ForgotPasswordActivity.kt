package com.example.frigus_mobile.ui.forgotpassword

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.frigus_mobile.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ForgotPasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        val model = ViewModelProvider(this)[ForgotPasswordViewModel::class.java]
        val email = findViewById<TextInputEditText>(R.id.etEmail)
        val code = findViewById<TextInputEditText>(R.id.recoveryCode)
        val password = findViewById<TextInputEditText>(R.id.recoveryPassword)
        val confirmation = findViewById<TextInputEditText>(R.id.recoveryPasswordConfirmation)
        val submit = findViewById<MaterialButton>(R.id.btnEnviarInstrucoes)
        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<View>(R.id.tvVoltarLogin).setOnClickListener { finish() }
        submit.setOnClickListener {
            if (model.state.value?.step in setOf(RecoveryStep.COMPLETE, RecoveryStep.CODE_UNCERTAIN, RecoveryStep.RESET_UNCERTAIN)) finish()
            else model.submit(email.text.toString(), code.text.toString(), password.text.toString(), confirmation.text.toString())
        }
        model.state.observe(this) { state ->
            findViewById<TextView>(R.id.recoveryHeading).setText(when (state.step) {
                RecoveryStep.CODE -> R.string.recovery_check_email
                RecoveryStep.PASSWORD -> R.string.recovery_create_password
                RecoveryStep.COMPLETE -> R.string.recovery_done_heading
                else -> R.string.recovery_heading
            })
            findViewById<TextView>(R.id.recoveryDescription).setText(when (state.step) {
                RecoveryStep.CODE -> R.string.recovery_enter_code
                RecoveryStep.PASSWORD -> R.string.recovery_password_requirements
                RecoveryStep.COMPLETE, RecoveryStep.CODE_UNCERTAIN, RecoveryStep.RESET_UNCERTAIN -> R.string.auth_back_login
                else -> R.string.recovery_description
            })
            email.isEnabled = state.step == RecoveryStep.EMAIL && !state.busy
            if (state.email.isNotEmpty() && email.text.toString() != state.email) email.setText(state.email)
            findViewById<View>(R.id.recoveryCodeContainer).visibility =
                if (state.step == RecoveryStep.CODE) View.VISIBLE else View.GONE
            findViewById<View>(R.id.recoveryPasswordContainer).visibility =
                if (state.step == RecoveryStep.PASSWORD) View.VISIBLE else View.GONE
            code.isEnabled = !state.busy
            password.isEnabled = !state.busy
            confirmation.isEnabled = !state.busy
            submit.isEnabled = !state.busy
            submit.setText(when {
                state.busy -> R.string.recovery_working
                state.step == RecoveryStep.CODE -> R.string.recovery_verify_code
                state.step == RecoveryStep.PASSWORD -> R.string.recovery_save_password
                state.step in setOf(RecoveryStep.COMPLETE, RecoveryStep.CODE_UNCERTAIN, RecoveryStep.RESET_UNCERTAIN) -> R.string.auth_back_login
                else -> R.string.recovery_send_code
            })
            findViewById<TextView>(R.id.recoveryStatus).apply {
                text = if (state.step == RecoveryStep.COMPLETE) getString(R.string.recovery_complete) else state.message.orEmpty()
                visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            if (state.step != RecoveryStep.PASSWORD) { password.text?.clear(); confirmation.text?.clear() }
            if (state.step != RecoveryStep.CODE) code.text?.clear()
        }
    }
}
