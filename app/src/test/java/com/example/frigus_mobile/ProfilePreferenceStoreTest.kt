package com.example.frigus_mobile

import android.content.SharedPreferences
import com.example.frigus_mobile.data.local.PreferenceOwner
import com.example.frigus_mobile.data.local.ProfilePreferenceStore
import com.example.frigus_mobile.data.local.SessionCredential
import org.junit.Assert.*
import org.junit.Test

class ProfilePreferenceStoreTest {
    @Test fun preferencesSurviveReopeningAndAreSeparatedByAccount() {
        val disk = MemoryPreferences()
        var active: PreferenceOwner? = PreferenceOwner("account-a", SessionCredential("a", 1))
        val first = ProfilePreferenceStore(disk, active) { active }
        assertTrue(first.putBoolean("food_vegetarian", true))
        assertTrue(ProfilePreferenceStore(disk, active) { active }.getBoolean("food_vegetarian", false))
        active = PreferenceOwner("account-b", SessionCredential("b", 2))
        val second = ProfilePreferenceStore(disk, active) { active }
        assertFalse(second.getBoolean("food_vegetarian", false))
        assertTrue(second.putBoolean("food_vegetarian", false))
        active = PreferenceOwner("account-a", SessionCredential("a-new", 3))
        assertTrue(ProfilePreferenceStore(disk, active) { active }.getBoolean("food_vegetarian", false))
        assertFalse(first.putBoolean("food_vegetarian", false))
    }

    @Test fun logoutAndOldGenerationsCannotReadOrOverwriteTheActiveAccount() {
        val disk = MemoryPreferences()
        var active: PreferenceOwner? = PreferenceOwner("account-a", SessionCredential("a", 1))
        val previous = ProfilePreferenceStore(disk, active) { active }
        assertTrue(previous.putBoolean("food_vegan", true))
        active = null
        assertFalse(previous.isActive)
        assertFalse(previous.getBoolean("food_vegan", false))
        assertFalse(previous.putBoolean("food_vegan", false))
        active = PreferenceOwner("account-a", SessionCredential("a", 2))
        assertFalse(previous.isActive)
        assertTrue(ProfilePreferenceStore(disk, active) { active }.getBoolean("food_vegan", false))
    }

    @Test fun aFailedDiskCommitDoesNotPretendTheNewChoiceWasSaved() {
        val disk = MemoryPreferences()
        val active = PreferenceOwner("account-a", SessionCredential("a", 1))
        val store = ProfilePreferenceStore(disk, active) { active }
        assertTrue(store.putBoolean("food_no_gluten", false))
        disk.commitSucceeds = false
        assertFalse(store.putBoolean("food_no_gluten", true))
        assertFalse(store.getBoolean("food_no_gluten", true))
    }

    private class MemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()
        var commitSucceeds = true
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun contains(key: String?) = key in values
        override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = values[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = Editor()

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearing = false
            override fun putString(key: String?, value: String?) = apply { pending[requireNotNull(key)] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { pending[requireNotNull(key)] = values }
            override fun putInt(key: String?, value: Int) = apply { pending[requireNotNull(key)] = value }
            override fun putLong(key: String?, value: Long) = apply { pending[requireNotNull(key)] = value }
            override fun putFloat(key: String?, value: Float) = apply { pending[requireNotNull(key)] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { pending[requireNotNull(key)] = value }
            override fun remove(key: String?) = apply { pending[requireNotNull(key)] = null }
            override fun clear() = apply { clearing = true }
            private fun updateMemory() {
                if (clearing) values.clear()
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
            override fun commit(): Boolean { updateMemory(); return commitSucceeds }
            override fun apply() { updateMemory() }
        }
    }
}
