package com.rafat.canvos.ui.todo

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rafat.canvos.data.CourseInfo
import com.rafat.canvos.data.Palette
import com.rafat.canvos.data.api.PlannerItem
import com.rafat.canvos.data.api.PlannerOverride
import com.rafat.canvos.ui.common.EmptyState
import com.rafat.canvos.ui.common.Loaded
import com.rafat.canvos.ui.common.Pill
import com.rafat.canvos.ui.common.SectionHeader
import com.rafat.canvos.ui.common.readable
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.common.userMessage
import com.rafat.canvos.ui.nav.EventRoute
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.Navigator
import com.rafat.canvos.ui.theme.Danger
import com.rafat.canvos.ui.theme.Success
import com.rafat.canvos.ui.theme.Warning
import com.rafat.canvos.util.dayHeader
import com.rafat.canvos.util.localDate
import com.rafat.canvos.util.pointsText
import com.rafat.canvos.util.time
import com.rafat.canvos.util.toInstant
import kotlinx.coroutines.launch
import java.time.LocalDate

fun plannerIcon(type: String): ImageVector = when (type) {
    "assignment", "sub_assignment" -> Icons.AutoMirrored.Outlined.Assignment
    "quiz" -> Icons.Outlined.Quiz
    "discussion_topic" -> Icons.Outlined.Forum
    "wiki_page" -> Icons.Outlined.Description
    "planner_note" -> Icons.Outlined.EditNote
    "calendar_event" -> Icons.Outlined.Event
    "announcement" -> Icons.Outlined.Campaign
    "assessment_request" -> Icons.Outlined.RateReview
    else -> Icons.Outlined.Event
}

fun PlannerItem.date(): LocalDate? = (plannable.dueAt ?: plannable.todoDate ?: plannable.startAt ?: plannableDate).toInstant()?.localDate()

/** Open a planner item in the right screen. */
fun Navigator.openPlannerItem(item: PlannerItem) {
    if (item.plannableType == "calendar_event") go(EventRoute(item.plannableId.toString()))
    else openLink(item.htmlUrl)
}

private val todoTypes = setOf("assignment", "sub_assignment", "quiz", "discussion_topic", "wiki_page", "planner_note", "assessment_request")

data class TodoData(val items: List<PlannerItem>, val courses: List<CourseInfo>)

@Composable
fun TodoScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = rememberLoader("todo") { force ->
        val today = LocalDate.now()
        val items = planner(today.minusDays(21), today.plusDays(43)).filter { it.plannableType in todoTypes }
        TodoData(items, runCatching { dashboardCourses(force) }.getOrDefault(emptyList()))
    }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var courseFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var filterMenu by remember { mutableStateOf(false) }

    fun toggle(item: PlannerItem, done: Boolean) {
        val key = item.plannableType to item.plannableId
        val prev = item.plannerOverride
        vm.update { d -> d.copy(items = d.items.map { if ((it.plannableType to it.plannableId) == key) it.copy(plannerOverride = (prev ?: PlannerOverride()).copy(markedComplete = done)) else it }) }
        scope.launch {
            try {
                val o = repo().setPlannerComplete(item, done)
                vm.update { d -> d.copy(items = d.items.map { if ((it.plannableType to it.plannableId) == key) it.copy(plannerOverride = o) else it }) }
            } catch (e: Exception) {
                vm.update { d -> d.copy(items = d.items.map { if ((it.plannableType to it.plannableId) == key) it.copy(plannerOverride = prev) else it }) }
                Toast.makeText(ctx, e.userMessage(), Toast.LENGTH_LONG).show()
            }
        }
    }

    Loaded(vm) { data ->
        val colors = data.courses.associate { it.id to it.color }
        val today = LocalDate.now()
        val visible = data.items
            .filter { courseFilter == null || it.courseId == courseFilter }
            .filter { showCompleted || !it.isComplete }
        val overdue = visible.filter { !it.isComplete && (it.date()?.isBefore(today) ?: false) }
            .sortedBy { it.date() }
        val upcoming = visible.filter { it !in overdue && (it.date()?.let { d -> !d.isBefore(today) } ?: true) }
        val past = visible.filter { it !in overdue && it !in upcoming }
        val groups = upcoming.groupBy { it.date() }.toSortedMap(nullsLast(naturalOrder<LocalDate>()))

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(showCompleted, { showCompleted = !showCompleted }, label = { Text("Show completed") })
                Box {
                    FilterChip(
                        courseFilter != null, { filterMenu = true },
                        label = { Text(data.courses.firstOrNull { it.id == courseFilter }?.code?.ifBlank { null } ?: if (courseFilter != null) "Course" else "All courses", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(filterMenu, { filterMenu = false }) {
                        DropdownMenuItem(text = { Text("All courses") }, onClick = { courseFilter = null; filterMenu = false })
                        data.courses.forEach { c ->
                            DropdownMenuItem(text = { Text(c.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { courseFilter = c.id; filterMenu = false })
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                EmptyState(Icons.Outlined.TaskAlt, "All caught up!", "Nothing to do in the next six weeks. Pull down to refresh.")
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (overdue.isNotEmpty()) {
                    item { SectionHeader("Overdue (${overdue.size})") }
                    items(overdue, key = { "o" + it.plannableType + it.plannableId }) { TodoRow(it, colors, ::toggle, nav) }
                }
                groups.forEach { (date, list) ->
                    item(key = "h$date") { SectionHeader(date?.dayHeader() ?: "No date") }
                    items(list.sortedBy { it.plannable.dueAt ?: it.plannableDate }, key = { "u" + it.plannableType + it.plannableId }) {
                        TodoRow(it, colors, ::toggle, nav)
                    }
                }
                if (past.isNotEmpty()) {
                    item { SectionHeader("Earlier (completed)") }
                    items(past.sortedByDescending { it.date() }, key = { "p" + it.plannableType + it.plannableId }) { TodoRow(it, colors, ::toggle, nav) }
                }
            }
        }
    }
}

@Composable
fun TodoRow(item: PlannerItem, colors: Map<Long, Int>, onToggle: (PlannerItem, Boolean) -> Unit, nav: Navigator) {
    val color = readable(Color(colors[item.courseId] ?: Palette.fallback(item.courseId ?: 0)))
    val done = item.isComplete
    val due = (item.plannable.dueAt ?: item.plannable.todoDate ?: item.plannableDate).toInstant()
    Row(
        Modifier.fillMaxWidth().clickable { nav.openPlannerItem(item) }.padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = { onToggle(item, it) })
        Icon(plannerIcon(item.plannableType), null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.contextName ?: if (item.plannableType == "planner_note") "To do" else "",
                color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val meta = listOfNotNull(
                due?.let { "Due ${it.time()}" }, pointsText(item.plannable.pointsPossible),
            ).joinToString(" · ")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    item.isGraded -> Pill("Graded", Success)
                    item.isMissing && !done -> Pill("Missing", Danger)
                    item.isLate -> Pill("Late", Warning)
                    item.isSubmitted -> Pill("Submitted", Success)
                    item.newActivity -> Pill("New", MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 92.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
