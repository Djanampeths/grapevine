package com.grapevine.grapevine.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapevine.grapevine.matrix.MatrixViewModel

@Composable
fun CreateJoinRoomDialog(
    viewModel: MatrixViewModel,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Join tab state
    var joinInput by remember { mutableStateOf("") }

    // Create tab state
    var createName  by remember { mutableStateOf("") }
    var createTopic by remember { mutableStateOf("") }

    val roomActionError by viewModel.roomActionError.collectAsState()

    AlertDialog(
        onDismissRequest = {
            viewModel.clearRoomActionError()
            onDismiss()
        },
        title = { Text("Rooms") },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        text = { Text("Join") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        text = { Text("Create") })
                }
                Spacer(Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        OutlinedTextField(
                            value = joinInput,
                            onValueChange = { joinInput = it },
                            label = { Text("Room ID or alias") },
                            placeholder = { Text("!id:server or #alias:server") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    1 -> {
                        OutlinedTextField(
                            value = createName,
                            onValueChange = { createName = it },
                            label = { Text("Room name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = createTopic,
                            onValueChange = { createTopic = it },
                            label = { Text("Topic (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (roomActionError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        roomActionError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (selectedTab) {
                        0 -> if (joinInput.isNotBlank()) viewModel.joinRoom(joinInput.trim())
                        1 -> viewModel.createRoom(createName.trim(), createTopic.trim())
                    }
                    onDismiss()
                }
            ) {
                Text(if (selectedTab == 0) "Join" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.clearRoomActionError()
                onDismiss()
            }) { Text("Cancel") }
        }
    )
}
