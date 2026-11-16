package za.co.hawkiesza.lectiosa.content

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyContentViewModelTest {
    @Test
    fun splitsFullBookAlternatives() {
        assertEquals(
            listOf("Matthew 1:1-25", "Matthew 1:18-25"),
            splitAlternatives("Matthew 1:1-25 or Matthew 1:18-25"),
        )
    }

    @Test
    fun keepsCitationWithoutAlternativesUnchanged() {
        assertEquals(
            listOf("Prov 31:10-13 19-20 30-31"),
            splitAlternatives("Prov 31:10-13 19-20 30-31"),
        )
    }
}
