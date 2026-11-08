package com.rafat.canvos.ui.course

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.OfflinePin
import com.rafat.canvos.CanvosApp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rafat.canvos.data.api.CanvasFile
import com.rafat.canvos.data.api.Folder
import com.rafat.canvos.data.api.ModuleItem
import com.rafat.canvos.data.api.User
import com.rafat.canvos.ui.common.Avatar
import com.rafat.canvos.ui.common.CanvosTopBar
import com.rafat.canvos.ui.common.EmptyState
import com.rafat.canvos.ui.common.Loaded
import com.rafat.canvos.ui.common.SectionHeader
import com.rafat.canvos.ui.common.TypeIcon
import com.rafat.canvos.ui.common.readable
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.nav.AssignmentRoute
import com.rafat.canvos.ui.nav.ComposeRoute
import com.rafat.canvos.ui.nav.DiscussionRoute
import com.rafat.canvos.ui.nav.FileActions
import com.rafat.canvos.ui.nav.FilesRoute
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.Navigator
import com.rafat.canvos.ui.nav.PageRoute
import com.rafat.canvos.ui.nav.WebRoute
import com.rafat.canvos.ui.notifications.StreamRow
import com.rafat.canvos.ui.theme.Success
import com.rafat.canvos.util.ago
import com.rafat.canvos.util.dateTime
import com.rafat.canvos.util.formatSize
import com.rafat.canvos.util.htmlToText
import com.rafat.canvos.util.pointsText
import com.rafat.canvos.util.pretty
import com.rafat.canvos.util.toInstant
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ modules

fun moduleItemIcon(type: String): ImageVector = when (type) {
    "Assignment" -> Icons.AutoMirrored.Outlined.Assignment
    "Quiz" -> Icons.Outlined.Quiz
    "Discussion" -> Icons.Outlined.Forum
    "Page" -> Icons.Outlined.Description
    "File" -> Icons.AutoMirrored.Outlined.InsertDriveFile
    "ExternalUrl" -> Icons.Outlined.Link
    "ExternalTool" -> Icons.Outlined.Extension
    else -> Icons.Outlined.ViewModule
}

fun Navigator.openModuleItem(courseId: Long, item: ModuleItem, context: android.content.Context) {
    val cid = item.contentId
    when (item.type) {
        "Assignment" -> cid?.let { go(AssignmentRoute(courseId, it)) }
        "Discussion" -> cid?.let { go(DiscussionRoute(courseId, it)) }
        "Page" -> item.pageUrl?.let { go(PageRoute(courseId, it)) }
        "File" -> cid?.let { FileActions.openById(context, it, courseId) }
        "Quiz" -> go(WebRoute(cid?.let { repo().domain + "/courses/$courseId/quizzes/$it" } ?: item.htmlUrl ?: return, item.title))
        "ExternalUrl" -> item.externalUrl?.let { openBrowser(it) }
        "ExternalTool" -> item.htmlUrl?.let { go(WebRoute(it, item.title)) }
        "SubHeader" -> Unit
        else -> item.htmlUrl?.let { go(WebRoute(it, item.title)) }
    }
}

