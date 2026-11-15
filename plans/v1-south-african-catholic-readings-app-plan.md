# South African Catholic Daily Readings — Android App Plan

Prepared: 11 November 2026  
Status: Implementation plan; calendar sourcing and licensed lectionary text remain subject to confirmation.

## 1. Product and initial decisions

Build a native Android app that opens directly to today's Catholic Scripture readings for South Africa and works without an internet connection, including on its first launch.

The first version will use the World English Bible with Catholic book order, imported from eBible.org's SWORD module into SQLite during development. The app will follow a reviewed South African reading schedule. It will describe its text as a translation for personal reading and preparation, with wording that differs from the authorised lectionary heard at Mass.

When the bishops respond, an authorised South African lectionary can be added as a separate content edition without rebuilding the calendar or changing the main reading interface.

| Decision | Plan |
| --- | --- |
| First platform | Android phones; layouts also remain usable on tablets. |
| Language | English. |
| Rite and calendar | Current Roman Rite; South African national calendar within the SACBC region. |
| Initial Scripture | WEB Catholic, publisher ID `eng-web-c`. |
| Runtime storage | SQLite accessed through Room. |
| UI and application language | Kotlin, Jetpack Compose, Material 3. |
| Offline behaviour | All daily reading content is bundled; no first-run download. |
| Accounts and backend | None for version 1. |
| Content updates | Bundled in app releases initially. |
| Initial distribution | Internal APK testing, then a Google Play test track and public release. |
| Commercial assumptions | Plan initially assumes a free app without advertisements; this is a proposed product choice. |
| Other platforms | Keep content formats portable; defer iOS and web implementation. |

The current workspace contains no application repository or existing stack to preserve. Kotlin is the proposed primary stack. Use Android 8.0/API 26 as the initial minimum, subject to the actual minimums of the stable libraries selected at project setup. Target the Android version required by Google Play at release.

Success means a user can install the app, enable airplane mode, open today's readings, change dates, and read comfortably without registration or connectivity.

## 2. What the app will show

### Essential version 1 features

- Today's date, celebration, season, and liturgical colour, with text labels as well as colour.
- The first reading, selected psalm verses, second reading where assigned, and Gospel.
- Additional readings for packaged celebrations such as the Easter Vigil.
- Previous/next day navigation, a date picker, and a clear return-to-today action.
- A choice of Mass form where relevant, such as Christmas Vigil, Night, Dawn, and Day.
- Long/short reading options where supplied in the reviewed dataset.
- Readable typography, adjustable text size, system/light/dark themes, and optional verse numbers.
- Remembered reading position and preferences after closing the app.
- An About and Sources screen showing edition, attribution, calendar scope, covered dates, and content version.

Daily readings here include Good Friday's celebration of the Passion, even though it is not a Mass. The interface must handle this without showing an incorrect Mass label.

### Deliberate limits

Version 1 is a daily Scripture reader. Defer a complete Bible browser, full-text search, commentaries, audio, accounts, cloud synchronisation, widgets, reminders, and additional languages. The complete WEB Catholic Bible can still be bundled to support passage resolution.

Use the South African national calendar as the default. Diocesan, parish, and religious-order calendars require additional verified data; they are not implicitly covered. Explain that a parish may legitimately use another permitted set of readings.

Support the principal daily reading set and the alternatives explicitly packaged with it. Do not attempt to implement every votive, funeral, wedding, or optional memorial choice in the first release.

### Translation and psalm presentation

Place a small persistent label near the readings:

> WEB Catholic · South African national calendar

Provide an explanation in About:

> Scripture passages selected for the South African calendar, presented in the World English Bible with Catholic book order. The wording differs from the authorised Mass lectionary. Local celebrations and permitted choices may differ.

