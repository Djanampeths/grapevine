package com.grapevine.grapevine.matrix

import io.ktor.http.ContentType
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.key
import net.folivo.trixnity.client.login
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.flatten
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.client.room.message.audio
import net.folivo.trixnity.client.room.message.file
import net.folivo.trixnity.client.room.message.image
import net.folivo.trixnity.client.room.message.reply
import net.folivo.trixnity.client.room.message.replace
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.room.message.video
import net.folivo.trixnity.client.room.toFlowList
import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.client.verification
import net.folivo.trixnity.client.verification.SelfVerificationMethod
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.DirectoryVisibility
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.TopicEventContent
import net.folivo.trixnity.core.toByteArrayFlow
import kotlin.time.Duration.Companion.seconds

class GrapevineMatrixClient(private val scope: CoroutineScope) {

    private var client: MatrixClient? = null
    private var baseHttpUrl: String = ""

    var activeRoomId: RoomId? = null

    private val _loginState = MutableStateFlow<LoginState>(LoginState.LoggedOut)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    // ── Session ──────────────────────────────────────────────────────────────

    suspend fun tryRestoreSession() {
        _loginState.value = LoginState.Loading
        try {
            val restored = MatrixClient.fromStore(
                repositoriesModule = createRepositoriesModule(),
                mediaStore = createMediaStore(),
                scope = scope,
            ).getOrNull()
            if (restored != null) {
                client = restored
                baseHttpUrl = restored.di.get<AccountStore>().baseUrl.value
                    ?.toString()?.trimEnd('/') ?: ""
                println("GV-DEBUG restore success: ${restored.userId.full}")
                scope.launch {
                    try { restored.startSync() }
                    catch (e: Exception) { println("GV-DEBUG restore startSync error: $e") }
                }
                startBackgroundNotifications(restored)
                _loginState.value = LoginState.LoggedIn(userId = restored.userId.full)
            } else {
                println("GV-DEBUG restore: no stored session")
                _loginState.value = LoginState.LoggedOut
            }
        } catch (e: Exception) {
            println("GV-DEBUG restore error: $e")
            _loginState.value = LoginState.LoggedOut
        }
    }

    suspend fun login(homeserver: String, username: String, password: String) {
        _loginState.value = LoginState.Loading
        try {
            val serverUrl = Url(
                if (homeserver.startsWith("http")) homeserver else "https://$homeserver"
            )
            val newClient = MatrixClient.login(
                baseUrl = serverUrl,
                identifier = IdentifierType.User(username),
                password = password,
                repositoriesModule = createRepositoriesModule(),
                mediaStore = createMediaStore(),
                scope = scope,
                initialDeviceDisplayName = "Grapevine",
            ).getOrThrow()
            client = newClient
            baseHttpUrl = serverUrl.toString().trimEnd('/')
            println("GV-DEBUG login success: ${newClient.userId.full}, starting sync")
            scope.launch {
                try { newClient.startSync() }
                catch (e: Exception) { println("GV-DEBUG startSync error: $e") }
            }
            startBackgroundNotifications(newClient)
            _loginState.value = LoginState.LoggedIn(userId = newClient.userId.full)
        } catch (e: Exception) {
            println("GV-DEBUG login error: $e")
            _loginState.value = LoginState.Error(e.message ?: "Login failed")
        }
    }

    suspend fun logout() {
        client?.cancelSync(false)
        client = null
        baseHttpUrl = ""
        _loginState.value = LoginState.LoggedOut
    }

    // ── Rooms ─────────────────────────────────────────────────────────────────

    val currentUserId: String? get() = client?.userId?.full

