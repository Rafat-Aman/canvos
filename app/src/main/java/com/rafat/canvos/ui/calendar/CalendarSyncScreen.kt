package com.rafat.canvos.ui.calendar

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rafat.canvos.data.CalendarSync
import com.rafat.canvos.data.SyncWorker
import com.rafat.canvos.ui.common.CanvosTopBar
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.openInBrowser
import com.rafat.canvos.util.ago
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.Instant

private val reminderChoices = listOf(-1 to "No reminder", 0 to "At due time", 15 to "15 minutes before", 60 to "1 hour before", 180 to "3 hours before", 1440 to "1 day before")

@Composable
fun CalendarSyncScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { CalendarSync.Prefs(ctx) }
    val status by CalendarSync.status.collectAsState()
    var hasPerm by remember { mutableStateOf(CalendarSync.hasPermission(ctx)) }
    var calendars by remember { mutableStateOf(CalendarSync.calendars(ctx)) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var calId by remember { mutableLongStateOf(prefs.calendarId) }
    var reminder by remember { mutableIntStateOf(prefs.reminderMinutes) }
    var includeEvents by remember { mutableStateOf(prefs.includeEvents) }
    var reminderMenu by remember { mutableStateOf(false) }
    var feedUrl by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = CalendarSync.hasPermission(ctx)
        calendars = CalendarSync.calendars(ctx)
    }
    // Refresh the list when coming back (e.g. after creating a calendar in Google Calendar).
    LifecycleResumeEffect(Unit) {
        hasPerm = CalendarSync.hasPermission(ctx)
        calendars = CalendarSync.calendars(ctx)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        feedUrl = runCatching { repo().profile().calendar?.ics }.getOrNull()
    }

    fun syncNow() {
        scope.launch {
            val msg = CalendarSync.sync(ctx, repo())
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(topBar = { CanvosTopBar("Sync to Google Calendar", onBack = { nav.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Keep a Google calendar updated", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Due dates and course events are added to the calendar you pick below, with reminders. " +
                                    "Your phone's Google account sync uploads them to Google Calendar.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = enabled,
                            enabled = hasPerm && calId >= 0,
                            onCheckedChange = {
                                enabled = it
                                prefs.enabled = it
                                SyncWorker.schedule(ctx)
                                if (it) syncNow()
                            },
                        )
                    }
                    if (!hasPerm) {
                        Button(onClick = {
                            permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }) { Text("Allow calendar access") }
                        return@Column
                    }
                    HorizontalDivider()
                    Text("Calendar", style = MaterialTheme.typography.labelLarge)
                    if (calendars.isEmpty()) {
                        Text(
                            "No calendars you can write to. Add your Google account under Settings → Passwords & accounts, " +
                                "and make sure Calendar sync is on for it.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                        )
                    }
                    calendars.groupBy { it.account }.forEach { (account, list) ->
                        Text(
                            account + if (list.first().isGoogle) "  (Google)" else "",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        list.forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                    calId = c.id
                                    prefs.calendarId = c.id
                                    if (enabled) syncNow()
                                }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = calId == c.id, onClick = null, modifier = Modifier.padding(8.dp))
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                                Spacer(Modifier.width(10.dp))
                                Text(c.name + if (c.isPrimary) " (main)" else "", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    Text(
                        "Tip: create a separate calendar called “Canvas” at calendar.google.com first. It keeps these events " +
                            "apart and you can hide it in one tap. New calendars can take a minute to show up here.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reminder", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Box {
                            TextButton(onClick = { reminderMenu = true }) { Text(reminderChoices.firstOrNull { it.first == reminder }?.second ?: "$reminder min before") }
                            DropdownMenu(reminderMenu, { reminderMenu = false }) {
                                reminderChoices.forEach { (m, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        reminder = m; prefs.reminderMinutes = m; reminderMenu = false
                                        if (enabled) syncNow()
                                    })
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Include course events", style = MaterialTheme.typography.bodyLarge)
                            Text("Classes, exams and other calendar events, not only due dates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(includeEvents, { includeEvents = it; prefs.includeEvents = it; if (enabled) syncNow() })
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { syncNow() }, enabled = !status.running && calId >= 0) {
                            if (status.running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sync now")
                        }
                        OutlinedButton(onClick = { scope.launch { CalendarSync.removeAll(ctx) } }, enabled = !status.running) {
                            Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Remove events")
                        }
                    }
                    val last = prefs.lastSync
                    val msg = status.message ?: prefs.lastResult
                    if (msg != null) {
                        Text(
                            msg + if (last > 0 && !status.running) " · ${Instant.ofEpochMilli(last).ago()}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Syncs automatically every few hours while enabled. Events cover the last 2 weeks and the next 5 months.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Or subscribe from Google Calendar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Adds your Canvas calendar feed to Google Calendar directly. Nothing to keep running on the phone, " +
                            "but Google only refreshes subscribed feeds every few hours and there are no reminders.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val url = feedUrl
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = url != null, onClick = {
                            val webcal = url!!.replaceFirst("https://", "webcal://")
                            openInBrowser(ctx, "https://calendar.google.com/calendar/r?cid=" + URLEncoder.encode(webcal, "UTF-8"))
                        }) { Text("Add to Google Calendar") }
                        OutlinedButton(enabled = url != null, onClick = { copy(ctx, url!!) }) {
                            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy link")
                        }
                    }
                    Text(
                        "Keep this link private: anyone with it can see your Canvas calendar.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Canvas calendar feed", text))
    Toast.makeText(ctx, "Link copied", Toast.LENGTH_SHORT).show()
}
