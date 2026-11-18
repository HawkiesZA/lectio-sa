# Content sources and review status

The first release uses the World English Bible Catholic edition for Scripture and a South African calendar assembled from the Southern African Jesuit Ordo and its SACBC proper-calendar page:

- Full Ordo: <https://ordo.jesuitssouthern.africa/ordo_full.php>
- SACBC proper calendar: <https://ordo.jesuitssouthern.africa/sacbc.php>

The Ordo is a research and comparison source, not by itself proof of SACBC approval for redistribution. Calendar rows must remain `needs_review` until they have been checked against the applicable annual authority and reviewed by the project owner or a liturgical reviewer. Do not publish the sample rows as complete coverage.

The WEB SWORD module and its metadata remain under `engwebc2025eb/`. Record its retrieval date, module version, source hash, and licence notice when the importer pipeline is formalised.

From the repository root, inspect and prepare the module with the native SWORD tools:

```sh
./gradlew :content-tool:run --args='--inspect-sword engwebc2025eb'
./gradlew :content-tool:run --args='--export-sword engwebc2025eb /private/tmp/engwebc2025eb.imp'
./gradlew :content-tool:run --args='--normalize-imp /private/tmp/engwebc2025eb.imp /private/tmp/engwebc2025eb.tsv'
./gradlew :content-tool:run --args='--validate-tsv /private/tmp/engwebc2025eb.tsv'
./gradlew :content-tool:run --args='--render-tsv /private/tmp/engwebc2025eb.tsv /private/tmp/engwebc2025eb-rendered.tsv'
./gradlew :content-tool:run --args='--validate-books /private/tmp/engwebc2025eb-rendered.tsv'
./gradlew :content-tool:run --args='--compile-db /private/tmp/engwebc2025eb-rendered.tsv content/calendar /private/tmp/lectio-content.db'
```

The `.imp` and `.tsv` files are preparation intermediates. Keep them outside packaged Android outputs and retain their source hash in the content manifest.

## WEB Catholic Psalm 119 source overlay

The current SWORD module exports Psalm 119 with an invalid verse boundary: verses after 6 are aggregated under verse 7. Obtain the verse-level Psalm 119 text from the pinned WEB Catholic upstream source and save it as a four-column TSV:

```text
book	chapter	verse	text
Psalms	119	1	...
...
Psalms	119	176	...
```

For the downloaded VPL source, generate that TSV with:

```sh
python3 content/sources/extract_psalm119.py \
  eng-web-c_vpl/eng-web-c_vpl.txt \
  /private/tmp/psalms-119.tsv
```

Apply the checked-in source before compiling the database:

```sh
./gradlew :content-tool:run --args='--overlay-source /private/tmp/engwebc2025eb-rendered.tsv content/sources/psalms-119.tsv /private/tmp/engwebc2025eb-rendered-overlaid.tsv'
./gradlew :content-tool:run --args='--validate-books /private/tmp/engwebc2025eb-rendered-overlaid.tsv'
./gradlew :content-tool:run --args='--compile-db /private/tmp/engwebc2025eb-rendered-overlaid.tsv content/calendar /private/tmp/lectio-content.db'
```

After a Room schema change, run `./gradlew :app:compileDebugKotlin` before `--compile-db`; the compiler reads the generated Room identity hash from the current schema file under `app/schemas/...`.

The overlay command requires exactly one non-empty row for every Psalm 119 verse from 1 through 176 and removes the malformed module verse rows. Record the upstream URL and SHA-256 of the TSV alongside the extract.

Current extract provenance: `https://ebible.org/eng-web-c/PSA119.htm`; SHA-256 `ec62b5de60fe0fee18bab7356708d13242561d0e63f0a85c053f75675384691e`.

## WEB Catholic Psalm 78 source overlay

The SWORD module also aggregates Psalm 78 after verse 12, so citations such as `Ps 78:56-57 58-59 61-62` cannot resolve from the module export. The checked-in replacement extract was generated from the VPL source with:

```sh
python3 content/sources/extract_psalm.py \
  eng-web-c_vpl/eng-web-c_vpl.txt 78 content/sources/psalms-78.tsv
./gradlew :content-tool:run --args='--overlay-source /private/tmp/engwebc2025eb-rendered-overlaid.tsv content/sources/psalms-78.tsv /private/tmp/engwebc2025eb-rendered-overlaid-78.tsv'
```

Then compile the database from `engwebc2025eb-rendered-overlaid-78.tsv`. Provenance: `https://ebible.org/eng-web-c/PSA78.htm`; SHA-256 `aebb47e5a7102ac10ff4ec41b4f767536b53c21edffe08ab29b42b0269d00b8c`.

## Psalm boundary audit

The SWORD export has the same kind of lost verse boundaries in multiple Psalms. Compare every Psalm against the VPL source and generate a combined overlay for all affected chapters:

```sh
python3 content/sources/audit_psalm_boundaries.py \
  eng-web-c_vpl/eng-web-c_vpl.txt \
  /private/tmp/engwebc2025eb-rendered.tsv \
  --overlay-output content/sources/psalms-affected.tsv

./gradlew :content-tool:run --args='--overlay-source /private/tmp/engwebc2025eb-rendered.tsv content/sources/psalms-affected.tsv /private/tmp/engwebc2025eb-rendered-overlaid.tsv'
```

