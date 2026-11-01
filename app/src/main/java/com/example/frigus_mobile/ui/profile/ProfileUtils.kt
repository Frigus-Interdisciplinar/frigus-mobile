package com.example.frigus_mobile.ui.profile

import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R

/** Abre uma tela do perfil (sem animação) empilhando no back stack. */
fun Fragment.abrirTela(fragmento: Fragment) {
    parentFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, fragmento)
        .addToBackStack(null)
        .commit()
}

/** Volta para a tela anterior. */
fun Fragment.voltarTela() {
    parentFragmentManager.popBackStack()
}

/** Liga o botão `btnVoltar` do layout ao back stack. */
fun Fragment.configurarVoltar(view: View) {
    view.findViewById<View>(R.id.btnVoltar)?.setOnClickListener { voltarTela() }
}

fun Fragment.mostrarToast(mensagem: String) {
    Toast.makeText(requireContext(), mensagem, Toast.LENGTH_SHORT).show()
}

/** "2026-10-25T00:00:00" -> "25/10/2026" (null se inválido). */
fun formatarDataIso(iso: String?): String? {
    if (iso.isNullOrBlank() || iso.length < 10) return null
    val partes = iso.substring(0, 10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else null
}

/** 29.9 -> "R$ 29,90" */
fun formatarMoeda(valor: Double): String =
    "R$ " + String.format(java.util.Locale.US, "%.2f", valor).replace(".", ",")

// Compatibility name for the existing profile screens; storage is local and account-scoped.
typealias ProfileStore = com.example.frigus_mobile.data.local.ProfilePreferenceStore
