package za.co.hawkiesza.lectiosa.content

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            val dao = database.contentDao()
            val days = dao.allDays()

            assertEquals(730, days.size)
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
