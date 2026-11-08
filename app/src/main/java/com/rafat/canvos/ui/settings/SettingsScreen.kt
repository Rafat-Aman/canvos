package com.rafat.canvos.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rafat.canvos.BuildConfig
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.data.DueReminder
import com.rafat.canvos.data.Notifier
import com.rafat.canvos.data.SyncWorker
import com.rafat.canvos.data.ThemeMode
import com.rafat.canvos.ui.common.CanvosTopBar
import com.rafat.canvos.ui.common.SectionHeader
import com.rafat.canvos.ui.nav.CalendarSyncRoute
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.OfflineRoute
import com.rafat.canvos.util.ago
import java.time.Instant

@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val app = CanvosApp.instance
    val s by app.settings.state.collectAsState()
    val off by app.offline.prefs.state.collectAsState()
    var themeDialog by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }
    var dueDialog by remember { mutableStateOf(false) }
    // Re-read permission / battery state when coming back from system settings.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val allowed = remember(tick, s.notifications) { Notifier.canNotify(ctx) }
    val unrestricted = remember(tick) { BackgroundAccess.isUnrestricted(ctx) }
    val lastCheck = remember(tick) { Notifier.lastCheck(ctx) }
    val anyNotifications = s.notifications || s.dueReminders != DueReminder.OFF
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        if (granted) SyncWorker.runOnce(ctx)
    }
    fun askPermission() {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        else BackgroundAccess.openNotificationSettings(ctx)
    }

    Scaffold(topBar = { CanvosTopBar("Settings", onBack = { nav.back() }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            SectionHeader("Appearance")
            ListItem(
                modifier = Modifier.clickable { themeDialog = true },
                leadingContent = { Icon(Icons.Outlined.DarkMode, null) },
                headlineContent = { Text("Theme") },
                supportingContent = { Text(when (s.theme) { ThemeMode.SYSTEM -> "Follow system"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }) },
            )
            if (Build.VERSION.SDK_INT >= 31) {
                Toggle(Icons.Outlined.Colorize, "Material You colours", "Use colours from your wallpaper", s.dynamicColor) { v -> app.settings.update { it.copy(dynamicColor = v) } }
            }
            Toggle(Icons.Outlined.Palette, "Colour overlay on course images", null, s.colorOverlay) { v -> app.settings.update { it.copy(colorOverlay = v) } }
            Toggle(Icons.Outlined.Grade, "Show grades on dashboard", null, s.showGrades) { v -> app.settings.update { it.copy(showGrades = v) } }

            SectionHeader("Notifications")
            Toggle(
                Icons.Outlined.NotificationsActive, "Canvas notifications",
                "New announcements, messages and grades, checked every 15 minutes" +
                    if (s.notifications && lastCheck > 0) " · last check ${Instant.ofEpochMilli(lastCheck).ago()}" else "",
                s.notifications,
            ) { v ->
                app.settings.update { it.copy(notifications = v) }
                SyncWorker.schedule(ctx)
                if (v) {
                    if (!allowed) askPermission() else SyncWorker.runOnce(ctx)
                }
            }
            ListItem(
                modifier = Modifier.clickable { dueDialog = true },
                leadingContent = { Icon(Icons.Outlined.Alarm, null) },
                headlineContent = { Text("Due date reminders") },
                supportingContent = { Text(s.dueReminders.label) },
            )
            if (anyNotifications && !allowed) {
                ListItem(
                    modifier = Modifier.clickable { BackgroundAccess.openNotificationSettings(ctx) },
                    leadingContent = { Icon(Icons.Outlined.NotificationsOff, null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text("Notifications are blocked", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Tap to allow Canvos to show notifications") },
                )
            }
            if (anyNotifications) {
                ListItem(
                    modifier = Modifier.clickable { if (!unrestricted) BackgroundAccess.request(ctx) },
                    leadingContent = {
                        Icon(
                            if (unrestricted) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryAlert, null,
                            tint = if (unrestricted) LocalContentColor.current else MaterialTheme.colorScheme.error,
                        )
                    },
                    headlineContent = { Text("Background activity") },
                    supportingContent = {
                        Text(
                            if (unrestricted) "Allowed, so checks run on time"
                            else "Restricted by battery saving, so notifications can be hours late. Tap to allow.",
                        )
                    },
                )
            }
            ListItem(
                modifier = Modifier.clickable { BackgroundAccess.openNotificationSettings(ctx) },
                leadingContent = { Icon(Icons.Outlined.Tune, null) },
                headlineContent = { Text("Notification types and sounds") },
                supportingContent = { Text("Announcements, messages, grades, due dates, discussions") },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
            )
            ListItem(
                modifier = Modifier.clickable {
                    if (!allowed) askPermission() else {
                        Notifier.test(ctx)
                        SyncWorker.runOnce(ctx)
                    }
                },
                leadingContent = { Icon(Icons.Outlined.NotificationAdd, null) },
                headlineContent = { Text("Send a test notification") },
                supportingContent = { Text("Also checks Canvas for anything new right now") },
            )

            SectionHeader("Offline & calendar")
            ListItem(
                modifier = Modifier.clickable { nav.go(OfflineRoute) },
                leadingContent = { Icon(Icons.Outlined.CloudDownload, null) },
                headlineContent = { Text("Offline content") },
                supportingContent = {
                    Text(
                        when {
                            !off.enabled -> "Off"
                            off.lastSuccess > 0 -> "On · updated ${Instant.ofEpochMilli(off.lastSuccess).ago()}"
                            else -> "On · downloads on Wi-Fi"
                        },
                    )
                },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
            )
            ListItem(
                modifier = Modifier.clickable { nav.go(CalendarSyncRoute) },
                leadingContent = { Icon(Icons.Outlined.CalendarMonth, null) },
                headlineContent = { Text("Sync to Google Calendar") },
                supportingContent = { Text("Put due dates and events in one of your Google calendars") },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
            )

            if (BuildConfig.DEBUG) {
                SectionHeader("Developer")
                Toggle(Icons.Outlined.Lock, "Read-only mode", "Block every change to Canvas (testing)", s.readOnly) { v ->
                    app.settings.update { it.copy(readOnly = v) }
                    app.reconnect()
                }
            }

            SectionHeader("Account")
            ListItem(
                modifier = Modifier.clickable { logoutDialog = true },
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(app.repo.value?.domain?.removePrefix("https://") ?: "") },
            )
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, null) },
                headlineContent = { Text("Canvos ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text("An unofficial Canvas client. Your token stays encrypted on this device and is only sent to your Canvas site.") },
            )
        }
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (m, label) ->
                        ListItem(
                            modifier = Modifier.clickable { app.settings.update { it.copy(theme = m) }; themeDialog = false },
                            leadingContent = { RadioButton(selected = s.theme == m, onClick = null) },
                            headlineContent = { Text(label) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("Close") } },
        )
    }
    if (dueDialog) {
        AlertDialog(
            onDismissRequest = { dueDialog = false },
            title = { Text("Due date reminders") },
            text = {
                Column {
                    Text(
                        "For assignments, quizzes and discussions you haven't handed in yet.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DueReminder.entries.forEach { r ->
                        ListItem(
                            modifier = Modifier.clickable {
                                app.settings.update { it.copy(dueReminders = r) }
                                SyncWorker.schedule(ctx)
                                dueDialog = false
                                if (r != DueReminder.OFF && !allowed) askPermission()
                            },
                            leadingContent = { RadioButton(selected = s.dueReminders == r, onClick = null) },
                            headlineContent = { Text(r.label) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dueDialog = false }) { Text("Close") } },
        )
    }
    if (logoutDialog) {
        AlertDialog(
            onDismissRequest = { logoutDialog = false },
            title = { Text("Log out?") },
            text = { Text("Your access token and offline content will be removed from this phone. Synced calendar events stay unless you remove them first.") },
            confirmButton = {
                TextButton(onClick = {
                    logoutDialog = false
                    app.signOut()
                }) { Text("Log out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { logoutDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Toggle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
