package com.example.frigus_mobile.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.frigus_mobile.data.local.SessionManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public Google image media only. API requests and credentials remain on MobileHttp/BFF. */
class ProfileAvatarRepository(context: Context) {
    private val context = context.applicationContext
    private val session = SessionManager(this.context)
    private val cache = File(this.context.cacheDir, "profile_avatars")

    fun sourceKey(): String? {
        val id = session.userId?.takeIf(String::isNotBlank) ?: return null
        val url = sourceUrl() ?: return null
        return ProfileAvatarLocation.cacheKey(id, url)
    }

    private fun sourceUrl(): String? {
        if (!session.isLoggedIn) return null
        // The SDK account is used only when its email belongs to the current BFF identity.
        val email = session.userEmail?.takeIf(String::isNotBlank)
        val sdkPhoto = if (email == null) null else runCatching { GoogleSignIn.getLastSignedInAccount(context)
            ?.takeIf { email.equals(it.email, ignoreCase = true) }?.photoUrl?.toString() }.getOrNull()
        return listOf(session.googlePhotoUrl, session.state.user?.profilePictureUrl, sdkPhoto)
            .firstNotNullOfOrNull(ProfileAvatarLocation::googleHttpsUrl)
    }

    suspend fun load(): Bitmap? = withContext(Dispatchers.IO) {
        val credential = session.state.credential() ?: return@withContext null
        val user = session.userId ?: return@withContext null
        val url = sourceUrl() ?: return@withContext null
        fun current() = session.isLoggedIn && session.userId == user && session.state.credential() == credential && sourceUrl() == url
        val key = ProfileAvatarLocation.cacheKey(user, url)
        val target = File(cache, "$key.png")
        val cached = runCatching {
            if (target.isFile && target.length() in 1..MAX_BYTES) decode(target.readBytes()) else null
        }.getOrNull()
        if (cached != null) return@withContext cached.takeIf { current() }

        val bitmap = runCatching {
            val request = Request.Builder().url(url).header("Accept", "image/jpeg,image/png,image/webp").build()
            mediaClient.newCall(request).execute().use { response ->
                if (response.code() != 200 || !current()) return@use null
                val body = response.body() ?: return@use null
                val type = body.contentType()?.toString()?.substringBefore(';')?.lowercase()
                if (type !in setOf("image/jpeg", "image/png", "image/webp") || body.contentLength() > MAX_BYTES) return@use null
                val bytes = ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (bytes.size() + count > MAX_BYTES || !current()) throw IOException("Public image limit exceeded")
                        bytes.write(buffer, 0, count)
                    }
                }
                decode(bytes.toByteArray())
            }
        }.getOrNull() ?: return@withContext null
        if (!current()) return@withContext null
        runCatching {
            cache.mkdirs()
            val temporary = File(cache, "$key.tmp")
            temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (current()) {
                if (!temporary.renameTo(target)) temporary.delete()
            } else temporary.delete()
        }
        bitmap.takeIf { current() }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 8192 || bounds.outHeight > 8192) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private const val MAX_BYTES = 2L * 1024 * 1024
        private val mediaClient = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .authenticator(Authenticator.NONE)
            .proxyAuthenticator(Authenticator.NONE)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    }
}
