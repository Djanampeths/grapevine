package com.grapevine.grapevine.matrix

import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.TopicEventContent

class GrapevineMatrixClient(private val scope: CoroutineScope) {

    private var client: MatrixClient? = null
    private var baseHttpUrl: String = ""

    private val _loginState = MutableStateFlow<LoginState>(LoginState.LoggedOut)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

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
                initialDeviceDisplayName = "Grapevine Desktop",
            ).getOrThrow()

            client = newClient
            baseHttpUrl = serverUrl.toString().trimEnd('/')
            scope.launch { newClient.startSync() }
            _loginState.value = LoginState.LoggedIn(userId = newClient.userId.full)
        } catch (e: Exception) {
            _loginState.value = LoginState.Error(e.message ?: "Login failed")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun roomsWithDetailsFlow(): Flow<List<RoomInfo>> {
        val c = client ?: return flowOf(emptyList())
        return c.room.getAll().flatMapLatest { roomMap ->
            if (roomMap.isEmpty()) {
                flowOf(emptyList())
            } else {
                val roomInfoFlows: List<Flow<RoomInfo?>> = roomMap.map { (roomId, roomFlow) ->
                    combine(
                        roomFlow,
                        c.room.getState(roomId, "", TopicEventContent::class),
                        c.room.getAllState(roomId, MemberEventContent::class),
                    ) { room, topicEvent, memberMap ->
                        room?.toRoomInfo(
                            topic = topicEvent?.content?.topic,
                            memberCount = memberMap?.size ?: 0,
                        )
                    }
                }
                combine(roomInfoFlows) { it.filterNotNull().toList() }
            }
        }
    }

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
            txnId = "gv-${Clock.System.now().toEpochMilliseconds()}",
        )
    }

    suspend fun markRoomRead(roomId: RoomId, eventId: EventId) {
        client?.api?.rooms?.setReadMarkers(
            roomId = roomId,
            fullyRead = eventId,
            read = eventId,
        )
    }

    suspend fun logout() {
        client?.cancelSync(false)
        client = null
        baseHttpUrl = ""
        _loginState.value = LoginState.LoggedOut
    }

    // Converts mxc://server/mediaId → https://homeserver/_matrix/media/v3/download/server/mediaId
    private fun mxcToHttp(mxcUri: String): String? {
        if (!mxcUri.startsWith("mxc://")) return null
        val path = mxcUri.removePrefix("mxc://")
        return "$baseHttpUrl/_matrix/media/v3/download/$path"
    }

    private fun TimelineEvent.toMessageInfo(): MessageInfo? {
        val msgEvent = event as? Event.MessageEvent<*> ?: return null
        val content = msgEvent.content as? RoomMessageEventContent ?: return null
        val base = MessageInfo(
            eventId = eventId.full,
            senderId = msgEvent.sender.full,
            body = content.body,
            timestamp = msgEvent.originTimestamp,
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

            else -> null // drop verification requests and other non-chat events
        }
    }
}

internal fun Room.toRoomInfo(topic: String?, memberCount: Int) = RoomInfo(
    id = roomId,
    name = name?.explicitName,
    topic = topic,
    memberCount = memberCount,
    unreadCount = unreadMessageCount,
)

sealed class LoginState {
    object LoggedOut : LoginState()
    object Loading : LoginState()
    data class LoggedIn(val userId: String) : LoginState()
    data class Error(val message: String) : LoginState()
}
