package com.example.grossrecipes.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.grossrecipes.data.ConnectivityObserver
import com.example.grossrecipes.data.DeviceIdProvider
import com.example.grossrecipes.data.ListsRepository
import com.example.grossrecipes.data.Session
import com.example.grossrecipes.data.SessionManager
import com.example.grossrecipes.data.SyncStateManager
import com.example.grossrecipes.data.local.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val connectivityObserver = ConnectivityObserver(application)
    private val database = AppDatabase.getInstance(application)
    private val repository = ListsRepository(
        database.listDao(),
        database.listItemDao(),
        database.outboxEventDao(),
        DeviceIdProvider(application),
        SyncStateManager(application),
        sessionManager
    )

    val session: StateFlow<Session> = sessionManager.sessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Session())

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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
