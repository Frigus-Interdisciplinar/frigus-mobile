package com.example.frigus_mobile.utils

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object NetworkGuard {

    private var activeDialog: AlertDialog? = null

    /**
     * Verifica se há conexão ativa com a internet no aparelho.
     */
    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Exibe um diálogo bloqueante (não cancelável) avisando que a internet está desligada
     * ou a API está fora do ar, impedindo o uso do app até que a conexão seja restabelecida.
     */
    fun showBlockingError(
        activity: Activity?,
        customMessage: String? = null,
        onRetry: () -> Unit
    ) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return

        // Evita empilhar múltiplos diálogos simultaneamente
        if (activeDialog?.isShowing == true) return

        val hasInternet = isInternetAvailable(activity)
        val title = if (!hasInternet) "Sem conexão com a internet" else "Servidor indisponível"
        val message = customMessage ?: if (!hasInternet) {
            "Sua internet parece estar desligada. Conecte-se a uma rede Wi-Fi ou dados móveis para continuar usando o Frigus."
        } else {
            "Não foi possível conectar ao servidor do Frigus no momento. A API pode estar fora do ar ou em manutenção."
        }

        activeDialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Tentar novamente") { dialog, _ ->
                dialog.dismiss()
                activeDialog = null
                if (!isInternetAvailable(activity)) {
                    showBlockingError(activity, customMessage, onRetry)
                } else {
                    onRetry()
                }
            }
            .setNegativeButton("Sair do app") { dialog, _ ->
                dialog.dismiss()
                activeDialog = null
                activity.finishAffinity()
            }
            .create()

        activeDialog?.show()
    }

    /**
     * Verifica imediatamente se há internet; caso não haja, bloqueia a tela.
     */
    fun ensureConnected(activity: Activity?, onConnected: () -> Unit): Boolean {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return false
        return if (!isInternetAvailable(activity)) {
            showBlockingError(activity, null, onConnected)
            false
        } else {
            true
        }
    }
}

/**
 * Extensão para Activity que encapsula verificação de rede, escopo de corrotina e tratamento de erros.
 */
fun AppCompatActivity.launchSafeNetwork(
    onRetry: () -> Unit,
    block: suspend CoroutineScope.() -> Unit
): Job? {
    if (!NetworkGuard.ensureConnected(this, onRetry)) return null
    return lifecycleScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            // Cancelamento normal quando a tela é destruída ou encerrada
            throw e
        } catch (e: Exception) {
            NetworkGuard.showBlockingError(this@launchSafeNetwork, e.message, onRetry)
        }
    }
}

/**
 * Extensão para Fragment que encapsula verificação de rede, escopo de corrotina e tratamento de erros.
 */
fun Fragment.launchSafeNetwork(
    onRetry: () -> Unit,
    block: suspend CoroutineScope.() -> Unit
): Job? {
    val act = activity ?: return null
    if (!NetworkGuard.ensureConnected(act, onRetry)) return null
    return viewLifecycleOwner.lifecycleScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            // Cancelamento normal quando o usuário troca de aba ou navega para outra tela
            throw e
        } catch (e: Exception) {
            NetworkGuard.showBlockingError(activity, e.message, onRetry)
        }
    }
}