The publisher describes this edition as containing the Catholic book set and the Greek additions in Esther and Daniel. Its name does not establish Catholic ecclesiastical approval. Preserve the text and its edition identity. [eBible edition and reuse notice](https://ebible.org/find/details.php?id=eng-web-c)

For the psalm, show the assigned Scripture verses in WEB. Include a response only when it can be sourced and mapped to the WEB text or is otherwise authorised. If an official response is unavailable, say so briefly rather than inventing a liturgical response. Apply the same rule to Gospel acclamations. Sequences, official reading introductions, prayers, and other liturgical prose are outside the WEB text and are excluded unless separately sourced and cleared.

## 3. South African calendar and reading assignments

### Sourcing decision

Create a reviewed, precomputed annual dataset during development. The Android app will look up the resolved day locally; it will not calculate a full liturgical calendar or scrape daily-reading websites at runtime.

Use the current SACBC-approved annual Ordo/calendar and lectionary references as the intended authority. Ask the SACBC for the authorised source, current edition, reusable reference data, and any distribution conditions.

The [Southern African Jesuit online Ordo](https://ordo.jesuitssouthern.africa/ordo_full.php) is useful for research and comparison. It is not a ready-made national feed: its editor explains that it includes Jesuit celebrations and selected local observances, and that errors are possible. Do not import its daily default wholesale. [Ordo scope and editorial notes](https://ordo.jesuitssouthern.africa/intro.php)

Its [SACBC proper-calendar page](https://ordo.jesuitssouthern.africa/sacbc.php) is a useful checklist for regional observances and transfers. Confirm each applicable rule against the current annual source; do not treat that page as complete, current authority for every year or diocese.

### Preparation workflow

1. Obtain an appropriate annual South African reference source and establish permitted reuse of the required data.
2. Enter or import dates, celebrations, ranks, colours, reading references, Mass forms, and available alternatives into structured source files.
3. Record a source citation and review status for each assignment; record corrections explicitly.
4. Separate national observances from diocesan and religious-order additions.
5. Independently check the national dataset against the annual source, using a South African liturgy reviewer where possible.
6. Compile and validate it into the app's content database.

A calendar API may assist the preparation tool later. Adopt one only after verifying South African coverage, readings coverage, provenance, and terms. A generic Catholic calendar or the USCCB feed is insufficient evidence of South African correctness.

### Coverage and expiry

- Prototype: a small reviewed set of ordinary and exceptional days; visibly identify it as sample coverage.
- Initial public release: aim for the release calendar year and the following year, with every date in the advertised range covered. At this planning date, that means 2026 and 2027 if release occurs in 2026.
- Extend coverage annually before the bundled range expires. If release moves to 2027, shift the coverage target accordingly.
- Outside the supported range, show an explicit unavailable-calendar message. Do not substitute the US calendar, repeat last year's dates, or invent assignments.
- Show the supported date range in Settings and alert users within the app when it is nearing its end.

Content preparation must cover seasonal precedence, feast transfers, optional alternatives, the Sunday cycle change at Advent, and the weekday cycle. Store the resolved choices rather than deriving them with a simple civil-year formula.

### Permission status

WEB's public-domain status covers its Scripture text. It does not establish permission to redistribute another publisher's Ordo, calendar compilation, editorial notes, or lectionary material. The earlier conversation overstated certainty about freely copying calendar data. Confirm the calendar dataset's provenance and reuse terms separately; avoid reproducing source-page prose or layout.

Development of the reader and passage importer can proceed while this is resolved. Public South African calendar coverage depends on obtaining an appropriate dataset and completing review.

## 4. Import WEB Catholic from SWORD

Use SWORD as a development-time source format. Ship the resulting SQLite content, keeping SWORD and its native runtime out of the Android application.

The publisher currently lists the SWORD archive as `engwebc2025eb.zip`. Inspect the archive's module configuration for its actual module identifier, version, versification, and notices; do not infer those values from the filename. The same page also offers VPL + SQL, USFM, and USFX exports. These provide useful comparison sources and a publisher-supplied fallback if the SWORD export has problems. Starting with SWORD remains the agreed plan. [Publisher downloads](https://ebible.org/find/details.php?id=eng-web-c)

### Reproducible build pipeline

```text
Pinned WEB Catholic SWORD archive + module configuration
                         |
             SWORD export / linked-entry resolution
                         |
           Normalised text and paragraph structure
                         |
           Reference mapping + annual assignments
                         |
              Validation and review report
                         |
         Room-compatible SQLite + content manifest
                         |
                  Android app assets
```

1. Acquire the publisher's archive and record its URL, retrieval date, SHA-256, module metadata, and licence notice.
2. Use supported SWORD tooling, evaluating `mod2osis` first, or a small SWORD-based exporter if needed. Pin the tool version in the preparation environment.
3. Preserve book identifiers, native verse labels, paragraph and poetry breaks, and meaningful text. Separate headings and notes from Scripture; exclude optional apparatus from the initial reader.
4. Resolve linked entries and verse bridges explicitly. A linked or combined entry must not disappear, repeat incorrectly, or be mistaken for an intentionally absent verse.
5. Parse structured markup with a parser. Produce a limited, documented representation suitable for Compose; avoid arbitrary HTML rendering and regex-only tag removal.
6. Write the content using the Room-exported schema, then validate the generated database on Android.
7. Produce a manifest containing schema version, content version, source hashes, edition, versification, date coverage, and review status.

CrossWire documents that `mod2imp` skips link entries, so a naive `mod2imp` dump is not an adequate complete importer. Verify export completeness against the module and a publisher-provided alternate representation. [CrossWire module tools](https://wiki.crosswire.org/DevTools:Modules)

Implement orchestration and normalisation as a small Kotlin/JVM content tool alongside the Android project, with external SWORD tools confined to preparation. Keep original inputs, editable calendar files, mappings, and generated artifacts in distinct directories.

## 5. References, selections, and verse numbering

The simple schema proposed earlier, with one start and end verse per reading, is insufficient. A reading can cross chapters, omit verses, use part of a verse, offer a short form, or use numbering that differs between editions.

Use these concepts:

- **Reading assignment:** which reading is selected for a date, celebration, and Mass form.
- **Reading option:** a long form, short form, or other permitted alternative.
- **Passage segment:** one ordered contiguous selection within an option; a reading can contain several segments.
- **Edition mapping:** the source-reference selection mapped into a particular Bible edition and versification.
- **Rendered reading:** the resolved segments and their formatting, ready for the reader.

Store the original citation alongside the normalised references. Use stable book codes rather than display names or assumed Protestant book indexes. Keep original labels for combined or unusual verse entries, with a separate ordering field for queries.

### Partial-verse policy for the WEB edition

Preserve `a`, `b`, and similar selectors in the assignment data. Do not assume they identify the same words across translations.

Where a reviewed WEB mapping exists, select the corresponding WEB excerpt without paraphrasing. Where it does not, version 1 may include the whole boundary verse and show a notice such as “Whole verse included; the lectionary selects part of this verse.” Record that expansion in the data and validation report. Every such fallback must be visible and reviewed before release; unresolved book/verse mappings are release failures.

Handle Psalms, superscriptions, Greek Esther, Daniel's additions, and Baruch/Letter of Jeremiah explicitly. Matching chapter and verse numbers do not by themselves prove equivalent content. Psalm numbering must follow the chosen source and edition mappings.

Make the daily reader capable of displaying precomposed licensed readings later, including approved introductions and responses. Reconstructing those texts from a licensed full Bible may still fail to reproduce the lectionary exactly.

## 6. Android architecture and storage

Use a single Android app module initially, organised into UI, domain, and data packages. Add separate modules only when actual boundaries justify them. Use a small composition root and constructor injection to begin.

| Component | Choice and responsibility |
| --- | --- |
| UI | Compose and Material 3; accessible date navigation and reading screens. |
| State | ViewModels with immutable screen state, coroutines, and Flow. |
| Reading service | Resolves date, calendar scope, celebration, Mass form, and edition. |
| Content repository | Reads bundled calendar assignments, references, and Scripture. |
| Content database | Room/SQLite; replaceable application-owned data. |
| User preferences | DataStore; theme, font size, verse-number preference, selected edition. |
| Reading position | Small persistent local state keyed by date, Mass form, and edition. |
| User database | Add a separate Room database if bookmarks or annotations are introduced. |
| Content tool | Kotlin/JVM Gradle task plus pinned SWORD utilities. |

Android recommends Compose for native UI and a local source for core offline reads. These choices suit a reader whose content is already on the device. [Compose](https://developer.android.com/compose) · [Offline data architecture](https://developer.android.com/topic/architecture/data-layer/offline-first)

### Logical content schema

These are design entities, not final SQL definitions:

| Entity | Important fields |
| --- | --- |
| `content_metadata` | schema/content version, source hashes, build ID, coverage, review status |
| `edition` | stable ID, display name, source module, versification, attribution, rights metadata |
| `book` | stable code, display name, edition-specific order |
| `verse_unit` | edition, book, chapter, native verse label, stable key, sort order, text/structure |
| `calendar_day` | calendar scope, civil date, season, cycles, selected celebration, source |
| `celebration` | stable ID, name, rank, colour, national/local scope |
| `mass_form` | celebration/date association, form ID and label, default choice |
| `reading_assignment` | Mass form, role, display order, original citation, source, review status |
| `reading_option` | assignment, option label, default flag |
| `passage_segment` | option, order, source reference/versification and partial-verse selectors |
| `edition_segment_mapping` | segment, edition, ordered target units/spans, mapping status, expansion notice |
| `licensed_reading` | future authorised display text/structure, reading option, edition, attribution |

Validate relationships and indexes, especially lookups by date/scope, assignment order, and edition/book/chapter. Render the handful of daily passages on demand; do not duplicate complete reading text for every date when it can be resolved from WEB.

Room supports prepopulating from an asset and validates that asset against its schema. The database is copied into application storage; it is not queried directly inside the APK. Treat content as read-only through the repository, even if the database file is writable. [Room prepopulation](https://developer.android.com/training/data-storage/room/prepopulate)

### Content upgrades

`createFromAsset()` alone does not refresh an existing installation when the packaged content changes. Implement a content-version installer:

1. Compare the bundled manifest with installed content at startup.
2. Stage a new content database and validate its schema, integrity, manifest, and checksum.
3. Close content database connections before promoting it; retain the previous working version until activation succeeds.
4. Open the new content and restore date/position where possible. If preparation or activation fails, retain the previous usable content and report the problem.

Handle schema migrations separately from content versions. An app update must preserve user preferences and future personal data. Keep replaceable content out of personal backup where practical; confirm any future licensed text's backup conditions.

## 7. Screens and daily behaviour

### Today / selected day

The launch screen is the reading screen. A compact header shows date and celebration, followed by the ordered readings. Use readable spacing and poetry layout; avoid decorative elements that compete with the text. Give users an optional reading outline to jump to the psalm or Gospel.

Keep Mass-form and long/short choices visible when available. A Saturday evening user can choose Sunday's readings; do not change the selected day automatically at an arbitrary evening hour. Proper vigil readings remain distinct from the next day's principal Mass.

### Calendar

A month view and date picker show available dates and celebrations. Choosing a date opens its readings. Covered dates should be clear; unsupported dates display the coverage explanation.

### Settings and sources

Include appearance controls, translation explanation, South African national-calendar scope, covered dates, source and attribution notices, and the installed content version. Add an easy way to report a date/reference issue by copying diagnostic details for the user to share.

### Date and accessibility rules

- Use the device's current local date for “today”; the selected calendar remains South African regardless of travel. No location permission is required.
- Refresh “today” after midnight and on resume, without jumping away from a date the user deliberately selected.
- Use an injectable clock for deterministic tests, including `Africa/Johannesburg` fixtures.
- Support TalkBack, large system fonts, adequate contrast, and touch targets. Do not rely on liturgical colour alone.
- Reading and scrolling continue after process recreation, rotation, and offline restart.

## 8. Future licensed South African text

Continue the SACBC enquiry in parallel with the WEB implementation. Confirm the current approved lectionary rather than treating the historical RSV-2CE/Revised Grail description as a complete current specification.

Request the exact reading texts, psalms and responses, acclamations, introductions, reference data, available digital formats, and relevant rights contacts. State explicitly that content will be bundled in an Android app and available offline; clarify geographical distribution, backup, excerpt sharing, attribution, review, and fees as applicable.

Ignatius Press publishes a specific RSV-2CE permissions route. This is a contact path, not evidence that a licence or digital files have already been offered. [Ignatius permissions](https://ignatius.com/permission-requests/)

When suitable permission and files arrive:

1. Add a new edition with its own source, version, and rights metadata.
2. Import the supplied full readings directly where available; otherwise resolve licensed Scripture through reviewed mappings.
3. Verify the South African wording, psalm responses, special Mass forms, and reference differences.
4. Enable the authorised edition only for coverage and distribution permitted by the agreement.
5. Preserve existing WEB availability and user settings. Explain the new edition and let the user choose or confirm a new default.

Receiving text or permission does not by itself make the app SACBC-approved. Describe approval only to the extent expressly granted. If permission is declined or delayed, the reviewed WEB edition remains a viable personal-reading product.

## 9. Delivery sequence

| Milestone | Work and deliverable | Exit condition |
| --- | --- | --- |
| 1. Content proof | Acquire the WEB module; inspect export; prepare a small South African sample covering an ordinary day and difficult cases. | Correct Scripture and ordered references can be resolved locally; source and mapping issues are documented. |
| 2. Android vertical slice | Create Kotlin/Compose app, content DB installation, and one complete daily reading screen. | Fresh installation in airplane mode renders the sample without setup or a network request. |
| 3. Reference correctness | Add ordered segments, cross-chapter passages, partial-verse handling, Mass forms, and long/short choices. | Automated fixtures and manual comparison confirm no hidden omissions or incorrect selections. |
| 4. Annual content | Secure suitable sources and reuse terms; prepare, review, and compile the advertised year range. | Every covered date resolves; national rules and all exceptions are reviewed. |
| 5. Reading experience | Date navigation, appearance, accessibility, position restoration, and sources/coverage screens. | Small user test can complete the core tasks without guidance. |
| 6. Release hardening | Exercise upgrades, offline restarts, older phones, current Android, and Play test-track installation. | Release criteria below pass; build and content versions are traceable. |
| 7. Licensed edition | Import and review authorised South African lectionary content when supplied. | Applicable agreement, review, files, and distribution conditions are satisfied. |

Milestones 2 and 3 can proceed with the small sample while calendar sourcing continues. Annual content review is likely to be the main schedule uncertainty; do not promise a release date before measuring the import and reference-mapping work.

## 10. Verification and release criteria

### Content validation

- All 73 Catholic books are represented, with the expected Greek additions; do not impose a universal verse count.
- No accidental duplicate, empty, lost linked, or repeated bridged entries after export.
- Scripture text matches the pinned source; formatting changes do not alter wording.
- Every advertised date has a principal reading set, source, and review status.
- Every passage segment resolves in order; every whole-verse expansion is reviewed and disclosed.
- Psalm numbering, cross-chapter passages, omitted verses, long/short options, and deuterocanonical references have explicit fixtures.
- South African transfer dates and national observances are compared against the annual authority, with no accidental Jesuit or US defaults.
- Advent/year boundaries, Holy Week, Good Friday, Easter Vigil, Christmas forms, and optional choices receive manual review.

### App validation

- First launch, date changes, readings, appearance, and restart all work in airplane mode.
- Midnight rollover, travel/time-zone changes, and manually selected dates behave predictably.
- Content upgrades and failed replacements preserve existing usable data and user preferences.
- Unsupported dates show a clear message and never use another country's assignments silently.
- TalkBack and large fonts preserve access to date navigation and all reading text.
- Opening a day feels immediate on a representative older phone. Initial performance target: show today's readings within two seconds on the agreed test device, then measure and adjust using real builds.

Use JVM tests for reference resolution and validation, Android database tests for installation and upgrades, and a small set of Compose tests for core navigation. Human liturgical review checks the source selections themselves; tests alone cannot establish that the input calendar is correct.

### Public release gate

Release when the full advertised date range is reviewed, sources and reuse conditions are recorded, the WEB/lectionary distinction is clear, and core offline/accessibility/upgrade checks pass. Prepare a signed Android App Bundle, privacy statement, store description, support contact, and accurate Play data declarations. Version 1 should need no runtime permission for its core reader and no application server.

## 11. Repository and maintenance

Keep the future project small and explicit:

```text
app/                         Android application
content-tool/                JVM importer, compiler, and validators
content/sources/             Pinned original inputs and provenance
content/calendar/            Editable annual assignments and source references
content/mappings/            Reviewed edition/versification mappings
content/notices/             Attributions and permitted-use records
docs/                        Architecture and content-maintenance instructions
build/                       Generated intermediates; never hand-edited
```

The build produces databases and reports from editable sources. Generated assets, packaged outputs, and build directories must not become the place to fix text or calendar mistakes. Respect the repository's RTK and language-specific instructions when implementation begins.

For each annual release, acquire the new authority, update assignments and mappings, run the validation report, review South African exceptions, and test an upgrade from the previous app. Record the source and reviewer for every correction. Maintain the current year plus the next year where verified data is available, and state coverage honestly when it is not.

Defer downloadable content packages until bundled updates become burdensome. If added, download and validate a complete package before activating it, preserve the last working content, and keep all reading paths local. Retain the portable content format and Kotlin domain logic so a later iOS implementation can reuse the editorial work without dictating its UI technology now.
