package com.rafat.canvos.ui.main

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.data.DueReminder
import com.rafat.canvos.data.Notifier
import com.rafat.canvos.data.SyncWorker
import com.rafat.canvos.ui.calendar.CalendarScreen
import com.rafat.canvos.ui.common.Avatar
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.course.StatusBarFor
import com.rafat.canvos.ui.dashboard.DashboardScreen
import com.rafat.canvos.ui.inbox.InboxScreen
import com.rafat.canvos.ui.nav.AllCoursesRoute
import com.rafat.canvos.ui.nav.CalendarSyncRoute
import com.rafat.canvos.ui.nav.ComposeRoute
import com.rafat.canvos.ui.nav.FilesRoute
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.OfflineRoute
import com.rafat.canvos.ui.settings.BackgroundAccess
import com.rafat.canvos.ui.nav.SettingsRoute
import com.rafat.canvos.ui.nav.openInBrowser
import com.rafat.canvos.ui.notifications.NotificationsScreen
import com.rafat.canvos.ui.todo.TodoScreen
import kotlinx.coroutines.launch

private data class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("Dashboard", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    Tab("Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Tab("To Do", Icons.Outlined.TaskAlt, Icons.Filled.TaskAlt),
    Tab("Notifications", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    Tab("Inbox", Icons.Outlined.Mail, Icons.Filled.Mail),
)

/** Lets other code (e.g. notification taps) switch the bottom tab. */
object MainTabs {
    val selected = kotlinx.coroutines.flow.MutableStateFlow(-1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val app = CanvosApp.instance
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val requested by MainTabs.selected.collectAsState()
    LaunchedEffect(requested) {
        if (requested >= 0) { tab = requested; MainTabs.selected.value = -1 }
    }
    var logout by remember { mutableStateOf(false) }
    val profileVm = rememberLoader("profile") { profile() }
    val profile = profileVm.state.collectAsState().value.data
    StatusBarFor(null)

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && tab != 0) { tab = 0 }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Column(Modifier.padding(24.dp)) {
                        Avatar(profile?.avatarUrl, profile?.name, 72.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(profile?.name ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            profile?.primaryEmail ?: profile?.loginId ?: "", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            repo().domain.removePrefix("https://"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    fun item(label: String, icon: ImageVector, onClick: () -> Unit): @Composable () -> Unit = {
                        NavigationDrawerItem(
                            label = { Text(label) }, icon = { Icon(icon, null) }, selected = false,
                            onClick = { scope.launch { drawer.close() }; onClick() },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    item("All courses", Icons.Outlined.School) { nav.go(AllCoursesRoute) }()
                    item("My files", Icons.Outlined.Folder) { nav.go(FilesRoute()) }()
                    item("Offline content", Icons.Outlined.CloudDownload) { nav.go(OfflineRoute) }()
                    item("Sync to Google Calendar", Icons.Outlined.Event) { nav.go(CalendarSyncRoute) }()
                    item("Settings", Icons.Outlined.Settings) { nav.go(SettingsRoute) }()
                    item("Open Canvas in browser", Icons.Outlined.Language) { openInBrowser(ctx, repo().domain) }()
                    item("Help", Icons.AutoMirrored.Outlined.Help) { openInBrowser(ctx, "https://community.canvaslms.com/t5/Student-Guide/tkb-p/student") }()
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    item("Log out", Icons.AutoMirrored.Outlined.Logout) { logout = true }()
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(tabs[tab].label, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            if (profile?.avatarUrl != null) Avatar(profile.avatarUrl, profile.name, 32.dp) else Icon(Icons.Outlined.Menu, "Menu")
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(if (tab == i) t.selectedIcon else t.icon, t.label) },
                            label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == 4) FloatingActionButton(onClick = { nav.go(ComposeRoute()) }) { Icon(Icons.Outlined.Edit, "New message") }
            },
        ) { pad ->
            Column(Modifier.padding(pad)) {
                when (tab) {
                    0 -> DashboardScreen()
                    1 -> CalendarScreen()
                    2 -> TodoScreen()
                    3 -> NotificationsScreen()
                    else -> InboxScreen()
                }
            }
        }
    }

    StartupPrompts()

    if (logout) {
        AlertDialog(
            onDismissRequest = { logout = false },
            title = { Text("Log out?") },
            text = { Text("Your access token and offline content will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { logout = false; app.signOut() }) {
                    Text("Log out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Once after signing in: ask for the notification permission (Android 13+), then offer
 * to lift battery limits, without which the 15-minute checks can be delayed for hours.
 */
@Composable
private fun StartupPrompts() {
    val ctx = LocalContext.current
    val app = CanvosApp.instance
    val prefs = remember { ctx.getSharedPreferences("prompts", Context.MODE_PRIVATE) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.edit().putBoolean("asked_notifications", true).apply()
        if (granted) SyncWorker.runOnce(ctx)
        step = 1
    }
    LaunchedEffect(step) {
        val s = app.settings.state.value
        val wanted = s.notifications || s.dueReminders != DueReminder.OFF
        when (step) {
            0 -> if (wanted && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(ctx) && !prefs.getBoolean("asked_notifications", false)) {
                perm.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else step = 1
            1 -> if (!wanted || !Notifier.canNotify(ctx) || BackgroundAccess.isUnrestricted(ctx) || prefs.getBoolean("asked_battery", false)) step = 3
            else step = 2
        }
    }
    if (step == 2) {
        fun done() {
            prefs.edit().putBoolean("asked_battery", true).apply()
            step = 3
        }
        AlertDialog(
            onDismissRequest = { done() },
            icon = { Icon(Icons.Outlined.NotificationsActive, null) },
            title = { Text("Get notifications on time") },
            text = {
                Text(
                    "Canvas can't send push notifications to this app, so Canvos checks for new announcements, " +
                        "messages, grades and due dates every 15 minutes. Battery saving can hold those checks back " +
                        "for hours. Allow Canvos to run in the background?",
                )
            },
            confirmButton = { TextButton(onClick = { done(); BackgroundAccess.request(ctx) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { done() }) { Text("Not now") } },
        )
    }
}
