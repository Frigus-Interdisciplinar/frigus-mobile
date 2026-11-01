package com.example.frigus_mobile.ui.profile

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.repository.ProfileAvatarRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ProfileAvatarState(val userId: String? = null, val generation: Long? = null, val bitmap: Bitmap? = null)

class ProfileAvatarViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    private val repository = ProfileAvatarRepository(application)
    private val mutableState = MutableLiveData(ProfileAvatarState())
    val state: LiveData<ProfileAvatarState> = mutableState
    private var loadedKey: String? = null
    private var pending: Job? = null

    fun load() {
        val credential = session.state.credential() ?: return
        val user = session.userId ?: return
        if (!session.isLoggedIn) return
        val key = repository.sourceKey()
        val current = state.value
        if (key == loadedKey && current?.userId == user && current.generation == credential.generation &&
            (current.bitmap != null || pending?.isActive == true)) return
        pending?.cancel()
        loadedKey = key
        mutableState.value = ProfileAvatarState(userId = user, generation = credential.generation)
        if (key == null) return
        pending = viewModelScope.launch {
            val bitmap = repository.load()
            if (session.isLoggedIn && session.userId == user && session.state.credential() == credential &&
                repository.sourceKey() == key) mutableState.value = ProfileAvatarState(user, credential.generation, bitmap)
        }
    }
}
