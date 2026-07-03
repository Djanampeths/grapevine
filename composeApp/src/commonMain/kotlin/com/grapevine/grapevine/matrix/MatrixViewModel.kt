package com.grapevine.grapevine.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grapevine.grapevine.GrapevineNotifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.verification.SelfVerificationMethod
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

// ── Data models ───────────────────────────────────────────────────────────────

data class RoomInfo(
    val id: RoomId,
    val name: String?,
    val topic: String?,
    val memberCount: Int,
    val unreadCount: Long = 0L,
    val lastMessage: String? = null,
    val lastActivityMs: Long = 0L,
    val isDirect: Boolean = false,
)

enum class MessageStatus { Sending, Sent, Failed }

enum class MessageType { Text, Image, File, Audio, Video, Encrypted }

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
    val status: MessageStatus = MessageStatus.Sent,
)

data class MemberInfo(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
) {
    val displayOrUser: String get() = displayName
        ?: userId.removePrefix("@").substringBefore(":")
}

// ── Security state ────────────────────────────────────────────────────────────

sealed class SecurityStatus {
    object Loading : SecurityStatus()
    object AlreadyVerified : SecurityStatus()
    data class NeedVerification(
        val methods: Set<SelfVerificationMethod>,
        val hasPassphrase: Boolean,
        val hasRecoveryKey: Boolean,
    ) : SecurityStatus()
    object NoCrossSigning : SecurityStatus()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MatrixViewModel : ViewModel() {

    private val matrixClient = GrapevineMatrixClient(viewModelScope)

    val loginState: StateFlow<LoginState> = matrixClient.loginState

    // Rooms
    private val _rooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val rooms: StateFlow<List<RoomInfo>> = _rooms.asStateFlow()

    private val _roomsLoading = MutableStateFlow(false)
    val roomsLoading: StateFlow<Boolean> = _roomsLoading.asStateFlow()

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

    // Invite
    private val _inviteError = MutableStateFlow<String?>(null)
    val inviteError: StateFlow<String?> = _inviteError.asStateFlow()

    // Profile
    private val _displayName = MutableStateFlow<String?>(null)
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private val _profileAvatarUrl = MutableStateFlow<String?>(null)
    val profileAvatarUrl: StateFlow<String?> = _profileAvatarUrl.asStateFlow()

    private val _profileLoading = MutableStateFlow(false)
    val profileLoading: StateFlow<Boolean> = _profileLoading.asStateFlow()

    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError.asStateFlow()

    // Security
    private val _securityStatus = MutableStateFlow<SecurityStatus>(SecurityStatus.Loading)
    val securityStatus: StateFlow<SecurityStatus> = _securityStatus.asStateFlow()

    private val _securityError = MutableStateFlow<String?>(null)
    val securityError: StateFlow<String?> = _securityError.asStateFlow()

    private val _bootstrapRecoveryKey = MutableStateFlow<String?>(null)
    val bootstrapRecoveryKey: StateFlow<String?> = _bootstrapRecoveryKey.asStateFlow()

    private var securityJob: Job? = null
    private var messagesJob: Job? = null
    private var typingJob: Job? = null
    private var membersJob: Job? = null
    private var roomsJob: Job? = null

