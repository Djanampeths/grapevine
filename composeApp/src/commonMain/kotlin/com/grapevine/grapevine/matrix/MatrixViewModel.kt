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

// ── Data models ───────────────────────────────────────────────────────────────

data class RoomInfo(
    val id: RoomId,
    val name: String?,
    val topic: String?,
    val memberCount: Int,
    val unreadCount: Long = 0L,
    val lastMessage: String? = null,
    val isDirect: Boolean = false,
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
    val replyToEventId: String? = null,
)

data class MemberInfo(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
) {
    val displayOrUser: String get() = displayName
        ?: userId.removePrefix("@").substringBefore(":")
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MatrixViewModel : ViewModel() {

    private val matrixClient = GrapevineMatrixClient(viewModelScope)

    val loginState: StateFlow<LoginState> = matrixClient.loginState

    // Rooms
    private val _rooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val rooms: StateFlow<List<RoomInfo>> = _rooms.asStateFlow()

    private val _dmRoomIds = MutableStateFlow<Set<RoomId>>(emptySet()) // kept for isDmRoom() helper

    // Current room
    private val _currentRoomId = MutableStateFlow<RoomId?>(null)
    val currentRoomId: StateFlow<RoomId?> = _currentRoomId.asStateFlow()

    // Messages
    private val _messages = MutableStateFlow<List<MessageInfo>>(emptyList())
    val messages: StateFlow<List<MessageInfo>> = _messages.asStateFlow()

    private val _isLoadingMessages = MutableStateFlow(false)
    val isLoadingMessages: StateFlow<Boolean> = _isLoadingMessages.asStateFlow()

    private val _messageError = MutableStateFlow<String?>(null)
    val messageError: StateFlow<String?> = _messageError.asStateFlow()

    private val _messageCount = MutableStateFlow(50)
    val messageCount: StateFlow<Int> = _messageCount.asStateFlow()

    // Reply
    private val _replyingTo = MutableStateFlow<MessageInfo?>(null)
    val replyingTo: StateFlow<MessageInfo?> = _replyingTo.asStateFlow()

    // Typing
    private val _typingUsers = MutableStateFlow<List<String>>(emptyList())
    val typingUsers: StateFlow<List<String>> = _typingUsers.asStateFlow()

    // Members (for room info screen)
    private val _roomMembers = MutableStateFlow<List<MemberInfo>>(emptyList())
    val roomMembers: StateFlow<List<MemberInfo>> = _roomMembers.asStateFlow()

    // Room action feedback
    private val _roomActionError = MutableStateFlow<String?>(null)
    val roomActionError: StateFlow<String?> = _roomActionError.asStateFlow()

    private var messagesJob: Job? = null
    private var typingJob: Job? = null
    private var membersJob: Job? = null

    init {
        viewModelScope.launch { matrixClient.tryRestoreSession() }
        viewModelScope.launch {
            loginState.collect { state ->
                if (state is LoginState.LoggedIn) {
                    collectRooms()
                } else {
                    _rooms.value = emptyList()
                }
            }
        }
        // dmRoomIdsFlow removed — Room.isDirect is used directly in toRoomInfo()
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    fun login(homeserver: String, username: String, password: String) {
        viewModelScope.launch { matrixClient.login(homeserver, username, password) }
    }

    fun logout() {
        stopRoomJobs()
        _rooms.value = emptyList()
        _messages.value = emptyList()
        _currentRoomId.value = null
        viewModelScope.launch { matrixClient.logout() }
    }

    // ── Room navigation ───────────────────────────────────────────────────────

    fun openRoom(roomId: RoomId) {
        _currentRoomId.value = roomId
        _messages.value = emptyList()
        _messageError.value = null
        _replyingTo.value = null
        _messageCount.value = 50
        stopRoomJobs()

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

        typingJob = viewModelScope.launch {
            matrixClient.typingUsersFlow(roomId).collect { _typingUsers.value = it }
        }

        membersJob = viewModelScope.launch {
            matrixClient.roomMembersFlow(roomId).collect { _roomMembers.value = it }
        }
    }

    fun closeRoom() {
        stopRoomJobs()
        _currentRoomId.value = null
        _messages.value = emptyList()
        _typingUsers.value = emptyList()
        _roomMembers.value = emptyList()
        _replyingTo.value = null
    }

    fun stopMessages() = closeRoom()

    fun loadMoreMessages() { _messageCount.value += 50 }

    fun retryMessages() { _currentRoomId.value?.let { openRoom(it) } }

    // ── Messaging ─────────────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        val roomId = _currentRoomId.value ?: return
        val reply = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            if (reply != null) {
                matrixClient.sendReply(roomId, EventId(reply.eventId), text)
            } else {
                matrixClient.sendMessage(roomId, text)
            }
        }
    }

    fun setReplyingTo(message: MessageInfo?) { _replyingTo.value = message }

    fun editMessage(eventId: String, newBody: String) {
        val roomId = _currentRoomId.value ?: return
        viewModelScope.launch { matrixClient.editMessage(roomId, EventId(eventId), newBody) }
    }

    fun deleteMessage(eventId: String) {
        val roomId = _currentRoomId.value ?: return
        viewModelScope.launch { matrixClient.deleteMessage(roomId, EventId(eventId)) }
    }

    fun setTyping(isTyping: Boolean) {
        val roomId = _currentRoomId.value ?: return
        viewModelScope.launch { matrixClient.setTyping(roomId, isTyping) }
    }

    // ── Room actions ──────────────────────────────────────────────────────────

    fun joinRoom(input: String) {
        _roomActionError.value = null
        viewModelScope.launch {
            try {
                val trimmed = input.trim()
                if (trimmed.startsWith("!")) {
                    matrixClient.joinRoom(RoomId(trimmed))
                } else {
                    matrixClient.joinRoomByAlias(trimmed)
                }
            } catch (e: Exception) {
                _roomActionError.value = e.message ?: "Failed to join room"
            }
        }
    }

    fun createRoom(name: String, topic: String) {
        _roomActionError.value = null
        viewModelScope.launch {
            try {
                matrixClient.createRoom(name, topic)
            } catch (e: Exception) {
                _roomActionError.value = e.message ?: "Failed to create room"
            }
        }
    }

    fun leaveCurrentRoom() {
        val roomId = _currentRoomId.value ?: return
        closeRoom()
        viewModelScope.launch {
            try {
                matrixClient.leaveRoom(roomId)
            } catch (e: Exception) {
                _roomActionError.value = e.message ?: "Failed to leave room"
            }
        }
    }

    fun clearRoomActionError() { _roomActionError.value = null }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun mxcToHttp(mxcUri: String): String? = matrixClient.mxcToHttp(mxcUri)

    fun isDmRoom(roomId: RoomId): Boolean = roomId in _dmRoomIds.value

    private fun stopRoomJobs() {
        messagesJob?.cancel(); messagesJob = null
        typingJob?.cancel();   typingJob = null
        membersJob?.cancel();  membersJob = null
    }

    private suspend fun collectRooms() {
        matrixClient.roomsWithDetailsFlow().collect { list ->
            _rooms.value = list.sortedByDescending { it.unreadCount }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRoomJobs()
    }
}
