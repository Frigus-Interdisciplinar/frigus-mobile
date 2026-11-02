package com.example.frigus_mobile.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object ProductImageLoader {
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(30) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadInto(
        imageView: ImageView,
        imageUrl: String?,
        @DrawableRes fallbackRes: Int
    ) {
        if (imageUrl.isNullOrBlank() || (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://"))) {
            imageView.tag = null
            imageView.clearColorFilter()
            imageView.setImageResource(fallbackRes)
            return
        }

        val cached = memoryCache.get(imageUrl)
        if (cached != null) {
            imageView.tag = imageUrl
            imageView.clearColorFilter()
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setImageBitmap(cached)
            return
        }

        // Set fallback first and tag
        imageView.tag = imageUrl
        imageView.clearColorFilter()
        imageView.setImageResource(fallbackRes)

        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = fetchAndDecode(imageUrl)
            if (bitmap != null) {
                memoryCache.put(imageUrl, bitmap)
                withContext(Dispatchers.Main) {
                    if (imageView.tag == imageUrl) {
                        imageView.clearColorFilter()
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun fetchAndDecode(url: String): Bitmap? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "image/jpeg,image/png,image/webp")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body() ?: return null
            val bytes = body.bytes()
            if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}
