package com.grapevine.grapevine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapevine.grapevine.matrix.MatrixViewModel
import com.grapevine.grapevine.matrix.RoomInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListScreen(viewModel: MatrixViewModel, onLogout: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grapevine") },
                actions = {
                    TextButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Text("Logout")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            state.userId?.let { userId ->
                Text(
                    text = "Logged in as: $userId",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            LazyColumn {
                items(state.rooms) { room ->
                    RoomItem(room = room)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun RoomItem(room: RoomInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Open Room */ }
            .padding(16.dp)
    ) {
        Text(
            text = room.name ?: "Unnamed Room",
            style = MaterialTheme.typography.titleMedium
        )
        room.topic?.let { topic ->
            Text(
                text = topic,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text (
            text = "${room.memberCount} members",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}