package com.rafat.canvos.ui.course

import android.app.Activity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.rafat.canvos.data.CourseInfo
import com.rafat.canvos.ui.common.CanvosTopBar
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.nav.LocalNav

/** Sets status-bar icon colour to suit [background] while on screen. */
@Composable
fun StatusBarFor(background: Color?) {
    val view = LocalView.current
    val defaultLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val light = background?.let { it.luminance() > 0.55f } ?: defaultLight
    DisposableEffect(light) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.isAppearanceLightStatusBars = light
        onDispose { ctl?.isAppearanceLightStatusBars = defaultLight }
    }
}

/** A screen inside a course: course-coloured bar with the course name as subtitle. */
@Composable
fun CourseScaffold(
    courseId: Long,
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    snackbar: SnackbarHostState? = null,
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (course: CourseInfo?, padding: PaddingValues) -> Unit,
) {
    val nav = LocalNav.current
    val vm = rememberLoader("course-info-$courseId") { courseInfo(courseId) }
    val st by vm.state.collectAsState()
    val course = st.data
    val color = course?.let { Color(it.color) }
    StatusBarFor(color)
    Scaffold(
        topBar = { CanvosTopBar(title, subtitle = course?.displayName, color = color, onBack = { nav.back() }, actions = actions) },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
    ) { pad -> content(course, pad) }
}

fun CourseInfo?.colorOr(fallback: Color): Color = this?.let { Color(it.color) } ?: fallback

@Composable
fun rememberSnackbar() = remember { SnackbarHostState() }
