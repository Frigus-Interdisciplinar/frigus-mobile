package com.example.frigus_mobile.push

import android.content.Context
import com.example.frigus_mobile.data.api.PushTokenRegistration
import com.example.frigus_mobile.data.api.PushTokenRemoval
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Registers the current FCM token only after BFF session validation. */
object PushRegistration {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var registered: Pair<Long, String>? = null
    @Volatile private var pendingDeletion: Task<Void>? = null
    @Volatile private var registrationJob: Job? = null

    fun onSessionChanged(context: Context, authenticated: Boolean) {
        if (authenticated) {
            sync(context)
        } else {
            registrationJob?.cancel()
            registered = null
            pendingDeletion = FirebaseMessaging.getInstance().deleteToken()
        }
    }

    fun sync(context: Context, newToken: String? = null) {
        val appContext = context.applicationContext
        val deletion = pendingDeletion
        if (deletion != null && !deletion.isComplete) {
            deletion.addOnCompleteListener { sync(appContext) }
            return
        }
        val session = SessionManager(appContext)
        if (!session.isLoggedIn) return
        val generation = session.state.generation
        if (newToken != null) {
            register(appContext, generation, newToken)
        } else {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                register(appContext, generation, token)
            }
        }
    }

    private fun register(context: Context, generation: Long, token: String) {
        val session = SessionManager(context)
        if (!session.isLoggedIn || session.state.generation != generation ||
            registered == (generation to token)) return
        registrationJob?.cancel()
        registrationJob = scope.launch {
            try {
                val response = RetrofitClient.notificationService.registerPushToken(PushTokenRegistration(token))
                if (response.isSuccessful && session.isLoggedIn && session.state.generation == generation) {
                    registered = generation to token
                }
            } catch (_: Exception) {
                // A later resume or token refresh retries the idempotent registration.
            }
        }
    }

    suspend fun unregister(context: Context) {
        registrationJob?.cancel()
        val session = SessionManager(context.applicationContext)
        if (!session.isLoggedIn) return
        val generation = session.state.generation
        withTimeoutOrNull(3_000L) {
            val token = suspendCancellableCoroutine<String?> { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result else null)
                }
            } ?: return@withTimeoutOrNull
            if (session.isLoggedIn && session.state.generation == generation) {
                try {
                    RetrofitClient.notificationService.unregisterPushToken(PushTokenRemoval(token))
                } catch (_: Exception) {
                    // Local token deletion still stops delivery if the BFF is unavailable.
                }
            }
        }
    }
}
