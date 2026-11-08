package com.rafat.canvos.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rafat.canvos.data.CourseInfo
import com.rafat.canvos.data.Palette
import com.rafat.canvos.data.api.StreamItem
import com.rafat.canvos.ui.common.EmptyState
import com.rafat.canvos.ui.common.Loaded
import com.rafat.canvos.ui.common.TypeIcon
import com.rafat.canvos.ui.common.readable
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.nav.ConversationRoute
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.Navigator
import com.rafat.canvos.util.ago
import com.rafat.canvos.util.htmlToText
import com.rafat.canvos.util.pretty
import com.rafat.canvos.util.toInstant

fun streamIcon(type: String): ImageVector = when (type) {
    "Announcement" -> Icons.Outlined.Campaign
    "DiscussionTopic", "DiscussionEntry" -> Icons.Outlined.Forum
    "Submission" -> Icons.Outlined.AssignmentTurnedIn
    "Conversation" -> Icons.Outlined.Mail
    "WebConference" -> Icons.Outlined.Videocam
    "Collaboration" -> Icons.Outlined.Groups
    "AssessmentRequest" -> Icons.Outlined.RateReview
    else -> Icons.Outlined.Notifications
}

fun Navigator.openStreamItem(item: StreamItem) {
    when {
        item.type == "Conversation" && item.conversationId != null -> go(ConversationRoute(item.conversationId))
        else -> openLink(item.htmlUrl)
    }
}

private val filters = listOf(
    "All" to null,
    "Announcements" to setOf("Announcement"),
    "Discussions" to setOf("DiscussionTopic", "DiscussionEntry"),
    "Grades" to setOf("Submission"),
    "Messages" to setOf("Conversation", "Message"),
)

data class StreamData(val items: List<StreamItem>, val courses: Map<Long, CourseInfo>)

@Composable
fun NotificationsScreen() {
    val vm = rememberLoader("stream") { force ->
        val items = activityStream()
        val courses = runCatching { allCourses() }.getOrDefault(emptyList()).associateBy { it.id }
        StreamData(items, courses)
    }
    var filter by rememberSaveable { mutableStateOf(0) }
    Loaded(vm) { data ->
        val types = filters[filter].second
        val list = data.items.filter { types == null || it.type in types }
            .sortedByDescending { it.updatedAt ?: it.createdAt }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEachIndexed { i, (label, _) -> FilterChip(filter == i, { filter = i }, label = { Text(label) }) }
            }
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.NotificationsNone, "No notifications", "Announcements, grades and replies from your courses will show up here.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { StreamRow(it, data.courses[it.courseId]) }
                }
            }
        }
    }
}

@Composable
fun StreamRow(item: StreamItem, course: CourseInfo?) {
    val nav = LocalNav.current
    val color = readable(Color(course?.color ?: Palette.fallback(item.courseId ?: 0)))
    val unread = item.readState == false
    val preview = remember(item.message) { item.message.htmlToText(160) }
    val title = when (item.type) {
        "Submission" -> item.assignment?.name ?: item.title ?: "Submission"
        else -> item.title ?: item.type
    }
    Row(
        Modifier.fillMaxWidth().clickable { nav.openStreamItem(item) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TypeIcon(streamIcon(item.type), color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    course?.displayName ?: if (item.type == "Conversation") "Inbox" else "",
                    color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                (item.updatedAt ?: item.createdAt).toInstant()?.let {
                    Text(it.ago(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                title, style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val sub = if (item.type == "Submission") {
                listOfNotNull(
                    item.grade?.let { "Grade: $it" } ?: item.score?.let { "Score: ${it.pretty()}" },
                    item.assignment?.pointsPossible?.let { "out of ${it.pretty()}" },
                ).joinToString(" ").ifBlank { preview }
            } else preview
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (unread) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
