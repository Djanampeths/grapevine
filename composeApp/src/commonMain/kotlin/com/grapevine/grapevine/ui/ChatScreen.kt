package com.grapevine.grapevine.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.grapevine.grapevine.matrix.LoginState
import com.grapevine.grapevine.matrix.MatrixViewModel
import com.grapevine.grapevine.matrix.MemberInfo
import com.grapevine.grapevine.matrix.MessageInfo
import com.grapevine.grapevine.matrix.MessageStatus
import com.grapevine.grapevine.matrix.MessageType
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MatrixViewModel,
    roomName: String,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val messages      by viewModel.messages.collectAsState()
    val isLoading     by viewModel.isLoadingMessages.collectAsState()
    val error         by viewModel.messageError.collectAsState()
    val loginState    by viewModel.loginState.collectAsState()
    val messageCount  by viewModel.messageCount.collectAsState()
    val replyingTo    by viewModel.replyingTo.collectAsState()
    val typingUsers   by viewModel.typingUsers.collectAsState()
    val roomMembers   by viewModel.roomMembers.collectAsState()
    val currentUserId = (loginState as? LoginState.LoggedIn)?.userId

    val memberByUserId: Map<String, MemberInfo> = remember(roomMembers) {
        roomMembers.associateBy { it.userId }
    }

    val pickFile = rememberFilePicker { file ->
        if (file != null) viewModel.sendMedia(file.bytes, file.name, file.mimeType)
    }

    var inputText by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<MessageInfo?>(null) }
    val listState = rememberLazyListState()

    // Scroll to bottom when a new message from the bottom arrives
    val latestId = messages.lastOrNull()?.eventId
    LaunchedEffect(latestId) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // Trigger load-more when scrolled to the top
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index == 0 && messages.size >= messageCount) viewModel.loadMoreMessages()
            }
    }

    // Populate input when editing
    LaunchedEffect(editingMessage) {
        inputText = editingMessage?.body ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(roomName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenInfo) {
                        Icon(Icons.Default.Info, contentDescription = "Room info")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Typing indicator
                if (typingUsers.isNotEmpty()) {
                    val label = when (typingUsers.size) {
                        1 -> "${typingUsers[0].removePrefix("@").substringBefore(":")} is typing…"
                        2 -> "${typingUsers[0].removePrefix("@").substringBefore(":")} and ${typingUsers[1].removePrefix("@").substringBefore(":")} are typing…"
                        else -> "Several people are typing…"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
                    )
                }
                // Reply / edit preview bar
                if (replyingTo != null || editingMessage != null) {
                    ReplyEditBar(
                        replyingTo = replyingTo,
                        editingMessage = editingMessage,
                        onDismiss = {
                            viewModel.setReplyingTo(null)
                            editingMessage = null
                            inputText = ""
                        }
                    )
                }
                MessageInput(
                    text = inputText,
                    onTextChange = { text ->
                        inputText = text
                        viewModel.setTyping(text.isNotEmpty())
                    },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val editing = editingMessage
                            if (editing != null) {
                                viewModel.editMessage(editing.eventId, inputText.trim())
                                editingMessage = null
                            } else {
                                viewModel.sendMessage(inputText.trim())
                            }
                            inputText = ""
                            viewModel.setTyping(false)
                        }
                    },
                    onAttach = pickFile,
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading && messages.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                error != null && messages.isEmpty() ->
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Failed to load messages", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.retryMessages() }) { Text("Retry") }
                    }

                messages.isEmpty() ->
                    Text(
                        "No messages yet",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                else ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (messages.size >= messageCount) {
                            item("load-earlier") {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { viewModel.loadMoreMessages() }) {
                                        Text("Load earlier messages")
                                    }
                                }
                            }
                        }
                        items(messages, key = { it.eventId }) { message ->
                            val isOwn = message.senderId == currentUserId
                            val replySource = message.replyToEventId
                                ?.let { id -> messages.find { it.eventId == id } }
                            val senderMember = memberByUserId[message.senderId]

                            MessageBubble(
                                message = message,
                                isOwnMessage = isOwn,
                                replySource = replySource,
                                senderMember = senderMember,
                                onReply = { viewModel.setReplyingTo(message) },
                                onEdit = if (isOwn) ({ editingMessage = message }) else null,
                                onDelete = if (isOwn) ({ viewModel.deleteMessage(message.eventId) }) else null,
                            )
                        }
                    }
            }
        }
    }
}

// ── Reply / Edit bar ──────────────────────────────────────────────────────────

