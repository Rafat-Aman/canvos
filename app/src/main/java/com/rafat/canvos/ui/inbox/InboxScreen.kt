package com.rafat.canvos.ui.inbox

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.rafat.canvos.data.api.Conversation
import com.rafat.canvos.ui.common.Avatar
import com.rafat.canvos.ui.common.EmptyState
import com.rafat.canvos.ui.common.Loaded
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.common.userMessage
import com.rafat.canvos.ui.nav.ConversationRoute
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.util.ago
import com.rafat.canvos.util.toInstant
import kotlinx.coroutines.launch

val inboxScopes = listOf("inbox" to "Inbox", "unread" to "Unread", "starred" to "Starred", "sent" to "Sent", "archived" to "Archived")

/** Bumped after sending/replying so the inbox list reloads. */
object InboxRefresh {
    val tick = kotlinx.coroutines.flow.MutableStateFlow(0)
}

data class InboxData(val conversations: List<Conversation>, val courses: List<CourseInfo>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scopeIdx by rememberSaveable { mutableStateOf(0) }
    var courseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var courseMenu by remember { mutableStateOf(false) }
    val scopeKey = inboxScopes[scopeIdx].first
    val vm = rememberLoader("inbox-$scopeKey-$courseId") { force ->
        InboxData(conversations(scopeKey, courseId), runCatching { dashboardCourses(force) }.getOrDefault(emptyList()))
    }
    val tick by InboxRefresh.tick.collectAsState()
    val st by vm.state.collectAsState()
    LaunchedEffect(tick) { if (tick > 0) vm.refresh() }

    fun act(c: Conversation, starred: Boolean? = null, state: String? = null) {
        scope.launch {
            try {
                val updated = repo().updateConversation(c.id, starred, state)
                val removeFromList = (state == "archived" && scopeKey != "archived") || (state != null && state != "archived" && scopeKey == "archived") ||
                    (starred == false && scopeKey == "starred") || (state == "read" && scopeKey == "unread")
                vm.update { d ->
                    d.copy(conversations = if (removeFromList) d.conversations.filter { it.id != c.id } else d.conversations.map {
                        if (it.id == c.id) it.copy(starred = updated.starred, workflowState = updated.workflowState) else it
                    })
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, e.userMessage(), Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            inboxScopes.forEachIndexed { i, (_, label) -> FilterChip(scopeIdx == i, { scopeIdx = i }, label = { Text(label) }) }
            Box {
                val courses = st.data?.courses.orEmpty()
                FilterChip(
                    courseId != null, { courseMenu = true },
                    label = { Text(courses.firstOrNull { it.id == courseId }?.code?.ifBlank { null } ?: "All courses", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp)) },
                )
                DropdownMenu(courseMenu, { courseMenu = false }) {
                    DropdownMenuItem(text = { Text("All courses") }, onClick = { courseId = null; courseMenu = false })
                    courses.forEach { c ->
                        DropdownMenuItem(text = { Text(c.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { courseId = c.id; courseMenu = false })
                    }
                }
            }
        }
        Loaded(vm) { data ->
            if (data.conversations.isEmpty()) {
                EmptyState(Icons.Outlined.Inbox, "Nothing here", if (scopeKey == "inbox") "Messages from your teachers and classmates will appear here." else null)
                return@Loaded
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(data.conversations, key = { it.id }) { c ->
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        ConversationRow(
                            c,
                            Modifier.combinedClickable(
                                onClick = {
                                    if (c.workflowState == "unread") vm.update { d -> d.copy(conversations = d.conversations.map { if (it.id == c.id) it.copy(workflowState = "read") else it }) }
                                    nav.go(ConversationRoute(c.id))
                                },
                                onLongClick = { menu = true },
                            ),
                        )
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (c.starred) "Unstar" else "Star") },
                                leadingIcon = { Icon(if (c.starred) Icons.Outlined.StarOutline else Icons.Filled.Star, null) },
                                onClick = { menu = false; act(c, starred = !c.starred) },
                            )
                            val unread = c.workflowState == "unread"
                            DropdownMenuItem(
                                text = { Text(if (unread) "Mark as read" else "Mark as unread") },
                                leadingIcon = { Icon(if (unread) Icons.Outlined.MarkEmailRead else Icons.Outlined.MarkEmailUnread, null) },
                                onClick = { menu = false; act(c, state = if (unread) "read" else "unread") },
                            )
                            val archived = c.workflowState == "archived"
                            DropdownMenuItem(
                                text = { Text(if (archived) "Move to inbox" else "Archive") },
                                leadingIcon = { Icon(if (archived) Icons.Outlined.MoveToInbox else Icons.Outlined.Archive, null) },
                                onClick = { menu = false; act(c, state = if (archived) "read" else "archived") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: Conversation, modifier: Modifier) {
    val unread = c.workflowState == "unread"
    val selfId = repo().self?.id
    val others = c.participants.filter { it.id != selfId }
    val names = others.joinToString(", ") { it.name ?: "" }.ifBlank { c.participants.joinToString(", ") { it.name ?: "" } }
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Avatar(c.avatarUrl ?: others.firstOrNull()?.avatarUrl, others.firstOrNull()?.name ?: names, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    names.ifBlank { "No participants" }, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                (c.lastMessageAt ?: c.lastAuthoredMessageAt).toInstant()?.let {
                    Text(it.ago(), style = MaterialTheme.typography.labelSmall, color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                c.subject?.ifBlank { null } ?: "(No subject)", style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.lastMessage.orEmpty().replace('\n', ' '), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (c.starred) Icon(Icons.Filled.Star, "Starred", tint = Color(0xFFF2A900), modifier = Modifier.size(18.dp))
                if (unread) Box(Modifier.padding(start = 6.dp).size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
            c.contextName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
