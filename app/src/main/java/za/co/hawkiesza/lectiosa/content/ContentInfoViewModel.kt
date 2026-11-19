package za.co.hawkiesza.lectiosa.content

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ContentInfo(
    val edition: EditionEntity,
    val metadata: Map<String, String>,
    val supportedDateRange: SupportedDateRange?,
    val calendarDayCount: Int,
    val verseCount: Int,
    val calendarSourceUrls: List<String>,
)

sealed interface ContentInfoState {
    data object Loading : ContentInfoState
    data class Available(val info: ContentInfo) : ContentInfoState
    data class Unavailable(val message: String) : ContentInfoState
}

class ContentInfoViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<ContentInfoState>(ContentInfoState.Loading)
    val state: StateFlow<ContentInfoState> = _state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val database = ContentDatabase.create(application)
                try {
                    val dao = database.contentDao()
                    val edition = dao.editions().firstOrNull()
                        ?: error("No Scripture edition is bundled")
                    ContentInfo(
                        edition = edition,
                        metadata = dao.metadata().associate { metadata -> metadata.key to metadata.value },
                        supportedDateRange = dao.calendarDateRange("SACBC").toSupportedDateRangeOrNull(),
                        calendarDayCount = dao.calendarDayCount(),
                        verseCount = dao.verseCount(),
                        calendarSourceUrls = dao.calendarSourceUrls("SACBC"),
                    )
                } finally {
                    database.close()
                }
            }.onSuccess { _state.value = ContentInfoState.Available(it) }
                .onFailure { error ->
                    _state.value = ContentInfoState.Unavailable(error.message ?: "database error")
                }
        }
    }
}
