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

After a Room schema change, run `./gradlew :app:compileDebugKotlin` before `--compile-db`; the compiler reads the generated Room identity hash from `app/schemas/.../2.json`.

The overlay command requires exactly one non-empty row for every Psalm 119 verse from 1 through 176 and removes the malformed module verse rows. Record the upstream URL and SHA-256 of the TSV alongside the extract.

Current extract provenance: `https://ebible.org/eng-web-c/PSA119.htm`; SHA-256 `ec62b5de60fe0fee18bab7356708d13242561d0e63f0a85c053f75675384691e`.

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

The audit only repairs unambiguous Psalm/second-reading column shifts. It also reports other likely column shifts, editorial notes, cross-chapter citations, and alternatives. These remain explicit findings for source review rather than being guessed or silently rewritten.

## Multi-reading celebrations

Celebrations with more than the ordinary first reading, psalm, second reading, and Gospel use `content/calendar/multi_readings.csv`. It contains one ordered row per reading unit, with optional paired psalms and an `obligatory` or `optional` selection rule. The compiler validates contiguous sequences and adds these rows to the same Room reading-assignment table. The Easter Vigil on 2027-03-27 is the first such entry and contains ten ordered units.
