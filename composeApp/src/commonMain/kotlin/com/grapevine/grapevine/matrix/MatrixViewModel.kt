package com.grapevine.grapevine.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

data class RoomInfo(
    val id: RoomId,
    val name: String?,
    val topic: String?,
    val memberCount: Int,
    val unreadCount: Long = 0L,
    val lastMessage: String? = null,
)

enum class MessageType { Text, Image, File, Audio, Video }

data class MessageInfo(
    val eventId: String,
    val senderId: String,
    val body: String,
    val timestamp: Long,
    val type: MessageType = MessageType.Text,
    val mediaUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Int? = null,
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

    // Drives how many timeline events are loaded; increment to paginate backwards.
    private val _messageCount = MutableStateFlow(50)
    val messageCount: StateFlow<Int> = _messageCount.asStateFlow()

    private var messagesJob: Job? = null

    init {
        viewModelScope.launch { matrixClient.tryRestoreSession() }
        viewModelScope.launch {
            loginState.collect { state ->
                if (state is LoginState.LoggedIn) collectRooms()
                else _rooms.value = emptyList()
            }
        }
    }

    fun login(homeserver: String, username: String, password: String) {
        viewModelScope.launch { matrixClient.login(homeserver, username, password) }
    }

    fun logout() {
        stopMessages()
        _rooms.value = emptyList()
        _messages.value = emptyList()
        _currentRoomId.value = null
        viewModelScope.launch { matrixClient.logout() }
    }

    fun openRoom(roomId: RoomId) {
        _currentRoomId.value = roomId
        _messages.value = emptyList()
        _messageError.value = null
        _messageCount.value = 50
        stopMessages()
        messagesJob = viewModelScope.launch {
            _isLoadingMessages.value = true
            try {
                var lastSeenEventId: EventId? = null
                matrixClient.messagesFlow(roomId, _messageCount).collect { msgs ->
                    _messages.value = msgs
                    _messageError.value = null
                    _isLoadingMessages.value = false
                    val latestId = msgs.lastOrNull()?.eventId?.let { EventId(it) }
                    if (latestId != null && latestId != lastSeenEventId) {
                        lastSeenEventId = latestId
                        matrixClient.markRoomRead(roomId, latestId)
                    }
                }
            } catch (e: Exception) {
                _messageError.value = e.message ?: "Failed to load messages"
                _isLoadingMessages.value = false
            }
        }
    }

    fun loadMoreMessages() {
        _messageCount.value += 50
    }

    fun stopMessages() {
        messagesJob?.cancel()
        messagesJob = null
    }

    fun retryMessages() {
        val roomId = _currentRoomId.value ?: return
        openRoom(roomId)
    }

    fun sendMessage(text: String) {
        val roomId = _currentRoomId.value ?: return
        viewModelScope.launch { matrixClient.sendMessage(roomId, text) }
    }

    private suspend fun collectRooms() {
        matrixClient.roomsWithDetailsFlow().collect { roomList ->
            _rooms.value = roomList
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMessages()
    }
}
