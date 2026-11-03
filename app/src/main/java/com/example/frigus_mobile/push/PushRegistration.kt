package com.example.frigus_mobile.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging

/**
 * The BFF has no published /mobile/push-tokens route yet. Keep existing UI hooks
 * harmless until the server contract exists; clear the local token on logout.
 */
object PushRegistration {
    fun onSessionChanged(@Suppress("UNUSED_PARAMETER") context: Context, authenticated: Boolean) {
        if (!authenticated) FirebaseMessaging.getInstance().deleteToken()
    }

    fun sync(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") newToken: String? = null
    ) = Unit

    suspend fun unregister(@Suppress("UNUSED_PARAMETER") context: Context) {
        FirebaseMessaging.getInstance().deleteToken()
    }
}
