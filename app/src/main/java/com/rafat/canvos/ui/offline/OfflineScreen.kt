package com.rafat.canvos.ui.offline

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.data.CourseInfo
import com.rafat.canvos.data.offline.LocalFile
import com.rafat.canvos.data.offline.OfflineSync
import com.rafat.canvos.data.offline.OfflineSyncWorker
import com.rafat.canvos.ui.common.CanvosTopBar
import com.rafat.canvos.ui.common.EmptyState
import com.rafat.canvos.ui.common.SectionHeader
import com.rafat.canvos.ui.common.TypeIcon
import com.rafat.canvos.ui.common.readable
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.course.fileIcon
import com.rafat.canvos.ui.nav.FileActions
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.OfflineFilesRoute
import com.rafat.canvos.util.ago
import com.rafat.canvos.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

private data class Usage(val files: List<LocalFile>, val totalBytes: Long) {
    val byCourse: Map<Long, Pair<Int, Long>> = files.groupBy { it.courseId }
        .mapValues { (_, l) -> l.size to l.sumOf { it.file.size ?: 0L } }
}

private val sizeChoices = listOf(25, 100, 250, 1000, 0)
private fun sizeLabel(mb: Int) = when {
    mb <= 0 -> "No limit"
    mb >= 1000 -> "${mb / 1000} GB"
    else -> "$mb MB"
}

