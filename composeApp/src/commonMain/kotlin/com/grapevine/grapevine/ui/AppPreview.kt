package com.grapevine.grapevine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.grapevine.grapevine.matrix.MessageInfo
import com.grapevine.grapevine.matrix.MessageType
import com.grapevine.grapevine.matrix.RoomInfo
import net.folivo.trixnity.core.model.RoomId

// ── Fake data ─────────────────────────────────────────────────────────────────

private val fakeRooms = listOf(
    RoomInfo(RoomId("!a:m.org"), "Grapevine Dev", "Building something cool", 5, unreadCount = 3),
    RoomInfo(RoomId("!b:m.org"), "Design",        "UI/UX discussions",       8, unreadCount = 0),
    RoomInfo(RoomId("!c:m.org"), "General",        null,                     12, unreadCount = 1),
)

private val fakeMessages = listOf(
    MessageInfo("1", "@alice:m.org", "Hey, have you seen the new design?",   1_750_000_000_000L),
    MessageInfo("2", "@me:m.org",    "Just checked it — looks great!",        1_750_000_060_000L),
    MessageInfo("3", "@alice:m.org", "Can you share the mockup file?",        1_750_000_120_000L),
    MessageInfo("4", "@me:m.org",    "mockup.fig", 1_750_000_180_000L,
        type = MessageType.File, fileName = "mockup.fig", fileSize = 2_048_000),
    MessageInfo("5", "@bob:m.org",   "The purple looks perfect for Grapevine", 1_750_000_240_000L),
)

// ── Previews ──────────────────────────────────────────────────────────────────
// In IntelliJ: click the gutter icon next to @Preview to open the preview panel.
// While the desktop app is running (`./gradlew run`), changes to Theme.kt are
// reflected live via Compose Hot Reload — no restart needed.

@Preview
@Composable
fun PreviewRoomListLight() {
    GrapevineTheme(darkTheme = false) { PreviewRoomList() }
}

@Preview
@Composable
fun PreviewRoomListDark() {
    GrapevineTheme(darkTheme = true) { PreviewRoomList() }
}

@Preview
@Composable
fun PreviewBubblesLight() {
    GrapevineTheme(darkTheme = false) { PreviewBubbles() }
}

@Preview
@Composable
fun PreviewBubblesDark() {
    GrapevineTheme(darkTheme = true) { PreviewBubbles() }
}

// ── Stub composables ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewRoomList() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Grapevine") }) }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            fakeRooms.forEach { room ->
                item(room.id.full) { RoomItem(room = room, onClick = {}) }
                item("d-${room.id.full}") { HorizontalDivider() }
            }
        }
    }
}

@Composable
private fun PreviewBubbles() {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        fakeMessages.forEach { msg ->
            MessageBubble(
                message = msg,
                isOwnMessage = msg.senderId == "@me:m.org",
                replySource = null,
                onReply = {},
                onEdit = null,
                onDelete = null,
            )
        }
    }
}
