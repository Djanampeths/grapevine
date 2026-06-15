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
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.login
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

class GrapevineMatrixClient(private val scope: CoroutineScope) {

    private var client: MatrixClient? = null

    private val _loginState = MutableStateFlow<LoginState>(LoginState.LoggedOut)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

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
                mediaStoreModule = createMediaStoreModule(),
                coroutineContext = scope.coroutineContext,
            ).getOrThrow()

            client = newClient
            _loginState.value = LoginState.LoggedIn(userId = newClient.userId.full)
        } catch (e: Exception) {
            _loginState.value = LoginState.Error(e.message ?: "Login failed")
        }
    }

    // flatMapLatest is experimental in some coroutines versions — opt-in required.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun roomsFlow(): Flow<List<Room>> {
        val c = client ?: return flowOf(emptyList())
        return c.room.getAll().flatMapLatest { roomMap ->
            if (roomMap.isEmpty()) {
                flowOf(emptyList())
            } else {
                val flows: List<Flow<Room?>> = roomMap.values.toList()
                combine(flows) { rooms: Array<Room?> -> rooms.filterNotNull() }
            }
        }
    }

    // Use the REST API directly to send a message.
    // `api.room` (not `api.rooms`) is the current name in Trixnity 3.6.
    suspend fun sendMessage(roomId: RoomId, text: String) {
        client?.api?.room?.sendMessageEvent(
            roomId = roomId,
            eventContent = RoomMessageEventContent.TextBased.Text(body = text),
            txnId = "gv-${System.currentTimeMillis()}",
        )
    }

    // `getTimelineEvents` on the room service is an internal sync method.
    // The correct public API for reading message history is `api.room.getEvents`,
    // which makes a direct paginated REST call to the homeserver.
    suspend fun getMessages(roomId: RoomId): List<MessageInfo> {
        // getEvents returns Result<GetEvents.Response> — unwrap with getOrNull().
        // `from` is non-null in Trixnity 3.6; pass "" to get the latest chunk.
        val response = client?.api?.room?.getEvents(
            roomId = roomId,
            from = "",
            dir = GetEvents.Direction.BACKWARDS,
            limit = 50,
        )?.getOrNull() ?: return emptyList()

        // REST API events are ClientEvent<*> where the id field is `eventId` (not `id`).
        return response.chunk.orEmpty()
            .mapNotNull { event ->
                // content is a direct field in Trixnity's REST response model, not Result-wrapped.
                // `as?` is a safe cast — returns null instead of throwing if the type doesn't match.
                val content = event.content as? RoomMessageEventContent.TextBased.Text
                    ?: return@mapNotNull null
                MessageInfo(
                    eventId = event.id.full,
                    senderId = event.sender.full,
                    body = content.body,
                    timestamp = event.originTimestamp,
                )
            }
            .reversed()
    }

    suspend fun logout() {
        client?.cancelSync()
        client = null
        _loginState.value = LoginState.LoggedOut
    }
}

sealed class LoginState {
    object LoggedOut : LoginState()
    object Loading : LoginState()
    data class LoggedIn(val userId: String) : LoginState()
    data class Error(val message: String) : LoginState()
}