The checked-in overlay currently repairs 62 affected Psalms, including Psalms 78 and 119. The audit exits with status 1 when repairs are needed and status 0 when the rendered import has no missing non-empty Psalm verse rows. Its source provenance is `https://ebible.org/eng-web-c/PSA{chapter}.htm`; SHA-256 of the current combined overlay is `5f15a9be4789d08a332561088ed5c5417ea297b1cc5e3d1431b8ca626e1fdb78`.

### Repairing non-Psalm SWORD boundaries

The SWORD export can also merge a non-Psalm verse into the previous verse. Compare the rendered import with the publisher VPL source and generate an overlay for affected chapters:

```sh
python3 content/sources/extract_vpl.py \
  eng-web-c_vpl/eng-web-c_vpl.txt \
  /private/tmp/engwebc2025eb-rendered.tsv \
  /private/tmp/engwebc2025eb-vpl-overlay.tsv
./gradlew :content-tool:run --args='--overlay-source /private/tmp/engwebc2025eb-rendered.tsv /private/tmp/engwebc2025eb-vpl-overlay.tsv /private/tmp/engwebc2025eb-rendered-overlaid.tsv'
```

The overlay is limited to chapters where the VPL has non-empty verse rows missing from the rendered import. It replaces the affected chapter’s imported text with the publisher VPL text and records `source-override:eng-web-c-vpl` as provenance. Intentionally blank VPL verses, such as textual variants, remain unresolved and are not fabricated.

The validator and app also apply explicit WEB versification mappings: Romans 16:25–27 to Romans 14:24–26, Malachi 3:19–24 to Malachi 4:1–6, Joel 4 to Joel 3, Hosea 14:10 to 14:9, Genesis 32:33 to 32:32, Jonah 2:11 to 2:10, and Zechariah 2:14–15 to 3:1–2. These mappings preserve the original calendar citation while selecting the corresponding WEB source units.

Greek Esther Addition C is represented specially. The WEB Catholic VPL merges C into Esther chapter 4 beginning at WEB Esther 4:18, so `Esth C:12 14-16 23-25` maps to Esther 4:29, 31-33, and 40-42. The original Greek-addition citation remains visible in calendar data.

## Annual Ordo review extracts

The year-selected Ordo form is submitted with `year=2026` or `year=2027`. The repeatable preparation script is `content/sources/scrape_ordo.py`; it writes the review-only extracts below:

- `content/calendar/2026_to_review.csv`
- `content/calendar/2027_to_review.csv`

Each extract contains one SACBC-scoped row per day, uses the annual Ordo's embedded SACBC local-calendar blocks where present, and keeps every row at `needs_review`.

The scraper prints a warning for dates whose selected row contains no reading citation at all. These are source-review cases, not silently filled data: most are SACBC proper-calendar entries that provide only a common form, while 19 October is a Jesuit observance whose page refers users to seasonal weekday readings without printing them.

After review, merge the approved rows into `content/calendar/2026.csv` and `content/calendar/2027.csv`. Files ending in `_to_review.csv` are retained as preparation inputs and are deliberately excluded from database compilation.

To refresh the canonical files after editing the review extracts, run:

```sh
python3 content/sources/merge_calendar_review.py
```

The script preserves canonical rows already marked `reviewed` and imports the remaining rows from the review extracts.

Audit canonical calendar structure before compiling:

```sh
python3 content/sources/audit_calendar.py
python3 content/sources/audit_calendar.py --fix-safe-shifts
```

Validate every annual and multi-reading citation against the rendered WEB verse index before compiling:

```sh
./gradlew :content-tool:run --args='--validate-calendar /private/tmp/engwebc2025eb-rendered-overlaid.tsv content/calendar'
```

This validation is also run automatically by `--compile-db`. It checks alternatives, paired Psalms, cross-chapter ranges, separated verse groups, and partial-verse selectors. Partial selectors use the approved whole-verse fallback and are counted in the report; malformed or missing references fail the build.

Documented source exceptions live in `content/calendar/content_exceptions.csv`. Use them only for reviewed edition-specific omissions or source gaps; each entry is compiled into the database and shown as a small notice below the affected reading.

The WEB source uses a different Psalm verse numbering convention for some Psalms. The validator/app currently record explicit offsets for Psalms 51 and 52, and otherwise use a preceding-verse fallback only when the cited verse is absent. Exact WEB verse matches remain unchanged. Parenthesized alternatives such as `Ps 40 (39):7-8` are normalized to the primary Psalm number. Psalm selectors such as `1b-e` are treated as whole-verse selections under the approved partial-verse policy.

The audit only repairs unambiguous Psalm/second-reading column shifts. It also reports other likely column shifts, editorial notes, supported cross-chapter citations, and alternatives. Cross-chapter citations are informational: the Android resolver supports same-book ranges such as `John 18:1-19:42`. These findings are not data errors and do not need rewriting.

## Multi-reading celebrations

Celebrations with more than one permitted Mass form use `content/calendar/mass_forms.csv` and `content/calendar/mass_readings.csv`. The form file defines the visible label, order, and default; the readings file contains one ordered row per reading unit, with optional paired psalms and an `obligatory` or `optional` selection rule. The compiler validates form references and contiguous sequences and adds the rows to the same Room reading-assignment table. The files cover the 2026 and 2027 Easter Vigils, Nativity of St John the Baptist, and Christmas forms.
