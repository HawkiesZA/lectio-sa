package za.co.hawkiesza.lectiosa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LectioSaApp() }
    }
}

private data class Reading(
    val title: String,
    val reference: String,
    val text: String,
)

private val sampleReadings = listOf(
    Reading(
        title = "First reading",
        reference = "Sample content pending calendar review",
        text = "The reviewed South African reading assignment will appear here. This vertical slice is intentionally offline and contains no network dependency.",
    ),
    Reading(
        title = "Psalm",
        reference = "Sample response pending source review",
        text = "The assigned psalm verses and response will be added after the calendar and passage mappings are reviewed.",
    ),
    Reading(
        title = "Gospel",
        reference = "Sample content pending calendar review",
        text = "WEB Catholic text will be resolved locally from the bundled content database once the importer and reviewed assignments are in place.",
    ),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LectioSaApp() {
    val contentStatusViewModel: za.co.hawkiesza.lectiosa.content.ContentStatusViewModel = viewModel()
    val contentStatus by contentStatusViewModel.status.collectAsState()
    val dailyContentViewModel: za.co.hawkiesza.lectiosa.content.DailyContentViewModel = viewModel()
    val dailyContent by dailyContentViewModel.content.collectAsState()
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(selectedDate)
    val displayDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    LaunchedEffect(selectedDate) { dailyContentViewModel.load(selectedDate) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(title = { Text("Lectio SA") })
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "South African Catholic daily readings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "WEB Catholic · South African national calendar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = contentStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    DateNavigation(
                        displayDate = displayDate,
                        isToday = date == LocalDate.now(),
                        onPrevious = { selectedDate = date.minusDays(1).toString() },
                        onNext = { selectedDate = date.plusDays(1).toString() },
                        onToday = { selectedDate = LocalDate.now().toString() },
                    )
                    Spacer(Modifier.height(16.dp))
                    when (val state = dailyContent) {
                        is za.co.hawkiesza.lectiosa.content.DailyContentState.Available -> {
                            Text(state.celebration, style = MaterialTheme.typography.titleMedium)
                            if (state.reviewStatus != "reviewed") {
                                Text("Development data: calendar review pending", color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(12.dp))
                            state.readings.forEachIndexed { index, reading ->
                                ReadingCard(
                                    Reading(
                                        title = reading.role.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                        reference = reading.citation,
                                        text = reading.text,
                                    ),
                                )
                                if (index < state.readings.lastIndex) Spacer(Modifier.height(12.dp))
                            }
                        }
                        za.co.hawkiesza.lectiosa.content.DailyContentState.Loading -> Text("Loading readings…")
                        za.co.hawkiesza.lectiosa.content.DailyContentState.Unavailable -> {
                            Text("No calendar coverage", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text("This date is not yet available in the reviewed South African calendar.")
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun DateNavigation(
    displayDate: String,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(displayDate, style = MaterialTheme.typography.titleSmall)
            if (!isToday) {
                Button(onClick = onToday) { Text("Today") }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
        }
    }
}

@Composable
private fun ReadingCard(reading: Reading) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(reading.title, style = MaterialTheme.typography.titleLarge)
            Text(
                reading.reference,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(reading.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