    init {
        viewModelScope.launch { matrixClient.tryRestoreSession() }
        matrixClient.onBackgroundMessage = { roomId, roomName, sender, preview ->
            GrapevineNotifications.showMessageNotification(roomId, roomName, sender, preview)
        }

        viewModelScope.launch {
            loginState.collect { state ->
                roomsJob?.cancel(); roomsJob = null
                securityJob?.cancel(); securityJob = null
                if (state is LoginState.LoggedIn) {
                    roomsJob = viewModelScope.launch { collectRooms() }
                    securityJob = viewModelScope.launch { collectSecurityStatus() }
                    viewModelScope.launch { loadProfile() }
                } else {
                    _rooms.value = emptyList()
                    _securityStatus.value = SecurityStatus.Loading
                    _displayName.value = null
                    _profileAvatarUrl.value = null
                }
            }
        }
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    fun login(homeserver: String, username: String, password: String) {
        viewModelScope.launch { matrixClient.login(homeserver, username, password) }
    }

    fun logout() {
        roomsJob?.cancel(); roomsJob = null
        securityJob?.cancel(); securityJob = null
        stopRoomJobs()
        _rooms.value = emptyList()
        _messages.value = emptyList()
        _currentRoomId.value = null
        viewModelScope.launch { matrixClient.logout() }
    }

    // ── Room navigation ───────────────────────────────────────────────────────

    fun openRoom(roomId: RoomId) {
        matrixClient.activeRoomId = roomId
        _currentRoomId.value = roomId
        _messages.value = emptyList()
        _messageError.value = null
        _replyingTo.value = null
        _messageCount.value = 50
        stopRoomJobs()

        messagesJob = viewModelScope.launch {
            var lastSeenEventId: EventId? = null
            matrixClient.messagesFlow(roomId, _messageCount).collect { msgs ->
                if (msgs == null) {
                    // Only show spinner on initial load, not background refreshes
                    if (_messages.value.isEmpty()) _isLoadingMessages.value = true
                } else {
                    // Pending sends and dedup against the confirmed timeline are handled
                    // natively (outbox + timeline transactionId correlation) — no manual
                    // reconciliation needed here anymore.
                    _messages.value = msgs
                    _messageError.value = null
                    _isLoadingMessages.value = false

                    // Pending outbox entries use a synthetic "pending-<txnId>" id, not a
                    // real Matrix event id — only mark confirmed timeline events as read.
                    val latestId = msgs.lastOrNull { !it.eventId.startsWith("pending-") }
                        ?.eventId?.let { EventId(it) }
                    if (latestId != null && latestId != lastSeenEventId) {
                        lastSeenEventId = latestId
                        matrixClient.markRoomRead(roomId, latestId)
                    }
                }
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
        matrixClient.activeRoomId = null
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

    // Sending puts the message straight into Trixnity's outbox, which shows up in
    // messagesFlow (as "Sending") immediately — no local optimistic copy or manual
    // dedup/reconciliation needed anymore.
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

    // ── Security ──────────────────────────────────────────────────────────────

    fun verifySelfWithPassphrase(passphrase: String) {
        val methods = (_securityStatus.value as? SecurityStatus.NeedVerification)?.methods ?: return
        _securityError.value = null
        viewModelScope.launch {
            val result = matrixClient.verifySelfWithPassphrase(methods, passphrase)
            if (result.isFailure) {
                _securityError.value = result.exceptionOrNull()?.message ?: "Verification failed"
            }
        }
    }

    fun verifySelfWithRecoveryKey(recoveryKey: String) {
        val methods = (_securityStatus.value as? SecurityStatus.NeedVerification)?.methods ?: return
        _securityError.value = null
        viewModelScope.launch {
            val result = matrixClient.verifySelfWithRecoveryKey(methods, recoveryKey)
            if (result.isFailure) {
                _securityError.value = result.exceptionOrNull()?.message ?: "Verification failed"
            }
        }
    }

    fun bootstrapCrossSigning(passphrase: String) {
        _securityError.value = null
        viewModelScope.launch {
            val result = matrixClient.bootstrapCrossSigningWithPassphrase(passphrase)
            if (result.isSuccess) {
                _bootstrapRecoveryKey.value = result.getOrNull()
            } else {
                _securityError.value = result.exceptionOrNull()?.message ?: "Security setup failed"
            }
        }
    }

    fun clearSecurityError() { _securityError.value = null }
    fun clearBootstrapRecoveryKey() { _bootstrapRecoveryKey.value = null }

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

    // ── Invite ────────────────────────────────────────────────────────────────

    fun inviteUser(userIdString: String) {
        val roomId = _currentRoomId.value ?: return
        _inviteError.value = null
        viewModelScope.launch {
            try {
                val trimmed = userIdString.trim().let {
                    if (it.startsWith("@")) it else "@$it"
                }
                matrixClient.inviteUser(roomId, UserId(trimmed))
            } catch (e: Exception) {
                _inviteError.value = e.message ?: "Failed to invite user"
            }
        }
    }

    fun clearInviteError() { _inviteError.value = null }

    // ── Profile ───────────────────────────────────────────────────────────────

    fun loadProfile() {
        _profileLoading.value = true
        viewModelScope.launch {
            val (name, avatarMxc) = matrixClient.getProfile()
            _displayName.value = name
            _profileAvatarUrl.value = avatarMxc?.let { matrixClient.mxcToHttp(it) }
            _profileLoading.value = false
        }
    }

    fun updateDisplayName(name: String) {
        _profileError.value = null
        viewModelScope.launch {
            try {
                matrixClient.setDisplayName(name)
                _displayName.value = name
            } catch (e: Exception) {
                _profileError.value = e.message ?: "Failed to update display name"
            }
        }
    }

    fun clearProfileError() { _profileError.value = null }

    // ── Media ─────────────────────────────────────────────────────────────────

    fun sendMedia(bytes: ByteArray, filename: String, mimeType: String) {
        val roomId = _currentRoomId.value ?: return
        viewModelScope.launch {
            try {
                matrixClient.sendMedia(roomId, bytes, filename, mimeType)
            } catch (e: Exception) {
                println("GV-DEBUG sendMedia error: $e")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun mxcToHttp(mxcUri: String): String? = matrixClient.mxcToHttp(mxcUri)

    private fun stopRoomJobs() {
        messagesJob?.cancel(); messagesJob = null
        typingJob?.cancel();   typingJob = null
        membersJob?.cancel();  membersJob = null
    }

    private suspend fun collectSecurityStatus() {
        matrixClient.selfVerificationMethodsFlow().collect { methods ->
            _securityStatus.value = when (methods) {
                is VerificationService.SelfVerificationMethods.AlreadyCrossSigned ->
                    SecurityStatus.AlreadyVerified

                is VerificationService.SelfVerificationMethods.CrossSigningEnabled ->
                    SecurityStatus.NeedVerification(
                        methods = methods.methods,
                        hasPassphrase = methods.methods.any {
                            it is SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase
                        },
                        hasRecoveryKey = methods.methods.any {
                            it is SelfVerificationMethod.AesHmacSha2RecoveryKey
                        },
                    )

                is VerificationService.SelfVerificationMethods.NoCrossSigningEnabled ->
                    SecurityStatus.NoCrossSigning

                else -> SecurityStatus.Loading
            }
        }
    }

    private suspend fun collectRooms() {
        matrixClient.roomsWithDetailsFlow().collect { list ->
            if (list == null) {
                _roomsLoading.value = true
            } else {
                _roomsLoading.value = false
                _rooms.value = list.sortedByDescending { it.lastActivityMs }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        roomsJob?.cancel()
        securityJob?.cancel()
        stopRoomJobs()
    }
}
