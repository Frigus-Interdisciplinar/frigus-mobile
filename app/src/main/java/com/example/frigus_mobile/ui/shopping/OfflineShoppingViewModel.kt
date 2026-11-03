package com.example.frigus_mobile.ui.shopping

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.AuthResult
import com.example.frigus_mobile.data.repository.AuthRepository

/** Revalidation grants backend access only after the existing M03 restore succeeds. */
class OfflineShoppingViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    private val repository = AuthRepository(application)
    private val owner = session.userId
    private val credential = session.state.credential()
    private var restoring = false
    private val mutableRevalidated = MutableLiveData(0L)
    val revalidated: LiveData<Long> = mutableRevalidated

    fun hasCurrentCachedOwner(): Boolean = session.canReadCachedShopping &&
        session.userId == owner && session.state.credential() == credential

    fun restoreWhenOnline(online: Boolean) {
        if (!online || restoring || !hasCurrentCachedOwner() || session.isLoggedIn) return
        restoring = true
        repository.restore { result ->
            if (result != AuthResult.Loading) restoring = false
            if (result == AuthResult.Authenticated && hasCurrentCachedOwner() && session.isLoggedIn) {
                mutableRevalidated.value = (mutableRevalidated.value ?: 0L) + 1L
            }
        }
    }

    fun cancelRestore() {
        repository.cancel()
        restoring = false
    }

    override fun onCleared() { cancelRestore() }
}
