package com.example

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityLaunchTest {

    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_launchesWithoutFlagSecure_andRendersServersAndNavigationTabs() {
        activityRule.waitUntil(timeoutMillis = 5000L) {
            activityRule.onAllNodesWithText("PocketSSH").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify FLAG_SECURE is disabled by default so emulator/streamed displays render visibly
        val flags = activityRule.activity.window.attributes.flags
        assertEquals(0, flags and WindowManager.LayoutParams.FLAG_SECURE)

        // Verify primary home screen renders visibly
        activityRule.onNodeWithText("PocketSSH").assertIsDisplayed()
        activityRule.onNodeWithTag("add_profile_fab").assertIsDisplayed()

        // Navigate across tabs to confirm full UI renders without blank screens
        activityRule.onNodeWithTag("nav_terminal").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("ยังไม่มี Terminal Session ที่เปิดอยู่").assertIsDisplayed()

        activityRule.onNodeWithTag("nav_files").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("จัดการไฟล์ SFTP").assertIsDisplayed()

        activityRule.onNodeWithTag("nav_settings").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("ตั้งค่าและความปลอดภัย").assertIsDisplayed()
    }
}
