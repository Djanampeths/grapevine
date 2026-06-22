package com.grapevine.grapevine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.grapevine.grapevine.matrix.LoginState
import com.grapevine.grapevine.matrix.MatrixViewModel
import com.grapevine.grapevine.matrix.MessageInfo
import com.grapevine.grapevine.matrix.MessageType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MatrixViewModel,
    roomName: String,
    onBack: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoadingMessages.collectAsState()
    val error by viewModel.messageError.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val messageCount by viewModel.messageCount.collectAsState()
    val currentUserId = (loginState as? LoginState.LoggedIn)?.userId

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom only when a new message arrives (not when loading more old ones)
    val latestId = messages.lastOrNull()?.eventId
    LaunchedEffect(latestId) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // Load more when user scrolls to the very top
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index == 0 && messages.size >= messageCount) {
                    viewModel.loadMoreMessages()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(roomName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            MessageInput(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading && messages.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null && messages.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Failed to load messages", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.retryMessages() }) { Text("Retry") }
                    }
                }
                messages.isEmpty() -> {
                    Text(
                        text = "No messages yet",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // "Load earlier" button shown at top when there may be older messages
                        if (messages.size >= messageCount) {
                            item("load-earlier") {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { viewModel.loadMoreMessages() }) {
                                        Text("Load earlier messages")
                                    }
                                }
                            }
                        }
                        items(messages, key = { it.eventId }) { message ->
                            MessageBubble(
                                message = message,
                                isOwnMessage = message.senderId == currentUserId
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageInfo, isOwnMessage: Boolean) {
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwnMessage)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isOwnMessage)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = RoundedCornerShape(
        topStart = if (isOwnMessage) 16.dp else 4.dp,
        topEnd   = if (isOwnMessage) 4.dp else 16.dp,
        bottomStart = 16.dp,
        bottomEnd   = 16.dp,
    )

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (!isOwnMessage) {
            Text(
                text = message.senderId.removePrefix("@").substringBefore(":"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            when (message.type) {
                MessageType.Text -> Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                MessageType.Image -> ImageBubbleContent(message, textColor)

                MessageType.File,
                MessageType.Audio,
                MessageType.Video -> MediaFileBubbleContent(message, textColor)
            }
        }

        Text(
            text = formatTimestamp(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, end = 4.dp)
        )
    }
}

@Composable
private fun ImageBubbleContent(message: MessageInfo, textColor: androidx.compose.ui.graphics.Color) {
    val ctx = LocalPlatformContext.current
    if (message.mediaUrl != null) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(message.mediaUrl)
                .crossfade(true)
                .build(),
            contentDescription = message.fileName ?: "Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp)),
            error = {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BrokenImage, contentDescription = null,
                        tint = textColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(message.fileName ?: "Image", color = textColor,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    } else {
        // Encrypted or URL-less image — show placeholder
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BrokenImage, contentDescription = null,
                tint = textColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(message.fileName ?: "Image", color = textColor,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MediaFileBubbleContent(message: MessageInfo, textColor: androidx.compose.ui.graphics.Color) {
    val icon = when (message.type) {
        MessageType.Audio -> Icons.Default.AudioFile
        MessageType.Video -> Icons.Default.VideoFile
        else              -> Icons.Default.AttachFile
    }
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = message.fileName ?: message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
            )
            message.fileSize?.let {
                Text(
                    text = formatFileSize(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now().toLocalDateTime(tz)
    val local = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    val daysDiff = now.date.toEpochDays() - local.date.toEpochDays()
    return when {
        daysDiff == 0 -> time
        daysDiff == 1 -> "Yesterday $time"
        else -> "${local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${local.dayOfMonth} $time"
    }
}

private fun formatFileSize(bytes: Int): String = when {
    bytes < 1_024       -> "$bytes B"
    bytes < 1_048_576   -> "${bytes / 1_024} KB"
    else                -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
}

@Composable
fun MessageInput(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Message…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
