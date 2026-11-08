package com.rafat.canvos.ui.inbox

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rafat.canvos.data.api.ConversationMessage
import com.rafat.canvos.data.api.ConversationParticipant
import com.rafat.canvos.ui.common.Avatar
import com.rafat.canvos.ui.common.CanvosTopBar
import com.rafat.canvos.ui.common.LinkifiedText
import com.rafat.canvos.ui.common.Loaded
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.common.userMessage
import com.rafat.canvos.ui.nav.FileActions
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.util.dateTime
import com.rafat.canvos.util.toInstant
import kotlinx.coroutines.launch

@Composable
fun ConversationScreen(id: Long) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = rememberLoader("conversation-$id") {
        if (self == null) runCatching { profile() }
        conversation(id)
    }
    val st by vm.state.collectAsState()
    val conv = st.data
    var menu by remember { mutableStateOf(false) }
    var reply by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    fun update(starred: Boolean? = null, state: String? = null, then: () -> Unit = {}) {
        scope.launch {
            try {
                val u = repo().updateConversation(id, starred, state)
                vm.update { it.copy(starred = u.starred, workflowState = u.workflowState) }
                InboxRefresh.tick.value++
                then()
            } catch (e: Exception) {
                Toast.makeText(ctx, e.userMessage(), Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            CanvosTopBar(
                conv?.subject?.ifBlank { null } ?: "(No subject)", subtitle = conv?.contextName, onBack = { nav.back() },
                actions = {
                    if (conv != null) {
                        IconButton(onClick = { update(starred = !conv.starred) }) {
                            Icon(if (conv.starred) Icons.Filled.Star else Icons.Outlined.StarOutline, if (conv.starred) "Unstar" else "Star",
                                tint = if (conv.starred) Color(0xFFF2A900) else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Mark as unread") }, leadingIcon = { Icon(Icons.Outlined.MarkEmailUnread, null) },
                                onClick = { menu = false; update(state = "unread") { nav.back() } })
                            DropdownMenuItem(
                                text = { Text(if (conv.workflowState == "archived") "Move to inbox" else "Archive") },
                                leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                onClick = { menu = false; update(state = if (conv.workflowState == "archived") "read" else "archived") { nav.back() } },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (conv != null && conv.cannotReply != true) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        OutlinedTextField(
                            reply, { reply = it }, modifier = Modifier.weight(1f), placeholder = { Text("Reply…") },
                            maxLines = 6, shape = RoundedCornerShape(24.dp), enabled = !sending,
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            enabled = reply.isNotBlank() && !sending,
                            onClick = {
                                sending = true
                                scope.launch {
                                    try {
                                        repo().replyConversation(id, reply.trim())
                                        reply = ""
                                        vm.refresh()
                                        InboxRefresh.tick.value++
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, e.userMessage(), Toast.LENGTH_LONG).show()
                                    } finally {
                                        sending = false
                                    }
                                }
                            },
                            modifier = Modifier.size(52.dp),
                        ) {
                            if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Filled.Send, "Send")
                        }
                    }
                }
            }
        },
    ) { pad ->
        Loaded(vm, Modifier.padding(pad)) { c ->
            val people = c.participants.associateBy { it.id }
            val messages = c.messages.sortedBy { it.createdAt ?: "" }
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1) }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        c.participants.joinToString(", ") { it.name.orEmpty() }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                items(messages, key = { it.id }) { m -> MessageCard(m, people, repo().self?.id) }
            }
        }
    }
}

@Composable
private fun MessageCard(m: ConversationMessage, people: Map<Long, ConversationParticipant>, selfId: Long?) {
    val ctx = LocalContext.current
    val author = people[m.authorId]
    val mine = m.authorId != null && m.authorId == selfId
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(author?.avatarUrl, author?.name, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (mine) "You" else author?.name ?: "Unknown", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    m.createdAt.toInstant()?.let { Text(it.dateTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.size(8.dp))
            LinkifiedText(m.body.orEmpty().trim())
            m.attachments.forEach { f ->
                AssistChip(onClick = { FileActions.open(ctx, f) }, label = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)) })
            }
            m.mediaComment?.url?.let { url ->
                AssistChip(onClick = { com.rafat.canvos.ui.nav.openInBrowser(ctx, url) }, label = { Text(m.mediaComment.displayName ?: "Media message") })
            }
            m.forwardedMessages.forEach { fm ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Forwarded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinkifiedText(fm.body.orEmpty().trim())
                    }
                }
            }
        }
    }
}
