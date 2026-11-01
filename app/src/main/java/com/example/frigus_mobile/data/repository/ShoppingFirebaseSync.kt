package com.example.frigus_mobile.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.example.frigus_mobile.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Durable local outbox + authenticated, idempotent Firestore document synchronization. */
class ShoppingFirebaseSync(
    context: Context,
    private val local: ShoppingLocalStore,
    private val scope: CoroutineScope
) {
    private val application = context.applicationContext
    private val network = application.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var owner: String? = null
    private var active: () -> Boolean = { false }
    private var report: (ShoppingSyncStatus, Int, ApiError?) -> Unit = { _, _, _ -> }
    private var listener: ListenerRegistration? = null
    private var runId = 0L
    private var sending = false
    private var authenticating = false
    private var serverSeen = false
    private var closed = false
    private val connectivity = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { main.post { syncNow() } }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { main.post { syncNow() } }
        override fun onLost(network: Network) { main.post {
            if (!online()) { serverSeen = false; emit(ShoppingSyncStatus.LOCAL_PENDING) } else syncNow()
        } }
    }
    private val authChanged = FirebaseAuth.AuthStateListener { main.post { syncNow() } }

    init {
        runCatching { network.registerDefaultNetworkCallback(connectivity) }
        runCatching { FirebaseAuth.getInstance().addAuthStateListener(authChanged) }
    }

    fun start(user: String, guard: () -> Boolean, changed: (ShoppingSyncStatus, Int, ApiError?) -> Unit) {
        if (owner != user || !active()) {
            listener?.remove(); listener = null
            runId++; sending = false; authenticating = false; serverSeen = false
        }
        owner = user; active = guard; report = changed
        syncNow()
    }

    fun syncNow() {
        if (closed || !active()) return
        val user = owner ?: return
        if (!online()) { emit(ShoppingSyncStatus.LOCAL_PENDING); return }
        val authenticated = runCatching { FirebaseAuth.getInstance().currentUser?.uid == user }.getOrDefault(false)
        if (!authenticated) {
            listener?.remove(); listener = null; serverSeen = false
            if (authenticating) return
            authenticating = true
            emit(ShoppingSyncStatus.AUTH_REQUIRED)
            val run = runId
            scope.launch {
                val success = runCatching { FirebaseShoppingSession(application).ensureAuthenticated() }.getOrDefault(false)
                if (closed || run != runId || !active() || owner != user) return@launch
                authenticating = false
                if (success && FirebaseAuth.getInstance().currentUser?.uid == user) syncNow()
                else emit(ShoppingSyncStatus.AUTH_REQUIRED, ApiError(code = "SHOPPING_FIREBASE_AUTH_REQUIRED"))
            }
            return
        }
        try {
            val firestore = FirebaseFirestore.getInstance()
            val collection = firestore.collection("users").document(user).collection("shopping_list")
            if (listener == null) {
                val run = runId
                listener = collection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (closed || run != runId || !active() || owner != user) return@addSnapshotListener
                    if (error != null) {
                        listener?.remove(); listener = null; serverSeen = false
                        emit(ShoppingSyncStatus.ERROR, ApiError(code = "SHOPPING_FIREBASE_SYNC_ERROR"))
                        return@addSnapshotListener
                    }
                    if (snapshot == null || snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                    try {
                        val remote = snapshot.documents.map { document ->
                            val revision = document.getString("revision") ?: "legacy/${document.id}"
                            if (document.getBoolean("deleted") == true) ShoppingLocalChange(document.id, revision)
                            else {
                                val quantity = document.getLong("quantity")?.takeIf { it >= 1L && it <= Int.MAX_VALUE.toLong() }?.toInt()
                                    ?: throw IllegalStateException()
                                val name = document.getString("name")?.takeIf { it.isNotBlank() } ?: throw IllegalStateException()
                                val unit = document.getString("unit")?.takeIf { it.isNotBlank() } ?: throw IllegalStateException()
                                val category = document.getString("categoryCode")?.takeIf { it.isNotBlank() } ?: throw IllegalStateException()
                                ShoppingLocalChange(document.id, revision, ShoppingListItem(id = document.id,
                                    name = name, quantity = quantity, unit = unit, categoryCode = category,
                                    category = ShoppingListItem.mapCodeToCategory(category),
                                    observation = document.getString("observation").orEmpty(), addedBy = "",
                                    isChecked = document.getBoolean("checked") ?: document.getBoolean("isChecked") ?: false,
                                    unitCode = document.getString("unitCode") ?: ShoppingListItem.mapUnitToCode(unit)))
                            }
                        }
                        local.merge(user, remote)
                        serverSeen = true
                        emit(if (sending) ShoppingSyncStatus.SYNCING else if (local.document(user).pending.isEmpty())
                            ShoppingSyncStatus.SYNCED else ShoppingSyncStatus.LOCAL_PENDING)
                        syncNow()
                    } catch (_: Exception) { emit(ShoppingSyncStatus.ERROR, ApiError(code = "SHOPPING_FIREBASE_SYNC_ERROR")) }
                }
            }
            // Merge the initial server snapshot before uploading migrated cache; it may be stale on this device.
            if (!serverSeen) { emit(ShoppingSyncStatus.LOCAL_PENDING); return }
            if (sending) { emit(ShoppingSyncStatus.SYNCING); return }
            val changes = local.document(user).pending.take(400)
            if (changes.isEmpty()) {
                emit(if (serverSeen) ShoppingSyncStatus.SYNCED else ShoppingSyncStatus.LOCAL_PENDING)
                return
            }
            val batch = firestore.batch()
            changes.forEach { change ->
                val item = change.item
                val fields = mutableMapOf<String, Any>("revision" to change.revision,
                    "deleted" to (item == null), "updatedAt" to FieldValue.serverTimestamp())
                if (item != null) fields.putAll(mapOf("id" to item.id, "name" to item.name,
                    "quantity" to item.quantity, "unit" to item.unit, "unitCode" to (item.unitCode ?: ShoppingListItem.mapUnitToCode(item.unit)),
                    "categoryCode" to item.categoryCode, "observation" to item.observation, "checked" to item.isChecked))
                // Content URIs remain on the originating device; no inaccessible URI is presented as a synced photo.
                batch.set(collection.document(change.id), fields)
            }
            sending = true
            emit(ShoppingSyncStatus.SYNCING)
            val run = runId
            batch.commit().addOnSuccessListener {
                // Acknowledge only exact revisions, even when the user edited again during the request.
                val acknowledged = runCatching { local.acknowledge(user, changes) }.isSuccess
                if (closed || run != runId || !active() || owner != user) return@addOnSuccessListener
                sending = false
                if (acknowledged) syncNow()
                else emit(ShoppingSyncStatus.ERROR, ApiError(code = "SHOPPING_LOCAL_SAVE_FAILED"))
            }.addOnFailureListener {
                if (closed || run != runId || !active() || owner != user) return@addOnFailureListener
                sending = false
                emit(ShoppingSyncStatus.ERROR, ApiError(code = "SHOPPING_FIREBASE_SYNC_ERROR"))
            }
        } catch (_: Exception) { emit(ShoppingSyncStatus.ERROR, ApiError(code = "SHOPPING_FIREBASE_SYNC_ERROR")) }
    }

    private fun emit(status: ShoppingSyncStatus, error: ApiError? = null) {
        if (closed || !active()) return
        val user = owner ?: return
        val count = runCatching { local.document(user).pending.size }.getOrDefault(0)
        report(status, count, error)
    }

    private fun online(): Boolean = runCatching {
        val capabilities = network.getNetworkCapabilities(network.activeNetwork)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    fun stop() {
        listener?.remove(); listener = null; runId++
        owner = null; active = { false }; sending = false; authenticating = false; serverSeen = false
    }

    fun close() {
        stop(); closed = true
        runCatching { network.unregisterNetworkCallback(connectivity) }
        runCatching { FirebaseAuth.getInstance().removeAuthStateListener(authChanged) }
    }
}
