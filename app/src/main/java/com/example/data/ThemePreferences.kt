package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Расширение Context для Preferences DataStore
private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Хранилище пользовательских настроек на основе Preferences DataStore.
 * Сейчас хранит только выбранный режим темы.
 */
class ThemePreferences(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")

    /** Поток выбранного режима темы (по умолчанию — SYSTEM). */
    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { preferences ->
            val stored = preferences[themeModeKey]
            ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
        }

    /** Сохраняет выбранный режим темы. */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }
}
