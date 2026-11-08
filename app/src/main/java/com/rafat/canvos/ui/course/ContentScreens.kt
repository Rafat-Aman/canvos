package com.rafat.canvos.ui.course

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rafat.canvos.data.CourseInfo
import com.rafat.canvos.data.api.AssignmentGroup
import com.rafat.canvos.data.api.CanvasException
import com.rafat.canvos.data.api.CanvasFile
import com.rafat.canvos.data.api.DiscussionEntry
import com.rafat.canvos.data.api.DiscussionParticipant
import com.rafat.canvos.data.api.DiscussionTopic
import com.rafat.canvos.data.api.DiscussionView
import com.rafat.canvos.ui.common.Avatar
import com.rafat.canvos.ui.common.EmptyState
import com.rafat.canvos.ui.common.HtmlContent
import com.rafat.canvos.ui.common.Loaded
import com.rafat.canvos.ui.common.Pill
import com.rafat.canvos.ui.common.SectionHeader
import com.rafat.canvos.ui.common.SimpleHtml
import com.rafat.canvos.ui.common.StatusChip
import com.rafat.canvos.ui.common.TypeIcon
import com.rafat.canvos.ui.common.readable
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.common.userMessage
import com.rafat.canvos.ui.dashboard.gradeLabel
import com.rafat.canvos.ui.nav.AssignmentRoute
import com.rafat.canvos.ui.nav.DiscussionRoute
import com.rafat.canvos.ui.nav.FileActions
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.PageRoute
import com.rafat.canvos.util.ago
import com.rafat.canvos.util.dateTime
import com.rafat.canvos.util.gradeText
import com.rafat.canvos.util.htmlToText
import com.rafat.canvos.util.pretty
import com.rafat.canvos.util.status
import com.rafat.canvos.util.toInstant
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ grades

data class GradesData(val course: CourseInfo, val groups: List<AssignmentGroup>)

