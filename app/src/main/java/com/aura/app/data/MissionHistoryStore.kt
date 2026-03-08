package com.aura.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aura.app.model.CompletedMissionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.missionHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "mission_history_prefs")

object MissionHistoryStore {
    private val KEY_HISTORY = stringPreferencesKey("mission_history_json")

    fun historyFlow(context: Context): Flow<List<CompletedMissionRecord>> =
        context.missionHistoryDataStore.data.map { prefs ->
            val jsonStr = prefs[KEY_HISTORY] ?: "[]"
            try {
                Json.decodeFromString(jsonStr)
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun addRecord(context: Context, record: CompletedMissionRecord) {
        context.missionHistoryDataStore.edit { prefs ->
            val jsonStr = prefs[KEY_HISTORY] ?: "[]"
            val currentList = try {
                Json.decodeFromString<List<CompletedMissionRecord>>(jsonStr)
            } catch (e: Exception) {
                emptyList()
            }
            
            // Keep the most recent 20
            val newList = (listOf(record) + currentList).take(20)
            prefs[KEY_HISTORY] = Json.encodeToString(newList)
        }
    }
}
