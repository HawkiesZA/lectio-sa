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
