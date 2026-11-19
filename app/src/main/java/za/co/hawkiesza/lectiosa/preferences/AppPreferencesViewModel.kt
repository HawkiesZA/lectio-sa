package za.co.hawkiesza.lectiosa.preferences

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppPreferencesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppPreferencesRepository(application)

    val preferences: StateFlow<AppPreferences?> = repository.preferences
        .map { it as AppPreferences? }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun setTheme(theme: AppThemePreference) = viewModelScope.launch { repository.setTheme(theme) }

    fun setTextSize(textSize: AppTextSize) = viewModelScope.launch { repository.setTextSize(textSize) }

    fun setShowVerseNumbers(enabled: Boolean) = viewModelScope.launch {
        repository.setShowVerseNumbers(enabled)
    }

    fun setReadingPosition(date: String, massFormId: String) = viewModelScope.launch {
        repository.setReadingPosition(date, massFormId)
    }

    fun clearReadingPosition() = viewModelScope.launch { repository.clearReadingPosition() }
}
