package com.rafat.canvos.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.data.CourseInfo
import com.rafat.canvos.data.api.Tab
import com.rafat.canvos.ui.common.Loaded
import com.rafat.canvos.ui.common.SavedBanner
import com.rafat.canvos.ui.common.onColor
import com.rafat.canvos.ui.common.readable
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.dashboard.gradeLabel
import com.rafat.canvos.ui.nav.AnnouncementsRoute
import com.rafat.canvos.ui.nav.AssignmentsRoute
import com.rafat.canvos.ui.nav.CourseFeedRoute
import com.rafat.canvos.ui.nav.DiscussionsRoute
import com.rafat.canvos.ui.nav.FilesRoute
import com.rafat.canvos.ui.nav.GradesRoute
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.ModulesRoute
import com.rafat.canvos.ui.nav.Navigator
import com.rafat.canvos.ui.nav.PageRoute
import com.rafat.canvos.ui.nav.PagesRoute
import com.rafat.canvos.ui.nav.PeopleRoute
import com.rafat.canvos.ui.nav.QuizzesRoute
import com.rafat.canvos.ui.nav.SyllabusRoute
import com.rafat.canvos.ui.nav.WebRoute

data class CourseHome(val course: CourseInfo, val tabs: List<Tab>)

fun tabIcon(id: String): ImageVector = when {
    id == "home" -> Icons.Outlined.Home
    id == "announcements" -> Icons.Outlined.Campaign
    id == "assignments" -> Icons.AutoMirrored.Outlined.Assignment
    id == "discussions" -> Icons.Outlined.Forum
    id == "grades" -> Icons.Outlined.Grade
    id == "people" -> Icons.Outlined.People
    id == "pages" -> Icons.Outlined.Description
    id == "files" -> Icons.Outlined.Folder
    id == "syllabus" -> Icons.AutoMirrored.Outlined.MenuBook
    id == "modules" -> Icons.Outlined.ViewModule
    id == "quizzes" -> Icons.Outlined.Quiz
    id == "conferences" -> Icons.Outlined.Videocam
    id == "collaborations" -> Icons.Outlined.Groups
    id == "search" -> Icons.Outlined.Search
    id == "outcomes" -> Icons.AutoMirrored.Outlined.ListAlt
    id.startsWith("context_external_tool") -> Icons.Outlined.Extension
    else -> Icons.Outlined.OpenInBrowser
}

/** Navigate to the native screen for a tab, or a logged-in web view. */
fun Navigator.openTab(course: CourseInfo, tab: Tab) {
    val id = course.id
    when (tab.id) {
        "home" -> when (course.course.defaultView) {
            "assignments" -> go(AssignmentsRoute(id))
            "wiki" -> go(PageRoute(id))
            "syllabus" -> go(SyllabusRoute(id))
            "feed" -> go(CourseFeedRoute(id))
            else -> go(ModulesRoute(id))
        }
        "announcements" -> go(AnnouncementsRoute(id))
        "assignments" -> go(AssignmentsRoute(id))
        "discussions" -> go(DiscussionsRoute(id))
        "grades" -> go(GradesRoute(id))
        "people" -> go(PeopleRoute(id))
        "pages" -> go(PagesRoute(id))
        "files" -> go(FilesRoute(id))
        "syllabus" -> go(SyllabusRoute(id))
        "modules" -> go(ModulesRoute(id))
        "quizzes" -> go(QuizzesRoute(id))
        else -> {
            val url = tab.fullUrl ?: tab.htmlUrl?.let { repo().domain + it } ?: return
            go(WebRoute(url, tab.label))
        }
    }
}

@Composable
fun CourseHomeScreen(courseId: Long) {
    val nav = LocalNav.current
    val settings by CanvosApp.instance.settings.state.collectAsState()
    val vm = rememberLoader("course-home-$courseId") { force -> CourseHome(course(courseId, force), tabs(courseId)) }
    val st by vm.state.collectAsState()
    val color = st.data?.course?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
    StatusBarFor(color)
    Scaffold { pad ->
        Loaded(vm, Modifier.padding(bottom = pad.calculateBottomPadding()), savedBanner = false) { data ->
            val c = data.course
            LazyColumn(Modifier.fillMaxSize()) {
                item { Header(c, color, settings.colorOverlay, if (settings.showGrades) c.gradeLabel() else null) { nav.back() } }
                if (st.saved) item { SavedBanner() }
                items(data.tabs, key = { it.id }) { tab ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.openTab(c, tab) }.padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tabIcon(tab.id), null, tint = readable(color), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tab.label, style = MaterialTheme.typography.bodyLarge)
                            if (tab.id == "home") {
                                val what = when (c.course.defaultView) {
                                    "assignments" -> "Assignments"; "wiki" -> "Front page"; "syllabus" -> "Syllabus"
                                    "feed" -> "Recent activity"; else -> "Modules"
                                }
                                Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (tab.type == "external") Icon(Icons.Outlined.OpenInBrowser, "Opens web page", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        else Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun Header(c: CourseInfo, color: Color, overlay: Boolean, grade: String?, onBack: () -> Unit) {
    val fg = onColor(color)
    Box(Modifier.fillMaxWidth().background(color)) {
        if (!c.course.imageUrl.isNullOrBlank()) {
            AsyncImage(c.course.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(color.copy(alpha = if (overlay) 0.82f else 0.55f)))
        }
        Column(Modifier.statusBarsPadding().padding(bottom = 20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = fg) }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(c.displayName, color = fg, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(c.code.ifBlank { null }, c.course.term?.name?.takeIf { !it.contains("Default", true) }).joinToString(" · "),
                    color = fg.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
                )
                if (grade != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(50)) {
                        Text(
                            "Current grade: $grade", color = readable(color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
