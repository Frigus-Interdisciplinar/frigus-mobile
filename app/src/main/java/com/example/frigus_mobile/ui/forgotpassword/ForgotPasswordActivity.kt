package com.example.frigus_mobile.ui.forgotpassword

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.frigus_mobile.R

class ForgotPasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth_unavailable)
        findViewById<TextView>(R.id.unavailableMessage).setText(R.string.auth_password_reset_unavailable)
        findViewById<View>(R.id.btnUnavailableBack).setOnClickListener { finish() }
    }
}
