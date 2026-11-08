package com.rafat.canvos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.rafat.canvos.data.Palette
import com.rafat.canvos.ui.theme.Danger
import com.rafat.canvos.ui.theme.Success
import com.rafat.canvos.ui.theme.Warning
import com.rafat.canvos.util.SubmissionStatus

/**
 * A course colour adjusted so it stays readable as text/icon colour on the
 * current surface (dark course colours are lightened in dark mode).
 */
@Composable
fun readable(c: Color): Color {
    val darkUi = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when {
        darkUi && c.luminance() < 0.3f -> androidx.compose.ui.graphics.lerp(c, Color.White, 0.55f)
        !darkUi && c.luminance() > 0.7f -> androidx.compose.ui.graphics.lerp(c, Color.Black, 0.45f)
        else -> c
    }
}

/** Readable text colour on top of [bg]. */
fun onColor(bg: Color): Color = if (bg.luminance() > 0.55f) Color(0xFF1A1F24) else Color.White

/** Canvas' default avatar has "avatar-50.png" in it; treat as missing. */
fun realAvatar(url: String?): String? =
    url?.takeIf { it.isNotBlank() && !it.contains("avatar-50.png") && !it.contains("images/messages/avatar") }

@Composable
fun Avatar(url: String?, name: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val initials = remember(name) {
        name.orEmpty().split(' ', '-').filter { it.isNotBlank() && it[0].isLetter() }
            .let { if (it.size >= 2) "${it.first()[0]}${it.last()[0]}" else it.firstOrNull()?.take(1) ?: "?" }
            .uppercase()
    }
    val color = remember(name) { Color(Palette.fallback((name ?: "").hashCode().toLong())) }
    val fallback: @Composable () -> Unit = {
        Box(
            Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, color = color, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f).sp)
        }
    }
    val real = realAvatar(url)
    if (real == null) Box(modifier) { fallback() } else {
        SubcomposeAsyncImage(
            model = real, contentDescription = name, contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
            loading = { fallback() }, error = { fallback() },
        )
    }
}

@Composable
fun StatusChip(status: SubmissionStatus) {
    if (status == SubmissionStatus.NONE) return
    val c = when (status) {
        SubmissionStatus.GRADED, SubmissionStatus.SUBMITTED, SubmissionStatus.EXCUSED -> Success
        SubmissionStatus.LATE -> Warning
        SubmissionStatus.MISSING -> Danger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Pill(status.label, c)
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), maxLines = 1,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), letterSpacing = 0.8.sp,
        )
        trailing?.invoke()
    }
}

/** Coloured app bar used inside a course (course colour, white text). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvosTopBar(
    title: String,
    subtitle: String? = null,
    color: Color? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bg = color ?: MaterialTheme.colorScheme.surface
    val fg = if (color != null) onColor(color) else MaterialTheme.colorScheme.onSurface
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.85f),
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = bg, titleContentColor = fg, navigationIconContentColor = fg, actionIconContentColor = fg,
        ),
    )
}

/** Small coloured square icon used in lists. */
@Composable
fun TypeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
}

@Composable
fun MetaRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
