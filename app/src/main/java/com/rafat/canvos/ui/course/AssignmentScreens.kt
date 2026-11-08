package com.rafat.canvos.ui.course

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.rafat.canvos.data.UploadSource
import com.rafat.canvos.data.api.Assignment
import com.rafat.canvos.data.api.RubricCriterion
import com.rafat.canvos.data.api.Submission
import com.rafat.canvos.ui.common.Avatar
import com.rafat.canvos.ui.common.EmptyState
import com.rafat.canvos.ui.common.HtmlContent
import com.rafat.canvos.ui.common.Loaded
import com.rafat.canvos.ui.common.MetaRow
import com.rafat.canvos.ui.common.Pill
import com.rafat.canvos.ui.common.SectionHeader
import com.rafat.canvos.ui.common.StatusChip
import com.rafat.canvos.ui.common.TypeIcon
import com.rafat.canvos.ui.common.readable
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.common.userMessage
import com.rafat.canvos.ui.nav.AssignmentRoute
import com.rafat.canvos.ui.nav.DiscussionRoute
import com.rafat.canvos.ui.nav.FileActions
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.SubmissionRoute
import com.rafat.canvos.ui.nav.SubmitRoute
import com.rafat.canvos.ui.nav.WebRoute
import com.rafat.canvos.ui.theme.Success
import com.rafat.canvos.util.SubmissionStatus
import com.rafat.canvos.util.dateTime
import com.rafat.canvos.util.formatSize
import com.rafat.canvos.util.gradeText
import com.rafat.canvos.util.htmlToText
import com.rafat.canvos.util.pointsText
import com.rafat.canvos.util.pretty
import com.rafat.canvos.util.status
import com.rafat.canvos.util.submissionTypeLabel
import com.rafat.canvos.util.toInstant
import kotlinx.coroutines.launch
import java.time.Instant

fun assignmentIcon(a: Assignment): ImageVector = when {
    a.isQuizAssignment || "online_quiz" in a.submissionTypes -> Icons.Outlined.Quiz
    "discussion_topic" in a.submissionTypes || a.discussionTopic != null -> Icons.Outlined.Forum
    "external_tool" in a.submissionTypes -> Icons.Outlined.Extension
    else -> Icons.AutoMirrored.Outlined.Assignment
}

fun dueText(a: Assignment): String = a.dueAt.toInstant()?.let { "Due ${it.dateTime()}" } ?: "No due date"

// ------------------------------------------------------------------ list

@Composable
fun AssignmentsScreen(courseId: Long) {
    val nav = LocalNav.current
    val vm = rememberLoader("assignments-$courseId") { assignmentGroups(courseId) }
    var byGroup by rememberSaveable { mutableStateOf(false) }
    CourseScaffold(courseId, "Assignments") { course, pad ->
        val color = readable(course.colorOr(MaterialTheme.colorScheme.primary))
        Loaded(vm, Modifier.padding(pad)) { groups ->
            val all = groups.flatMap { it.assignments }.filter { it.published }
            if (all.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Outlined.Assignment, "No assignments", "This course has no assignments yet.")
                return@Loaded
            }
            val sections: List<Pair<String, List<Assignment>>> = if (byGroup) {
                groups.map { g -> (g.name + (g.groupWeight?.takeIf { it > 0 }?.let { " · ${it.pretty()}%" } ?: "")) to g.assignments.filter { it.published } }
            } else bucketByDue(all)
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!byGroup, { byGroup = false }, label = { Text("By due date") })
                        FilterChip(byGroup, { byGroup = true }, label = { Text("By group") })
                    }
                }
                sections.filter { it.second.isNotEmpty() }.forEach { (title, list) ->
                    item(key = "h-$title") { SectionHeader("$title (${list.size})") }
                    items(list, key = { "$title-${it.id}" }) { a ->
                        AssignmentRow(a, color) { nav.go(AssignmentRoute(courseId, a.id)) }
                    }
                }
            }
        }
    }
}

/** Overdue / Upcoming / Undated / Past, like Canvas. */
fun bucketByDue(all: List<Assignment>): List<Pair<String, List<Assignment>>> {
    val now = Instant.now()
    val overdue = ArrayList<Assignment>(); val upcoming = ArrayList<Assignment>()
    val undated = ArrayList<Assignment>(); val past = ArrayList<Assignment>()
    for (a in all) {
        val due = a.dueAt.toInstant()
        val st = a.status()
        when {
            due == null -> undated += a
            due.isAfter(now) -> upcoming += a
            st == SubmissionStatus.MISSING || st == SubmissionStatus.NOT_SUBMITTED -> overdue += a
            else -> past += a
        }
    }
    return listOf(
        "Overdue" to overdue.sortedBy { it.dueAt },
        "Upcoming" to upcoming.sortedBy { it.dueAt },
        "Undated" to undated.sortedBy { it.name.lowercase() },
        "Past" to past.sortedByDescending { it.dueAt },
    )
}

