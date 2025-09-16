package com.example.birthdaywidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.birthdaywidget.widget.BirthdayWidget
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.snapshotDataStore: DataStore<Preferences> by preferencesDataStore(name = "birthday_widget")

object BirthdayWidgetStateStore {
    val STATE_KEY = stringPreferencesKey("birthday_state_json")

    suspend fun saveSnapshot(context: Context, snapshot: BirthdayWidgetSnapshot) {
        val json = BirthdayWidgetStateSerializer.serialize(snapshot)
        context.snapshotDataStore.edit { prefs ->
            prefs[STATE_KEY] = json
        }
        propagateToWidgets(context, json)
    }

    suspend fun loadSnapshot(context: Context): BirthdayWidgetSnapshot {
        val prefs = context.snapshotDataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()
        val json = prefs[STATE_KEY]
        return BirthdayWidgetStateSerializer.deserialize(json)
    }

    suspend fun clear(context: Context) {
        saveSnapshot(context, BirthdayWidgetSnapshot())
    }

    private suspend fun propagateToWidgets(context: Context, json: String) {
        val widget = BirthdayWidget()
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(widget.javaClass)
        glanceIds.forEach { glanceId ->
            widget.updateState(context, glanceId, json)
        }
    }
}

data class BirthdayWidgetSnapshot(
    val hasAccount: Boolean = false,
    val entries: List<UpcomingBirthday> = emptyList(),
    val errorMessage: String? = null,
    val lastSyncedEpochMillis: Long? = null
)

data class UpcomingBirthday(
    val resourceName: String,
    val displayName: String,
    val dateIso8601: String,
    val photoPath: String?
)

object BirthdayWidgetStateSerializer {
    private val gson = com.google.gson.Gson()
    private val snapshotType = object : com.google.gson.reflect.TypeToken<BirthdayWidgetSnapshot>() {}.type

    fun serialize(snapshot: BirthdayWidgetSnapshot): String = gson.toJson(snapshot, snapshotType)

    fun deserialize(json: String?): BirthdayWidgetSnapshot {
        if (json.isNullOrBlank()) {
            return BirthdayWidgetSnapshot()
        }
        return gson.fromJson(json, snapshotType)
    }
}
