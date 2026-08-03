package com.example.grossrecipes.ui.lists

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.grossrecipes.data.ConnectivityObserver
import com.example.grossrecipes.data.ListsRepository
import com.example.grossrecipes.data.Session
import com.example.grossrecipes.data.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListsViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    // The singleton, not a fresh instance - see ListsRepository's class doc.
    // SettingsViewModel reaches the exact same instance, so its isSyncing
    // and outbox-derived state agree with what this screen shows.
    private val repository = ListsRepository.getInstance(application)
    private val connectivityObserver = ConnectivityObserver(application)

    val lists: StateFlow<List<GroceryList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val knownItemNames: StateFlow<List<String>> = repository.observeKnownItemNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real device connectivity - not a guess based on whether the last call worked.
    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Together, these are what an accurate "Synced" pill actually needs -
    // see ListsRepository.isSyncing/lastSyncError for why connectivity/outbox alone aren't enough.
    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val lastSyncError: StateFlow<String?> = repository.lastSyncError
    val pendingChangeCount: StateFlow<Int> = repository.observePendingChangeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Whatever the last action's Result.failure said, so the screen can show
    // it (e.g. "Sync failed: HTTP 500 - ..."). Cleared once shown.
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun errorShown() {
        _errorMessage.value = null
    }

    // The live "someone else changed something" listener (see
    // ListsRepository.listenForSyncSignals) - only ever one running at a
    // time, tied to actually being online. Held here rather than left to
    // structured-concurrency cleanup alone, since it needs to be explicitly
    // cancelled and restarted (not just cancelled once) every time
    // connectivity flips, and a plain child coroutine launched fresh inside
    // the collector below would leak a duplicate on every reconnect instead
    // of replacing the previous one.
    private var sseJob: Job? = null

    init {
        // ConnectivityObserver reports the CURRENT connectivity state the
        // instant it's collected (not just future changes), so this alone
        // already covers "first load" - it doesn't need a separate refresh()
        // call alongside it. Having both used to fire two syncs back-to-back
        // on every login/app-open; ListsRepository's syncMutex would keep
        // that safe now, but there's no reason to make the server do the
        // same full-history sync twice in a row.
        viewModelScope.launch {
            connectivityObserver.observe().distinctUntilChanged().collect { online ->
                sseJob?.cancel()
                sseJob = null
                if (online) {
                    withLoggedInSession { serverUrl, accessToken ->
                        repository.syncPendingChanges(serverUrl, accessToken)
                    }
                    // Separate child coroutine, not awaited inline here -
                    // listenForSyncSignals runs until cancelled (it's the
                    // reconnect loop itself), so awaiting it inline would
                    // block this collector from ever seeing the next
                    // connectivity change.
                    val session = sessionManager.currentSession()
                    if (session.isLoggedIn) {
                        sseJob = viewModelScope.launch {
                            repository.listenForSyncSignals(session.serverUrl, session.accessToken!!)
                        }
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

    fun createList(name: String, color: Color?, sharedWithUserId: String) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.createList(serverUrl, accessToken, name, color, sharedWithUserId)
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

    /**
     * Resolves a typed username to their UUID before ShareDialog (or
     * NewListDialog's share-on-create field) will let the user actually add
     * them - see AccountApi.lookupUsername.
     */
    suspend fun lookupUsername(username: String): Result<String?> {
        val session = sessionManager.currentSession()
        if (!session.isLoggedIn || session.accessToken == null) {
            return Result.failure(Exception("Not logged in"))
        }
        return com.example.grossrecipes.data.lookupUsername(session.serverUrl, session.accessToken, username)
    }

    /** The reverse - resolves a UUID back to its username, for ShareDialog's "shared with" pills. */
    suspend fun lookupUserId(userId: String): Result<String?> {
        val session = sessionManager.currentSession()
        if (!session.isLoggedIn || session.accessToken == null) {
            return Result.failure(Exception("Not logged in"))
        }
        return com.example.grossrecipes.data.lookupUserId(session.serverUrl, session.accessToken, userId)
    }

    fun shareList(listId: String, userId: String) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.shareList(serverUrl, accessToken, listId, userId)
            }
        }
    }

    fun unshareList(listId: String, userId: String) {
        viewModelScope.launch {
            withLoggedInSession { serverUrl, accessToken ->
                repository.unshareList(serverUrl, accessToken, listId, userId)
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

    /** Same as [reorderLists], one level down - reordering items within a single list. Purely local - never synced. */
    fun reorderItems(orderedItemIds: List<String>) {
        viewModelScope.launch { repository.updateItemSortOrder(orderedItemIds) }
    }

    /** Toggles whether a divider sits at this gap - see ListsRepository.toggleDivider. Purely local - never syncs anywhere. */
    fun toggleDivider(listId: String, gapIndex: Int) {
        viewModelScope.launch { repository.toggleDivider(listId, gapIndex) }
    }

    /** Carries a divider along when a drag crosses it - see ListsRepository.moveDivider. Purely local - never syncs anywhere. */
    fun moveDivider(listId: String, fromGapIndex: Int, toGapIndex: Int) {
        viewModelScope.launch { repository.moveDivider(listId, fromGapIndex, toGapIndex) }
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
                .onFailure { e ->
                    // If this failure was an expired access token that
                    // ListsApi's Authenticator already tried to silently
                    // refresh and couldn't, it's already logged us out as
                    // part of handling it - the nav graph reacts to that and
                    // sends us back to the login screen on its own. Showing a
                    // "sync failed: HTTP 401" toast on top of that redirect
                    // is just noise, not something the user can act on.
                    val stillLoggedIn = sessionManager.currentSession().isLoggedIn
                    if (stillLoggedIn) {
                        _errorMessage.value = e.message ?: "Something went wrong"
                    }
                }
        }
    }
}