@Composable
fun AssignmentRow(a: Assignment, color: Color, onClick: () -> Unit) {
    val st = a.status()
    val grade = gradeText(a.submission, a.pointsPossible, a.gradingType)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TypeIcon(if (a.lockedForUser) Icons.Outlined.Lock else assignmentIcon(a), color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(dueText(a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MetaRow {
                StatusChip(st)
                if (grade == null) pointsText(a.pointsPossible)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (grade != null) {
            Spacer(Modifier.width(8.dp))
            Text(grade, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }
    }
    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

// ------------------------------------------------------------------ details

@Composable
fun AssignmentScreen(courseId: Long, assignmentId: Long) {
    val nav = LocalNav.current
    val vm = rememberLoader("assignment-$courseId-$assignmentId") { assignment(courseId, assignmentId) }
    val st by vm.state.collectAsState()
    val a = st.data
    CourseScaffold(
        courseId, "Assignment",
        bottomBar = { if (a != null) AssignmentActions(courseId, a) },
    ) { course, pad ->
        val color = readable(course.colorOr(MaterialTheme.colorScheme.primary))
        Loaded(vm, Modifier.padding(pad)) { asg ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(asg.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    MetaRow {
                        pointsText(asg.pointsPossible)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        StatusChip(asg.status())
                    }
                    gradeText(asg.submission, asg.pointsPossible, asg.gradingType)?.let {
                        Card(
                            onClick = { nav.go(SubmissionRoute(courseId, asg.id)) },
                            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = color)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Your grade", style = MaterialTheme.typography.labelMedium)
                                    Text(it, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
                                }
                                Text("Feedback ›", color = color, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                HorizontalDivider()
                InfoRow("Due", asg.dueAt.toInstant()?.dateTime() ?: "No due date")
                asg.unlockAt.toInstant()?.takeIf { it.isAfter(Instant.now()) }?.let { InfoRow("Available from", it.dateTime()) }
                asg.lockAt.toInstant()?.let { InfoRow(if (it.isAfter(Instant.now())) "Available until" else "Closed", it.dateTime()) }
                if (asg.submissionTypes.isNotEmpty()) InfoRow("Submission", asg.submissionTypes.joinToString(", ") { submissionTypeLabel(it) })
                if (asg.allowedExtensions.isNotEmpty()) InfoRow("File types", asg.allowedExtensions.joinToString(", ") { ".$it" })
                asg.allowedAttempts?.takeIf { it > 0 }?.let { InfoRow("Attempts", "${asg.submission?.attempt ?: 0} of $it used") }
                asg.submission?.submittedAt.toInstant()?.let { InfoRow("Submitted", it.dateTime() + if (asg.submission?.late == true) " (late)" else "") }
                if (asg.lockedForUser) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(16.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(10.dp))
                            Text(asg.lockExplanation?.htmlToText(500)?.ifBlank { null } ?: "This assignment is locked.", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                if (asg.submission?.isSubmitted == true || asg.submission?.comments?.isNotEmpty() == true || asg.submission?.isGraded == true) {
                    OutlinedButton(onClick = { nav.go(SubmissionRoute(courseId, asg.id)) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Submission & feedback")
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                SectionHeader("Description")
                if (asg.description.isNullOrBlank()) {
                    Text("No description", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else HtmlContent(asg.description)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The bottom action: submit, take quiz, open discussion or launch tool. */
@Composable
private fun AssignmentActions(courseId: Long, a: Assignment) {
    val nav = LocalNav.current
    val types = a.submissionTypes
    val submittable = listOf("online_text_entry", "online_url", "online_upload").filter { it in types }
    val attemptsLeft = a.allowedAttempts?.let { it <= 0 || (a.submission?.attempt ?: 0) < it } ?: true
    val closed = a.lockAt.toInstant()?.isBefore(Instant.now()) == true
    val label: String
    val action: (() -> Unit)?
    when {
        a.isQuizAssignment || "online_quiz" in types -> {
            label = "Open quiz"
            action = { nav.go(WebRoute(a.htmlUrl ?: (repo().domain + "/courses/$courseId/quizzes/${a.quizId}"), a.name)) }
        }
        a.discussionTopic != null || "discussion_topic" in types -> {
            label = "Open discussion"
            action = a.discussionTopic?.let { t -> { nav.go(DiscussionRoute(courseId, t.id)) } }
        }
        "external_tool" in types || "student_annotation" in types || "media_recording" in types -> {
            label = "Open in Canvas"
            action = a.htmlUrl?.let { u -> { nav.go(WebRoute(u, a.name)) } }
        }
        submittable.isNotEmpty() -> {
            label = if (a.submission?.isSubmitted == true) "Resubmit" else "Submit assignment"
            action = if (a.lockedForUser || closed || !attemptsLeft) null else {
                { nav.go(SubmitRoute(courseId, a.id, submittable.joinToString(","))) }
            }
        }
        else -> return
    }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Button(onClick = { action?.invoke() }, enabled = action != null, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(label) }
            if (action == null && submittable.isNotEmpty()) {
                Text(
                    when {
                        !attemptsLeft -> "No attempts left"
                        closed -> "This assignment is closed"
                        else -> "This assignment is locked"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ submit

private data class PickedFile(val uri: Uri, val name: String, val size: Long, val mime: String)

@Composable
fun SubmitScreen(courseId: Long, assignmentId: Long, typesCsv: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val types = typesCsv.split(',').filter { it.isNotBlank() }
    var type by rememberSaveable { mutableStateOf(types.first()) }
    var text by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("") }
    val files = remember { mutableStateListOf<PickedFile>() }
    var busy by remember { mutableStateOf<String?>(null) }
    val asgVm = rememberLoader("assignment-$courseId-$assignmentId") { assignment(courseId, assignmentId) }
    val asg = asgVm.state.collectAsState().value.data
    val allowed = asg?.allowedExtensions.orEmpty().map { it.lowercase().trimStart('.') }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            val cr = ctx.contentResolver
            var name = "file"
            var size = -1L
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    if (!c.isNull(0)) name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            val ext = name.substringAfterLast('.', "").lowercase()
            if (allowed.isNotEmpty() && ext !in allowed) {
                Toast.makeText(ctx, "$name: only ${allowed.joinToString(", ") { ".$it" }} files are allowed", Toast.LENGTH_LONG).show()
            } else if (files.none { it.uri == uri }) {
                files += PickedFile(uri, name, size, cr.getType(uri) ?: "application/octet-stream")
            }
        }
    }

    val ready = when (type) {
        "online_text_entry" -> text.isNotBlank()
        "online_url" -> url.trim().let { it.startsWith("http://") || it.startsWith("https://") } && url.trim().length > 10
        else -> files.isNotEmpty()
    }

    fun submit() {
        busy = "Submitting…"
        scope.launch {
            try {
                val r = repo()
                when (type) {
                    "online_text_entry" -> r.submitText(courseId, assignmentId, textToHtml(text), comment)
                    "online_url" -> r.submitUrl(courseId, assignmentId, url.trim(), comment)
                    else -> r.submitFiles(courseId, assignmentId, files.map { f ->
                        UploadSource(f.name, f.size, f.mime) { ctx.contentResolver.openInputStream(f.uri) ?: error("Can't read ${f.name}") }
                    }, comment) { busy = it }
                }
                Toast.makeText(ctx, "Submitted!", Toast.LENGTH_LONG).show()
                asgVm.refresh()
                nav.back()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Submission failed: ${e.userMessage()}", Toast.LENGTH_LONG).show()
            } finally {
                busy = null
            }
        }
    }

    CourseScaffold(courseId, asg?.name ?: "Submit") { _, pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (types.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { t -> FilterChip(type == t, { type = t }, label = { Text(submissionTypeLabel(t)) }) }
                }
            }
            when (type) {
                "online_text_entry" -> OutlinedTextField(
                    text, { text = it }, label = { Text("Your answer") }, minLines = 10,
                    modifier = Modifier.fillMaxWidth(), enabled = busy == null,
                )
                "online_url" -> OutlinedTextField(
                    url, { url = it }, label = { Text("Website URL") }, placeholder = { Text("https://…") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Link, null) }, modifier = Modifier.fillMaxWidth(), enabled = busy == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowed.isNotEmpty()) Text("Allowed: " + allowed.joinToString(", ") { ".$it" }, style = MaterialTheme.typography.bodySmall)
                    files.forEach { f ->
                        Card(shape = RoundedCornerShape(12.dp)) {
                            ListItem(
                                headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(formatSize(f.size.takeIf { it >= 0 })) },
                                leadingContent = { Icon(Icons.Outlined.AttachFile, null) },
                                trailingContent = { IconButton(enabled = busy == null, onClick = { files.remove(f) }) { Icon(Icons.Outlined.Close, "Remove") } },
                            )
                        }
                    }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = busy == null) {
                        Icon(Icons.Outlined.UploadFile, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (files.isEmpty()) "Choose files" else "Add more files")
                    }
                }
            }
            OutlinedTextField(comment, { comment = it }, label = { Text("Comment (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth(), enabled = busy == null)
            Button(onClick = { submit() }, enabled = ready && busy == null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy != null) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(busy!!)
                } else Text("Submit")
            }
        }
    }
}

/** Plain text -> simple HTML paragraphs for text-entry submissions. */
fun textToHtml(text: String): String =
    text.trim().split(Regex("\\n\\s*\\n")).joinToString("") { para ->
        "<p>" + para.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</p>"
    }

// ------------------------------------------------------------------ submission & feedback

data class SubmissionData(val assignment: Assignment, val submission: Submission)

@Composable
fun SubmissionScreen(courseId: Long, assignmentId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = rememberLoader("submission-$courseId-$assignmentId") {
        SubmissionData(assignment(courseId, assignmentId), submission(courseId, assignmentId))
    }
    var comment by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    CourseScaffold(
        courseId, "Submission & feedback",
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(comment, { comment = it }, placeholder = { Text("Add a comment…") }, modifier = Modifier.weight(1f), maxLines = 4, enabled = !sending)
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = comment.isNotBlank() && !sending, onClick = {
                        sending = true
                        scope.launch {
                            try {
                                repo().addSubmissionComment(courseId, assignmentId, comment.trim())
                                comment = ""
                                vm.refresh()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, e.userMessage(), Toast.LENGTH_LONG).show()
                            } finally { sending = false }
                        }
                    }) { Text("Send") }
                }
            }
        },
    ) { course, pad ->
        val color = readable(course.colorOr(MaterialTheme.colorScheme.primary))
        Loaded(vm, Modifier.padding(pad)) { d ->
            val s = d.submission
            val a = d.assignment
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(a.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val g = gradeText(s, a.pointsPossible, a.gradingType)
                    Text(
                        g ?: if (s.isSubmitted) "Not graded yet" else "No submission",
                        style = MaterialTheme.typography.headlineMedium, color = if (g != null) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    MetaRow {
                        if (s.late) Pill("Late", com.rafat.canvos.ui.theme.Warning)
                        if (s.missing) Pill("Missing", com.rafat.canvos.ui.theme.Danger)
                        if (s.excused == true) Pill("Excused", Success)
                        s.pointsDeducted?.takeIf { it > 0 }?.let { Text("Late penalty: −${it.pretty()}", style = MaterialTheme.typography.bodySmall) }
                    }
                    s.submittedAt.toInstant()?.let { Text("Submitted ${it.dateTime()}" + (s.attempt?.let { n -> " · attempt $n" } ?: ""), style = MaterialTheme.typography.bodyMedium) }
                    s.gradedAt.toInstant()?.let { Text("Graded ${it.dateTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                // What was submitted
                when {
                    !s.body.isNullOrBlank() -> { SectionHeader("Your submission"); HtmlContent(s.body) }
                    !s.url.isNullOrBlank() -> {
                        SectionHeader("Your submission")
                        AssistChip(onClick = { com.rafat.canvos.ui.nav.openInBrowser(ctx, s.url) }, label = { Text(s.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Outlined.Link, null) }, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                if (s.attachments.isNotEmpty()) {
                    SectionHeader("Files")
                    s.attachments.forEach { f ->
                        ListItem(
                            modifier = Modifier.clickable { FileActions.open(ctx, f) },
                            headlineContent = { Text(f.name) }, supportingContent = { Text(formatSize(f.size)) },
                            leadingContent = { Icon(Icons.Outlined.AttachFile, null) },
                        )
                    }
                }
                if (a.rubric.isNotEmpty()) {
                    SectionHeader("Rubric")
                    a.rubric.forEach { RubricRow(it, s.rubricAssessment?.get(it.id), color) }
                }
                SectionHeader("Comments (${s.comments.size})")
                if (s.comments.isEmpty()) Text("No comments yet.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.comments.sortedBy { it.createdAt }.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Avatar(c.author?.avatarImageUrl, c.authorName ?: c.author?.displayName, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.authorName ?: c.author?.displayName ?: "Unknown", fontWeight = FontWeight.SemiBold)
                            c.createdAt.toInstant()?.let { Text(it.dateTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            com.rafat.canvos.ui.common.LinkifiedText(c.comment.orEmpty())
                            c.attachments.forEach { f ->
                                AssistChip(onClick = { FileActions.open(ctx, f) }, label = { Text(f.name, maxLines = 1) }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)) })
                            }
                            c.mediaComment?.url?.let { u -> AssistChip(onClick = { com.rafat.canvos.ui.nav.openInBrowser(ctx, u) }, label = { Text("Media comment") }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RubricRow(c: RubricCriterion, assessed: com.rafat.canvos.data.api.RubricAssessmentEntry?, color: Color) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(c.description ?: "Criterion", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(
                    (assessed?.points?.pretty() ?: "–") + " / " + (c.points?.pretty() ?: "–"),
                    color = color, fontWeight = FontWeight.Bold,
                )
            }
            val rating = c.ratings.firstOrNull { it.id == assessed?.ratingId }
            rating?.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            assessed?.comments?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
