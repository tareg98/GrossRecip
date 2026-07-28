package com.example.grossrecipes.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.grossrecipes.data.ConnectivityObserver
import com.example.grossrecipes.data.ListsRepository
import com.example.grossrecipes.data.Session
import com.example.grossrecipes.data.SessionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val connectivityObserver = ConnectivityObserver(application)
    // The singleton, not a fresh instance - so isSyncing/pendingChangeCount
    // here agree with what ListsScreen shows, instead of each screen having
    // its own separate (and possibly contradictory) idea of sync state.
    private val repository = ListsRepository.getInstance(application)

    val session: StateFlow<Session> = sessionManager.sessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Session())

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Whether an actual network sync is in flight right now - covers the gap
    // right after login where the outbox is legitimately empty (nothing
    // local to push yet) but the first pull from the server hasn't happened.
    val isSyncing: StateFlow<Boolean> = repository.isSyncing

    // Whether device connectivity is up says nothing about whether the
    // server actually accepted the last sync (e.g. an expired token gets
    // rejected even on a perfectly fine connection) - the outbox is the real
    // source of truth for "did everything actually make it to the server."
    val pendingChangeCount: StateFlow<Int> = repository.observePendingChangeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            // Wipe local lists/items/outbox and reset the sync cursor so a
            // different account logging in on this phone starts clean
            // instead of seeing (or re-pushing) the previous account's data.
            repository.clearAllLocalData()
            sessionManager.logout()
            onLoggedOut()
        }
    }
}
