package com.homedashboard.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homedashboard.app.auth.AuthState
import com.homedashboard.app.auth.ICloudAuthState
import com.homedashboard.app.settings.CalendarLayoutType
import com.homedashboard.app.settings.CalendarSettings
import com.homedashboard.app.settings.DisplayMode
import com.homedashboard.app.settings.ICloudLoginDialog
import com.homedashboard.app.sync.SyncState
import com.homedashboard.app.sync.SyncStatus
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: CalendarSettings,
    onBack: () -> Unit,
    onLayoutChange: (CalendarLayoutType) -> Unit,
    onShowTasksChange: (Boolean) -> Unit,
    onShowQuickAddChange: (Boolean) -> Unit,
    onEInkModeChange: (Boolean) -> Unit,
    // Google Calendar sync
    authState: AuthState = AuthState.Unknown,
    syncState: SyncState = SyncState(),
    onGoogleSignIn: () -> Unit = {},
    onGoogleSignOut: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    // iCloud Calendar sync
    iCloudAuthState: ICloudAuthState = ICloudAuthState.Unknown,
    onICloudConnect: (email: String, appPassword: String) -> Unit = { _, _ -> },
    onICloudDisconnect: () -> Unit = {},
    iCloudConnectError: String? = null,
    modifier: Modifier = Modifier
) {
    // State for showing iCloud login dialog
    var showICloudLoginDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        // iCloud login dialog
        if (showICloudLoginDialog) {
            ICloudLoginDialog(
                onDismiss = { showICloudLoginDialog = false },
                onConnect = { email, password ->
                    onICloudConnect(email, password)
                    showICloudLoginDialog = false
                },
                isConnecting = iCloudAuthState is ICloudAuthState.Authenticating,
                errorMessage = iCloudConnectError
            )
        }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Layout section
            item {
                SettingsSection(title = "Layout")
            }

            item {
                LayoutOption(
                    title = "Grid (3×3)",
                    description = "7 days in a 3x3 grid with tasks and quick-add areas",
                    icon = Icons.Default.GridView,
                    isSelected = settings.layoutType == CalendarLayoutType.GRID_3X3,
                    onClick = { onLayoutChange(CalendarLayoutType.GRID_3X3) }
                )
            }

            item {
                LayoutOption(
                    title = "Timeline",
                    description = "All 7 days in a horizontal row with tasks below",
                    icon = Icons.Default.ViewWeek,
                    isSelected = settings.layoutType == CalendarLayoutType.TIMELINE_HORIZONTAL,
                    onClick = { onLayoutChange(CalendarLayoutType.TIMELINE_HORIZONTAL) }
                )
            }

            item {
                LayoutOption(
                    title = "Today Focus",
                    description = "Today's schedule prominently displayed with week overview",
                    icon = Icons.Default.ViewDay,
                    isSelected = settings.layoutType == CalendarLayoutType.DASHBOARD_TODAY_FOCUS,
                    onClick = { onLayoutChange(CalendarLayoutType.DASHBOARD_TODAY_FOCUS) }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Google Calendar section
            item {
                SettingsSection(title = "Google Calendar")
            }

            item {
                GoogleAccountCard(
                    authState = authState,
                    syncState = syncState,
                    onSignIn = onGoogleSignIn,
                    onSignOut = onGoogleSignOut,
                    onSyncNow = onSyncNow
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // iCloud Calendar section
            item {
                SettingsSection(title = "iCloud Calendar")
            }

            item {
                ICloudAccountCard(
                    authState = iCloudAuthState,
                    syncState = syncState,
                    onConnect = { showICloudLoginDialog = true },
                    onDisconnect = onICloudDisconnect,
                    onSyncNow = onSyncNow
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Display section
            item {
                SettingsSection(title = "Display")
            }

            item {
                SettingsSwitch(
                    title = "Show Tasks",
                    description = "Display task list in the calendar view",
                    checked = settings.showTasks,
                    onCheckedChange = onShowTasksChange
                )
            }

            item {
                SettingsSwitch(
                    title = "Show Quick Add",
                    description = "Display handwriting input area for adding events",
                    checked = settings.showQuickAdd,
                    onCheckedChange = onShowQuickAddChange
                )
            }

            item {
                SettingsSwitch(
                    title = "E-Ink Optimizations",
                    description = "High contrast mode and reduced animations for e-ink displays",
                    checked = settings.eInkRefreshMode,
                    onCheckedChange = onEInkModeChange
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // About section
            item {
                SettingsSection(title = "About")
            }

            item {
                SettingsItem(
                    title = "Version",
                    description = "1.0.0"
                )
            }

            item {
                SettingsItem(
                    title = "Home Dashboard Calendar",
                    description = "A wall calendar for e-ink tablets with handwriting support"
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun LayoutOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsItem(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GoogleAccountCard(
    authState: AuthState,
    syncState: SyncState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit
) {
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (authState) {
                is AuthState.Unknown -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Checking authentication...")
                    }
                }

                is AuthState.NotAuthenticated -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Not signed in",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Sign in to sync with Google Calendar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onSignIn) {
                            Icon(
                                Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In")
                        }
                    }
                }

                is AuthState.Authenticated -> {
                    // Account info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = authState.displayName ?: "Google Account",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = authState.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onSignOut) {
                            Text("Sign Out")
                        }
                    }

                    HorizontalDivider()

                    // Sync status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (syncState.status) {
                                        SyncStatus.SYNCING -> Icons.Default.Sync
                                        SyncStatus.SUCCESS -> Icons.Default.CheckCircle
                                        SyncStatus.ERROR -> Icons.Default.Error
                                        SyncStatus.PARTIAL_SUCCESS -> Icons.Default.Warning
                                        else -> Icons.Default.CloudSync
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = when (syncState.status) {
                                        SyncStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                        SyncStatus.ERROR -> MaterialTheme.colorScheme.error
                                        SyncStatus.PARTIAL_SUCCESS -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = when (syncState.status) {
                                        SyncStatus.IDLE -> "Ready to sync"
                                        SyncStatus.SYNCING -> "Syncing..."
                                        SyncStatus.SUCCESS -> "Sync complete"
                                        SyncStatus.ERROR -> "Sync failed"
                                        SyncStatus.PARTIAL_SUCCESS -> "Sync completed with errors"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            syncState.lastSyncTime?.let { lastSync ->
                                Text(
                                    text = "Last synced: ${lastSync.format(dateTimeFormatter)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            syncState.error?.let { error ->
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Button(
                            onClick = onSyncNow,
                            enabled = syncState.status != SyncStatus.SYNCING
                        ) {
                            if (syncState.status == SyncStatus.SYNCING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Now")
                        }
                    }

                    // Show sync progress if syncing
                    if (syncState.status == SyncStatus.SYNCING && syncState.progress > 0) {
                        LinearProgressIndicator(
                            progress = { syncState.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        syncState.message?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is AuthState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Authentication error",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = authState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onSignIn) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ICloudAccountCard(
    authState: ICloudAuthState,
    syncState: SyncState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit
) {
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (authState) {
                is ICloudAuthState.Unknown -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Checking iCloud connection...")
                    }
                }

                is ICloudAuthState.NotAuthenticated -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Not connected",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Connect to sync with iCloud Calendar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onConnect) {
                            Icon(
                                Icons.Default.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect")
                        }
                    }
                }

                is ICloudAuthState.Authenticating -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Connecting to iCloud...")
                    }
                }

                is ICloudAuthState.Authenticated -> {
                    // Account info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "iCloud Calendar",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = authState.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onDisconnect) {
                            Text("Disconnect")
                        }
                    }

                    HorizontalDivider()

                    // Sync status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (syncState.status) {
                                        SyncStatus.SYNCING -> Icons.Default.Sync
                                        SyncStatus.SUCCESS -> Icons.Default.CheckCircle
                                        SyncStatus.ERROR -> Icons.Default.Error
                                        SyncStatus.PARTIAL_SUCCESS -> Icons.Default.Warning
                                        else -> Icons.Default.CloudSync
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = when (syncState.status) {
                                        SyncStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                        SyncStatus.ERROR -> MaterialTheme.colorScheme.error
                                        SyncStatus.PARTIAL_SUCCESS -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = when (syncState.status) {
                                        SyncStatus.IDLE -> "Ready to sync"
                                        SyncStatus.SYNCING -> "Syncing..."
                                        SyncStatus.SUCCESS -> "Sync complete"
                                        SyncStatus.ERROR -> "Sync failed"
                                        SyncStatus.PARTIAL_SUCCESS -> "Sync completed with errors"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            syncState.lastSyncTime?.let { lastSync ->
                                Text(
                                    text = "Last synced: ${lastSync.format(dateTimeFormatter)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onSyncNow,
                            enabled = syncState.status != SyncStatus.SYNCING
                        ) {
                            if (syncState.status == SyncStatus.SYNCING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Now")
                        }
                    }
                }

                is ICloudAuthState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Connection error",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = authState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onConnect) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
