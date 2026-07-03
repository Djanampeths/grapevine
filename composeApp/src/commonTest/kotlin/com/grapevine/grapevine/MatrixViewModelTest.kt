package com.grapevine.grapevine

import com.grapevine.grapevine.matrix.LoginState
import com.grapevine.grapevine.matrix.MemberInfo
import com.grapevine.grapevine.matrix.MessageInfo
import com.grapevine.grapevine.matrix.MessageType
import com.grapevine.grapevine.matrix.RoomInfo
import com.grapevine.grapevine.ui.formatFileSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.model.RoomId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Fake Matrix client — simulates a Matrix server without any real network.
//
// In real code GrapevineMatrixClient talks to a homeserver.
// Here we stub it so tests are instant and reproducible.
// ---------------------------------------------------------------------------
class FakeMatrixSession {

    // MutableStateFlow — a StateFlow you can push new values into.
    // Tests change _loginState.value to simulate server responses.
    private val _loginState = MutableStateFlow<LoginState>(LoginState.LoggedOut)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _rooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val rooms: StateFlow<List<RoomInfo>> = _rooms.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageInfo>>(emptyList())
    val messages: StateFlow<List<MessageInfo>> = _messages.asStateFlow()

    // Simulate a successful login
    fun simulateLogin(userId: String) {
        _loginState.value = LoginState.LoggedIn(userId)
        _rooms.value = listOf(
            RoomInfo(RoomId("!general:matrix.org"), "General", "Welcome!", 42),
            RoomInfo(RoomId("!dev:matrix.org"), "Dev", "KMP discussion", 8),
        )
    }

    // Simulate a failed login (wrong password, server unreachable, etc.)
    fun simulateLoginError(message: String) {
        _loginState.value = LoginState.Error(message)
    }

    fun simulateLogout() {
        _loginState.value = LoginState.LoggedOut
        _rooms.value = emptyList()
        _messages.value = emptyList()
    }

    fun simulateMessagesLoaded(roomId: RoomId) {
        _messages.value = listOf(
            MessageInfo("evt1", "@alice:matrix.org", "Hey there!", 1000L),
            MessageInfo("evt2", "@bob:matrix.org", "Hi Alice!", 2000L),
            MessageInfo("evt3", "@alice:matrix.org", "How's the KMP project going?", 3000L),
        )
    }
}

// ---------------------------------------------------------------------------
// Tests — each function is an independent scenario.
// `runTest` is a coroutine test runner that advances virtual time instantly,
// so even delay() / suspend functions complete without real waiting.
// ---------------------------------------------------------------------------
@OptIn(ExperimentalCoroutinesApi::class)
class MatrixSessionTest {

    @Test
    fun `initial state is LoggedOut`() {
        val session = FakeMatrixSession()
        assertIs<LoginState.LoggedOut>(session.loginState.value)
    }

    @Test
    fun `login success sets LoggedIn with correct userId`() = runTest {
        val session = FakeMatrixSession()
        session.simulateLogin("@testuser:matrix.org")

        val state = session.loginState.value
        assertIs<LoginState.LoggedIn>(state)
        assertEquals("@testuser:matrix.org", state.userId)
    }

    @Test
    fun `login success populates room list`() = runTest {
        val session = FakeMatrixSession()
        session.simulateLogin("@testuser:matrix.org")

        assertEquals(2, session.rooms.value.size)
        assertEquals("General", session.rooms.value[0].name)
    }

    @Test
    fun `login error carries the error message`() = runTest {
        val session = FakeMatrixSession()
        session.simulateLoginError("Invalid password")

        val state = session.loginState.value
        assertIs<LoginState.Error>(state)
        assertEquals("Invalid password", state.message)
    }

    @Test
    fun `logout clears all state`() = runTest {
        val session = FakeMatrixSession()
        session.simulateLogin("@testuser:matrix.org")
        session.simulateLogout()

        assertIs<LoginState.LoggedOut>(session.loginState.value)
        assertTrue(session.rooms.value.isEmpty())
    }

    @Test
    fun `messages load for a room`() = runTest {
        val session = FakeMatrixSession()
        session.simulateLogin("@testuser:matrix.org")

        val roomId = RoomId("!general:matrix.org")
        session.simulateMessagesLoaded(roomId)

        val messages = session.messages.value
        assertEquals(3, messages.size)
        assertEquals("Hey there!", messages[0].body)
        assertEquals("@alice:matrix.org", messages[0].senderId)
    }

