# Lectio SA TODO

This checklist tracks the implementation plan against the current prototype.

## Completed

- [x] Android app foundation with package `za.co.hawkiesza.lectiosa`, app icon, API 26 minimum, and API 37 target.
- [x] Compose/Material 3 offline reading screen.
- [x] Room/SQLite bundled content database with schema migrations through version 4, including documented content exceptions.
- [x] WEB Catholic SWORD import, normalisation, rendering, validation, and database compilation.
- [x] Psalm boundary audit and source overlays, including the 62 affected Psalms found in the SWORD export.
- [x] Reviewed 2026 and 2027 South African calendar coverage.
- [x] Multi-reading celebrations and Mass-form selection.
- [x] Alternative reading navigation, cross-chapter citations, and date previous/next navigation.
- [x] Content-tool and citation parser regression tests.

## Next: content correctness

- [x] Add a strict automated validation pass proving every calendar citation resolves in the WEB source used for the bundled database or has a reviewed edition/source exception.
- [x] Preserve and detect partial-verse selectors (`a`, `b`, `bc`, etc.) and show a compact whole-verse fallback notice when the full boundary verse is displayed.
- [x] Test every 2026/2027 date, alternative, cross-chapter citation, and Mass form, including documented no-reading dates.
- [ ] Confirm calendar provenance and redistribution rights before public release.

## Next: app features

- [ ] Add month calendar/date-picker navigation and supported-date-range display.
- [ ] Add About and Sources screens with edition, attribution, provenance, and coverage details.
- [ ] Add Settings for system/light/dark theme, text size, and optional verse numbers.
- [ ] Add DataStore preferences and remembered reading position.
- [ ] Refresh “today” on resume and after midnight without disrupting a deliberately selected date.
- [ ] Add a report-issue action that copies date, citation, edition, and content-version diagnostics.

## Next: reliability and release

- [ ] Add a content manifest with content version, source hashes, rights metadata, build ID, and coverage.
- [ ] Implement staged content replacement with validation, checksum verification, and rollback.
- [ ] Add Android tests for offline launch, database integrity, rotation, process recreation, accessibility, and large fonts.
- [ ] Verify TalkBack, contrast, touch targets, typography, and tablet layouts on devices.
- [ ] Complete release signing, internal Play testing, support/privacy text, and store metadata.
- [ ] Rebuild and verify the final bundled database after every content change.

## Deliberately deferred

- [ ] Additional authorised South African lectionary edition, pending source files and licensing.
- [ ] Full Bible browser, search, commentaries, audio, accounts, synchronisation, widgets, reminders, and additional languages.
