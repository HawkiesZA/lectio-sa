# Repository Guidelines

## Project Structure & Module Organization

This repository contains the Android application foundation and Bible source content. The main paths are:

- `app/` — Android application module and Compose UI.
- `content-tool/` — Kotlin/JVM importer and validator for source content.
- `content/` — editable calendar data and source/provenance notes; rows require review before release.
- `plans/` — product and technical plans, including the proposed South African Catholic readings app architecture.
- `assets/` — source artwork such as the application icon.
- `engwebc2025eb/mods.d/` — SWORD module configuration for the World English Bible Catholic edition.
- `engwebc2025eb/modules/texts/` — compressed SWORD Bible content. Treat these files as source content; do not edit generated or packaged content directly.

The planned application stack is Kotlin, Android, Jetpack Compose, Material 3, Room/SQLite, and DataStore. Keep source inputs, editable calendar data, and generated artifacts in separate directories when implementation begins.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repository root:

```sh
./gradlew assembleDebug  # Build a debug APK
./gradlew test           # Run JVM tests
./gradlew lint           # Run Android lint
./gradlew :content-tool:test :content-tool:run  # Validate calendar inputs
```

Content preparation should be reproducible: pin importer/tool versions, record source hashes and metadata, and validate the generated Room-compatible database before packaging it.

## Coding Style & Naming Conventions

Use standard Kotlin formatting with four-space indentation, `PascalCase` for types/composables, `camelCase` for functions and properties, and clear stable identifiers for books, readings, editions, and calendar entries. Prefer immutable state, constructor injection, coroutines, and `Flow`. Keep liturgical/calendar rules in domain or content-preparation code rather than UI code.

## Testing Guidelines

Add unit tests for date resolution, South African calendar coverage, passage/verse mapping, partial-verse fallbacks, and content-version upgrades. Use an injectable clock with `Africa/Johannesburg` fixtures. Add Android/instrumentation tests for offline launch, database integrity, navigation, and process recreation.

## Commit & Pull Request Guidelines

This checkout has no Git history, so no existing commit convention can be inferred. Use concise imperative commits (for example, `Add WEB content manifest`). Pull requests should describe scope, cite the relevant plan or source, include validation commands and results, and call out any changes to bundled content, licensing, calendar coverage, or generated outputs.

## Content, Licensing & Safety

Preserve the SWORD module metadata and attribution. Do not assume that calendar or lectionary data is freely redistributable merely because the WEB text is public domain. Record provenance and reuse status for every external dataset, and never substitute an unverified calendar for South African assignments.