    // Native store-backed room list — live-updates automatically as the ongoing
    // startSync() loop processes rooms/state/timeline, no manual polling needed.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun roomsWithDetailsFlow(): Flow<List<RoomInfo>?> {
        val c = client ?: return flowOf(emptyList())
        // Trixnity's store keeps a Room entry after you leave it (membership just flips to
        // LEAVE/BAN rather than being deleted), so it must be filtered out here or a left
        // room would linger in the list forever.
        val merged: Flow<List<RoomInfo>?> = c.room.getAll().flatten()
            .map { rooms -> rooms.filter { it.membership == Membership.JOIN } }
            .flatMapLatest { rooms ->
                if (rooms.isEmpty()) flowOf(emptyList())
                else combine(rooms.map { roomInfoFlow(c, it) }) { it.toList() }
            }
        return merged.onStart { emit(null) }
            .map { list -> list?.sortedByDescending { it.lastActivityMs } }
    }

    private fun roomInfoFlow(c: MatrixClient, room: Room): Flow<RoomInfo> {
        // Matrix DM rooms normally have no m.room.name — Trixnity already resolves this via
        // the room summary "heroes" (representative other members), so fall back to the
        // first hero's live display name instead of guessing it ourselves.
        val nameFlow: Flow<String?> = room.name?.explicitName?.let { flowOf(it) }
            ?: room.name?.heroes?.firstOrNull()?.let { hero ->
                c.user.getById(room.roomId, hero).map { it?.name }
            } ?: flowOf(null)
        val topicFlow = c.room.getState<TopicEventContent>(room.roomId).map { it?.content?.topic }
        val lastEventFlow: Flow<TimelineEvent?> = room.lastEventId
            ?.let { c.room.getTimelineEvent(room.roomId, it) { decryptionTimeout = 5.seconds } }
            ?: flowOf(null)
        return combine(nameFlow, topicFlow, lastEventFlow) { name, topic, lastEvent ->
            RoomInfo(
                id = room.roomId,
                name = name,
                topic = topic,
                memberCount = (room.name?.otherUsersCount ?: 0L).toInt() + 1,
                unreadCount = room.unreadMessageCount,
                lastMessage = lastEvent?.previewText(),
                lastActivityMs = lastEvent?.event?.originTimestamp ?: 0L,
                isDirect = room.isDirect,
            )
        }
    }

    suspend fun joinRoom(roomId: RoomId) {
        client?.api?.rooms?.joinRoom(roomId = roomId)
    }

    suspend fun joinRoomByAlias(alias: String) {
        val aliasId = RoomAliasId(if (alias.startsWith("#")) alias else "#$alias")
        client?.api?.rooms?.joinRoom(roomAliasId = aliasId)
    }

    suspend fun createRoom(name: String, topic: String) {
        client?.api?.rooms?.createRoom(
            visibility = DirectoryVisibility.PRIVATE,
            name = name.ifBlank { null },
            topic = topic.ifBlank { null },
            preset = CreateRoom.Request.Preset.PRIVATE,
        )
    }

    suspend fun leaveRoom(roomId: RoomId) {
        client?.api?.rooms?.leaveRoom(roomId = roomId)
    }

    suspend fun inviteUser(roomId: RoomId, userId: UserId) {
        client?.api?.rooms?.inviteUser(roomId = roomId, userId = userId)
    }

    // ── Room members ──────────────────────────────────────────────────────────

    // Native store-backed member list (UserService), live-updating like everything else now
    // that the underlying room store populates correctly.
    fun roomMembersFlow(roomId: RoomId): Flow<List<MemberInfo>> {
        val c = client ?: return flowOf(emptyList())
        return c.user.getAll(roomId).map { users ->
            users?.map { user ->
                MemberInfo(
                    userId = user.userId.full,
                    displayName = user.name,
                    avatarUrl = user.event.content.avatarUrl?.let { mxcToHttp(it) },
                )
            }?.sortedBy { it.displayOrUser } ?: emptyList()
        }
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    // Callback invoked when a message arrives in a room not currently active (for notifications).
    var onBackgroundMessage: ((roomId: String, roomName: String, sender: String, preview: String) -> Unit)? = null

    // Live stream of every new timeline event from the moment sync starts (Trixnity resolves
    // decryption for each one internally, bounded by decryptionTimeout, before emitting) — the
    // natural source for "did a message just arrive" notifications, since it's driven by the
    // ongoing sync stream rather than polling.
    private fun startBackgroundNotifications(c: MatrixClient) {
        scope.launch {
            try {
                c.room.getTimelineEventsFromNowOn(decryptionTimeout = 10.seconds).collect { timelineEvent ->
                    val roomId = timelineEvent.roomId
                    if (roomId == activeRoomId) return@collect
                    val senderId = timelineEvent.event.sender
                    if (senderId == c.userId) return@collect
                    val info = timelineEvent.toMessageInfo() ?: return@collect
                    val roomName = resolveNotificationRoomName(c, roomId)
                    val senderName = c.user.getById(roomId, senderId).first()?.name
                        ?: senderId.full.removePrefix("@").substringBefore(":")
                    onBackgroundMessage?.invoke(roomId.full, roomName, senderName, info.body)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                println("GV-DEBUG background notifications error: $e")
            }
        }
    }

    private suspend fun resolveNotificationRoomName(c: MatrixClient, roomId: RoomId): String {
        val room = c.room.getById(roomId).first()
        room?.name?.explicitName?.let { return it }
        room?.name?.heroes?.firstOrNull()?.let { hero ->
            c.user.getById(roomId, hero).first()?.name?.let { return it }
        }
        return roomId.full
    }

    // Native timeline (decrypts automatically, no manual RoomEventDecryptionService calls needed)
    // merged with the outbox (pending sends). An outbox entry is dropped once its transactionId
    // shows up on a timeline event (the local-echo mechanism) — Trixnity's own outbox handler only
    // clears it ~10s after sending, which would otherwise show a duplicate in that window.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun messagesFlow(roomId: RoomId, countFlow: StateFlow<Int>): Flow<List<MessageInfo>?> {
        val c = client ?: return flowOf(emptyList())
        val timelineFlow: Flow<List<TimelineEvent>> = c.room
            .getLastTimelineEvents(roomId) { decryptionTimeout = 10.seconds }
            .toFlowList(countFlow)
            .flatMapLatest { flows -> if (flows.isEmpty()) flowOf(emptyList()) else combine(flows) { it.toList() } }
        val outboxFlow: Flow<List<RoomOutboxMessage<*>>> = c.room.getOutbox()
            .map { all -> all.filter { it.roomId == roomId } }
        val merged: Flow<List<MessageInfo>?> = combine(timelineFlow, outboxFlow) { timelineEvents, outbox ->
            val sentTxnIds = timelineEvents.mapNotNull { it.event.unsigned?.transactionId }.toSet()
            val timelineMessages = timelineEvents.mapNotNull { it.toMessageInfo() }
            val pendingMessages = outbox
                .filter { it.transactionId !in sentTxnIds }
                .mapNotNull { it.toMessageInfo() }
            (timelineMessages + pendingMessages).sortedBy { it.timestamp }
        }
        return merged.onStart { emit(null) }
    }

    suspend fun sendMessage(roomId: RoomId, text: String) {
        client?.room?.sendMessage(roomId) { text(text) }
    }

    suspend fun sendReply(roomId: RoomId, replyToEventId: EventId, text: String) {
        client?.room?.sendMessage(roomId) {
            text(text)
            reply(replyToEventId, null)
        }
    }

    suspend fun editMessage(roomId: RoomId, eventId: EventId, newText: String) {
        client?.room?.sendMessage(roomId) {
            text(newText)
            replace(eventId)
        }
    }

    suspend fun deleteMessage(roomId: RoomId, eventId: EventId) {
        client?.api?.rooms?.redactEvent(
            roomId = roomId,
            eventId = eventId,
            txnId = txnId(),
        )
    }

    suspend fun markRoomRead(roomId: RoomId, eventId: EventId) {
        client?.api?.rooms?.setReadMarkers(
            roomId = roomId,
            fullyRead = eventId,
            read = eventId,
        )
    }

    // ── Typing ────────────────────────────────────────────────────────────────

    fun typingUsersFlow(roomId: RoomId): Flow<List<String>> {
        val c = client ?: return flowOf(emptyList())
        val myUserId = c.userId.full
        return c.room.usersTyping
            .map { map ->
                map[roomId]?.users
                    ?.map { it.full }
                    ?.filter { it != myUserId }
                    ?: emptyList()
            }
    }

    suspend fun setTyping(roomId: RoomId, isTyping: Boolean) {
        val c = client ?: return
        c.api.rooms.setTyping(
            roomId = roomId,
            userId = c.userId,
            typing = isTyping,
            timeout = if (isTyping) 30_000L else null,
        )
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    suspend fun getProfile(): Pair<String?, String?> {
        val c = client ?: return null to null
        return try {
            val resp = c.api.users.getProfile(c.userId).getOrThrow()
            resp.displayName to resp.avatarUrl
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null to null
        }
    }

    suspend fun setDisplayName(name: String) {
        val c = client ?: return
        try {
            c.api.users.setDisplayName(userId = c.userId, displayName = name)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    // Media goes through the native outbox/MessageBuilder pipeline, same as text — this is
    // what makes it encryption-aware (image/audio/video/file builders check the room's
    // encryption state and prepare an EncryptedFile automatically) and handles the actual
    // upload asynchronously via OutboxMessageEventHandler once queued, no manual upload call
    // or mxc:// URI plumbing needed on our side.
    suspend fun sendMedia(roomId: RoomId, bytes: ByteArray, filename: String, mimeType: String) {
        val contentType = ContentType.parse(mimeType)
        val byteFlow = bytes.toByteArrayFlow()
        client?.room?.sendMessage(roomId) {
            when {
                mimeType.startsWith("image/") -> image(body = filename, image = byteFlow, type = contentType, size = bytes.size)
                mimeType.startsWith("audio/") -> audio(body = filename, audio = byteFlow, type = contentType, size = bytes.size)
                mimeType.startsWith("video/") -> video(body = filename, video = byteFlow, type = contentType, size = bytes.size)
                else -> file(body = filename, file = byteFlow, type = contentType, size = bytes.size, name = filename)
            }
        }
    }

    // ── Security / Cross-signing ──────────────────────────────────────────────

    fun selfVerificationMethodsFlow(): Flow<VerificationService.SelfVerificationMethods?> {
        val c = client ?: return flowOf(null)
        return c.verification.getSelfVerificationMethods()
    }

    // Bootstrap cross-signing from scratch (only call when NoCrossSigningEnabled).
    // Returns the human-readable recovery key on success so the UI can display it.
    // Note: result is Result<UIA<Unit>> — we treat any success as done (UIAA handled by server).
    suspend fun bootstrapCrossSigningWithPassphrase(passphrase: String): Result<String> {
        val c = client ?: return Result.failure(IllegalStateException("Not logged in"))
        return try {
            val bootstrap = c.key.bootstrapCrossSigningFromPassphrase(passphrase)
            bootstrap.result.fold(
                onSuccess = { Result.success(bootstrap.recoveryKey) },
                onFailure = { Result.failure(it) },
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    // Verify this session using a security passphrase (when CrossSigningEnabled).
    suspend fun verifySelfWithPassphrase(
        methods: Set<SelfVerificationMethod>,
        passphrase: String,
    ): Result<Unit> {
        val method = methods
            .filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase>()
            .firstOrNull()
            ?: return Result.failure(IllegalStateException("Passphrase method not available"))
        return try {
            method.verify(passphrase)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    // Verify this session using a raw recovery key string (when CrossSigningEnabled).
    suspend fun verifySelfWithRecoveryKey(
        methods: Set<SelfVerificationMethod>,
        recoveryKey: String,
    ): Result<Unit> {
        val method = methods
            .filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>()
            .firstOrNull()
            ?: return Result.failure(IllegalStateException("Recovery key method not available"))
        return try {
            method.verify(recoveryKey)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun mxcToHttp(mxcUri: String): String? {
        if (!mxcUri.startsWith("mxc://")) return null
        return "$baseHttpUrl/_matrix/media/v3/download/${mxcUri.removePrefix("mxc://")}"
    }

    private var txnSeq = 0L
    private fun txnId() = "gv-${++txnSeq}"

    private fun TimelineEvent.previewText(): String? {
        val result = content ?: return null
        val raw = result.getOrNull() ?: return "🔒 Encrypted message"
        return (raw as? RoomMessageEventContent)?.body
    }

    private fun TimelineEvent.toMessageInfo(): MessageInfo? {
        val roomEvent = event
        val contentResult = content
            ?: return MessageInfo(
                eventId = eventId.full,
                senderId = roomEvent.sender.full,
                body = "🔒 Decrypting…",
                timestamp = roomEvent.originTimestamp,
                type = MessageType.Encrypted,
            )
        val rawContent = contentResult.getOrNull()
            ?: return MessageInfo(
                eventId = eventId.full,
                senderId = roomEvent.sender.full,
                body = "🔒 Unable to decrypt message",
                timestamp = roomEvent.originTimestamp,
                type = MessageType.Encrypted,
            )
        val messageContent = rawContent as? RoomMessageEventContent ?: return null
        val base = MessageInfo(
            eventId = eventId.full,
            senderId = roomEvent.sender.full,
            body = messageContent.body,
            timestamp = roomEvent.originTimestamp,
            replyToEventId = (messageContent.relatesTo as? RelatesTo.Reply)
                ?.replyTo?.eventId?.full,
        )
        return messageContentToInfo(base, messageContent)
    }

    private fun RoomOutboxMessage<*>.toMessageInfo(): MessageInfo? {
        val messageContent = content as? RoomMessageEventContent ?: return null
        val base = MessageInfo(
            eventId = "pending-$transactionId",
            senderId = client?.userId?.full ?: "",
            body = messageContent.body,
            timestamp = System.currentTimeMillis(),
            status = when {
                sentAt != null -> MessageStatus.Sent
                reachedMaxRetryCount -> MessageStatus.Failed
                else -> MessageStatus.Sending
            },
            replyToEventId = (messageContent.relatesTo as? RelatesTo.Reply)
                ?.replyTo?.eventId?.full,
        )
        return messageContentToInfo(base, messageContent)
    }

    private fun messageContentToInfo(base: MessageInfo, content: RoomMessageEventContent): MessageInfo? =
        when (content) {
            is RoomMessageEventContent.TextMessageEventContent,
            is RoomMessageEventContent.NoticeMessageEventContent,
            is RoomMessageEventContent.EmoteMessageEventContent -> base

            is RoomMessageEventContent.ImageMessageEventContent -> base.copy(
                type = MessageType.Image,
                mediaUrl = content.url?.let { mxcToHttp(it) },
                fileName = content.body,
                mimeType = content.info?.mimeType,
                fileSize = content.info?.size,
            )
            is RoomMessageEventContent.FileMessageEventContent -> base.copy(
                type = MessageType.File,
                mediaUrl = content.url?.let { mxcToHttp(it) },
                fileName = content.fileName ?: content.body,
                mimeType = content.info?.mimeType,
                fileSize = content.info?.size,
            )
            is RoomMessageEventContent.AudioMessageEventContent -> base.copy(
                type = MessageType.Audio,
                mediaUrl = content.url?.let { mxcToHttp(it) },
                fileName = content.body,
                mimeType = content.info?.mimeType,
                fileSize = content.info?.size,
            )
            is RoomMessageEventContent.VideoMessageEventContent -> base.copy(
                type = MessageType.Video,
                mediaUrl = content.url?.let { mxcToHttp(it) },
                fileName = content.body,
                mimeType = content.info?.mimeType,
                fileSize = content.info?.size,
            )
            else -> null
        }
}

sealed class LoginState {
    object LoggedOut : LoginState()
    object Loading : LoginState()
    data class LoggedIn(val userId: String) : LoginState()
    data class Error(val message: String) : LoginState()
}
