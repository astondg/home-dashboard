package com.homedashboard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homedashboard.app.auth.AuthState
import com.homedashboard.app.auth.ICloudAuthState
import com.homedashboard.app.data.model.Calendar
import com.homedashboard.app.data.model.CalendarProvider
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
    onWallCalendarModeChange: (Boolean) -> Unit,
    onEInkModeChange: (Boolean) -> Unit,
    isEInkDetected: Boolean = false,
    // Weather
    onShowWeatherChange: (Boolean) -> Unit = {},
    onWeatherTempUnitChange: (com.homedashboard.app.data.weather.TempUnit) -> Unit = {},
    onWeatherLocationSet: (lat: Double, lon: Double, name: String) -> Unit = { _, _, _ -> },
    // Always-on display
    hasFrontlight: Boolean = true,
    onAlwaysOnDisplayChange: (Boolean) -> Unit = {},
    onNightDimEnabledChange: (Boolean) -> Unit = {},
    onNightDimStartHourChange: (Int) -> Unit = {},
    onNightDimEndHourChange: (Int) -> Unit = {},
    onNightDimBrightnessChange: (Float) -> Unit = {},
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
    // Calendar selection
    calendars: List<Calendar> = emptyList(),
    defaultCalendarId: String? = null,
    onCalendarVisibilityChange: (calendarId: String, visible: Boolean) -> Unit = { _, _ -> },
    onDefaultCalendarChange: (calendarId: String?) -> Unit = {},
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

            // Calendar visibility toggles (show when we have calendars)
            if (calendars.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                item {
                    SettingsSection(title = "Calendars")
                }

                // Google calendars
                val googleCalendars = calendars.filter { it.providerType == CalendarProvider.GOOGLE }
                if (googleCalendars.isNotEmpty()) {
                    item {
                        Text(
                            text = "Google",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(googleCalendars, key = { it.id }) { calendar ->
                        CalendarToggleRow(
                            calendar = calendar,
                            onVisibilityChange = { visible ->
                                onCalendarVisibilityChange(calendar.id, visible)
                            }
                        )
                    }
                }

                // iCloud calendars
                val iCloudCalendars = calendars.filter { it.providerType == CalendarProvider.ICLOUD }
                if (iCloudCalendars.isNotEmpty()) {
                    item {
                        Text(
                            text = "iCloud",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(iCloudCalendars, key = { it.id }) { calendar ->
                        CalendarToggleRow(
                            calendar = calendar,
                            onVisibilityChange = { visible ->
                                onCalendarVisibilityChange(calendar.id, visible)
                            }
                        )
                    }
                }

                // Default calendar picker
                val writableCalendars = calendars.filter { it.isVisible && !it.isReadOnly }
                if (writableCalendars.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        DefaultCalendarPicker(
                            calendars = writableCalendars,
                            selectedCalendarId = defaultCalendarId,
                            onCalendarSelected = onDefaultCalendarChange
                        )
                    }
                }
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
                    title = "Wall Calendar Mode",
                    description = "Large text and touch targets for wall-mounted use",
                    checked = settings.wallCalendarMode,
                    onCheckedChange = onWallCalendarModeChange
                )
            }

            item {
                SettingsSwitch(
                    title = "E-ink Display",
                    description = "High contrast monochrome for e-ink screens" +
                        if (isEInkDetected) " (detected)" else "",
                    checked = settings.eInkRefreshMode || isEInkDetected,
                    onCheckedChange = onEInkModeChange
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Weather section
            item {
                SettingsSection(title = "Weather")
            }

            item {
                SettingsSwitch(
                    title = "Show Weather",
                    description = "Display weather forecast in calendar headers",
                    checked = settings.showWeather,
                    onCheckedChange = onShowWeatherChange
                )
            }

            if (settings.showWeather) {
                item {
                    WeatherLocationRow(
                        locationName = settings.weatherLocationName,
                        onLocationSet = onWeatherLocationSet
                    )
                }

                item {
                    WeatherTempUnitRow(
                        currentUnit = settings.weatherTempUnit,
                        onUnitChange = onWeatherTempUnitChange
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Always-On Display section
            item {
                SettingsSection(title = "Always-On Display")
            }

            item {
                SettingsSwitch(
                    title = "Keep Screen On",
                    description = "Prevent the screen from turning off (for wall-mounted use)",
                    checked = settings.alwaysOnDisplay,
                    onCheckedChange = onAlwaysOnDisplayChange
                )
            }

            // Night dimming — only show if device has a frontlight/backlight
            if (hasFrontlight) {
                item {
                    SettingsSwitch(
                        title = "Night Dimming",
                        description = "Reduce screen brightness on a schedule",
                        checked = settings.nightDimEnabled,
                        onCheckedChange = onNightDimEnabledChange
                    )
                }

                if (settings.nightDimEnabled) {
                    item {
                        HourPickerRow(
                            label = "Dim start",
                            hour = settings.nightDimStartHour,
                            onHourChange = onNightDimStartHourChange
                        )
                    }

                    item {
                        HourPickerRow(
                            label = "Dim end",
                            hour = settings.nightDimEndHour,
                            onHourChange = onNightDimEndHourChange
                        )
                    }

                    item {
                        BrightnessSliderRow(
                            brightness = settings.nightDimBrightness,
                            onBrightnessChange = onNightDimBrightnessChange
                        )
                    }
                }
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
private fun HourPickerRow(
    label: String,
    hour: Int,
    onHourChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { onHourChange((hour - 1 + 24) % 24) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease hour")
            }

            Text(
                text = String.format("%02d:00", hour),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            IconButton(
                onClick = { onHourChange((hour + 1) % 24) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase hour")
            }
        }
    }
}

@Composable
private fun BrightnessSliderRow(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Night brightness",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${(brightness * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = brightness,
            onValueChange = onBrightnessChange,
            valueRange = 0.01f..0.5f,
            steps = 9
        )
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

@Composable
private fun CalendarToggleRow(
    calendar: Calendar,
    onVisibilityChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onVisibilityChange(!calendar.isVisible) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Color dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(calendar.color.toLong() or 0xFF000000L))
        )

        Text(
            text = calendar.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        if (calendar.isReadOnly) {
            Text(
                text = "Read-only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = calendar.isVisible,
            onCheckedChange = onVisibilityChange
        )
    }
}

@Composable
private fun DefaultCalendarPicker(
    calendars: List<Calendar>,
    selectedCalendarId: String?,
    onCalendarSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedCalendar = calendars.find { it.id == selectedCalendarId }
    val displayName = selectedCalendar?.name ?: "Auto (first available)"

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Default calendar",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "New events will be added to: $displayName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                HorizontalDivider()

                // Auto option
                DefaultCalendarOption(
                    name = "Auto (first available)",
                    color = null,
                    isSelected = selectedCalendarId == null,
                    onClick = { onCalendarSelected(null) }
                )

                // Calendar options
                for (calendar in calendars) {
                    DefaultCalendarOption(
                        name = calendar.name,
                        color = Color(calendar.color.toLong() or 0xFF000000L),
                        isSelected = calendar.id == selectedCalendarId,
                        onClick = { onCalendarSelected(calendar.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultCalendarOption(
    name: String,
    color: Color?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (color != null) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        } else {
            Spacer(modifier = Modifier.size(12.dp))
        }

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
    }
}

@Composable
private fun WeatherLocationRow(
    locationName: String?,
    onLocationSet: (lat: Double, lon: Double, name: String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Location",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = locationName ?: "Not set - tap to configure",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = "Set location",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        WeatherLocationDialog(
            onDismiss = { showDialog = false },
            onLocationSelected = { lat, lon, name ->
                onLocationSet(lat, lon, name)
                showDialog = false
            }
        )
    }
}

@Composable
private fun WeatherLocationDialog(
    onDismiss: () -> Unit,
    onLocationSelected: (lat: Double, lon: Double, name: String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.homedashboard.app.data.weather.GeocodingResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val weatherService = remember { com.homedashboard.app.data.weather.WeatherService() }

    // Debounced autosearch: triggers 400ms after the user stops typing
    LaunchedEffect(searchText) {
        if (searchText.length >= 2) {
            kotlinx.coroutines.delay(400L)
            isSearching = true
            val result = weatherService.geocodeCity(searchText)
            result.onSuccess { results = it }
            result.onFailure { results = emptyList() }
            isSearching = false
        } else {
            results = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Weather Location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("City name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                results.forEach { result ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLocationSelected(result.latitude, result.longitude, result.displayName)
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = result.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun WeatherTempUnitRow(
    currentUnit: com.homedashboard.app.data.weather.TempUnit,
    onUnitChange: (com.homedashboard.app.data.weather.TempUnit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val displayName = when (currentUnit) {
        com.homedashboard.app.data.weather.TempUnit.AUTO -> "Auto (from locale)"
        com.homedashboard.app.data.weather.TempUnit.CELSIUS -> "Celsius (\u00B0C)"
        com.homedashboard.app.data.weather.TempUnit.FAHRENHEIT -> "Fahrenheit (\u00B0F)"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Temperature unit",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Change unit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                com.homedashboard.app.data.weather.TempUnit.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = {
                            Text(when (unit) {
                                com.homedashboard.app.data.weather.TempUnit.AUTO -> "Auto (from locale)"
                                com.homedashboard.app.data.weather.TempUnit.CELSIUS -> "Celsius (\u00B0C)"
                                com.homedashboard.app.data.weather.TempUnit.FAHRENHEIT -> "Fahrenheit (\u00B0F)"
                            })
                        },
                        onClick = {
                            onUnitChange(unit)
                            expanded = false
                        },
                        trailingIcon = {
                            if (unit == currentUnit) {
                                Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        }
                    )
                }
            }
        }
    }
}
