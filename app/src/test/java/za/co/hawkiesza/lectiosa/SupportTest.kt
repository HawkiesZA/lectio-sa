package za.co.hawkiesza.lectiosa

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportTest {
    @Test
    fun tippingJarUrlIsStable() {
        assertEquals("https://tippingjar.co.za/creator/hawkiesza", TIPPING_JAR_URL)
    }
}
