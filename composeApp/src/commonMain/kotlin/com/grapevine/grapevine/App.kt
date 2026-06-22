package com.grapevine.grapevine

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grapevine.grapevine.matrix.LoginState
import com.grapevine.grapevine.matrix.MatrixViewModel
import com.grapevine.grapevine.ui.ChatScreen
import com.grapevine.grapevine.ui.GrapevineTheme
import com.grapevine.grapevine.ui.LoginScreen
import com.grapevine.grapevine.ui.RoomListScreen
import net.folivo.trixnity.core.model.RoomId

// sealed class gives us type-safe navigation.
// Unlike an enum, each subclass can carry data (e.g. Chat holds the roomId).
sealed class Screen {
    object Login : Screen()
    object RoomList : Screen()
    data class Chat(val roomId: RoomId, val roomName: String) : Screen()
}

@Composable
fun App() {
    GrapevineTheme {
    val viewModel = viewModel { MatrixViewModel() }
    val loginState by viewModel.loginState.collectAsState()

    // Remember navigation state; using `by` delegates to the State so we read .value automatically
    var screen by remember { mutableStateOf<Screen>(Screen.Login) }

    // React to log in state changes — if the server logs us out, go back to Log in
    LaunchedEffect(loginState) {
        when (loginState) {
            is LoginState.LoggedIn -> if (screen is Screen.Login) screen = Screen.RoomList
            is LoginState.LoggedOut -> screen = Screen.Login
            else -> Unit
        }
    }

    // `when` on a sealed class is exhaustive — the compiler ensures all cases are covered
    when (val s = screen) {
        Screen.Login -> LoginScreen(viewModel = viewModel)
        Screen.RoomList -> RoomListScreen(
            viewModel = viewModel,
            onRoomClick = { roomId, roomName ->
                viewModel.openRoom(roomId)
                screen = Screen.Chat(roomId, roomName)
            },
            onLogout = {
                viewModel.logout()
                screen = Screen.Login
            }
        )
        is Screen.Chat -> ChatScreen(
            viewModel = viewModel,
            roomName = s.roomName,
            onBack = {
                viewModel.stopMessages()
                screen = Screen.RoomList
            }
        )
    } // when
    } // GrapevineTheme
}
