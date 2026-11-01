package com.example.frigus_mobile

import com.example.frigus_mobile.data.model.ShoppingListItem
import com.example.frigus_mobile.data.model.ShoppingLocalChange
import com.example.frigus_mobile.data.repository.ShoppingLocalStore
import com.example.frigus_mobile.data.repository.shoppingDurablePreferenceWrite
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ShoppingLocalStoreTest {
    private val data = mutableMapOf<String, String>()
    private val store = ShoppingLocalStore(data::get) { key, value -> data[key] = value; true }
    private fun item(id: String = "first") = ShoppingListItem(id = id, name = "Arroz", quantity = 2,
        unit = "KILOGRAM", categoryCode = "GRAIN", addedBy = "")

    @Test fun offlineCrudAndCheckboxPersistAcrossStoreRecreation() {
        store.save("account-a", listOf(item()))
        store.mutate("account-a") { list -> list.map { it.copy(name = "Feijão", isChecked = true) } }
        val reopened = ShoppingLocalStore(data::get) { key, value -> data[key] = value; true }
        assertEquals("Feijão", reopened.items("account-a").single().name)
        assertTrue(reopened.items("account-a").single().isChecked)
        assertEquals(1, reopened.document("account-a").pending.size)
        reopened.mutate("account-a") { emptyList() }
        assertTrue(reopened.items("account-a").isEmpty())
        assertNull(reopened.document("account-a").pending.single().item)
    }

    @Test fun accountsNeverReadGlobalOrLocalUserFallback() {
        data["shopping_list_user_items"] = Gson().toJson(listOf(item("global")))
        data["shopping_list_user_items-local_user"] = Gson().toJson(listOf(item("guest")))
        assertTrue(store.items("account-a").isEmpty())
        store.save("account-a", listOf(item()))
        assertTrue(store.items("account-b").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { store.items("local_user") }
    }

    @Test fun validPerAccountLegacyCacheIsMigratedWithADurableOutbox() {
        data["shopping_list_user_items-account-a"] = Gson().toJson(listOf(item().copy(productId = 123, remoteId = 1)))
        val migrated = store.items("account-a").single()
        assertEquals("Arroz", migrated.name)
        assertNull(migrated.productId)
        assertNull(migrated.remoteId)
        assertEquals(1, store.document("account-a").pending.size)
        assertTrue(data.containsKey("shopping_local_v3/account-a"))
    }

    @Test fun confirmedOldWriteCannotAcknowledgeANewerOfflineEdit() {
        store.save("account-a", listOf(item()))
        val sent = store.document("account-a").pending
        store.mutate("account-a") { it.map { item -> item.copy(quantity = 5) } }
        store.acknowledge("account-a", sent)
        assertEquals(5, store.document("account-a").pending.single().item?.quantity)
        val latest = store.document("account-a").pending
        store.acknowledge("account-a", latest)
        assertTrue(store.document("account-a").pending.isEmpty())
    }

    @Test fun staleMigratedCacheDoesNotOverwriteAnExistingServerItem() {
        data["shopping_list_user_items-account-a"] = Gson().toJson(listOf(item()))
        store.items("account-a")
        store.merge("account-a", listOf(ShoppingLocalChange("first", "server", item().copy(quantity = 8))))
        assertEquals(8, store.items("account-a").single().quantity)
        assertTrue(store.document("account-a").pending.isEmpty())
    }

    @Test fun actualOfflineEditAfterMigrationStillWinsOverTheServerSnapshot() {
        data["shopping_list_user_items-account-a"] = Gson().toJson(listOf(item()))
        store.mutate("account-a") { it.map { row -> row.copy(quantity = 3) } }
        store.merge("account-a", listOf(ShoppingLocalChange("first", "server", item().copy(quantity = 8))))
        assertEquals(3, store.items("account-a").single().quantity)
        assertEquals(3, store.document("account-a").pending.single().item?.quantity)
    }

    @Test fun remoteSnapshotCannotResurrectAPendingLocalDelete() {
        store.save("account-a", listOf(item()))
        store.acknowledge("account-a", store.document("account-a").pending)
        store.mutate("account-a") { emptyList() }
        store.merge("account-a", listOf(ShoppingLocalChange("first", "remote", item())))
        assertTrue(store.items("account-a").isEmpty())
        assertNull(store.document("account-a").pending.single().item)
    }

    @Test fun diskFailureNeverReportsAnItemAsPersisted() {
        val failed = ShoppingLocalStore({ null }) { _, _ -> false }
        assertThrows(IllegalStateException::class.java) { failed.save("account-a", listOf(item())) }
    }

    @Test fun androidCommitFailureRollsBackTheObservableMemoryAndDoesNotCreateAPhantomOutbox() {
        var fail = false
        val androidStore = mutableMapOf<String, String>()
        val durable = ShoppingLocalStore(androidStore::get) { key, value ->
            shoppingDurablePreferenceWrite(key, value, androidStore::get) { target, replacement ->
                if (replacement == null) androidStore.remove(target) else androidStore[target] = replacement
                !fail // Android has already updated memory when commit returns false.
            }
        }
        durable.save("account-a", listOf(item()))
        val confirmed = androidStore.toMap()
        fail = true
        assertThrows(IllegalStateException::class.java) {
            durable.mutate("account-a") { it.map { row -> row.copy(quantity = 10) } }
        }
        assertEquals(confirmed, androidStore)
        assertEquals(2, durable.items("account-a").single().quantity)
        assertEquals(2, durable.document("account-a").pending.single().item?.quantity)
    }

    @Test fun invalidCacheIsPreservedAndNeverSilentlyReplacedWithAnEmptyList() {
        val invalid = "[{\"id\":\"bad\",\"name\":\"Arroz\",\"quantity\":-2}]"
        data["shopping_list_user_items-account-a"] = invalid
        assertThrows(Exception::class.java) { store.items("account-a") }
        assertEquals(invalid, data["shopping_list_user_items-account-a"])
        assertFalse(data.containsKey("shopping_local_v3/account-a"))
    }
}
