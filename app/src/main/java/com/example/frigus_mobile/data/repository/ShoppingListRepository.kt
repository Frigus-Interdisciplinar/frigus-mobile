package com.example.frigus_mobile.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ShoppingListItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ShoppingListRepository(context: Context) {

    private val sessionManager = SessionManager(context)
    private val userId: String
        get() = sessionManager.userId?.takeIf { it.isNotBlank() } ?: "local_user"

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val firestore: FirebaseFirestore by lazy {
        val db = FirebaseFirestore.getInstance()
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()
            db.firestoreSettings = settings
        } catch (_: Exception) {}
        db
    }

    private val itemsCollection
        get() = firestore.collection("users").document(userId).collection("shopping_list")

    /**
     * Retorna os itens do cache local (rápido e síncrono).
     */
    fun getItems(): List<ShoppingListItem> {
        val userKey = "$KEY_ITEMS-$userId"
        val json = prefs.getString(userKey, null) ?: prefs.getString(KEY_ITEMS, null)
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<ShoppingListItem>>() {}.type
            gson.fromJson<List<ShoppingListItem>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveItems(items: List<ShoppingListItem>) {
        val userKey = "$KEY_ITEMS-$userId"
        val json = gson.toJson(items)
        prefs.edit().putString(userKey, json).apply()
    }

    /**
     * Adiciona um item na lista: salva no cache local e persiste no Firestore.
     */
    fun addItem(item: ShoppingListItem) {
        val current = getItems().toMutableList()
        current.add(0, item)
        saveItems(current)

        try {
            itemsCollection.document(item.id).set(item)
                .addOnFailureListener { e ->
                    Log.w("ShoppingListRepo", "Falha ao sincronizar item no Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w("ShoppingListRepo", "Firestore indisponível temporariamente: ${e.message}")
        }
    }

    /**
     * Atualiza um item localmente e no Firestore.
     */
    fun updateItem(item: ShoppingListItem) {
        val current = getItems().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index != -1) {
            current[index] = item
            saveItems(current)
        }

        try {
            itemsCollection.document(item.id).set(item)
                .addOnFailureListener { e ->
                    Log.w("ShoppingListRepo", "Falha ao atualizar no Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w("ShoppingListRepo", "Firestore indisponível: ${e.message}")
        }
    }

    /**
     * Deleta um item localmente e no Firestore.
     */
    fun deleteItem(id: String) {
        val current = getItems().toMutableList()
        current.removeAll { it.id == id }
        saveItems(current)

        try {
            itemsCollection.document(id).delete()
                .addOnFailureListener { e ->
                    Log.w("ShoppingListRepo", "Falha ao deletar no Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w("ShoppingListRepo", "Firestore indisponível: ${e.message}")
        }
    }

    /**
     * Alterna o estado checked do item.
     */
    fun toggleChecked(id: String, isChecked: Boolean) {
        val current = getItems().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = current[index].copy(isChecked = isChecked)
            current[index] = updated
            saveItems(current)

            try {
                itemsCollection.document(id).update("checked", isChecked)
                    .addOnFailureListener {
                        itemsCollection.document(id).set(updated)
                    }
            } catch (_: Exception) {}
        }
    }

    fun getItemById(id: String): ShoppingListItem? {
        return getItems().firstOrNull { it.id == id }
    }

    fun clearAll() {
        val userKey = "$KEY_ITEMS-$userId"
        prefs.edit().remove(userKey).apply()
    }

    /**
     * Escuta alterações em tempo real do Firestore para atualizar a lista local automaticamente.
     */
    fun listenToChanges(onItemsUpdated: (List<ShoppingListItem>) -> Unit): ListenerRegistration? {
        return try {
            itemsCollection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ShoppingListRepo", "Erro ao escutar Firestore: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val remoteItems = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ShoppingListItem::class.java)
                    }
                    if (remoteItems.isNotEmpty()) {
                        saveItems(remoteItems)
                        onItemsUpdated(remoteItems)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ShoppingListRepo", "Não foi possível registrar listener: ${e.message}")
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "shopping_list_prefs"
        private const val KEY_ITEMS = "shopping_list_user_items"
    }
}
