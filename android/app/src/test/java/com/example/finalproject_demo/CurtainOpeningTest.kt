package com.example.finalproject_demo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.FeelPrefs
import com.example.finalproject_demo.ui.motionFrozen
import com.example.finalproject_demo.ui.shell.Shell
import com.example.finalproject_demo.ui.shell.Step
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w640dp-h360dp-land-480dpi")
class CurtainOpeningTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var savedMotion = true
    private var savedSound = true
    @Before fun fresh() {
        savedMotion = motionFrozen; savedSound = FeelPrefs.soundOn
        Shell.resetToFirstRun(); ConsentStore.withdraw()
    }
    @After fun clean() {
        motionFrozen = savedMotion; FeelPrefs.setSound(savedSound)
        Shell.resetToFirstRun(); ConsentStore.withdraw()
    }

    @Test fun mutedOpeningCompletesWithoutWaitingForAnyServerOrAudio() {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("opening-rope").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { motionFrozen = false; FeelPrefs.setSound(false) }
        compose.onNodeWithTag("opening-rope").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 160f), 400)
        }
        compose.waitForIdle()
        compose.waitUntil(20000) { Shell.step == Step.LOGIN }
        assertEquals(false, ConsentStore.guardianAgreed)
    }

    @Test fun skippingStillRequiresTheExistingParentSetup() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("건너뛰기").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("건너뛰기").performClick()
        compose.waitUntil(5000) { Shell.step == Step.LOGIN }
        assertEquals(false, ConsentStore.guardianAgreed)
    }

    @Test fun holdingTheRopeDoesNotOpenTheCurtainUntilRelease() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("opening-rope").fetchSemanticsNodes().isNotEmpty()
        }
        val rope = compose.onNodeWithTag("opening-rope")
        rope.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, 160f)) }
        compose.onNodeWithText("좋아! 이제 손을 놓아 봐!").assertIsDisplayed()
        assertEquals(Step.TITLE, Shell.step)
        rope.performTouchInput { cancel() }
        compose.onNodeWithText("줄을 당겨 봐!").assertIsDisplayed()
        assertEquals(Step.TITLE, Shell.step)
    }
}