@Composable
fun GradesScreen(courseId: Long) {
    val nav = LocalNav.current
    val vm = rememberLoader("grades-$courseId") { force -> GradesData(course(courseId, force), assignmentGroups(courseId)) }
    var showFinal by rememberSaveable { mutableStateOf(false) }
    CourseScaffold(courseId, "Grades") { course, pad ->
        val color = readable(course.colorOr(MaterialTheme.colorScheme.primary))
        Loaded(vm, Modifier.padding(pad)) { d ->
            val e = d.course.studentEnrollment
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Total", style = MaterialTheme.typography.labelLarge)
                            val total = if (d.course.course.hideFinalGrades) null else if (showFinal) {
                                listOfNotNull(e?.finalGrade ?: e?.grades?.finalGrade, (e?.finalScore ?: e?.grades?.finalScore)?.let { "${it.pretty()}%" }).joinToString(" · ")
                            } else d.course.gradeLabel()
                            Text(
                                total?.ifBlank { null } ?: if (d.course.course.hideFinalGrades) "Hidden by your teacher" else "No grade yet",
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!showFinal, { showFinal = false }, label = { Text("Graded work only") })
                                FilterChip(showFinal, { showFinal = true }, label = { Text("Count ungraded as 0") })
                            }
                        }
                    }
                }
                val weighted = d.groups.any { (it.groupWeight ?: 0.0) > 0 }
                d.groups.forEach { g ->
                    val list = g.assignments.filter { it.published }
                    if (list.isEmpty()) return@forEach
                    item(key = "g${g.id}") {
                        SectionHeader(g.name + if (weighted) " · ${(g.groupWeight ?: 0.0).pretty()}% of total" else "")
                    }
                    items(list, key = { "a${it.id}" }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { nav.go(AssignmentRoute(courseId, a.id)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.dueAt.toInstant()?.let { "Due ${it.dateTime()}" } ?: "No due date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    StatusChip(a.status())
                                }
                            }
                            val g2 = gradeText(a.submission, a.pointsPossible, a.gradingType)
                            Text(
                                g2 ?: ("– / " + (a.pointsPossible?.pretty() ?: "–")),
                                color = if (g2 != null) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (g2 != null) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                if (d.groups.all { g -> g.assignments.none { it.published } }) {
                    item { EmptyState(Icons.Outlined.Grade, "No graded work yet", inList = true) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ announcements & discussions

@Composable
fun AnnouncementsScreen(courseId: Long) {
    val nav = LocalNav.current
    val vm = rememberLoader("announcements-$courseId") { announcements(courseId) }
    CourseScaffold(courseId, "Announcements") { course, pad ->
        val color = readable(course.colorOr(MaterialTheme.colorScheme.primary))
        Loaded(vm, Modifier.padding(pad)) { list ->
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.Campaign, "No announcements", "Your teacher hasn't posted anything yet.")
                return@Loaded
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { t -> TopicRow(t, color, announcement = true) { nav.go(DiscussionRoute(courseId, t.id, true)) } }
            }
        }
    }
}

@Composable
fun DiscussionsScreen(courseId: Long) {
    val nav = LocalNav.current
    val vm = rememberLoader("discussions-$courseId") { discussions(courseId) }
    CourseScaffold(courseId, "Discussions") { course, pad ->
        val color = readable(course.colorOr(MaterialTheme.colorScheme.primary))
        Loaded(vm, Modifier.padding(pad)) { list ->
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.Forum, "No discussions")
                return@Loaded
            }
            LazyColumn(Modifier.fillMaxSize()) {
                val pinned = list.filter { it.pinned }
                if (pinned.isNotEmpty()) {
                    item { SectionHeader("Pinned") }
                    items(pinned, key = { "p${it.id}" }) { t -> TopicRow(t, color, false) { nav.go(DiscussionRoute(courseId, t.id)) } }
                    item { SectionHeader("Discussions") }
                }
                items(list.filter { !it.pinned }, key = { it.id }) { t -> TopicRow(t, color, false) { nav.go(DiscussionRoute(courseId, t.id)) } }
            }
        }
    }
}

@Composable
private fun TopicRow(t: DiscussionTopic, color: Color, announcement: Boolean, onClick: () -> Unit) {
    val unread = t.readState == "unread" || t.unreadCount > 0
    val preview = remember(t.message) { t.message.htmlToText(180) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        val author = t.author?.displayName ?: t.userName
        when {
            announcement && author != null -> Avatar(t.author?.avatarImageUrl, author, 40.dp)
            announcement -> TypeIcon(Icons.Outlined.Campaign, color)
            else -> TypeIcon(if (t.lockedForUser || t.locked) Icons.Outlined.Lock else if (t.pinned) Icons.Outlined.PushPin else Icons.Outlined.Forum, color)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.title ?: "Untitled", style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (t.readState == "unread") FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (unread) Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
            Text(
                listOfNotNull(
                    t.author?.displayName ?: t.userName,
                    (t.postedAt ?: t.delayedPostAt).toInstant()?.ago(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.isNotBlank()) Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!announcement || t.replyCount > 0) {
                Text(
                    listOfNotNull(
                        "${t.replyCount} ${if (t.replyCount == 1) "reply" else "replies"}",
                        if (t.unreadCount > 0) "${t.unreadCount} unread" else null,
                        t.lastReplyAt.toInstant()?.let { "last ${it.ago()}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = color,
                )
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

data class TopicData(val topic: DiscussionTopic, val view: DiscussionView?, val viewError: String?)

@Composable
fun DiscussionScreen(courseId: Long, topicId: Long, announcement: Boolean) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val vm = rememberLoader("topic-$courseId-$topicId") {
        val t = topic(courseId, topicId)
        val v = try { topicView(courseId, topicId) to null } catch (e: CanvasException) { null to e.userMessage() }
        TopicData(t, v.first, v.second)
    }
    var replyTo by remember { mutableStateOf<Long?>(null) }
    var replying by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    LaunchedEffect(topicId) { repo().markTopicRead(courseId, topicId) }

    CourseScaffold(courseId, if (announcement) "Announcement" else "Discussion") { course, pad ->
        val color = readable(course.colorOr(MaterialTheme.colorScheme.primary))
        Loaded(vm, Modifier.padding(pad)) { d ->
            val t = d.topic
            val people = d.view?.participants.orEmpty().associateBy { it.id }
            val canReply = !t.locked && !t.lockedForUser && (t.permissions?.reply ?: true)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                val authorName = t.author?.displayName ?: t.userName
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Imported/blueprint topics come without an author.
                    if (authorName != null) {
                        Avatar(t.author?.avatarImageUrl, authorName, 44.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        if (authorName != null) Text(authorName, fontWeight = FontWeight.SemiBold)
                        (t.postedAt ?: t.delayedPostAt).toInstant()?.let {
                            Text((if (authorName == null) "Posted " else "") + it.dateTime(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(t.title ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(10.dp))
                if (t.lockedForUser && t.message.isNullOrBlank()) {
                    Text(t.lockExplanation.htmlToText(400).ifBlank { "This topic is locked." }, Modifier.padding(16.dp))
                } else HtmlContent(t.message)
                Attachments(t.attachments)
                if (t.assignmentId != null) {
                    TextButton(onClick = { nav.go(AssignmentRoute(courseId, t.assignmentId)) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("View graded assignment ›") }
                }
                if (canReply) {
                    TextButton(onClick = { replyTo = null; replying = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Reply, null); Spacer(Modifier.width(6.dp)); Text("Reply")
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                val entries = d.view?.view.orEmpty()
                val unread = d.view?.unreadEntries.orEmpty().toSet()
                when {
                    d.viewError != null && t.requireInitialPost == true -> Text("Post a reply to see what others wrote.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    d.viewError != null -> Text(d.viewError, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    entries.isEmpty() && !announcement -> Text("No replies yet. Be the first!", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    entries.isNotEmpty() -> {
                        SectionHeader("Replies (${t.replyCount})")
                        flatten(entries).forEach { (e, depth) ->
                            EntryView(e, depth, people, e.id in unread, color, canReply) { replyTo = e.id; replying = true }
                        }
                    }
                }
            }
        }
    }
    if (replying) {
        ReplyDialog(
            title = if (replyTo == null) "Reply to topic" else "Reply",
            sending = posting,
            onDismiss = { replying = false },
        ) { text ->
            posting = true
            scope.launch {
                try {
                    repo().reply(courseId, topicId, replyTo, textToHtml(text))
                    replying = false
                    vm.refresh()
                } catch (e: Exception) {
                    Toast.makeText(ctx, e.userMessage(), Toast.LENGTH_LONG).show()
                } finally {
                    posting = false
                }
            }
        }
    }
}

private fun flatten(list: List<DiscussionEntry>, depth: Int = 0): List<Pair<DiscussionEntry, Int>> =
    list.flatMap { listOf(it to depth) + flatten(it.replies, depth + 1) }

@Composable
private fun EntryView(
    e: DiscussionEntry, depth: Int, people: Map<Long, DiscussionParticipant>, unread: Boolean, color: Color,
    canReply: Boolean, onReply: () -> Unit,
) {
    val p = people[e.userId]
    val indent = (depth.coerceAtMost(4) * 18).dp
    Row(Modifier.fillMaxWidth().padding(start = 16.dp + indent, end = 16.dp, top = 10.dp, bottom = 4.dp)) {
        if (depth > 0) Box(Modifier.width(2.dp).height(40.dp).background(color.copy(alpha = 0.3f)))
        if (depth > 0) Spacer(Modifier.width(10.dp))
        Avatar(p?.avatarImageUrl, p?.displayName, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p?.displayName ?: "Unknown", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                if (unread) { Spacer(Modifier.width(6.dp)); Pill("New", MaterialTheme.colorScheme.primary) }
            }
            e.createdAt.toInstant()?.let { Text(it.ago(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(4.dp))
            if (e.deleted) Text("This reply was deleted.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            else SimpleHtml(e.message)
            Attachments(listOfNotNull(e.attachment) + e.attachments, padded = false)
            if (canReply && !e.deleted) {
                Text(
                    "Reply", color = color, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onReply).padding(vertical = 6.dp, horizontal = 2.dp),
                )
            }
        }
    }
}

@Composable
fun Attachments(files: List<CanvasFile>, padded: Boolean = true) {
    val ctx = LocalContext.current
    if (files.isEmpty()) return
    Column(Modifier.padding(horizontal = if (padded) 16.dp else 0.dp)) {
        files.distinctBy { it.id }.forEach { f ->
            AssistChip(
                onClick = { FileActions.open(ctx, f) },
                label = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
fun ReplyDialog(title: String, sending: Boolean, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it }, minLines = 5, enabled = !sending,
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Write your reply…") },
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank() && !sending, onClick = { onSend(text.trim()) }) { Text(if (sending) "Posting…" else "Post") } },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ pages & syllabus

@Composable
fun PagesScreen(courseId: Long) {
    val nav = LocalNav.current
    val vm = rememberLoader("pages-$courseId") { pages(courseId) }
    CourseScaffold(courseId, "Pages") { course, pad ->
        val color = readable(course.colorOr(MaterialTheme.colorScheme.primary))
        Loaded(vm, Modifier.padding(pad)) { list ->
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.Description, "No pages")
                return@Loaded
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(list.sortedByDescending { it.frontPage }, key = { it.url }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.go(PageRoute(courseId, p.url)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TypeIcon(if (p.frontPage) Icons.Outlined.Home else if (p.lockedForUser) Icons.Outlined.Lock else Icons.Outlined.Description, color)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(if (p.frontPage) "Front page" else null, p.updatedAt.toInstant()?.let { "Updated ${it.ago()}" }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun PageScreen(courseId: Long, url: String) {
    val vm = rememberLoader("page-$courseId-$url") { if (url.isBlank()) frontPage(courseId) else page(courseId, url) }
    val title = vm.state.collectAsState().value.data?.title
    CourseScaffold(courseId, title ?: if (url.isBlank()) "Front page" else "Page") { _, pad ->
        Loaded(vm, Modifier.padding(pad)) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                Text(p.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                if (p.lockedForUser) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(p.lockExplanation.htmlToText(500).ifBlank { "This page is locked." }, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                if (p.body.isNullOrBlank() && !p.lockedForUser) Text("This page is empty.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else HtmlContent(p.body)
            }
        }
    }
}

@Composable
fun SyllabusScreen(courseId: Long) {
    val vm = rememberLoader("syllabus-$courseId") { course(courseId, true) }
    CourseScaffold(courseId, "Syllabus") { _, pad ->
        Loaded(vm, Modifier.padding(pad)) { c ->
            val body = c.course.syllabusBody
            if (body.isNullOrBlank()) {
                EmptyState(Icons.AutoMirrored.Outlined.MenuBook, "No syllabus", "Your teacher hasn't added a syllabus to this course.")
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) { HtmlContent(body) }
            }
        }
    }
}
