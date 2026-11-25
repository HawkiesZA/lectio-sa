package za.co.hawkiesza.lectiosa.content

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledContentDatabaseTest {
    private val noReadingDates = setOf(
        "2026-09-15",
        "2026-10-19",
        "2026-11-02",
        "2027-09-15",
        "2027-10-19",
        "2027-11-02",
    )

    @Test
    fun everyReviewedDateAndMassFormHasAssignments() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = ContentDatabase.create(context)
        try {
            val sqliteDatabase = database.openHelper.readableDatabase
            sqliteDatabase.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            sqliteDatabase.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(4, cursor.getInt(0))
            }
            val dao = database.contentDao()
            val days = dao.allDays()
            val range = dao.calendarDateRange("SACBC").toSupportedDateRangeOrNull()
            val edition = dao.editions().single()
            val metadata = dao.metadata().associate { it.key to it.value }
            val manifestJson = metadata["manifest_json"]
            assertNotNull(manifestJson)
            val manifest = ContentManifest.parse(manifestJson!!)

            assertEquals(730, days.size)
            assertEquals(LocalDate.parse("2026-01-01"), range?.start)
            assertEquals(LocalDate.parse("2027-12-31"), range?.end)
            assertEquals("World English Bible Catholic", edition.displayName)
            assertTrue(dao.calendarSourceUrls("SACBC").isNotEmpty())
            assertEquals("2026.11", metadata["content_version"])
            assertEquals(64, metadata["build_id"]?.length)
            assertEquals(1, manifest.manifestVersion)
            assertEquals("2026.11", manifest.contentVersion)
            assertEquals(64, manifest.buildId.length)
            assertEquals("public_domain", manifest.editionRightsStatus)
            assertTrue(manifest.sources.isNotEmpty())
            assertTrue(manifest.sourceHashes.isNotEmpty())
            assertEquals(1, manifest.coverage.size)
            assertEquals("SACBC", manifest.coverage.single().scope)
            assertEquals(730, days.map { it.date to it.calendarScope }.toSet().size)
            days.forEach { day ->
                val parsedDate = LocalDate.parse(day.date)
                assertTrue(parsedDate.year == 2026 || parsedDate.year == 2027)

                val forms = dao.forms(day.date, day.calendarScope)
                assertFalse("No Mass forms for ${day.date}", forms.isEmpty())
                assertEquals(1, forms.count { it.isDefault == 1 })

                forms.forEach { form ->
                    val assignments = dao.assignments(day.date, day.calendarScope, form.formId)
                    if (assignments.isEmpty()) {
                        assertTrue(day.date in noReadingDates)
                        assertEquals(1, forms.size)
                        return@forEach
                    }
                    assertEquals((1..assignments.size).toList(), assignments.map { it.sequence })
                    assignments.forEach { assignment ->
                        assertTrue(assignment.originalCitation.isNotBlank())
                        assertTrue(assignment.reviewStatus == "reviewed")
                    }
                }
            }
        } finally {
            database.close()
        }
    }
}
