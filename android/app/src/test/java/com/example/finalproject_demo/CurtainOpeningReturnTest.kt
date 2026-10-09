package com.example.finalproject_demo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.finalproject_demo.net.Accounts
import com.example.finalproject_demo.net.AuthProvider
import com.example.finalproject_demo.net.Guardian
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.shell.Shell
import com.example.finalproject_demo.ui.shell.Step
import com.example.finalproject_demo.ui.shell.TERMS_VERSION
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w640dp-h360dp-land-480dpi")
class CurtainOpeningReturnTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var savedGuardian: Guardian? = null

    @Before fun fresh() {
        savedGuardian = Accounts.guardian
        Shell.resetToFirstRun(); ConsentStore.withdraw()
    }

    @After fun clean() {
        Shell.resetToFirstRun(); ConsentStore.withdraw()
        Accounts.guardian = savedGuardian
    }

    private fun completeOpening() {
        compose.runOnIdle {
            Accounts.guardian = Guardian(AuthProvider.EMAIL, "opening@example.invalid", 1L)
            ConsentStore.agree()
            Shell.saveConsent(TERMS_VERSION, false, 1L)
            Shell.finishOnboarding()
            Shell.step = Step.TITLE
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("opening-rope").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("건너뛰기").performClick()
        compose.waitUntil(5000) { Shell.step == Step.APP }
        compose.waitForIdle()
        assertTrue("Completing or skipping the opening must persist it", Shell.openingSeen)
    }

    private fun passSplash() {
        compose.waitUntil(5000) { compose.onAllNodesWithText("C").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("C")), useUnmergedTree = true).performClick()
    }

    @Test fun completedOpeningIsSkippedOnTheNextLaunch() {
        completeOpening()
        compose.runOnIdle { Shell.step = Step.CLAP }
        passSplash()
        compose.waitUntil(5000) { Shell.step == Step.APP }
        compose.onNodeWithTag("opening-rope").assertDoesNotExist()
    }

    @Test fun repeatLaunchStillRequiresCurrentConsent() {
        completeOpening()
        compose.runOnIdle {
            Shell.saveConsent("previous-version", false, 1L)
            Shell.step = Step.CLAP
        }
        passSplash()
        compose.waitUntil(5000) { Shell.step == Step.CONSENT }
        compose.onNodeWithText("약관이 바뀌었어요").assertIsDisplayed()
        compose.onNodeWithTag("opening-rope").assertDoesNotExist()
    }

    @Test fun repeatLaunchStillRequiresAnAccount() {
        completeOpening()
        compose.runOnIdle { Accounts.guardian = null; Shell.step = Step.CLAP }
        passSplash()
        compose.waitUntil(5000) { Shell.step == Step.EXPIRED }
        compose.onNodeWithText("카카오로 시작하기").assertIsDisplayed()
        compose.onNodeWithTag("opening-rope").assertDoesNotExist()
    }
    @Test fun backingOutOfReconsentDoesNotReplayTheOpening() {
        completeOpening()
        compose.runOnIdle { Shell.saveConsent("previous-version", false, 1L); Shell.step = Step.CONSENT }
        compose.onNodeWithText("약관이 바뀌었어요").assertIsDisplayed()
        compose.onNodeWithText("←").performClick()
        compose.onNodeWithText("눌러서 시작").assertIsDisplayed()
        compose.onNodeWithTag("opening-rope").assertDoesNotExist()
        compose.onNodeWithText("눌러서 시작").performClick()
        compose.onNodeWithText("약관이 바뀌었어요").assertIsDisplayed()
    }
}
