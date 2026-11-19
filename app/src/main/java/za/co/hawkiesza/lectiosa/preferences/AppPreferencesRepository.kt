package za.co.hawkiesza.lectiosa.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "lectio_preferences"

private val Context.lectioPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME,
)

class AppPreferencesRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.lectioPreferencesDataStore)

    val preferences: Flow<AppPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            AppPreferences(
                theme = values[Keys.theme].toEnumOrDefault(AppThemePreference.SYSTEM),
                textSize = values[Keys.textSize].toEnumOrDefault(AppTextSize.DEFAULT),
                showVerseNumbers = values[Keys.showVerseNumbers] ?: false,
                rememberedDate = values[Keys.rememberedDate],
                rememberedMassFormId = values[Keys.rememberedMassFormId],
            )
        }

    suspend fun setTheme(theme: AppThemePreference) {
        dataStore.edit { it[Keys.theme] = theme.name }
    }

    suspend fun setTextSize(textSize: AppTextSize) {
        dataStore.edit { it[Keys.textSize] = textSize.name }
    }

    suspend fun setShowVerseNumbers(enabled: Boolean) {
        dataStore.edit { it[Keys.showVerseNumbers] = enabled }
    }

    suspend fun setReadingPosition(date: String, massFormId: String) {
        dataStore.edit {
            it[Keys.rememberedDate] = date
            it[Keys.rememberedMassFormId] = massFormId
        }
    }

    suspend fun clearReadingPosition() {
        dataStore.edit {
            it.remove(Keys.rememberedDate)
            it.remove(Keys.rememberedMassFormId)
        }
    }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val textSize = stringPreferencesKey("text_size")
        val showVerseNumbers = booleanPreferencesKey("show_verse_numbers")
        val rememberedDate = stringPreferencesKey("remembered_date")
        val rememberedMassFormId = stringPreferencesKey("remembered_mass_form_id")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
