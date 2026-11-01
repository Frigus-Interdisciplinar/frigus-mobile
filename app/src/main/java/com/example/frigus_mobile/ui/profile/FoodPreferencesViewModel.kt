package com.example.frigus_mobile.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.FoodPreference
import com.example.frigus_mobile.data.model.FoodPreferences
import com.example.frigus_mobile.data.repository.FoodPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FoodPreferencesState(
    val preferences: FoodPreferences? = null,
    val saving: Boolean = false,
    val failed: Boolean = false
)

class FoodPreferencesViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    private val repository = FoodPreferencesRepository(application)
    private val credential = session.state.credential()
    private val mutableState = MutableLiveData(FoodPreferencesState(preferences = repository.snapshot()))
    val state: LiveData<FoodPreferencesState> = mutableState

    fun update(preference: FoodPreference, enabled: Boolean) {
        val current = state.value ?: return
        if (!session.isLoggedIn || session.state.credential() != credential || current.saving ||
            current.preferences == null || current.preferences.isEnabled(preference) == enabled) return
        mutableState.value = current.copy(saving = true, failed = false)
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { repository.update(preference, enabled) }.getOrDefault(false) }
            if (!session.isLoggedIn || session.state.credential() != credential) return@launch
            mutableState.value = FoodPreferencesState(preferences = repository.snapshot(), failed = !saved)
        }
    }
}
