package com.example.frigus_mobile.data.repository

import android.content.Context
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Firebase UID is the validated Core UUID, never an anonymous account or a Core bearer token. */
class FirebaseShoppingSession(context: Context) {
    private val session = SessionManager(context.applicationContext)

    suspend fun ensureAuthenticated(): Boolean = lock.withLock {
        val credential = session.state.credential() ?: return@withLock false
        val owner = session.userId?.takeIf { session.isLoggedIn } ?: return@withLock false
        fun active() = session.isLoggedIn && session.userId == owner && session.state.credential() == credential
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser?.uid == owner) return@withLock active()
        auth.signOut()
        try {
            val response = withContext(Dispatchers.IO) { RetrofitClient.firebaseSessionService.create().execute() }
            if (!active() || response.code() != 200) return@withLock false
            val body = response.body() ?: return@withLock false
            if (body.uid != owner || body.projectId != FirebaseApp.getInstance().options.projectId ||
                body.customToken.isNullOrBlank()) return@withLock false
            withContext(NonCancellable) {
                // Keep the auth lock until the native Task completes, even if a screen closes.
                val result = auth.signInWithCustomToken(body.customToken).awaitResult()
                if (!active() || result.user?.uid != owner) {
                    auth.signOut()
                    false
                } else true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { false }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

    companion object {
        private val lock = Mutex()
        fun signOut() { runCatching { FirebaseAuth.getInstance().signOut() } }
    }
}
