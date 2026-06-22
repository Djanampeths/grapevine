package com.grapevine.grapevine.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.grapevine.grapevine.matrix.MatrixViewModel
import com.grapevine.grapevine.matrix.MemberInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomInfoScreen(
    viewModel: MatrixViewModel,
    roomName: String,
    onBack: () -> Unit,
    onLeft: () -> Unit,
) {
    val members by viewModel.roomMembers.collectAsState()
    var showLeaveConfirm by remember { mutableStateOf(false) }

    // Derive topic from current rooms list
    val currentRoomId by viewModel.currentRoomId.collectAsState()
    val rooms by viewModel.rooms.collectAsState()
    val roomInfo = rooms.find { it.id == currentRoomId }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave room?") },
            text = { Text("You will no longer receive messages from \"$roomName\".") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        viewModel.leaveCurrentRoom()
                        onLeft()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showLeaveConfirm = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Leave room",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Header section
            item("header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (roomName.firstOrNull() ?: '#').uppercaseChar().toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(roomName, style = MaterialTheme.typography.titleLarge)
                    roomInfo?.memberCount?.let { count ->
                        Text(
                            "$count members",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Topic
            roomInfo?.topic?.let { topic ->
                item("topic") {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text("Topic", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(topic, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                    }
                }
            }

            // Members header
            item("members-header") {
                Text(
                    "Members (${members.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            items(members, key = { it.userId }) { member ->
                MemberRow(member = member)
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }

            item("spacer-bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MemberRow(member: MemberInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ctx = LocalPlatformContext.current
        if (member.avatarUrl != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(ctx).data(member.avatarUrl).crossfade(true).build(),
                contentDescription = member.displayOrUser,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape),
                error = { MemberAvatar(member) }
            )
        } else {
            MemberAvatar(member)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(member.displayOrUser, style = MaterialTheme.typography.bodyMedium)
            if (member.displayName != null) {
                Text(
                    member.userId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MemberAvatar(member: MemberInfo) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = member.displayOrUser.first().uppercaseChar().toString(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
