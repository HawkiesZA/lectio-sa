package za.co.hawkiesza.lectiosa.content

import org.json.JSONObject

data class ContentManifest(
    val manifestVersion: Int,
    val contentVersion: String,
    val buildId: String,
    val editionRightsStatus: String,
    val editionRightsNotice: String,
    val sources: List<ContentManifestSource>,
    val sourceHashes: Map<String, String>,
    val coverage: List<ContentManifestCoverage>,
) {
    companion object {
        fun parse(json: String): ContentManifest {
            val root = JSONObject(json)
            val edition = root.getJSONObject("edition")
            val sourcesJson = root.getJSONArray("sources")
            val sources = (0 until sourcesJson.length()).map { index ->
                val source = sourcesJson.getJSONObject(index)
                ContentManifestSource(
                    id = source.getString("id"),
                    name = source.getString("name"),
                    url = source.getString("url"),
                    attribution = source.getString("attribution"),
                    rightsStatus = source.getString("rights_status"),
                    rightsNotice = source.getString("rights_notice"),
                )
            }
            val hashesJson = root.getJSONObject("source_hashes")
            val sourceHashes = hashesJson.keys().asSequence()
                .associate { key -> key to hashesJson.getString(key) }
            val coverageJson = root.getJSONArray("coverage")
            val coverage = (0 until coverageJson.length()).map { index ->
                val item = coverageJson.getJSONObject(index)
                val noReadings = item.getJSONArray("no_reading_dates")
                ContentManifestCoverage(
                    scope = item.getString("calendar_scope"),
                    start = item.getString("start"),
                    end = item.getString("end"),
                    calendarDays = item.getInt("calendar_days"),
                    massForms = item.getInt("mass_forms"),
                    readingAssignments = item.getInt("reading_assignments"),
                    noReadingDates = (0 until noReadings.length()).map(noReadings::getString),
                )
            }
            return ContentManifest(
                manifestVersion = root.getInt("manifest_version"),
                contentVersion = root.getString("content_version"),
                buildId = root.getString("build_id"),
                editionRightsStatus = edition.getString("rights_status"),
                editionRightsNotice = edition.getString("rights_notice"),
                sources = sources,
                sourceHashes = sourceHashes,
                coverage = coverage,
            )
        }
    }
}

data class ContentManifestSource(
    val id: String,
    val name: String,
    val url: String,
    val attribution: String,
    val rightsStatus: String,
    val rightsNotice: String,
)

data class ContentManifestCoverage(
    val scope: String,
    val start: String,
    val end: String,
    val calendarDays: Int,
    val massForms: Int,
    val readingAssignments: Int,
    val noReadingDates: List<String>,
)