@Composable
fun ModulesScreen(courseId: Long) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = rememberLoader("modules-$courseId") { modules(courseId) }
    val collapsed = remember { mutableStateMapOf<Long, Boolean>() }
    CourseScaffold(courseId, "Modules") { course, pad ->
        val color = readable(course.colorOr(MaterialTheme.colorScheme.primary))
        Loaded(vm, Modifier.padding(pad)) { mods ->
            if (mods.isEmpty()) {
                EmptyState(Icons.Outlined.ViewModule, "No modules", "This course doesn't use modules.")
                return@Loaded
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(mods, key = { it.id }) { m ->
                    val open = collapsed[m.id] != true
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { collapsed[m.id] = open }.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name.trim(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                val sub = listOfNotNull(
                                    when (m.state) { "locked" -> "Locked"; "completed" -> "Completed"; else -> null },
                                    m.unlockAt.toInstant()?.takeIf { it.isAfter(java.time.Instant.now()) }?.let { "Unlocks ${it.dateTime()}" },
                                    (m.items?.size ?: m.itemsCount).let { n -> if (n == 1) "1 item" else "$n items" },
                                ).joinToString(" · ")
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (m.state == "locked") Icon(Icons.Outlined.Lock, "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (m.state == "completed") Icon(Icons.Outlined.CheckCircle, "Completed", tint = Success)
                            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "Collapse" else "Expand")
                        }
                        AnimatedVisibility(open) {
                            Column {
                                m.items.orEmpty().sortedBy { it.position }.forEach { item ->
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    ModuleItemRow(item, color) {
                                        val locked = item.contentDetails?.lockedForUser == true
                                        if (locked) {
                                            Toast.makeText(ctx, item.contentDetails.lockExplanation?.htmlToText(300)?.ifBlank { null } ?: "This item is locked.", Toast.LENGTH_LONG).show()
                                        } else {
                                            if (item.completionRequirement?.type == "must_view" && item.completionRequirement.completed != true) {
                                                scope.launch { repo().markModuleItemRead(courseId, m.id, item.id) }
                                            }
                                            nav.openModuleItem(courseId, item, ctx)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleItemRow(item: ModuleItem, color: Color, onClick: () -> Unit) {
    if (item.type == "SubHeader") {
        Text(
            item.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = (16 + item.indent * 16).dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        return
    }
    val locked = item.contentDetails?.lockedForUser == true
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (12 + item.indent.coerceAtMost(4) * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (locked) Icons.Outlined.Lock else moduleItemIcon(item.type), null, tint = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val d = item.contentDetails
            val meta = listOfNotNull(
                d?.dueAt.toInstant()?.let { "Due ${it.dateTime()}" },
                pointsText(d?.pointsPossible),
                item.completionRequirement?.type?.let { requirementLabel(it, item.completionRequirement.minScore) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        val req = item.completionRequirement
        if (req != null) {
            Icon(
                if (req.completed == true) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                if (req.completed == true) "Done" else "Not done",
                tint = if (req.completed == true) Success else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun requirementLabel(type: String, min: Double?): String = when (type) {
    "must_view" -> "View"
    "must_submit" -> "Submit"
    "must_contribute" -> "Contribute"
    "min_score" -> "Score at least ${min?.pretty() ?: ""}"
    "must_mark_done" -> "Mark done"
    else -> type
}

// ------------------------------------------------------------------ files

fun fileIcon(f: CanvasFile): ImageVector {
    val ct = f.contentType.orEmpty()
    val ext = f.name.substringAfterLast('.', "").lowercase()
    return when {
        ct.startsWith("image/") -> Icons.Outlined.Image
        ct == "application/pdf" || ext == "pdf" -> Icons.Outlined.PictureAsPdf
        ct.startsWith("video/") -> Icons.Outlined.VideoFile
        ct.startsWith("audio/") -> Icons.Outlined.AudioFile
        ext in setOf("ppt", "pptx", "key", "odp") -> Icons.Outlined.Slideshow
        ext in setOf("xls", "xlsx", "csv", "ods") -> Icons.Outlined.TableChart
        ext in setOf("zip", "rar", "7z", "tar", "gz") -> Icons.Outlined.FolderZip
        ext in setOf("doc", "docx", "txt", "rtf", "odt") -> Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

data class FolderData(val folder: Folder, val folders: List<Folder>, val files: List<CanvasFile>)

@Composable
fun FilesScreen(courseId: Long, folderId: Long, title: String) {
    val nav = LocalNav.current
    val vm = rememberLoader("files-$courseId-$folderId") {
        val f = if (folderId == 0L) rootFolder(courseId.takeIf { it != 0L }) else folder(folderId)
        val (folders, files) = folderContents(f.id)
        FolderData(f, folders, files)
    }
    val content: @Composable (Color, androidx.compose.foundation.layout.PaddingValues) -> Unit = { color, pad ->
        Loaded(vm, Modifier.padding(pad)) { d ->
            if (d.folders.isEmpty() && d.files.isEmpty()) {
                EmptyState(Icons.Outlined.FolderOff, "This folder is empty")
                return@Loaded
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(d.folders, key = { "d${it.id}" }) { f ->
                    ListItem(
                        modifier = Modifier.clickable { nav.go(FilesRoute(courseId, f.id, f.name)) },
                        leadingContent = { TypeIcon(if (f.lockedForUser) Icons.Outlined.Lock else Icons.Outlined.Folder, color) },
                        headlineContent = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(listOfNotNull(
                                if (f.foldersCount > 0) "${f.foldersCount} folders" else null,
                                "${f.filesCount} files",
                            ).joinToString(" · "))
                        },
                    )
                }
                items(d.files, key = { "f${it.id}" }) { f -> FileRow(f, color) }
            }
        }
    }
    if (courseId == 0L) {
        androidx.compose.material3.Scaffold(topBar = { CanvosTopBar(title.ifBlank { "My files" }, onBack = { nav.back() }) }) { pad ->
            content(MaterialTheme.colorScheme.primary, pad)
        }
    } else {
        CourseScaffold(courseId, title.ifBlank { "Files" }) { course, pad -> content(readable(course.colorOr(MaterialTheme.colorScheme.primary)), pad) }
    }
}

@Composable
private fun FileRow(f: CanvasFile, color: Color) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val offline = CanvosApp.instance.offline.files
    val changes by offline.changes.collectAsState()
    val saved = remember(f.id, f.size, f.updatedAt, changes) { offline.current(f) != null }
    ListItem(
        modifier = Modifier.clickable { FileActions.open(ctx, f) },
        leadingContent = {
            val thumb = f.thumbnailUrl
            if (thumb != null && !f.lockedForUser) {
                AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
            } else TypeIcon(if (f.lockedForUser) Icons.Outlined.Lock else fileIcon(f), color)
        },
        headlineContent = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (saved) {
                    Icon(Icons.Outlined.OfflinePin, "Available offline", Modifier.size(15.dp), tint = Success)
                    Spacer(Modifier.width(4.dp))
                }
                Text(listOfNotNull(formatSize(f.size).ifBlank { null }, f.updatedAt.toInstant()?.ago()).joinToString(" · "))
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "File options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Save to Downloads") }, leadingIcon = { Icon(Icons.Outlined.Download, null) },
                        onClick = { menu = false; FileActions.saveToDownloads(ctx, f) })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menu = false; FileActions.share(ctx, f) })
                }
            }
        },
    )
}

// ------------------------------------------------------------------ people

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(courseId: Long) {
    val nav = LocalNav.current
    val vm = rememberLoader("people-$courseId") { people(courseId) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<User?>(null) }
    CourseScaffold(courseId, "People") { _, pad ->
        Loaded(vm, Modifier.padding(pad)) { list ->
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.People, "No people to show")
                return@Loaded
            }
            val filtered = list.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            val groups = filtered.groupBy { roleOf(it) }.toSortedMap(compareBy { roleOrder(it) })
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    OutlinedTextField(
                        query, { query = it }, placeholder = { Text("Search ${list.size} people") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                groups.forEach { (role, users) ->
                    item(key = "r$role") { SectionHeader("$role (${users.size})") }
                    items(users.sortedBy { it.sortableName ?: it.name }, key = { "u${it.id}-$role" }) { u ->
                        ListItem(
                            modifier = Modifier.clickable { selected = u },
                            leadingContent = { Avatar(u.avatarUrl, u.name) },
                            headlineContent = { Text(u.name + (u.pronouns?.let { " ($it)" } ?: "")) },
                        )
                    }
                }
            }
        }
    }
    selected?.let { u ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(u.avatarUrl, u.name, 88.dp)
                Text(u.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                u.pronouns?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(roleOf(u), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (u.id != repo().self?.id) {
                    Button(onClick = {
                        selected = null
                        nav.go(ComposeRoute(courseId = courseId, recipientId = u.id.toString(), recipientName = u.name))
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Send message")
                    }
                }
            }
        }
    }
}

private fun roleOf(u: User): String = when (u.enrollments.firstOrNull()?.type) {
    "TeacherEnrollment" -> "Teachers"
    "TaEnrollment" -> "Teaching assistants"
    "DesignerEnrollment" -> "Designers"
    "ObserverEnrollment" -> "Observers"
    "StudentEnrollment" -> "Students"
    else -> u.enrollments.firstOrNull()?.role ?: "Others"
}

private fun roleOrder(r: String) = listOf("Teachers", "Teaching assistants", "Designers", "Students", "Observers").indexOf(r).let { if (it < 0) 99 else it }

// ------------------------------------------------------------------ quizzes & feed

@Composable
fun QuizzesScreen(courseId: Long) {
    val nav = LocalNav.current
    val vm = rememberLoader("quizzes-$courseId") { quizzes(courseId) }
    CourseScaffold(courseId, "Quizzes") { course, pad ->
        val color = readable(course.colorOr(MaterialTheme.colorScheme.primary))
        Loaded(vm, Modifier.padding(pad)) { list ->
            val visible = list.filter { it.published != false }
            if (visible.isEmpty()) {
                EmptyState(Icons.Outlined.Quiz, "No quizzes")
                return@Loaded
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible.sortedBy { it.dueAt ?: "9999" }, key = { it.id }) { q ->
                    ListItem(
                        modifier = Modifier.clickable { nav.go(WebRoute(q.htmlUrl ?: (repo().domain + "/courses/$courseId/quizzes/${q.id}"), q.title)) },
                        leadingContent = { TypeIcon(if (q.lockedForUser) Icons.Outlined.Lock else Icons.Outlined.Quiz, color) },
                        headlineContent = { Text(q.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(listOfNotNull(
                                q.dueAt.toInstant()?.let { "Due ${it.dateTime()}" } ?: "No due date",
                                pointsText(q.pointsPossible),
                                q.questionCount?.let { "$it questions" },
                                q.timeLimit?.let { "$it min" },
                            ).joinToString(" · "))
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun CourseFeedScreen(courseId: Long) {
    val vm = rememberLoader("feed-$courseId") { activityStream(courseId) }
    CourseScaffold(courseId, "Recent activity") { course, pad ->
        Loaded(vm, Modifier.padding(pad)) { list ->
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.Notifications, "No recent activity")
                return@Loaded
            }
            LazyColumn(Modifier.fillMaxSize()) { items(list, key = { it.id }) { StreamRow(it, course) } }
        }
    }
}
