package za.co.hawkiesza.lectiosa

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZoneOffset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LectioSaApp() }
    }
}

private data class Reading(
    val title: String,
    val alternatives: List<ReadingAlternative>,
    val pairedPsalmAlternatives: List<ReadingAlternative> = emptyList(),
    val selectionRule: String = "obligatory",
)

private data class ReadingAlternative(
    val reference: String,
    val text: String,
    val fallbackNotice: String? = null,
    val exceptionNotice: String? = null,
    val verseLines: List<String> = emptyList(),
)

private enum class AppScreen { Daily, About, Sources, Support }

private val sampleReadings = listOf(
    Reading(
        title = "First reading",
        alternatives = listOf(ReadingAlternative("Sample content pending calendar review", "The reviewed South African reading assignment will appear here. This vertical slice is intentionally offline and contains no network dependency.")),
    ),
    Reading(
        title = "Psalm",
        alternatives = listOf(ReadingAlternative("Sample response pending source review", "The assigned psalm verses and response will be added after the calendar and passage mappings are reviewed.")),
    ),
    Reading(
        title = "Gospel",
        alternatives = listOf(ReadingAlternative("Sample content pending calendar review", "WEB Catholic text will be resolved locally from the bundled content database once the importer and reviewed assignments are in place.")),
    ),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LectioSaApp() {
    val contentStatusViewModel: za.co.hawkiesza.lectiosa.content.ContentStatusViewModel = viewModel()
    val contentStatus by contentStatusViewModel.status.collectAsState()
    val supportedDateRange by contentStatusViewModel.supportedDateRange.collectAsState()
    val contentInfoViewModel: za.co.hawkiesza.lectiosa.content.ContentInfoViewModel = viewModel()
    val contentInfo by contentInfoViewModel.state.collectAsState()
    val dailyContentViewModel: za.co.hawkiesza.lectiosa.content.DailyContentViewModel = viewModel()
    val dailyContent by dailyContentViewModel.content.collectAsState()
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var selectedMassFormId by rememberSaveable { mutableStateOf("") }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.Daily) }
    var browserUnavailable by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = screen != AppScreen.Daily) {
        screen = AppScreen.Daily
    }
    val date = LocalDate.parse(selectedDate)
    val displayDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    LaunchedEffect(selectedDate, selectedMassFormId) { dailyContentViewModel.load(selectedDate, selectedMassFormId) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            if (screen != AppScreen.Daily) {
                                IconButton(onClick = { screen = AppScreen.Daily }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to readings")
                                }
                            }
                        },
                        title = {
                            Text(
                                when (screen) {
                                    AppScreen.Daily -> "Lectio SA"
                                    AppScreen.About -> "About Lectio SA"
                                    AppScreen.Sources -> "Sources"
                                    AppScreen.Support -> "Support Lectio SA"
                                },
                            )
                        },
                        actions = {
                            if (screen == AppScreen.Daily) {
                                TextButton(onClick = { screen = AppScreen.About }) { Text("About") }
                                TextButton(onClick = { screen = AppScreen.Sources }) { Text("Sources") }
                                TextButton(onClick = { browserUnavailable = false; screen = AppScreen.Support }) { Text("Support") }
                            }
                        },
                    )
                },
            ) { padding ->
                if (screen == AppScreen.Daily) {
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
                    supportedDateRange?.let { range ->
                        Text(
                            text = "Supported dates: ${range.start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))} – " +
                                range.end.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    DateNavigation(
                        displayDate = displayDate,
                        isToday = date == LocalDate.now(),
                        canGoPrevious = supportedDateRange?.let { date.isAfter(it.start) } ?: true,
                        canGoNext = supportedDateRange?.let { date.isBefore(it.end) } ?: true,
                        canChooseDate = supportedDateRange != null,
                        onPrevious = { selectedDate = date.minusDays(1).toString(); selectedMassFormId = "" },
                        onNext = { selectedDate = date.plusDays(1).toString(); selectedMassFormId = "" },
                        onToday = { selectedDate = LocalDate.now().toString(); selectedMassFormId = "" },
                        onChooseDate = { datePickerOpen = true },
                    )
                    if (datePickerOpen && supportedDateRange != null) {
                        SupportedDatePicker(
                            selectedDate = date,
                            supportedDateRange = supportedDateRange!!,
                            onDismiss = { datePickerOpen = false },
                            onSelect = {
                                selectedDate = it.toString()
                                selectedMassFormId = ""
                                datePickerOpen = false
                            },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    when (val state = dailyContent) {
                        is za.co.hawkiesza.lectiosa.content.DailyContentState.Available -> {
                            Text(state.celebration, style = MaterialTheme.typography.titleMedium)
                            if (state.forms.size > 1) {
                                MassFormPicker(
                                    forms = state.forms,
                                    selectedFormId = state.selectedFormId,
                                    onSelect = { selectedMassFormId = it },
                                )
                            }
                            if (state.reviewStatus != "reviewed") {
                                Text("Development data: calendar review pending", color = MaterialTheme.colorScheme.primary)
                            }
                            if (state.readings.isEmpty()) {
                                Text(
                                    "No readings are available for this date.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            state.readings.forEachIndexed { index, reading ->
                                ReadingCard(
                                    Reading(
                                        title = when (reading.role) {
                                            "reading" -> "Reading ${reading.sequence}"
                                            "epistle" -> "Epistle"
                                            "alleluia_psalm" -> "Alleluia Psalm"
                                            "gospel" -> "Gospel"
                                            else -> reading.role.replace('_', ' ').replaceFirstChar { it.uppercase() }
                                        },
                                        alternatives = reading.alternatives.map { alternative ->
                                            ReadingAlternative(alternative.citation, alternative.text, alternative.fallbackNotice, alternative.exceptionNotice, alternative.verseLines)
                                        },
                                        pairedPsalmAlternatives = reading.pairedPsalmAlternatives.map { alternative ->
                                            ReadingAlternative(alternative.citation, alternative.text, alternative.fallbackNotice, alternative.exceptionNotice, alternative.verseLines)
                                        },
                                        selectionRule = reading.selectionRule,
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
                } else {
                    if (screen == AppScreen.Support) {
                        SupportScreen(
                            modifier = Modifier.padding(padding),
                            browserUnavailable = browserUnavailable,
                            onOpenTippingJar = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TIPPING_JAR_URL))
                                    it.startActivity(intent)
                                    browserUnavailable = false
                                } catch (_: ActivityNotFoundException) {
                                    browserUnavailable = true
                                }
                            },
                        )
                    } else {
                        ContentInfoScreen(
                            modifier = Modifier.padding(padding),
                            state = contentInfo,
                            showSources = screen == AppScreen.Sources,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentInfoScreen(
    modifier: Modifier,
    state: za.co.hawkiesza.lectiosa.content.ContentInfoState,
    showSources: Boolean,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(if (showSources) "Sources" else "About", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        when (state) {
            za.co.hawkiesza.lectiosa.content.ContentInfoState.Loading -> {
                Text("Loading bundled content details…")
            }
            is za.co.hawkiesza.lectiosa.content.ContentInfoState.Unavailable -> {
                Text("Content details unavailable", color = MaterialTheme.colorScheme.primary)
                Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is za.co.hawkiesza.lectiosa.content.ContentInfoState.Available -> {
                if (showSources) SourcesContent(state.info) else AboutContent(state.info)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SupportScreen(
    modifier: Modifier,
    browserUnavailable: Boolean,
    onOpenTippingJar: (android.content.Context) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Support Lectio SA", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "If Lectio SA is useful to you, you can support its continued development with a voluntary tip.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = { onOpenTippingJar(context) }) {
            Text("Open tipping jar")
        }
        if (browserUnavailable) {
            Spacer(Modifier.height(12.dp))
            Text(
                "No browser is available on this device to open the tipping jar.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutContent(info: za.co.hawkiesza.lectiosa.content.ContentInfo) {
    Text("Lectio SA", style = MaterialTheme.typography.titleLarge)
    Text(
        "Offline South African Catholic daily readings with locally bundled Scripture text.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(20.dp))
    DetailSection("Scripture edition") {
        DetailRow("Edition", info.edition.displayName)
        DetailRow("Source module", info.edition.sourceModule)
        DetailRow("Versification", info.edition.versification)
        DetailRow("Attribution", info.edition.attribution)
        DetailRow("Rights", info.edition.rightsNotice)
    }
    Spacer(Modifier.height(16.dp))
    DetailSection("Bundled coverage") {
        DetailRow("Calendar", "South African national calendar")
        info.supportedDateRange?.let {
            DetailRow("Dates", "${formatShortDate(it.start)} – ${formatShortDate(it.end)}")
        }
        DetailRow("Calendar days", info.calendarDayCount.toString())
        DetailRow("Scripture records", info.verseCount.toString())
    }
}

@Composable
private fun SourcesContent(info: za.co.hawkiesza.lectiosa.content.ContentInfo) {
    Text(
        "All readings are resolved locally from the bundled database; network access is not required.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(20.dp))
    DetailSection("Scripture source") {
        DetailRow("Edition", info.edition.displayName)
        DetailRow("Module", info.edition.sourceModule)
        DetailRow("Attribution", info.edition.attribution)
        DetailRow("Rights", info.edition.rightsNotice)
    }
    Spacer(Modifier.height(16.dp))
    DetailSection("Calendar source") {
        DetailRow("Scope", "SACBC")
        if (info.calendarSourceUrls.isEmpty()) {
            Text("No source URL recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            info.calendarSourceUrls.forEach { DetailRow("Source URL", it) }
        }
        info.supportedDateRange?.let {
            DetailRow("Coverage", "${formatShortDate(it.start)} – ${formatShortDate(it.end)}")
        }
    }
    Spacer(Modifier.height(16.dp))
    DetailSection("Build metadata") {
        info.metadata["schema_version"]?.let { DetailRow("Schema version", it) }
        info.metadata["rendered_source_sha256"]?.let { DetailRow("Rendered source SHA-256", it) }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    content()
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
private fun DateNavigation(
    displayDate: String,
    isToday: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    canChooseDate: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onChooseDate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious, enabled = canGoPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            TextButton(onClick = onChooseDate, enabled = canChooseDate) {
                Text(displayDate, style = MaterialTheme.typography.titleSmall)
            }
            if (!isToday) {
                Button(onClick = onToday) { Text("Today") }
            }
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SupportedDatePicker(
    selectedDate: LocalDate,
    supportedDateRange: za.co.hawkiesza.lectiosa.content.SupportedDateRange,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.toEpochMilliUtc(),
        yearRange = supportedDateRange.start.year..supportedDateRange.end.year,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return supportedDateRange.contains(utcMillisToLocalDate(utcTimeMillis))
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis
                        ?.let(::utcMillisToLocalDate)
                        ?.let(onSelect)
                },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text("Select") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun LocalDate.toEpochMilliUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToLocalDate(utcTimeMillis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun MassFormPicker(
    forms: List<za.co.hawkiesza.lectiosa.content.AvailableMassForm>,
    selectedFormId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = forms.firstOrNull { it.id == selectedFormId } ?: forms.first()
    Box {
        Button(onClick = { expanded = true }) { Text("Mass: ${selected.label}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            forms.forEach { form ->
                DropdownMenuItem(
                    text = { Text(form.label) },
                    onClick = {
                        expanded = false
                        onSelect(form.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun ReadingCard(reading: Reading) {
    var alternativeIndex by rememberSaveable(reading.title, reading.alternatives.joinToString { it.reference }) {
        mutableStateOf(0)
    }
    var psalmAlternativeIndex by rememberSaveable(reading.title, "psalm", reading.pairedPsalmAlternatives.joinToString { it.reference }) {
        mutableStateOf(0)
    }
    val selectedAlternative = reading.alternatives.getOrNull(alternativeIndex)
        ?: reading.alternatives.first()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(reading.title, style = MaterialTheme.typography.titleLarge)
            if (reading.selectionRule == "optional") {
                Text("Optional reading", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            } else if (reading.title.startsWith("Reading ")) {
                Text("Obligatory reading", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            AlternativeNavigation(alternativeIndex, reading.alternatives.size, { alternativeIndex-- }, { alternativeIndex++ })
            Text(selectedAlternative.reference, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            selectedAlternative.fallbackNotice?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            selectedAlternative.exceptionNotice?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            PassageBody(selectedAlternative)
            if (reading.pairedPsalmAlternatives.isNotEmpty()) {
                val selectedPsalm = reading.pairedPsalmAlternatives[psalmAlternativeIndex.coerceIn(0, reading.pairedPsalmAlternatives.lastIndex)]
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Psalm", style = MaterialTheme.typography.titleMedium)
                AlternativeNavigation(psalmAlternativeIndex, reading.pairedPsalmAlternatives.size, { psalmAlternativeIndex-- }, { psalmAlternativeIndex++ })
                Text(selectedPsalm.reference, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                selectedPsalm.fallbackNotice?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                selectedPsalm.exceptionNotice?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                PassageBody(selectedPsalm)
            }
        }
    }
}

@Composable
private fun PassageBody(alternative: ReadingAlternative) {
    if (alternative.verseLines.isEmpty()) {
        Text(alternative.text, style = MaterialTheme.typography.bodyLarge)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            alternative.verseLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun AlternativeNavigation(
    index: Int,
    count: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (count > 1) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevious, enabled = index > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous alternative")
            }
            Text("Alternative ${index + 1} of $count", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onNext, enabled = index < count - 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next alternative")
            }
        }
    }
}
