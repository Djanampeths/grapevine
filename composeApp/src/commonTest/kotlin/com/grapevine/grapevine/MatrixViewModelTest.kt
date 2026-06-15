package com.grapevine.grapevine

import com.grapevine.grapevine.matrix.LoginState
import com.grapevine.grapevine.matrix.MessageInfo
import com.grapevine.grapevine.matrix.RoomInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.model.RoomId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        // This test demonstrates why sealed classes beat enums for state:
        // the compiler forces you to handle every case.
        val state: LoginState = LoginState.Loading

        // `when` with a sealed class — if you add a new subclass and forget
        // to handle it here, this won't compile.
        val description = when (state) {
            is LoginState.LoggedOut -> "not logged in"
            is LoginState.Loading   -> "logging in…"
            is LoginState.LoggedIn  -> "logged in as ${state.userId}"
            is LoginState.Error     -> "error: ${state.message}"
        }

        assertEquals("logging in…", description)
    }
}
