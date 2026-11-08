package com.rafat.canvos.ui.inbox

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rafat.canvos.data.api.Recipient
import com.rafat.canvos.ui.common.Avatar
import com.rafat.canvos.ui.common.CanvosTopBar
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.common.userMessage
import com.rafat.canvos.ui.nav.ComposeRoute
import com.rafat.canvos.ui.nav.LocalNav
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ComposeScreen(route: ComposeRoute) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val coursesVm = rememberLoader("compose-courses") { dashboardCourses() }
    val courses = coursesVm.state.collectAsState().value.data.orEmpty()
    var courseId by rememberSaveable { mutableStateOf(route.courseId.takeIf { it != 0L }) }
    var courseMenu by remember { mutableStateOf(false) }
    val recipients = remember {
        mutableStateListOf<Recipient>().apply {
            if (route.recipientId.isNotBlank()) add(Recipient(id = route.recipientId, name = route.recipientName.ifBlank { "Recipient" }))
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Recipient>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var subject by rememberSaveable { mutableStateOf(route.subject) }
    var body by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(query, courseId) {
        val cid = courseId
        if (cid == null || query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        searching = true
        results = runCatching { repo().searchRecipients(cid, query.trim()) }.getOrDefault(emptyList())
            .filter { r -> recipients.none { it.id == r.id } }
        searching = false
    }

    fun send() {
        sending = true
        scope.launch {
            try {
                repo().createConversation(courseId, recipients.map { it.id }, subject.trim(), body.trim())
                InboxRefresh.tick.value++
                Toast.makeText(ctx, "Message sent", Toast.LENGTH_SHORT).show()
                nav.back()
            } catch (e: Exception) {
                Toast.makeText(ctx, e.userMessage(), Toast.LENGTH_LONG).show()
            } finally {
                sending = false
            }
        }
    }

    Scaffold(topBar = {
        CanvosTopBar("New message", onBack = { nav.back() }, actions = {
            IconButton(enabled = !sending && recipients.isNotEmpty() && body.isNotBlank() && courseId != null, onClick = { send() }) {
                if (sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        })
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                OutlinedButton(onClick = { courseMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(courses.firstOrNull { it.id == courseId }?.displayName ?: "Choose a course", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(courseMenu, { courseMenu = false }) {
                    courses.forEach { c ->
                        DropdownMenuItem(text = { Text(c.displayName) }, onClick = {
                            if (courseId != c.id) recipients.clear()
                            courseId = c.id; courseMenu = false
                        })
                    }
                }
            }
            if (recipients.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    recipients.forEach { r ->
                        InputChip(selected = false, onClick = { recipients.remove(r) }, label = { Text(r.name, maxLines = 1) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "Remove", Modifier.size(16.dp)) })
                    }
                }
            }
            OutlinedTextField(
                query, { query = it }, enabled = courseId != null,
                label = { Text(if (courseId == null) "Choose a course first" else "To: search people or groups") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
            )
            if (results.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        results.forEach { r ->
                            Row(
                                Modifier.fillMaxWidth().clickable { recipients.add(r); query = ""; results = emptyList() }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (r.type == "context") Icon(Icons.Outlined.Groups, null, Modifier.size(36.dp))
                                else Avatar(r.avatarUrl, r.name, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(r.name)
                                    r.userCount?.let { Text("$it people", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text("Message") }, minLines = 6, modifier = Modifier.fillMaxWidth())
        }
    }
}
