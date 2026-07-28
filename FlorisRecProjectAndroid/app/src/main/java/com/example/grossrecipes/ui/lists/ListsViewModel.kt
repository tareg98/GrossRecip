package com.example.grossrecipes.ui.lists

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.grossrecipes.data.ConnectivityObserver
import com.example.grossrecipes.data.ListsRepository
import com.example.grossrecipes.data.Session
import com.example.grossrecipes.data.SessionManager
import com.example.grossrecipes.data.SyncStateManager
import com.example.grossrecipes.data.local.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListsViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val database = AppDatabase.getInstance(application)
    private val repository = ListsRepository(
        database.listDao(),
        database.listItemDao(),
        database.outboxEventDao(),
        SyncStateManager(application),
        sessionManager
    )
    private val connectivityObserver = ConnectivityObserver(application)

    val lists: StateFlow<List<GroceryList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val knownItemNames: StateFlow<List<String>> = repository.observeKnownItemNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real device connectivity - not a guess based on whether the last call worked.
    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Whatever the last action's Result.failure said, so the screen can show
    // it (e.g. "Sync failed: HTTP 500 - ..."). Cleared once shown.
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun errorShown() {
        _errorMessage.value = null
    }

    init {
        // First load.
        refresh()

        // Automatic sync the moment the phone reconnects - no button.
        viewModelScope.launch {
            connectivityObserver.observe().distinctUntilChanged().collect { online ->
                if (online) {
                    withLoggedInSession { serverUrl, accessToken ->
                        repository.syncPendingChanges(serverUrl, accessToken)
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                // Same call used everywhere else - push outbox, pull since our
                // cursor. On a fresh install the cursor is 0, so this pulls the
                // entire history and bootstraps the local database from scratch.
                repository.syncPendingChanges(serverUrl, accessToken)
            }
        }
    }

    fun createList(name: String, color: Color?, sharedWithUsername: String) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.createList(serverUrl, accessToken, name, color, sharedWithUsername)
            }
        }
    }

    fun addItem(listId: String, name: String) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.addItem(serverUrl, accessToken, listId, name)
            }
        }
    }

    fun toggleChecked(itemId: String, newCheckedValue: Boolean) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.setChecked(serverUrl, accessToken, itemId, newCheckedValue)
            }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.deleteList(serverUrl, accessToken, listId)
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.deleteItem(serverUrl, accessToken, itemId)
            }
        }
    }

    fun setColor(listId: String, color: Color?) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.setColor(serverUrl, accessToken, listId, color)
            }
        }
    }

    fun toggleCheckedSectionExpanded(listId: String, expanded: Boolean) {
        // Purely local UI state - was never meant to sync anywhere.
        viewModelScope.launch { repository.setCheckedSectionExpandedLocalOnly(listId, expanded) }
    }

    fun shareList(listId: String, username: String) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.shareList(serverUrl, accessToken, listId, username)
            }
        }
    }

    fun unshareList(listId: String, username: String) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.unshareList(serverUrl, accessToken, listId, username)
            }
        }
    }

    fun markSharedExternally(listId: String) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.markSharedExternally(serverUrl, accessToken, listId)
            }
        }
    }

    /** Called after a drag-and-drop reorder finishes, with the full new order. Purely local - never synced. */
    fun reorderLists(orderedListIds: List<String>) {
        viewModelScope.launch { repository.updateSortOrder(orderedListIds) }
    }

    // Every action function's lambda already ends with a repository call that
    // returns Result<Unit> - previously that value just fell on the floor
    // here (block's declared return type was Unit), so a failed sync or
    // action failed completely silently. Now the Result is captured and, on
    // failure, its message is published to errorMessage for the screen to show.
    private suspend fun withLoggedInSession(block: suspend (serverUrl: String, accessToken: String) -> Result<Unit>) {
        val session: Session = sessionManager.currentSession()
        if (session.isLoggedIn) {
            block(session.serverUrl, session.accessToken!!)
                .onFailure { e -> _errorMessage.value = e.message ?: "Something went wrong" }
        }
    }
}
