package com.rafat.canvos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import android.widget.Toast
import com.rafat.canvos.data.offline.OfflineSyncWorker
import com.rafat.canvos.ui.calendar.CalendarSyncScreen
import com.rafat.canvos.ui.calendar.EventScreen
import com.rafat.canvos.ui.common.AuthEvents
import com.rafat.canvos.ui.common.ErrorEvents
import com.rafat.canvos.ui.course.AnnouncementsScreen
import com.rafat.canvos.ui.course.AssignmentScreen
import com.rafat.canvos.ui.course.AssignmentsScreen
import com.rafat.canvos.ui.course.CourseFeedScreen
import com.rafat.canvos.ui.course.CourseHomeScreen
import com.rafat.canvos.ui.course.DiscussionScreen
import com.rafat.canvos.ui.course.DiscussionsScreen
import com.rafat.canvos.ui.course.FilesScreen
import com.rafat.canvos.ui.course.GradesScreen
import com.rafat.canvos.ui.course.ModulesScreen
import com.rafat.canvos.ui.course.PageScreen
import com.rafat.canvos.ui.course.PagesScreen
import com.rafat.canvos.ui.course.PeopleScreen
import com.rafat.canvos.ui.course.QuizzesScreen
import com.rafat.canvos.ui.course.SubmissionScreen
import com.rafat.canvos.ui.course.SubmitScreen
import com.rafat.canvos.ui.course.SyllabusScreen
import com.rafat.canvos.ui.dashboard.AllCoursesScreen
import com.rafat.canvos.ui.inbox.ComposeScreen
import com.rafat.canvos.ui.inbox.ConversationScreen
import com.rafat.canvos.ui.login.LoginScreen
import com.rafat.canvos.ui.main.MainScreen
import com.rafat.canvos.ui.nav.AllCoursesRoute
import com.rafat.canvos.ui.nav.AnnouncementsRoute
import com.rafat.canvos.ui.nav.AssignmentRoute
import com.rafat.canvos.ui.nav.AssignmentsRoute
import com.rafat.canvos.ui.nav.CalendarSyncRoute
import com.rafat.canvos.ui.nav.ComposeRoute
import com.rafat.canvos.ui.nav.ConversationRoute
import com.rafat.canvos.ui.nav.CourseFeedRoute
import com.rafat.canvos.ui.nav.CourseRoute
import com.rafat.canvos.ui.nav.DiscussionRoute
import com.rafat.canvos.ui.nav.DiscussionsRoute
import com.rafat.canvos.ui.nav.EventRoute
import com.rafat.canvos.ui.nav.FileActions
import com.rafat.canvos.ui.nav.FilesRoute
import com.rafat.canvos.ui.nav.GradesRoute
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.MainRoute
import com.rafat.canvos.ui.nav.ModulesRoute
import com.rafat.canvos.ui.nav.OfflineFilesRoute
import com.rafat.canvos.ui.nav.OfflineRoute
import com.rafat.canvos.ui.nav.Navigator
import com.rafat.canvos.ui.nav.PageRoute
import com.rafat.canvos.ui.nav.PagesRoute
import com.rafat.canvos.ui.nav.PeopleRoute
import com.rafat.canvos.ui.nav.QuizzesRoute
import com.rafat.canvos.ui.nav.SettingsRoute
import com.rafat.canvos.ui.nav.SubmissionRoute
import com.rafat.canvos.ui.nav.SubmitRoute
import com.rafat.canvos.ui.nav.SyllabusRoute
import com.rafat.canvos.ui.nav.WebRoute
import com.rafat.canvos.ui.offline.OfflineFilesScreen
import com.rafat.canvos.ui.offline.OfflineScreen
import com.rafat.canvos.ui.settings.SettingsScreen
import com.rafat.canvos.ui.theme.CanvosTheme
import com.rafat.canvos.ui.web.WebScreen
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** A link to open once the UI is ready (from a notification tap). */
    private val pending = MutableStateFlow<Pair<String?, Long>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readIntent(intent)
        // Catch up on offline content when it is stale (waits for Wi-Fi if required).
        if (CanvosApp.instance.repo.value != null) OfflineSyncWorker.onAppStart(this)
        setContent {
            val app = CanvosApp.instance
            val settings by app.settings.state.collectAsState()
            CanvosTheme(settings) {
                Surface(color = MaterialTheme.colorScheme.background) { Root() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(i: Intent?) {
        val url = i?.getStringExtra(EXTRA_OPEN_URL)
        val conv = i?.getLongExtra(EXTRA_CONVERSATION, 0L) ?: 0L
        if (url != null || conv != 0L) pending.value = url to conv
    }

    @Composable
    private fun Root() {
        val app = CanvosApp.instance
        val repo by app.repo.collectAsState()
        val expired by AuthEvents.expired.collectAsState()
        val ctx = LocalContext.current

        val r = repo
        if (r == null) {
            LoginScreen()
        } else {
            // A new account means a fresh navigation stack.
            key(r) {
                val navController = rememberNavController()
                val navigator = remember(navController) { Navigator(navController, this) }
                CompositionLocalProvider(LocalNav provides navigator) {
                    NavHost(
                        navController, startDestination = MainRoute,
                        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() },
                        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
                    ) { graph() }
                    val p by pending.collectAsState()
                    LaunchedEffect(p) {
                        val (url, conv) = p ?: return@LaunchedEffect
                        pending.value = null
                        if (conv != 0L) navigator.go(ConversationRoute(conv)) else navigator.openLink(url)
                    }
                    FileProgressDialog()
                }
            }
        }

        LaunchedEffect(Unit) {
            ErrorEvents.events.collect { e -> if (e != null) Toast.makeText(ctx, e.second, Toast.LENGTH_SHORT).show() }
        }
        if (expired && r != null) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Session expired") },
                text = { Text("Canvas no longer accepts your access token (it may have expired or been deleted). Sign in again with a new token.") },
                confirmButton = {
                    TextButton(onClick = {
                        AuthEvents.reset()
                        // Same person with a new token: keep their offline courses.
                        app.signOut(keepOffline = true)
                    }) { Text("Sign in again") }
                },
            )
        }
    }

    private fun NavGraphBuilder.graph() {
        composable<MainRoute> { MainScreen() }
        composable<AllCoursesRoute> { AllCoursesScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<CalendarSyncRoute> { CalendarSyncScreen() }
        composable<OfflineRoute> { OfflineScreen() }
        composable<OfflineFilesRoute> { OfflineFilesScreen() }
        composable<CourseRoute> { CourseHomeScreen(it.toRoute<CourseRoute>().courseId) }
        composable<AnnouncementsRoute> { AnnouncementsScreen(it.toRoute<AnnouncementsRoute>().courseId) }
        composable<AssignmentsRoute> { AssignmentsScreen(it.toRoute<AssignmentsRoute>().courseId) }
        composable<AssignmentRoute> { val r = it.toRoute<AssignmentRoute>(); AssignmentScreen(r.courseId, r.assignmentId) }
        composable<SubmitRoute> { val r = it.toRoute<SubmitRoute>(); SubmitScreen(r.courseId, r.assignmentId, r.type) }
        composable<SubmissionRoute> { val r = it.toRoute<SubmissionRoute>(); SubmissionScreen(r.courseId, r.assignmentId) }
        composable<GradesRoute> { GradesScreen(it.toRoute<GradesRoute>().courseId) }
        composable<DiscussionsRoute> { DiscussionsScreen(it.toRoute<DiscussionsRoute>().courseId) }
        composable<DiscussionRoute> { val r = it.toRoute<DiscussionRoute>(); DiscussionScreen(r.courseId, r.topicId, r.announcement) }
        composable<PagesRoute> { PagesScreen(it.toRoute<PagesRoute>().courseId) }
        composable<PageRoute> { val r = it.toRoute<PageRoute>(); PageScreen(r.courseId, r.url) }
        composable<FilesRoute> { val r = it.toRoute<FilesRoute>(); FilesScreen(r.courseId, r.folderId, r.title) }
        composable<SyllabusRoute> { SyllabusScreen(it.toRoute<SyllabusRoute>().courseId) }
        composable<PeopleRoute> { PeopleScreen(it.toRoute<PeopleRoute>().courseId) }
        composable<ModulesRoute> { ModulesScreen(it.toRoute<ModulesRoute>().courseId) }
        composable<QuizzesRoute> { QuizzesScreen(it.toRoute<QuizzesRoute>().courseId) }
        composable<CourseFeedRoute> { CourseFeedScreen(it.toRoute<CourseFeedRoute>().courseId) }
        composable<ConversationRoute> { ConversationScreen(it.toRoute<ConversationRoute>().conversationId) }
        composable<ComposeRoute> { ComposeScreen(it.toRoute()) }
        composable<WebRoute> { val r = it.toRoute<WebRoute>(); WebScreen(r.url, r.title) }
        composable<EventRoute> { EventScreen(it.toRoute<EventRoute>().eventId) }
    }

    companion object {
        const val EXTRA_OPEN_URL = "open_url"
        const val EXTRA_CONVERSATION = "conversation_id"
    }
}

@Composable
private fun FileProgressDialog() {
    val p by FileActions.state.collectAsState()
    val s = p ?: return
    AlertDialog(
        onDismissRequest = { FileActions.dismiss() },
        title = { Text(if (s.error != null) "Couldn't open file" else "Opening…") },
        text = {
            Column {
                Text(s.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                when {
                    s.error != null -> Text(s.error, color = MaterialTheme.colorScheme.error)
                    s.fraction != null -> LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
                    else -> CircularProgressIndicator()
                }
            }
        },
        confirmButton = { TextButton(onClick = { FileActions.dismiss() }) { Text(if (s.error != null) "OK" else "Cancel") } },
    )
}
