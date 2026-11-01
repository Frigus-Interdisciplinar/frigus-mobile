package com.example.frigus_mobile.data.repository

import java.util.concurrent.atomic.AtomicLong

/** Invalidates the recipe GET cache after a confirmed assistant reply. Contains no user data. */
internal object AiRecipeUpdates {
    private val revision = AtomicLong()
    fun current(): Long = revision.get()
    fun confirmed() { revision.incrementAndGet() }
}
