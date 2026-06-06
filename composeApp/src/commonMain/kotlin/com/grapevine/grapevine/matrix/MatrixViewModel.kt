package com.grapevine.grapevine.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MatrixState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val userId: String? = null,
    val rooms: List<RoomInfo> = emptyList()
)

data class RoomInfo(
    val id: String,
    val name: String?,
    val topic: String?,
    val memberCount: Int
)

class MatrixViewModel : ViewModel() {
    private val _state = MutableStateFlow(MatrixState())
    val state: StateFlow<MatrixState> = _state.asStateFlow()

    fun login(
        homeserver: String,
        username: String,
        password: String
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                //  TODO: actual matrix login implementation with Trixnity sdk
                delay(1000)
                _state.value = _state.value.copy(
                    isLoggedIn = true,
                    isLoading = false,
                    userId = "@$username:$homeserver",
                    rooms = getMockRooms()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Login Failed"
                )
            }
        }
    }

    fun logout() { _state.value = MatrixState() }

    private fun getMockRooms(): List<RoomInfo> = listOf(
        RoomInfo("!room1:matrix.org", "General Chat", "General discussion room", 42),
        RoomInfo("!room2:matrix.org", "Random", "Random stuff", 15),
        RoomInfo("!room3:matrix.org", "Development", "Kotlin Multiplatform chat", 8)
    )

    fun clearError() { _state.value = _state.value.copy(error = null) }
}