@Composable
private fun ReplyEditBar(
    replyingTo: MessageInfo?,
    editingMessage: MessageInfo?,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (editingMessage != null) Icons.Default.Edit else Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (editingMessage != null) "Editing message" else
                        "Replying to ${replyingTo?.senderId?.removePrefix("@")?.substringBefore(":")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = (editingMessage ?: replyingTo)?.body ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Message bubble ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageInfo,
    isOwnMessage: Boolean,
    replySource: MessageInfo?,
    senderMember: MemberInfo? = null,
    onReply: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwnMessage)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isOwnMessage)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(
        topStart    = if (isOwnMessage) 16.dp else 4.dp,
        topEnd      = if (isOwnMessage) 4.dp else 16.dp,
        bottomStart = 16.dp,
        bottomEnd   = 16.dp,
    )

    var menuVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (!isOwnMessage) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            ) {
                SenderAvatar(senderMember)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = senderMember?.displayOrUser
                        ?: message.senderId.removePrefix("@").substringBefore(":"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box {
            Surface(
                shape = shape,
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuVisible = true }
                    )
            ) {
                Column {
                    // Quoted reply
                    if (replySource != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(
                                topStart    = if (isOwnMessage) 16.dp else 4.dp,
                                topEnd      = if (isOwnMessage) 4.dp else 16.dp,
                                bottomStart = 0.dp,
                                bottomEnd   = 0.dp,
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp, 6.dp)) {
                                Text(
                                    text = replySource.senderId.removePrefix("@").substringBefore(":"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = replySource.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                    }

                    // Main content
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
                        MessageType.Encrypted -> Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Encrypted message",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.6f),
                                fontStyle = FontStyle.Italic,
                            )
                        }
                    }
                }
            }

            // Context menu
            DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                    onClick = { menuVisible = false; onReply() }
                )
                if (onEdit != null) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuVisible = false; onEdit() }
                    )
                }
                if (onDelete != null) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuVisible = false; onDelete() }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (isOwnMessage) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when (message.status) {
                        MessageStatus.Sending -> "○"
                        MessageStatus.Sent    -> "✓"
                        MessageStatus.Failed  -> "✗"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (message.status) {
                        MessageStatus.Sending -> MaterialTheme.colorScheme.outline
                        MessageStatus.Sent    -> MaterialTheme.colorScheme.primary
                        MessageStatus.Failed  -> MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

// ── Media content ─────────────────────────────────────────────────────────────

@Composable
private fun ImageBubbleContent(
    message: MessageInfo,
    textColor: androidx.compose.ui.graphics.Color,
) {
    val ctx = LocalPlatformContext.current
    if (message.mediaUrl != null) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(ctx).data(message.mediaUrl).crossfade(true).build(),
            contentDescription = message.fileName ?: "Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp)),
            error = { MediaFallbackRow(Icons.Default.BrokenImage, message.fileName ?: "Image", textColor) }
        )
    } else {
        MediaFallbackRow(Icons.Default.BrokenImage, message.fileName ?: "Image", textColor)
    }
}

@Composable
private fun MediaFileBubbleContent(
    message: MessageInfo,
    textColor: androidx.compose.ui.graphics.Color,
) {
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
                overflow = TextOverflow.Ellipsis,
            )
            message.fileSize?.let {
                Text(formatFileSize(it), style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun MediaFallbackRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    textColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = textColor, style = MaterialTheme.typography.bodySmall)
    }
}

// ── Sender avatar ─────────────────────────────────────────────────────────────

private const val SENDER_AVATAR_SIZE = 20

@Composable
private fun SenderAvatar(member: MemberInfo?) {
    val ctx = LocalPlatformContext.current
    val initials = (member?.displayOrUser ?: "?").firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    if (member?.avatarUrl != null) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(ctx).data(member.avatarUrl).crossfade(true).build(),
            contentDescription = member.displayOrUser,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(SENDER_AVATAR_SIZE.dp).clip(CircleShape),
            error = { AvatarInitials(initials, SENDER_AVATAR_SIZE) },
        )
    } else {
        AvatarInitials(initials, SENDER_AVATAR_SIZE)
    }
}

@Composable
private fun AvatarInitials(initial: String, size: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initial,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit = {},
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attach file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Message…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun formatTimestamp(epochMs: Long): String {
    val h = (epochMs / 3_600_000 % 24).toInt()
    val m = (epochMs / 60_000 % 60).toInt()
    val time = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    val nowDays = System.currentTimeMillis() / 86_400_000
    val msgDays = epochMs / 86_400_000
    return when (nowDays - msgDays) {
        0L -> time
        1L -> "Yesterday $time"
        else -> {
            val day = (epochMs / 86_400_000 % 31 + 1).toInt()
            val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            val month = months[((epochMs / 2_629_800_000) % 12).toInt()]
            "$month $day $time"
        }
    }
}

internal fun formatFileSize(bytes: Int): String = when {
    bytes < 1_024     -> "$bytes B"
    bytes < 1_048_576 -> "${bytes / 1_024} KB"
    else              -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
}
