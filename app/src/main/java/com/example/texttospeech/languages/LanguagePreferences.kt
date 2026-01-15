package com.example.texttospeech.languages

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("language_prefs")

object LanguagePreferences {

    private val SELECTED_LANGUAGE_KEY = stringPreferencesKey("selected_language")

    fun getSelectedLanguage(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[SELECTED_LANGUAGE_KEY]
        }
    }

    suspend fun saveSelectedLanguage(context: Context, language: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_LANGUAGE_KEY] = language
        }
    }
}
