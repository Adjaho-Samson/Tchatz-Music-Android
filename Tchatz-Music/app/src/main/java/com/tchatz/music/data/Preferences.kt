package com.tchatz.music.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import com.tchatz.music.domain.Sort
private val Context.settings by preferencesDataStore("settings")
class Preferences(private val context:Context) {
 val sort=context.settings.data.map { p -> runCatching { Sort.valueOf(p[stringPreferencesKey("sort")] ?: "TITLE") }.getOrDefault(Sort.TITLE) }
 val compact=context.settings.data.map { it[booleanPreferencesKey("compact")] ?: false }
 val folder=context.settings.data.map { it[stringPreferencesKey("folder")] }
 suspend fun sort(v:Sort) { context.settings.edit { it[stringPreferencesKey("sort")]=v.name } }
 suspend fun compact(v:Boolean) { context.settings.edit { it[booleanPreferencesKey("compact")]=v } }
 suspend fun folder(v:String?) { context.settings.edit { if(v==null) it.remove(stringPreferencesKey("folder")) else it[stringPreferencesKey("folder")]=v } }
}