/** Settings and status of offline content: which courses, Wi-Fi only, file size limit, storage. */
@Composable
fun OfflineScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val store = CanvosApp.instance.offline
    val s by store.prefs.state.collectAsState()
    val status by OfflineSync.status.collectAsState()
    val changes by store.files.changes.collectAsState()
    val coursesVm = rememberLoader("offline-courses") { force ->
        dashboardCourses(force) to runCatching { allCourses() }.getOrDefault(emptyList())
    }
    val courses = coursesVm.state.collectAsState().value.data
    val usage by produceState<Usage?>(null, changes, status.running) {
        value = withContext(Dispatchers.IO) { Usage(store.files.all(), store.bytesUsed()) }
    }
    var sizeDialog by remember { mutableStateOf(false) }
    var removeDialog by remember { mutableStateOf(false) }
    var showPast by rememberSaveable { mutableStateOf(false) }

    fun reschedule() = OfflineSyncWorker.schedule(ctx)

    Scaffold(topBar = { CanvosTopBar("Offline content", onBack = { nav.back() }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                StatusCard(
                    running = status.running, message = status.message, fraction = status.fraction,
                    enabled = s.enabled, lastSuccess = s.lastSuccess, lastError = s.lastError,
                    usage = usage, wifiOnly = s.wifiOnly,
                    onSync = {
                        OfflineSyncWorker.syncNow(ctx, userAsked = true)
                        Toast.makeText(ctx, "Updating offline content…", Toast.LENGTH_SHORT).show()
                    },
                    onFiles = { nav.go(OfflineFilesRoute) },
                )
            }
            item { SectionHeader("Settings") }
            item {
                Toggle(
                    Icons.Outlined.CloudDownload, "Keep courses available offline",
                    "Save pages, assignments, announcements, grades, modules and files so they open without internet",
                    s.enabled,
                ) { v ->
                    store.prefs.update { it.copy(enabled = v) }
                    reschedule()
                    if (v) OfflineSyncWorker.syncNow(ctx, userAsked = false)
                }
            }
            if (s.enabled) {
                item {
                    Toggle(
                        Icons.Outlined.Wifi, "Update on Wi-Fi only",
                        if (s.wifiOnly) "Updates by itself whenever you're on Wi-Fi" else "Also updates over mobile data",
                        s.wifiOnly,
                    ) { v -> store.prefs.update { it.copy(wifiOnly = v) }; reschedule() }
                }
                item {
                    Toggle(Icons.Outlined.Folder, "Download course files", "PDFs, slides and documents from Files, Modules and pages", s.downloadFiles) { v ->
                        store.prefs.update { it.copy(downloadFiles = v) }
                    }
                }
                if (s.downloadFiles) item {
                    ListItem(
                        modifier = Modifier.clickable { sizeDialog = true },
                        leadingContent = { Icon(Icons.Outlined.Straighten, null) },
                        headlineContent = { Text("Largest file to download") },
                        supportingContent = { Text(sizeLabel(s.maxFileMb)) },
                    )
                }
                item { SectionHeader("Courses") }
                val (dash, all) = courses ?: (emptyList<CourseInfo>() to emptyList())
                if (courses == null) item {
                    Text("Loading courses…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(dash, key = { "d${it.id}" }) { c ->
                    CourseToggle(c, s.isSelected(c.id, true), usage?.byCourse?.get(c.id)) { on ->
                        store.prefs.update { it.copy(excluded = if (on) it.excluded - c.id else it.excluded + c.id) }
                    }
                }
                val dashIds = dash.map { it.id }.toSet()
                val past = all.filter { it.id !in dashIds }.sortedBy { it.displayName.lowercase() }
                if (past.isNotEmpty()) {
                    item {
                        ListItem(
                            modifier = Modifier.clickable { showPast = !showPast },
                            headlineContent = { Text("Other and past courses (${past.size})") },
                            trailingContent = { Icon(if (showPast) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
                        )
                    }
                    if (showPast) items(past, key = { "p${it.id}" }) { c ->
                        CourseToggle(c, s.isSelected(c.id, false), usage?.byCourse?.get(c.id)) { on ->
                            store.prefs.update { it.copy(extra = if (on) it.extra + c.id else it.extra - c.id) }
                        }
                    }
                }
            }
            item { SectionHeader("Storage") }
            item {
                ListItem(
                    modifier = Modifier.clickable { nav.go(OfflineFilesRoute) },
                    leadingContent = { Icon(Icons.Outlined.FolderOpen, null) },
                    headlineContent = { Text("Downloaded files") },
                    supportingContent = { Text(usage?.let { "${it.files.size} files · ${formatSize(it.files.sumOf { f -> f.file.size ?: 0L })}" } ?: "…") },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Folder, null) },
                    headlineContent = { Text("Saved in the app's storage") },
                    supportingContent = { Text("${store.filesLocation}\nRemoved when you log out or uninstall Canvos.") },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { removeDialog = true },
                    leadingContent = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text("Remove offline content", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text(usage?.let { formatSize(it.totalBytes) + " used" } ?: "") },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (sizeDialog) {
        AlertDialog(
            onDismissRequest = { sizeDialog = false },
            title = { Text("Largest file to download") },
            text = {
                Column {
                    sizeChoices.forEach { mb ->
                        ListItem(
                            modifier = Modifier.clickable { store.prefs.update { it.copy(maxFileMb = mb) }; sizeDialog = false },
                            leadingContent = { RadioButton(selected = s.maxFileMb == mb, onClick = null) },
                            headlineContent = { Text(sizeLabel(mb)) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sizeDialog = false }) { Text("Close") } },
        )
    }
    if (removeDialog) {
        AlertDialog(
            onDismissRequest = { removeDialog = false },
            title = { Text("Remove offline content?") },
            text = { Text("Downloaded files and saved pages (${usage?.let { formatSize(it.totalBytes) } ?: "all"}) are deleted from this phone, and offline updates are turned off.") },
            confirmButton = {
                TextButton(onClick = {
                    removeDialog = false
                    store.prefs.update { it.copy(enabled = false) }
                    OfflineSyncWorker.cancel(ctx)
                    store.wipe(keepOwner = true)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removeDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusCard(
    running: Boolean, message: String, fraction: Float?, enabled: Boolean, lastSuccess: Long, lastError: String?,
    usage: Usage?, wifiOnly: Boolean, onSync: () -> Unit, onFiles: () -> Unit,
) {
    val (icon, title, color) = when {
        running -> Triple(Icons.Outlined.CloudSync, "Updating…", MaterialTheme.colorScheme.primary)
        !enabled -> Triple(Icons.Outlined.CloudDownload, "Offline content is off", MaterialTheme.colorScheme.onSurfaceVariant)
        lastError != null -> Triple(Icons.Outlined.ErrorOutline, "Last update didn't finish", MaterialTheme.colorScheme.error)
        lastSuccess == 0L -> Triple(Icons.Outlined.CloudDownload, "Not downloaded yet", MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple(Icons.Outlined.CloudDone, "Available offline", Color(0xFF2E7D32))
    }
    val subtitle = when {
        running -> message
        !enabled -> "Turn it on to keep your courses on this phone."
        lastError != null -> lastError + if (lastSuccess > 0) "\nLast complete update ${Instant.ofEpochMilli(lastSuccess).ago()}." else ""
        lastSuccess == 0L -> if (wifiOnly) "Starts the next time you're on Wi-Fi, or tap Update now." else "Starts shortly, or tap Update now."
        else -> "Updated ${Instant.ofEpochMilli(lastSuccess).ago()}" + if (wifiOnly) " · updates on Wi-Fi" else ""
    }
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = readable(color), modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (running) {
                Spacer(Modifier.height(12.dp))
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (usage != null && usage.files.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${usage.files.size} files · ${formatSize(usage.totalBytes)} on this phone",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSync, enabled = enabled && !running) { Text("Update now") }
                OutlinedButton(onClick = onFiles) { Text("Downloaded files") }
            }
        }
    }
}

@Composable
private fun CourseToggle(c: CourseInfo, on: Boolean, stats: Pair<Int, Long>?, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!on) },
        leadingContent = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.color))) },
        headlineContent = { Text(c.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(
                    c.code.takeIf { it.isNotBlank() && it != c.displayName },
                    stats?.let { (n, b) -> "$n files · ${formatSize(b)}" },
                ).joinToString(" · ").ifBlank { if (on) "Waiting for the next update" else "Not kept offline" },
            )
        },
        trailingContent = { Checkbox(checked = on, onCheckedChange = onChange) },
    )
}

@Composable
private fun Toggle(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

/** Every downloaded file, grouped by course, opened straight from the phone. */
@Composable
fun OfflineFilesScreen() {
    val nav = LocalNav.current
    val store = CanvosApp.instance.offline
    val changes by store.files.changes.collectAsState()
    val groups by produceState<List<Pair<String, List<LocalFile>>>?>(null, changes) {
        value = withContext(Dispatchers.IO) {
            store.files.all().filter { store.files.fileOf(it).isFile }
                .groupBy { it.courseName }.toList().sortedBy { it.first.lowercase() }
                .map { (name, l) -> name to l.sortedBy { it.path.lowercase() } }
        }
    }
    Scaffold(topBar = { CanvosTopBar("Downloaded files", onBack = { nav.back() }) }) { pad ->
        val g = groups
        when {
            g == null -> Box(Modifier.fillMaxSize().padding(pad))
            g.isEmpty() -> EmptyState(
                Icons.AutoMirrored.Outlined.InsertDriveFile, "No files downloaded yet",
                "Course files download by themselves on Wi-Fi when offline content is on.",
                Modifier.padding(pad),
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                g.forEach { (course, files) ->
                    item(key = "h$course") {
                        SectionHeader("$course · ${formatSize(files.sumOf { it.file.size ?: 0L })}")
                    }
                    items(files, key = { "f${it.file.id}" }) { f -> SavedFileRow(f) }
                }
            }
        }
    }
}

@Composable
private fun SavedFileRow(f: LocalFile) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val folder = f.path.split('/').drop(1).dropLast(1).joinToString(" / ")
    ListItem(
        modifier = Modifier.clickable { FileActions.openSaved(ctx, f) },
        leadingContent = { TypeIcon(fileIcon(f.file), MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(f.file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(folder.ifBlank { null }, formatSize(f.file.size)).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "File options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Save to Downloads") }, leadingIcon = { Icon(Icons.Outlined.Download, null) },
                        onClick = { menu = false; FileActions.saveToDownloads(ctx, f.file) })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menu = false; FileActions.share(ctx, f.file) })
                }
            }
        },
    )
}