    @Test
    fun `messages are in chronological order`() = runTest {
        val session = FakeMatrixSession()
        val roomId = RoomId("!general:matrix.org")
        session.simulateMessagesLoaded(roomId)

        val timestamps = session.messages.value.map { it.timestamp }
        assertEquals(timestamps, timestamps.sorted())  // ascending order
    }

    @Test
    fun `sealed class when is exhaustive`() {
        val state: LoginState = LoginState.Loading
        val description = when (state) {
            is LoginState.LoggedOut -> "not logged in"
            is LoginState.Loading   -> "logging in…"
            is LoginState.LoggedIn  -> "logged in as ${state.userId}"
            is LoginState.Error     -> "error: ${state.message}"
        }
        assertEquals("logging in…", description)
    }
}

// ---------------------------------------------------------------------------
// MessageInfo model tests
// ---------------------------------------------------------------------------
class MessageInfoTest {

    @Test
    fun `default message type is Text`() {
        val msg = MessageInfo("evt1", "@alice:matrix.org", "Hello", 1000L)
        assertEquals(MessageType.Text, msg.type)
    }

    @Test
    fun `reply info is preserved`() {
        val msg = MessageInfo(
            eventId = "evt2",
            senderId = "@bob:matrix.org",
            body = "Reply text",
            timestamp = 2000L,
            replyToEventId = "evt1",
        )
        assertEquals("evt1", msg.replyToEventId)
    }

    @Test
    fun `encrypted message type`() {
        val msg = MessageInfo("evt3", "@carol:matrix.org", "🔒 Encrypted message", 3000L,
            type = MessageType.Encrypted)
        assertEquals(MessageType.Encrypted, msg.type)
    }

    @Test
    fun `media message carries url and filename`() {
        val msg = MessageInfo(
            eventId = "evt4",
            senderId = "@dave:matrix.org",
            body = "photo.jpg",
            timestamp = 4000L,
            type = MessageType.Image,
            mediaUrl = "https://matrix.org/_matrix/media/v3/download/matrix.org/abc123",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
        )
        assertEquals(MessageType.Image, msg.type)
        assertNotNull(msg.mediaUrl)
        assertEquals("photo.jpg", msg.fileName)
    }
}

// ---------------------------------------------------------------------------
// MemberInfo tests
// ---------------------------------------------------------------------------
class MemberInfoTest {

    @Test
    fun `displayOrUser returns displayName when set`() {
        val member = MemberInfo("@alice:matrix.org", "Alice", null)
        assertEquals("Alice", member.displayOrUser)
    }

    @Test
    fun `displayOrUser falls back to localpart when displayName is null`() {
        val member = MemberInfo("@alice:matrix.org", null, null)
        assertEquals("alice", member.displayOrUser)
    }
}

// ---------------------------------------------------------------------------
// formatFileSize utility tests
// ---------------------------------------------------------------------------
class FormatFileSizeTest {

    @Test
    fun `bytes under 1 KB displayed as B`() {
        assertEquals("512 B", formatFileSize(512))
    }

    @Test
    fun `1 KB displayed as KB`() {
        assertEquals("1 KB", formatFileSize(1024))
    }

    @Test
    fun `1 MB displayed as MB`() {
        assertEquals("1.0 MB", formatFileSize(1_048_576))
    }

    @Test
    fun `fractional MB displayed with one decimal`() {
        assertEquals("1.5 MB", formatFileSize(1_572_864))
    }
}

// ---------------------------------------------------------------------------
// RoomInfo model tests
// ---------------------------------------------------------------------------
class RoomInfoTest {

    @Test
    fun `isDirect defaults to false`() {
        val room = RoomInfo(RoomId("!room:matrix.org"), "Test", null, 5)
        assertFalse(room.isDirect)
    }

    @Test
    fun `isDirect flag is preserved`() {
        val room = RoomInfo(RoomId("!dm:matrix.org"), "Alice", null, 2, isDirect = true)
        assertTrue(room.isDirect)
    }

    @Test
    fun `lastMessage and lastActivityMs default to null and zero`() {
        val room = RoomInfo(RoomId("!room:matrix.org"), "Room", null, 10)
        assertNull(room.lastMessage)
        assertEquals(0L, room.lastActivityMs)
    }
}
