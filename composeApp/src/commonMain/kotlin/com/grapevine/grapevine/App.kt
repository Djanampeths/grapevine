package com.grapevine.grapevine

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grapevine.grapevine.matrix.MatrixViewModel
import com.grapevine.grapevine.ui.LoginScreen
import com.grapevine.grapevine.ui.RoomListScreen
import androidx.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    val viewModel = viewModel { MatrixViewModel() }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }

    when (currentScreen) {
        Screen.Login -> LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = { currentScreen = Screen.RoomList }
        )
        Screen.RoomList -> RoomListScreen(
            viewModel = viewModel,
            onLogout = { currentScreen = Screen.Login }
        )
    }
}

enum class Screen {
    Login,
    RoomList,
}
