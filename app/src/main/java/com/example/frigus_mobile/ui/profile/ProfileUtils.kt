package com.example.frigus_mobile.ui.profile

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

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

/**
 * Armazenamento local com cache offline e sincronização em nuvem via Firebase Firestore.
 * Indexado por usuário logado.
 * Nunca guardar dados sensíveis aqui (número completo de cartão, CVV, senha).
 */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("profile_local", Context.MODE_PRIVATE)
    private val userId = SessionManager(context).userId?.takeIf { it.isNotBlank() } ?: "anon"
    private val prefixo = "$userId."

    private val firestoreDoc by lazy {
        try {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("settings")
                .document("preferences")
        } catch (_: Exception) {
            null
        }
    }

    init {
        // Sincroniza preferências do Firestore para o cache local
        if (userId != "anon") {
            try {
                firestoreDoc?.addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val data = snapshot.data ?: return@addSnapshotListener
                    val editor = prefs.edit()
                    for ((k, v) in data) {
                        when (v) {
                            is Boolean -> editor.putBoolean(prefixo + k, v)
                            is String -> editor.putString(prefixo + k, v)
                            is List<*> -> editor.putString(prefixo + k, v.filterNotNull().joinToString("\n"))
                        }
                    }
                    editor.apply()
                }
            } catch (e: Exception) {
                Log.w("ProfileStore", "Aviso ao carregar dados do Firestore: ${e.message}")
            }
        }
    }

    fun getBoolean(chave: String, padrao: Boolean) = prefs.getBoolean(prefixo + chave, padrao)

    fun putBoolean(chave: String, valor: Boolean) {
        prefs.edit().putBoolean(prefixo + chave, valor).apply()
        syncToCloud(chave, valor)
    }

    fun getString(chave: String, padrao: String?) = prefs.getString(prefixo + chave, padrao)

    fun putString(chave: String, valor: String?) {
        prefs.edit().putString(prefixo + chave, valor).apply()
        if (valor != null) syncToCloud(chave, valor)
    }

    /** Lista de strings simples, separadas por '\n' (não usar para textos com quebra de linha). */
    fun getList(chave: String): List<String> =
        prefs.getString(prefixo + chave, null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    fun putList(chave: String, valores: List<String>) {
        prefs.edit().putString(prefixo + chave, valores.joinToString("\n")).apply()
        syncToCloud(chave, valores)
    }

    private fun syncToCloud(chave: String, valor: Any) {
        if (userId == "anon") return
        try {
            firestoreDoc?.set(mapOf(chave to valor), SetOptions.merge())
                ?.addOnFailureListener { e ->
                    Log.w("ProfileStore", "Falha ao sincronizar $chave no Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w("ProfileStore", "Erro ao acessar Firestore: ${e.message}")
        }
    }
}
