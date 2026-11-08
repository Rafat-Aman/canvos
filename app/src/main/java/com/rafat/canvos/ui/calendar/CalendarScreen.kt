package com.rafat.canvos.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rafat.canvos.data.Palette
import com.rafat.canvos.data.api.PlannerItem
import com.rafat.canvos.ui.common.ErrorState
import com.rafat.canvos.ui.common.Load
import com.rafat.canvos.ui.common.Pill
import com.rafat.canvos.ui.common.readable
import com.rafat.canvos.ui.common.SavedBanner
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.nav.CalendarSyncRoute
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.theme.Success
import com.rafat.canvos.ui.todo.date
import com.rafat.canvos.ui.todo.openPlannerItem
import com.rafat.canvos.ui.todo.plannerIcon
import com.rafat.canvos.util.dayHeader
import com.rafat.canvos.util.time
import com.rafat.canvos.util.toInstant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private data class MonthData(val items: List<PlannerItem>, val colors: Map<Long, Int>)

@Composable
fun CalendarScreen() {
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val ym = YearMonth.parse(month)
    val sel = LocalDate.parse(selected)
    val locale: Locale = LocalConfiguration.current.locales.let { if (it.isEmpty) Locale.ROOT else it[0] }
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    val gridStart = ym.atDay(1).with(TemporalAdjusters.previousOrSame(firstDow))
    val gridEnd = gridStart.plusDays(42)

    val vm = rememberLoader("calendar-$month") { force ->
        val items = planner(gridStart, gridEnd)
        val colors = runCatching { dashboardCourses(force) }.getOrDefault(emptyList()).associate { it.id to it.color }
        MonthData(items, colors)
    }
    val st by vm.state.collectAsState()
    val data = st.data
    val byDay = remember(data) { data?.items?.groupBy { it.date() } ?: emptyMap() }

    fun go(delta: Long) {
        val n = ym.plusMonths(delta)
        month = n.toString()
        selected = (if (n == YearMonth.now()) LocalDate.now() else n.atDay(1)).toString()
    }

    Column(Modifier.fillMaxSize()) {
        if (st.saved) SavedBanner()
        // Month header
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            if (st.load is Load.Loading || st.refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            val nav = LocalNav.current
            IconButton(onClick = { nav.go(CalendarSyncRoute) }) { Icon(Icons.Outlined.Sync, "Sync to Google Calendar") }
            TextButton(onClick = { month = YearMonth.now().toString(); selected = LocalDate.now().toString() }) { Text("Today") }
            IconButton(onClick = { go(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month") }
            IconButton(onClick = { go(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month") }
        }
        // Weekday labels
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            for (i in 0 until 7) {
                val d = firstDow.plus(i.toLong())
                Text(
                    d.getDisplayName(TextStyle.NARROW, locale), modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Grid (swipe left/right to change month)
        var drag by remember { mutableStateOf(0f) }
        Column(
            Modifier.padding(horizontal = 8.dp).pointerInput(month) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (drag > 120) go(-1) else if (drag < -120) go(1); drag = 0f },
                    onDragCancel = { drag = 0f },
                ) { _, d -> drag += d }
            },
        ) {
            for (w in 0 until 6) {
                Row(Modifier.fillMaxWidth()) {
                    for (dIdx in 0 until 7) {
                        val day = gridStart.plusDays((w * 7 + dIdx).toLong())
                        DayCell(
                            day, inMonth = YearMonth.from(day) == ym, isSelected = day == sel,
                            dots = byDay[day].orEmpty().map { Color(data?.colors?.get(it.courseId) ?: Palette.fallback(it.courseId ?: 0)) }.distinct().take(3),
                            modifier = Modifier.weight(1f),
                        ) {
                            selected = day.toString()
                            if (YearMonth.from(day) != ym) month = YearMonth.from(day).toString()
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
        Text(
            sel.dayHeader(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        when (val l = st.load) {
            is Load.Failed -> ErrorState(l.message) { vm.refresh() }
            else -> {
                val list = byDay[sel].orEmpty().sortedBy { it.plannable.startAt ?: it.plannable.dueAt ?: it.plannableDate }
                if (data != null && list.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.EventAvailable, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text("Nothing scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.plannableType + it.plannableId }) { AgendaRow(it, data?.colors.orEmpty()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, inMonth: Boolean, isSelected: Boolean, dots: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    val today = day == LocalDate.now()
    val cs = MaterialTheme.colorScheme
    Column(modifier.aspectRatio(1.15f).clip(CircleShape).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(
                when {
                    isSelected -> cs.primary
                    today -> cs.primaryContainer
                    else -> Color.Transparent
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                color = when {
                    isSelected -> cs.onPrimary
                    !inMonth -> cs.onSurfaceVariant.copy(alpha = 0.45f)
                    today -> cs.onPrimaryContainer
                    else -> cs.onSurface
                },
                fontWeight = if (today || isSelected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(Modifier.height(8.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dots.forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(if (inMonth) it else it.copy(alpha = 0.4f))) }
        }
    }
}

@Composable
private fun AgendaRow(item: PlannerItem, colors: Map<Long, Int>) {
    val nav = LocalNav.current
    val color = readable(Color(colors[item.courseId] ?: Palette.fallback(item.courseId ?: 0)))
    val p = item.plannable
    val whenText = when {
        item.plannableType == "calendar_event" && p.allDay == true -> "All day"
        item.plannableType == "calendar_event" -> listOfNotNull(p.startAt.toInstant()?.time(), p.endAt.toInstant()?.time()).joinToString(" – ")
        else -> (p.dueAt ?: p.todoDate ?: item.plannableDate).toInstant()?.let {
            when (item.plannableType) {
                "announcement" -> "Posted ${it.time()}"
                "planner_note", "wiki_page" -> "To do ${it.time()}"
                else -> "Due ${it.time()}"
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable { nav.openPlannerItem(item) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(40.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Icon(plannerIcon(item.plannableType), null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.contextName, whenText, p.locationName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.isComplete && item.plannableType != "calendar_event" && item.plannableType != "announcement") Pill("Done", Success)
    }
}
