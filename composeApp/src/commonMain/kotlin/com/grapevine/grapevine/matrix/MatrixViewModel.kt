package com.grapevine.grapevine.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.RoomId

data class RoomInfo(
    val id: RoomId,
    val name: String?,
    val topic: String?,
    val memberCount: Int,
    val lastMessage: String? = null,
)

data class MessageInfo(
    val eventId: String,
    val senderId: String,
    val body: String,
    val timestamp: Long,
)

class MatrixViewModel : ViewModel() {

    private val matrixClient = GrapevineMatrixClient(viewModelScope)

    val loginState: StateFlow<LoginState> = matrixClient.loginState

    private val _rooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val rooms: StateFlow<List<RoomInfo>> = _rooms.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageInfo>>(emptyList())
    val messages: StateFlow<List<MessageInfo>> = _messages.asStateFlow()

    private val _currentRoomId = MutableStateFlow<RoomId?>(null)
    val currentRoomId: StateFlow<RoomId?> = _currentRoomId.asStateFlow()

    private val _isLoadingMessages = MutableStateFlow(false)
    val isLoadingMessages: StateFlow<Boolean> = _isLoadingMessages.asStateFlow()

    private val _messageError = MutableStateFlow<String?>(null)
    val messageError: StateFlow<String?> = _messageError.asStateFlow()

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            loginState.collectLatest { state ->
                if (state is LoginState.LoggedIn) collectRooms()
                else _rooms.value = emptyList()
            }
        }
    }

    fun login(homeserver: String, username: String, password: String) {
        viewModelScope.launch {
            matrixClient.login(homeserver, username, password)
        }
    }

    fun logout() {
        stopPolling()
        _rooms.value = emptyList()
        _messages.value = emptyList()
        _currentRoomId.value = null
        viewModelScope.launch { matrixClient.logout() }
    }

    fun openRoom(roomId: RoomId) {
        _currentRoomId.value = roomId
        _messages.value = emptyList()
        _messageError.value = null
        stopPolling()
        pollingJob = viewModelScope.launch {
            _isLoadingMessages.value = true
            while (true) {
                try {
                    _messages.value = matrixClient.getMessages(roomId)
                    _messageError.value = null
                } catch (e: Exception) {
                    _messageError.value = e.message ?: "Failed to load messages"
                } finally {
                    _isLoadingMessages.value = false
                }
                delay(3_000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun retryMessages() {
        val roomId = _currentRoomId.value ?: return
        openRoom(roomId)
    }

    fun sendMessage(text: String) {
        val roomId = _currentRoomId.value ?: return
        viewModelScope.launch {
            matrixClient.sendMessage(roomId, text)
            _messages.value = matrixClient.getMessages(roomId)
        }
    }

    private suspend fun collectRooms() {
        matrixClient.roomsFlow().collect { roomList ->
            _rooms.value = roomList.map { it.toRoomInfo() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}

private fun Room.toRoomInfo() = RoomInfo(
    id = roomId,
    name = name?.explicitName,
    topic = null,
    memberCount = 0,
)
