package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.model.ShoppingListItem
import com.example.frigus_mobile.data.model.ShoppingLocalChange
import com.example.frigus_mobile.data.model.ShoppingLocalDocument
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/** SharedPreferences updates memory even when disk commit fails; restore the previous observable value. */
internal fun shoppingDurablePreferenceWrite(key: String, value: String?,
    read: (String) -> String?, write: (String, String?) -> Boolean): Boolean {
    val previous = read(key)
    if (runCatching { write(key, value) }.getOrDefault(false)) return true
    runCatching { write(key, previous) }
    return false
}

/** Small storage boundary: production uses private preferences; tests use an in-memory key/value store. */
class ShoppingLocalStore(
    private val read: (String) -> String?,
    private val write: (String, String) -> Boolean
) {
    private val gson = Gson()
    private val type = object : TypeToken<List<ShoppingListItem>>() {}.type

    fun items(owner: String): List<ShoppingListItem> {
        return document(owner).items
    }

    fun document(owner: String): ShoppingLocalDocument {
        requireOwner(owner)
        val current = read(key(owner))
        if (current != null) return gson.fromJson(current, ShoppingLocalDocument::class.java).also {
            validate(it.items)
            check(it.pending.all { change -> change.id.isNotBlank() && change.revision.isNotBlank() &&
                (change.item == null || change.item.id == change.id) })
        }
        // Old versions used this account-specific key. Global/local_user fallback is deliberately never read.
        val legacy = read("shopping_local_v2/$owner") ?: read("shopping_list_user_items-$owner")
            ?: return ShoppingLocalDocument()
        val migrated = decode(legacy).map { it.copy(remoteId = null, listId = null, productId = null, stockId = null) }
        return ShoppingLocalDocument(migrated, migrated.map { ShoppingLocalChange(it.id, revision(), it, fromLegacyCache = true) }).also {
            persist(owner, it)
        }
    }

    fun save(owner: String, items: List<ShoppingListItem>) {
        requireOwner(owner)
        validate(items)
        val old = document(owner)
        val pending = old.pending.associateBy { it.id }.toMutableMap()
        items.filter { item -> old.items.firstOrNull { it.id == item.id } != item }.forEach {
            pending[it.id] = ShoppingLocalChange(it.id, revision(), it)
        }
        old.items.filter { oldItem -> items.none { it.id == oldItem.id } }.forEach {
            pending[it.id] = ShoppingLocalChange(it.id, revision())
        }
        persist(owner, ShoppingLocalDocument(items, pending.values.toList()))
    }

    /** A newer local edit must survive the acknowledgement of an older in-flight write. */
    fun acknowledge(owner: String, changes: List<ShoppingLocalChange>) {
        val old = document(owner)
        val acknowledged = changes.associate { it.id to it.revision }
        persist(owner, old.copy(pending = old.pending.filterNot { acknowledged[it.id] == it.revision }))
    }

    /** Remote data never overwrites an unacknowledged local edit or tombstone. */
    fun merge(owner: String, remote: List<ShoppingLocalChange>) {
        val old = document(owner)
        val pendingIds = old.pending.filterNot { it.fromLegacyCache }.map { it.id }.toSet()
        val items = old.items.associateBy { it.id }.toMutableMap()
        remote.filter { it.id !in pendingIds }.forEach { change ->
            if (change.item == null) items.remove(change.id)
            else items[change.id] = change.item.copy(imageUrl = items[change.id]?.imageUrl ?: change.item.imageUrl)
        }
        validate(items.values.toList())
        val observed = remote.map { it.id }.toSet()
        persist(owner, old.copy(items = items.values.toList(),
            pending = old.pending.filterNot { it.fromLegacyCache && it.id in observed }))
    }

    private fun persist(owner: String, document: ShoppingLocalDocument) {
        check(write(key(owner), gson.toJson(document)))
    }

    private fun revision(): String = UUID.randomUUID().toString()

    fun mutate(owner: String, change: (List<ShoppingListItem>) -> List<ShoppingListItem>): List<ShoppingListItem> {
        val updated = change(items(owner))
        save(owner, updated)
        return updated
    }

    private fun decode(json: String): List<ShoppingListItem> {
        val items = gson.fromJson<List<ShoppingListItem>?>(json, type) ?: error("INVALID_LOCAL_SHOPPING")
        validate(items)
        return items
    }

    private fun validate(items: List<ShoppingListItem>) {
        check(items.all { !it.id.isNullOrBlank() && !it.name.isNullOrBlank() && it.quantity > 0 &&
            !it.unit.isNullOrBlank() && !it.categoryCode.isNullOrBlank() })
        check(items.map { it.id }.distinct().size == items.size)
    }

    private fun requireOwner(owner: String) { require(owner.isNotBlank() && owner != "local_user") }
    private fun key(owner: String) = "shopping_local_v3/$owner"
}
