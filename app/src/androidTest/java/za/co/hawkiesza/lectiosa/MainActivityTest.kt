package za.co.hawkiesza.lectiosa

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchActivity() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun offlineLaunchShowsBundledReadingShell() {
        waitForText("Lectio SA")
        composeRule.onNodeWithContentDescription("More options").assertExists()
        composeRule.onNodeWithContentDescription("Previous day").assertExists()
        composeRule.onNodeWithContentDescription("Next day").assertExists()
        composeRule.onNodeWithText("Support").assertExists()
    }

    @Test
    fun rotationPreservesCurrentScreen() {
        openMenuItem("Sources")
        waitForText("Sources")
        waitForText("Build metadata")

        scenario.recreate()

        waitForText("Sources")
        waitForText("Build metadata")
        composeRule.onNodeWithContentDescription("Back to readings").assertExists()
    }

    @Test
    fun processStateRecreationPreservesTextSizePreference() {
        openMenuItem("Settings")
        waitForText("Text size")
        composeRule.onNodeWithText("Extra large").performClick()
        composeRule.waitForIdle()

        // Recreate the activity after persistence has completed. This exercises the
        // same saved-state boundary available to the app without killing the test
        // instrumentation process itself.
        scenario.recreate()

        waitForText("Text size")
        composeRule.onNodeWithText("Extra large").assertIsSelected()
    }

    @Test
    fun coreNavigationControlsExposeAccessibleSemantics() {
        waitForText("Lectio SA")
        composeRule.onNodeWithContentDescription("More options").assertExists()
        composeRule.onNodeWithContentDescription("Previous day").assertExists()
        composeRule.onNodeWithContentDescription("Next day").assertExists()

        openMenuItem("About")
        waitForText("About Lectio SA")
        composeRule.onNodeWithContentDescription("Back to readings").assertExists()
        composeRule.onNodeWithText("Scripture edition").assertExists()
    }

    @Test
    fun extraLargeTextKeepsReadingContentReachable() {
        openMenuItem("Settings")
        waitForText("Text size")
        composeRule.onNodeWithText("Extra large").performClick()
        composeRule.onNodeWithContentDescription("Back to readings").performClick()

        waitForText("Lectio SA")
        composeRule.onNodeWithText("Gospel").assertExists()
        composeRule.onNodeWithContentDescription("Next day").assertExists()
    }

    private fun openMenuItem(label: String) {
        waitForText("Lectio SA")
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText(label).performClick()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
