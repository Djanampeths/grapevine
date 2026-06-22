package com.grapevine.grapevine.matrix

import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.login
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.toFlowList
import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.DirectoryVisibility
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

class GrapevineMatrixClient(private val scope: CoroutineScope) {

    private var client: MatrixClient? = null
    private var baseHttpUrl: String = ""

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
                scope.launch { restored.startSync() }
                _loginState.value = LoginState.LoggedIn(userId = restored.userId.full)
            } else {
                _loginState.value = LoginState.LoggedOut
            }
        } catch (e: Exception) {
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
            scope.launch { newClient.startSync() }
            _loginState.value = LoginState.LoggedIn(userId = newClient.userId.full)
        } catch (e: Exception) {
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

    fun roomsWithDetailsFlow(): Flow<List<RoomInfo>> = flow {
        val c = client ?: run { emit(emptyList()); return@flow }
        val allRooms = c.room.getAll()
        // Collect every time the room map changes (rooms added/removed),
        // and also poll periodically to catch unread-count / name updates.
        var lastMap: Map<*, *> = emptyMap<Any, Any>()
        while (true) {
            val roomMap = allRooms.value
            val rooms = roomMap.values.mapNotNull { roomFlow -> roomFlow.value?.toRoomInfo() }
            // Only emit when something actually changed to avoid spurious recompositions.
            if (roomMap !== lastMap) {
                lastMap = roomMap
                emit(rooms)
            }
            delay(1_500)
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

    // ── Room members ──────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    fun roomMembersFlow(roomId: RoomId): Flow<List<MemberInfo>> {
        val c = client ?: return flowOf(emptyList())
        return c.room.getAllState(roomId, MemberEventContent::class)
            .map { memberMap ->
                memberMap?.entries?.mapNotNull { (userId, event) ->
                    val content = event?.content ?: return@mapNotNull null
                    MemberInfo(
                        userId = "@$userId",
                        displayName = content.displayName,
                        avatarUrl = content.avatarUrl?.let { mxcToHttp(it) },
                    )
                } ?: emptyList()
            }
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    fun messagesFlow(roomId: RoomId, countFlow: StateFlow<Int>): Flow<List<MessageInfo>> {
        val c = client ?: return flowOf(emptyList())
        return c.room.getLastTimelineEvents(roomId)
            .toFlowList(countFlow)
            .flatMapLatest { eventFlows: List<Flow<TimelineEvent>> ->
                if (eventFlows.isEmpty()) flowOf(emptyList())
                else combine(eventFlows) { events: Array<TimelineEvent> ->
                    events.mapNotNull { it.toMessageInfo() }.reversed()
                }
            }
    }

    suspend fun sendMessage(roomId: RoomId, text: String) {
        client?.api?.rooms?.sendMessageEvent(
            roomId = roomId,
            eventContent = RoomMessageEventContent.TextMessageEventContent(body = text),
            txnId = txnId(),
        )
    }

    suspend fun sendReply(roomId: RoomId, replyToEventId: EventId, text: String) {
        client?.api?.rooms?.sendMessageEvent(
            roomId = roomId,
            eventContent = RoomMessageEventContent.TextMessageEventContent(
                body = text,
                relatesTo = RelatesTo.Reply(RelatesTo.ReplyTo(replyToEventId)),
            ),
            txnId = txnId(),
        )
    }

    suspend fun editMessage(roomId: RoomId, eventId: EventId, newText: String) {
        client?.api?.rooms?.sendMessageEvent(
            roomId = roomId,
            eventContent = RoomMessageEventContent.TextMessageEventContent(
                body = "* $newText",
                relatesTo = RelatesTo.Replace(
                    eventId = eventId,
                    newContent = RoomMessageEventContent.TextMessageEventContent(body = newText),
                ),
            ),
            txnId = txnId(),
        )
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun mxcToHttp(mxcUri: String): String? {
        if (!mxcUri.startsWith("mxc://")) return null
        return "$baseHttpUrl/_matrix/media/v3/download/${mxcUri.removePrefix("mxc://")}"
    }

    private fun txnId() = "gv-${Clock.System.now().toEpochMilliseconds()}"

    private fun TimelineEvent.toMessageInfo(): MessageInfo? {
        val msgEvent = event as? Event.MessageEvent<*> ?: return null
        val content = msgEvent.content as? RoomMessageEventContent ?: return null
        val base = MessageInfo(
            eventId = eventId.full,
            senderId = msgEvent.sender.full,
            body = content.body,
            timestamp = msgEvent.originTimestamp,
            replyToEventId = (content.relatesTo as? RelatesTo.Reply)
                ?.replyTo?.eventId?.full,
        )
        return when (content) {
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
}

internal fun Room.toRoomInfo() = RoomInfo(
    id = roomId,
    name = name?.explicitName,
    topic = null,
    memberCount = 0,
    unreadCount = unreadMessageCount,
    isDirect = isDirect,
)

sealed class LoginState {
    object LoggedOut : LoginState()
    object Loading : LoginState()
    data class LoggedIn(val userId: String) : LoginState()
    data class Error(val message: String) : LoginState()
}
