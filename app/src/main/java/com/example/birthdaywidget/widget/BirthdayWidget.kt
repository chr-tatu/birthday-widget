package com.example.birthdaywidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.clickable
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.dp
import androidx.glance.unit.sp
import com.example.birthdaywidget.MainActivity
import com.example.birthdaywidget.R
import com.example.birthdaywidget.data.BirthdayWidgetSnapshot
import com.example.birthdaywidget.data.BirthdayWidgetStateSerializer
import com.example.birthdaywidget.data.BirthdayWidgetStateStore
import com.example.birthdaywidget.data.UpcomingBirthday
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class BirthdayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            BirthdayWidgetContent()
        }
    }

    @Composable
    private fun BirthdayWidgetContent() {
        val context = LocalContext.current
        val preferences = currentState<Preferences>()
        val snapshot = BirthdayWidgetStateSerializer.deserialize(
            preferences[BirthdayWidgetStateStore.STATE_KEY]
        )
        val titleStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val bodyStyle = TextStyle(fontSize = 14.sp)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
                .clickable(actionStartActivity(MainActivity::class.java))
        ) {
            Text(text = context.getString(R.string.widget_title), style = titleStyle)
            Spacer(modifier = GlanceModifier.height(8.dp))
            when {
                !snapshot.hasAccount -> WidgetMessage(context.getString(R.string.widget_message_sign_in), bodyStyle)
                snapshot.errorMessage != null -> WidgetMessage(
                    context.getString(R.string.widget_message_error, snapshot.errorMessage.orEmpty()),
                    bodyStyle
                )
                snapshot.entries.isEmpty() -> WidgetMessage(context.getString(R.string.widget_message_empty), bodyStyle)
                else -> BirthdayList(snapshot, bodyStyle)
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            snapshot.lastSyncedEpochMillis?.let { lastSynced ->
                val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                    .withLocale(Locale.getDefault())
                    .format(Instant.ofEpochMilli(lastSynced).atZone(ZoneId.systemDefault()))
                Text(
                    text = context.getString(R.string.widget_last_updated, time),
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(R.color.widget_subtle_text))
                )
            }
        }
    }

    @Composable
    private fun WidgetMessage(message: String, style: TextStyle) {
        val context = LocalContext.current
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(text = message, style = style)
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = context.getString(R.string.widget_manage_hint),
                style = TextStyle(fontSize = 12.sp, color = ColorProvider(R.color.widget_subtle_text))
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            androidx.glance.appwidget.Button(
                text = context.getString(R.string.widget_manage_button),
                onClick = actionStartActivity(MainActivity::class.java)
            )
        }
    }

    @Composable
    private fun BirthdayList(snapshot: BirthdayWidgetSnapshot, style: TextStyle) {
        val today = LocalDate.now()
        val grouped = snapshot.entries
            .mapNotNull { entry ->
                runCatching { LocalDate.parse(entry.dateIso8601) to entry }.getOrNull()
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()

        LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
            grouped.forEach { (date, contacts) ->
                item(key = "header_${date}") {
                    Text(
                        text = formatDayLabel(LocalContext.current, date, today),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.padding(vertical = 4.dp)
                    )
                }
                items(contacts) { person ->
                    BirthdayRow(person, style)
                }
            }
        }
    }

    @Composable
    private fun BirthdayRow(entry: UpcomingBirthday, style: TextStyle) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageProvider = entry.photoPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    androidx.glance.ImageProvider(file)
                } else null
            } ?: androidx.glance.ImageProvider(R.drawable.ic_person_placeholder)

            androidx.glance.Image(
                provider = imageProvider,
                contentDescription = entry.displayName,
                modifier = GlanceModifier.size(40.dp)
            )
            Spacer(modifier = GlanceModifier.width(12.dp))
            Text(
                text = entry.displayName,
                style = style,
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }

    private fun formatDayLabel(context: Context, date: LocalDate, today: LocalDate): String {
        return when (date) {
            today -> context.getString(R.string.widget_header_today)
            today.plusDays(1) -> context.getString(R.string.widget_header_tomorrow)
            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    }

    suspend fun updateState(context: Context, glanceId: GlanceId, json: String) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[BirthdayWidgetStateStore.STATE_KEY] = json
        }
        update(context, glanceId)
    }

    suspend fun updateAll(context: Context) {
        val widget = this
        val manager = GlanceAppWidgetManager(context)
        val snapshot = BirthdayWidgetStateStore.loadSnapshot(context)
        val json = BirthdayWidgetStateSerializer.serialize(snapshot)
        manager.getGlanceIds(widget.javaClass).forEach { glanceId ->
            widget.updateState(context, glanceId, json)
        }
    }
}
