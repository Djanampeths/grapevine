package com.grapevine.grapevine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapevine.grapevine.matrix.LoginState
import com.grapevine.grapevine.matrix.MatrixViewModel
import com.grapevine.grapevine.matrix.RoomInfo
import net.folivo.trixnity.core.model.RoomId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListScreen(
    viewModel: MatrixViewModel,
    onRoomClick: (RoomId, String) -> Unit,
    onLogout: () -> Unit,
) {
    val loginState by viewModel.loginState.collectAsState()
    val rooms      by viewModel.rooms.collectAsState()
    val userId = (loginState as? LoginState.LoggedIn)?.userId

    var query by remember { mutableStateOf("") }
    var showCreateJoin by remember { mutableStateOf(false) }

    val filtered = if (query.isBlank()) rooms
    else rooms.filter { room ->
        val displayName = room.name ?: room.id.full
        displayName.contains(query, ignoreCase = true)
    }

    if (showCreateJoin) {
        CreateJoinRoomDialog(
            viewModel = viewModel,
            onDismiss = { showCreateJoin = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grapevine") },
                actions = {
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateJoin = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create or join room")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            userId?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp, 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search rooms…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            when {
                loginState is LoginState.Loading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading rooms…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                rooms.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No rooms yet. Use + to join or create one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                filtered.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No rooms match \"$query\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                else ->
                    LazyColumn {
                        items(filtered, key = { it.id.full }) { room ->
                            RoomItem(
                                room = room,
                                onClick = { onRoomClick(room.id, room.name ?: "Unnamed Room") }
                            )
                            HorizontalDivider()
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomItem(room: RoomInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (room.name?.firstOrNull() ?: '#').uppercaseChar().toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = room.name ?: "Unnamed Room",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (room.isDirect) {
                    Spacer(Modifier.width(6.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("DM", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
            val subtitle = room.lastMessage ?: room.topic
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (room.memberCount > 0) {
                Text(
                    text = "${room.memberCount} members",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        if (room.unreadCount > 0) {
            BadgedBox(badge = {
                Badge { Text(room.unreadCount.coerceAtMost(99).toString()) }
            }) {}
        }
    }
}
