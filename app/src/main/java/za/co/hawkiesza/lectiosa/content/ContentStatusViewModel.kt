package za.co.hawkiesza.lectiosa.content

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ContentStatusViewModel(application: Application) : AndroidViewModel(application) {
    private val _status = MutableStateFlow("Opening offline content…")
    val status: StateFlow<String> = _status
    private val _supportedDateRange = MutableStateFlow<SupportedDateRange?>(null)
    val supportedDateRange: StateFlow<SupportedDateRange?> = _supportedDateRange

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val database = ContentDatabase.create(application)
                val dao = database.contentDao()
                val metadata = dao.metadata().associate { it.key to it.value }
                val verseCount = dao.verseCount()
                val calendarDayCount = dao.calendarDayCount()
                _supportedDateRange.value = dao.calendarDateRange("SACBC").toSupportedDateRangeOrNull()
                "${metadata["source_module"] ?: "content"} · $verseCount records · $calendarDayCount calendar days"
            }.onSuccess { _status.value = it }
                .onFailure { error -> _status.value = "Content unavailable: ${error.message ?: "database error"}" }
        }
    }
}
