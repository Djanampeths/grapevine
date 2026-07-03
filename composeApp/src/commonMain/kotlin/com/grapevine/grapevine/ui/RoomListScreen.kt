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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapevine.grapevine.matrix.LoginState
import com.grapevine.grapevine.matrix.MatrixViewModel
import com.grapevine.grapevine.matrix.RoomInfo
import com.grapevine.grapevine.matrix.SecurityStatus
import net.folivo.trixnity.core.model.RoomId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListScreen(
    viewModel: MatrixViewModel,
    onRoomClick: (RoomId, String) -> Unit,
    onLogout: () -> Unit,
    onOpenProfile: () -> Unit = {},
) {
    val loginState      by viewModel.loginState.collectAsState()
    val rooms           by viewModel.rooms.collectAsState()
    val roomsLoading    by viewModel.roomsLoading.collectAsState()
    val securityStatus  by viewModel.securityStatus.collectAsState()
    val securityError   by viewModel.securityError.collectAsState()
    val recoveryKey     by viewModel.bootstrapRecoveryKey.collectAsState()
    val userId = (loginState as? LoginState.LoggedIn)?.userId

    var query by remember { mutableStateOf("") }
    var showCreateJoin by remember { mutableStateOf(false) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    var showBootstrapDialog by remember { mutableStateOf(false) }

    val filtered = if (query.isBlank()) rooms
    else rooms.filter { room ->
        val displayName = room.name ?: room.id.full
        displayName.contains(query, ignoreCase = true)
    }

    if (showCreateJoin) {
        CreateJoinRoomDialog(viewModel = viewModel, onDismiss = { showCreateJoin = false })
    }
    if (showVerifyDialog) {
        VerifySessionDialog(
            securityStatus = securityStatus,
            error = securityError,
            onVerifyPassphrase = { viewModel.verifySelfWithPassphrase(it) },
            onVerifyRecoveryKey = { viewModel.verifySelfWithRecoveryKey(it) },
            onDismiss = { showVerifyDialog = false; viewModel.clearSecurityError() },
        )
    }
    if (showBootstrapDialog) {
        BootstrapSecurityDialog(
            error = securityError,
            onBootstrap = { viewModel.bootstrapCrossSigning(it) },
            onDismiss = { showBootstrapDialog = false; viewModel.clearSecurityError() },
        )
    }
    recoveryKey?.let { key ->
        RecoveryKeyDialog(recoveryKey = key, onDismiss = { viewModel.clearBootstrapRecoveryKey() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grapevine") },
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                    }
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

            when (securityStatus) {
                is SecurityStatus.NeedVerification ->
                    SecurityBanner(
                        icon = Icons.Default.Shield,
                        message = "Session unverified — Element may show security warnings",
                        actionLabel = "Verify",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = { showVerifyDialog = true },
                    )
                is SecurityStatus.NoCrossSigning ->
                    SecurityBanner(
                        icon = Icons.Default.Lock,
                        message = "No security key set up on this account",
                        actionLabel = "Set up",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = { showBootstrapDialog = true },
                    )
                else -> Unit
            }

            when {
                (loginState is LoginState.Loading || roomsLoading) && rooms.isEmpty() ->
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

// ── Security composables ──────────────────────────────────────────────────────

@Composable
private fun SecurityBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    actionLabel: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(color = containerColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) {
                Text(actionLabel, color = contentColor)
            }
        }
    }
}

@Composable
private fun VerifySessionDialog(
    securityStatus: SecurityStatus,
    error: String?,
    onVerifyPassphrase: (String) -> Unit,
    onVerifyRecoveryKey: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val needVerification = securityStatus as? SecurityStatus.NeedVerification
    var tabIndex by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }

    val tabs = buildList {
        if (needVerification?.hasPassphrase == true) add("Passphrase")
        if (needVerification?.hasRecoveryKey == true) add("Recovery key")
    }
    if (tabs.isEmpty()) { onDismiss(); return }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify this session") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter your security passphrase or recovery key to verify this device.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (tabs.size > 1) {
                    TabRow(selectedTabIndex = tabIndex) {
                        tabs.forEachIndexed { i, title ->
                            Tab(selected = tabIndex == i, onClick = { tabIndex = i; input = "" },
                                text = { Text(title) })
                        }
                    }
                }
                val isPassphrase = tabs.getOrNull(tabIndex) == "Passphrase"
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(if (isPassphrase) "Security passphrase" else "Recovery key") },
                    singleLine = true,
                    visualTransformation = if (isPassphrase) PasswordVisualTransformation() else
                        androidx.compose.ui.text.input.VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tabs.getOrNull(tabIndex) == "Passphrase") onVerifyPassphrase(input)
                    else onVerifyRecoveryKey(input)
                },
                enabled = input.isNotBlank(),
            ) { Text("Verify") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BootstrapSecurityDialog(
    error: String?,
    onBootstrap: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up security key") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Text(
                    "Create a security passphrase. You'll need this to verify future sessions. " +
                    "A recovery key will be shown after setup — save it somewhere safe.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Security passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = confirm.isNotEmpty() && confirm != passphrase,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onBootstrap(passphrase) },
                enabled = passphrase.isNotBlank() && passphrase == confirm,
            ) { Text("Set up") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RecoveryKeyDialog(recoveryKey: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save your recovery key") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your security key has been set up. Copy and store your recovery key somewhere safe — " +
                    "you'll need it to verify sessions if you forget your passphrase.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = recoveryKey,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("I've saved it") } },
    )
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
