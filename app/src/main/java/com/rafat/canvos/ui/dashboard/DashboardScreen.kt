package com.rafat.canvos.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.data.CourseInfo
import com.rafat.canvos.data.Palette
import com.rafat.canvos.data.api.PlannerItem
import com.rafat.canvos.ui.common.EmptyState
import com.rafat.canvos.ui.common.Loaded
import com.rafat.canvos.ui.common.SectionHeader
import com.rafat.canvos.ui.common.onColor
import com.rafat.canvos.ui.common.readable
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.common.userMessage
import com.rafat.canvos.ui.nav.AllCoursesRoute
import com.rafat.canvos.ui.nav.CourseRoute
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.todo.plannerIcon
import com.rafat.canvos.util.dayHeader
import com.rafat.canvos.util.localDate
import com.rafat.canvos.util.pretty
import com.rafat.canvos.util.time
import com.rafat.canvos.util.toInstant
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class DashboardData(val courses: List<CourseInfo>, val dueSoon: List<PlannerItem>)

@Composable
fun DashboardScreen() {
    val vm = rememberLoader("dashboard") { force ->
        val courses = dashboardCourses(force)
        val today = LocalDate.now()
        val soon = runCatching { planner(today, today.plusDays(8)) }.getOrDefault(emptyList())
            .filter { it.plannableType in setOf("assignment", "quiz", "discussion_topic", "sub_assignment") && !it.isComplete }
            .filter { (it.plannable.dueAt ?: it.plannableDate).toInstant()?.isAfter(Instant.now()) == true }
            .sortedBy { it.plannable.dueAt ?: it.plannableDate }
            .take(4)
        DashboardData(courses, soon)
    }
    val settings by CanvosApp.instance.settings.state.collectAsState()
    val nav = LocalNav.current
    var editing by remember { mutableStateOf<CourseInfo?>(null) }

    Loaded(vm) { data ->
        if (data.courses.isEmpty()) {
            EmptyState(Icons.Outlined.School, "No courses yet", "Courses you're enrolled in will show up here. Favourite courses from All Courses to pin them.")
            return@Loaded
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (data.dueSoon.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DueSoon(data.dueSoon, data.courses)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "COURSES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { nav.go(AllCoursesRoute) }) { Text("All courses") }
                }
            }
            items(data.courses, key = { it.id }) { c ->
                CourseCard(
                    c, showGrade = settings.showGrades, overlay = settings.colorOverlay,
                    onClick = { nav.go(CourseRoute(c.id)) },
                    onCustomize = { editing = c },
                )
            }
        }
    }
    editing?.let { c -> CustomizeDialog(c, onDismiss = { editing = null }, onSaved = { editing = null; vm.refresh() }) }
}

@Composable
private fun DueSoon(items: List<PlannerItem>, courses: List<CourseInfo>) {
    val nav = LocalNav.current
    val colors = courses.associate { it.id to it.color }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Due soon", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            items.forEach { item ->
                val color = Color(colors[item.courseId] ?: Palette.fallback(item.courseId ?: 0))
                val due = (item.plannable.dueAt ?: item.plannableDate).toInstant()
                Row(
                    Modifier.fillMaxWidth().clickable { nav.openLink(item.htmlUrl) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(plannerIcon(item.plannableType), null, tint = readable(color), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(item.contextName, due?.let { "${it.localDate().dayHeader()} ${it.time()}" }).joinToString(" · "),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

fun CourseInfo.gradeLabel(): String? {
    if (course.hideFinalGrades) return null
    val e = studentEnrollment ?: return null
    val score = e.currentScore ?: e.grades?.currentScore
    val grade = e.currentGrade ?: e.grades?.currentGrade
    return when {
        grade != null && score != null -> "$grade · ${score.pretty()}%"
        score != null -> "${score.pretty()}%"
        grade != null -> grade
        else -> null
    }
}

@Composable
fun CourseCard(c: CourseInfo, showGrade: Boolean, overlay: Boolean, onClick: () -> Unit, onCustomize: (() -> Unit)?) {
    val color = Color(c.color)
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().height(96.dp).background(color)) {
            if (!c.course.imageUrl.isNullOrBlank()) {
                AsyncImage(c.course.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (overlay) Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.78f)))
            }
            val grade = if (showGrade) c.gradeLabel() else null
            if (grade != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(10.dp).align(Alignment.TopStart),
                ) {
                    Text(
                        grade, color = readable(color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            if (onCustomize != null) {
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Course options", tint = onColor(color)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Customize nickname & colour") },
                            leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                            onClick = { menu = false; onCustomize() },
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                c.displayName, color = readable(color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                c.code, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            c.course.term?.name?.takeIf { it.isNotBlank() && !it.contains("Default", true) }?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CustomizeDialog(c: CourseInfo, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf(c.nickname ?: "") }
    var color by remember { mutableStateOf(c.color) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Customize course") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    nickname, { nickname = it }, singleLine = true, label = { Text("Nickname") },
                    placeholder = { Text(c.course.name, maxLines = 1) }, modifier = Modifier.fillMaxWidth(),
                )
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.colors.forEach { p ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(p)).clickable { color = p }
                                .then(if (p == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { if (p == color) Icon(Icons.Outlined.Check, null, tint = Color.White) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        if (nickname.trim() != (c.nickname ?: "")) repo().setNickname(c.id, nickname)
                        if (color != c.color) repo().setColor(c.id, String.format("%06X", 0xFFFFFF and color))
                        onSaved()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, e.userMessage(), Toast.LENGTH_LONG).show()
                        busy = false
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AllCoursesScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = rememberLoader("all-courses") { allCourses() }
    val settings by CanvosApp.instance.settings.state.collectAsState()
    androidx.compose.material3.Scaffold(
        topBar = { com.rafat.canvos.ui.common.CanvosTopBar("All courses", onBack = { nav.back() }) },
    ) { pad ->
        Loaded(vm, Modifier.padding(pad)) { list ->
            val today = Instant.now()
            val (current, past) = list.partition {
                val end = it.course.endAt.toInstant() ?: it.course.term?.endAt.toInstant()
                it.course.workflowState != "completed" && it.course.concluded != true && (end == null || end.isAfter(today))
            }.let { (cur, old) -> cur to old.sortedByDescending { it.course.term?.endAt ?: it.course.endAt ?: "" } }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                fun section(title: String, courses: List<CourseInfo>) {
                    if (courses.isEmpty()) return
                    item { SectionHeader("$title (${courses.size})") }
                    items(courses.size) { i ->
                        val c = courses[i]
                        androidx.compose.material3.ListItem(
                            modifier = Modifier.clickable { nav.go(CourseRoute(c.id)) },
                            leadingContent = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.color))) },
                            headlineContent = { Text(c.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(c.code, c.course.term?.name, if (settings.showGrades) c.gradeLabel() else null).joinToString(" · "),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    scope.launch {
                                        try {
                                            repo().setFavorite(c.id, !c.course.isFavorite)
                                            vm.update { l -> l.map { if (it.id == c.id) it.copy(course = it.course.copy(isFavorite = !c.course.isFavorite)) else it } }
                                        } catch (e: Exception) {
                                            Toast.makeText(ctx, e.userMessage(), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }) {
                                    Icon(
                                        if (c.course.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        if (c.course.isFavorite) "Remove from dashboard" else "Add to dashboard",
                                        tint = if (c.course.isFavorite) Color(0xFFF2A900) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
                item {
                    Text(
                        "Starred courses appear on your dashboard.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                section("Current", current)
                section("Past", past)
            }
        }
    }
}
