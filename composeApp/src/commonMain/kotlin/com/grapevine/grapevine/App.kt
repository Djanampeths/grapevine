package com.grapevine.grapevine

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grapevine.grapevine.matrix.LoginState
import com.grapevine.grapevine.matrix.MatrixViewModel
import com.grapevine.grapevine.ui.ChatScreen
import com.grapevine.grapevine.ui.GrapevineTheme
import com.grapevine.grapevine.ui.LoginScreen
import com.grapevine.grapevine.ui.ProfileScreen
import com.grapevine.grapevine.ui.RoomInfoScreen
import com.grapevine.grapevine.ui.RoomListScreen
import net.folivo.trixnity.core.model.RoomId

sealed class Screen {
    object Login : Screen()
    object RoomList : Screen()
    data class Chat(val roomId: RoomId, val roomName: String) : Screen()
    data class RoomInfo(val roomName: String) : Screen()
    object Profile : Screen()
}

@Composable
fun App() {
    GrapevineTheme {
        val viewModel = viewModel { MatrixViewModel() }
        val loginState by viewModel.loginState.collectAsState()

        var screen by remember { mutableStateOf<Screen>(Screen.Login) }

        LaunchedEffect(loginState) {
            when (loginState) {
                is LoginState.LoggedIn -> if (screen is Screen.Login) screen = Screen.RoomList
                is LoginState.LoggedOut -> screen = Screen.Login
                else -> Unit
            }
        }

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
                },
                onOpenProfile = { screen = Screen.Profile }
            )

            Screen.Profile -> ProfileScreen(
                viewModel = viewModel,
                onBack = { screen = Screen.RoomList }
            )

            is Screen.Chat -> ChatScreen(
                viewModel = viewModel,
                roomName = s.roomName,
                onBack = {
                    viewModel.stopMessages()
                    screen = Screen.RoomList
                },
                onOpenInfo = {
                    screen = Screen.RoomInfo(s.roomName)
                }
            )

            is Screen.RoomInfo -> RoomInfoScreen(
                viewModel = viewModel,
                roomName = s.roomName,
                onBack = {
                    // Return to Chat, reuse the still-open room
                    val roomId = viewModel.currentRoomId.value
                    if (roomId != null) screen = Screen.Chat(roomId, s.roomName)
                    else screen = Screen.RoomList
                },
                onLeft = {
                    screen = Screen.RoomList
                }
            )
        }
    }
}
