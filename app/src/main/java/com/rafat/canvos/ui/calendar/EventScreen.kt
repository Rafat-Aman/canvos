package com.rafat.canvos.ui.calendar

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rafat.canvos.ui.common.CanvosTopBar
import com.rafat.canvos.ui.common.HtmlContent
import com.rafat.canvos.ui.common.Loaded
import com.rafat.canvos.ui.common.rememberLoader
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.util.dateTime
import com.rafat.canvos.util.htmlToText
import com.rafat.canvos.util.localDate
import com.rafat.canvos.util.time
import com.rafat.canvos.util.toInstant

@Composable
fun EventScreen(eventId: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val vm = rememberLoader("event-$eventId") { calendarEvent(eventId) }
    Scaffold(topBar = {
        CanvosTopBar("Event", onBack = { nav.back() }, actions = {
            val e = vm.state.collectAsState().value.data
            if (e != null) {
                IconButton(onClick = {
                    // Hand a copy to the phone's calendar app (one-off, without sync).
                    val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, e.title)
                        .putExtra(CalendarContract.Events.DESCRIPTION, e.description.htmlToText(2000))
                        .putExtra(CalendarContract.Events.EVENT_LOCATION, e.locationName)
                        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, e.allDay)
                    e.startAt.toInstant()?.let { i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.toEpochMilli()) }
                    e.endAt.toInstant()?.let { i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.toEpochMilli()) }
                    runCatching { ctx.startActivity(i) }
                }) { Icon(Icons.Outlined.EventAvailable, "Add to my calendar") }
            }
        })
    }) { pad ->
        Loaded(vm, Modifier.padding(pad)) { e ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.title ?: "Event", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                val start = e.startAt.toInstant()
                val end = e.endAt.toInstant()
                val whenText = when {
                    start == null -> "No time set"
                    e.allDay -> start.dateTime().substringBefore(" at ") + " (all day)"
                    end != null && end.localDate() == start.localDate() -> start.dateTime() + " – " + end.time()
                    end != null -> start.dateTime() + " – " + end.dateTime()
                    else -> start.dateTime()
                }
                ListItem(leadingContent = { Icon(Icons.Outlined.Schedule, null) }, headlineContent = { Text(whenText) })
                e.contextName?.let { ListItem(leadingContent = { Icon(Icons.Outlined.School, null) }, headlineContent = { Text(it) }) }
                listOfNotNull(e.locationName, e.locationAddress).filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotBlank() }?.let {
                    ListItem(leadingContent = { Icon(Icons.Outlined.Place, null) }, headlineContent = { Text(it) })
                }
                if (!e.description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    HtmlContent(e.description)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
