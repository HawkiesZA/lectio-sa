package za.co.hawkiesza.lectiosa.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppPreferencesRepositoryTest {
    private lateinit var dataStoreFile: java.io.File
    private lateinit var repository: AppPreferencesRepository

    @Before
    fun setUp() {
        dataStoreFile = Files.createTempFile("lectio-preferences-", ".preferences_pb").toFile()
        dataStoreFile.delete()
        val dataStore = PreferenceDataStoreFactory.create { dataStoreFile }
        repository = AppPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        dataStoreFile.delete()
    }

    @Test
    fun emptyStoreEmitsDefaults() = runBlocking {
        assertEquals(AppPreferences(), repository.preferences.first())
    }

    @Test
    fun updatesAreReflectedInPreferences() = runBlocking {
        repository.setTheme(AppThemePreference.DARK)
        repository.setTextSize(AppTextSize.EXTRA_LARGE)
        repository.setShowVerseNumbers(true)
        repository.setReadingPosition("2026-11-19", "mass-during-day")

        assertEquals(
            AppPreferences(
                theme = AppThemePreference.DARK,
                textSize = AppTextSize.EXTRA_LARGE,
                showVerseNumbers = true,
                rememberedDate = "2026-11-19",
                rememberedMassFormId = "mass-during-day",
            ),
            repository.preferences.first(),
        )
    }

    @Test
    fun clearingPositionLeavesOtherPreferencesUntouched() = runBlocking {
        repository.setTheme(AppThemePreference.LIGHT)
        repository.setReadingPosition("2026-11-19", "default")
        repository.clearReadingPosition()

        val preferences = repository.preferences.first()
        assertEquals(AppThemePreference.LIGHT, preferences.theme)
        assertNull(preferences.rememberedDate)
        assertNull(preferences.rememberedMassFormId)
    }

    @Test
    fun invalidEnumValuesFallBackToDefaults() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create { dataStoreFile }
        dataStore.edit {
            it[stringPreferencesKey("theme")] = "not-a-theme"
            it[stringPreferencesKey("text_size")] = "not-a-size"
        }
        repository = AppPreferencesRepository(dataStore)

        val preferences = repository.preferences.first()
        assertEquals(AppThemePreference.SYSTEM, preferences.theme)
        assertEquals(AppTextSize.DEFAULT, preferences.textSize)
        assertFalse(preferences.showVerseNumbers)
        assertTrue(preferences.rememberedDate == null)
    }
}
