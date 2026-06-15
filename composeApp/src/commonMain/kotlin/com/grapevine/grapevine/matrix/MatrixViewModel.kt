package com.grapevine.grapevine.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        _rooms.value = emptyList()
        _messages.value = emptyList()
        _currentRoomId.value = null
        viewModelScope.launch { matrixClient.logout() }
    }

    fun openRoom(roomId: RoomId) {
        _currentRoomId.value = roomId
        _messages.value = emptyList()
        viewModelScope.launch {
            _messages.value = matrixClient.getMessages(roomId)
        }
    }

    // Called by the UI's send button — also refreshes messages after sending.
    fun sendMessage(text: String) {
        val roomId = _currentRoomId.value ?: return
        viewModelScope.launch {
            matrixClient.sendMessage(roomId, text)
            // Re-fetch after sending so the new message appears immediately.
            _messages.value = matrixClient.getMessages(roomId)
        }
    }

    private suspend fun collectRooms() {
        matrixClient.roomsFlow().collect { roomList ->
            _rooms.value = roomList.map { it.toRoomInfo() }
        }
    }
}

// Extension function: adds .toRoomInfo() to Trixnity's Room type without modifying it.
private fun Room.toRoomInfo() = RoomInfo(
    id = roomId,
    name = name?.explicitName,
    topic = null,       // topic lives in room state events, not the Room summary
    memberCount = 0,    // member count requires fetching membership state separately
)
