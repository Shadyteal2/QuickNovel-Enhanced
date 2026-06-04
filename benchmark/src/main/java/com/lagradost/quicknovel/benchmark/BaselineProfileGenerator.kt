package com.lagradost.quicknovel.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import androidx.test.rule.GrantPermissionRule
import android.content.Intent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        val args = InstrumentationRegistry.getArguments()
        val context = InstrumentationRegistry.getInstrumentation().context
        val targetPackageName = args.getString("targetPackageName")
            ?: args.getString("androidx.benchmark.targetPackageName")
            ?: listOf("com.shady.neoqn.debug", "com.shady.neoqn").firstOrNull { pkg ->
                try {
                    context.packageManager.getPackageInfo(pkg, 0)
                    true
                } catch (e: Exception) {
                    false
                }
            } ?: "com.shady.neoqn.debug"

        baselineProfileRule.collect(
            packageName = targetPackageName,
            includeInStartupProfile = true
        ) {
            // Pre-grant notification permission to the target app via shell commands before launching it
            try {
                device.executeShellCommand("pm grant $targetPackageName android.permission.POST_NOTIFICATIONS")
            } catch (e: Exception) {
                // Ignore if command fails (e.g. on older Android versions where permission doesn't exist)
            }

            // App Startup Journey
            pressHome()
            val cmd = "am start -n $targetPackageName/com.lagradost.quicknovel.MainActivity --ez is_benchmark true"
            device.executeShellCommand(cmd)

            // Wait for the main library navigation item to load
            val mainContent = device.wait(Until.findObject(By.res(targetPackageName, "nav_item_download")), 10000)
            if (mainContent == null) {
                device.waitForIdle(5000)
            }

            // Handle Android 13+ notification permission popup if it appears
            try {
                val allowBtn = device.findObject(By.res("android:id/button1")) ?: 
                               device.findObject(By.text("Allow")) ?:
                               device.findObject(By.text("ALLOW"))
                allowBtn?.click()
                device.waitForIdle()
            } catch (e: Exception) {
                // Ignore if not present
            }

            // Dismiss any update/changelog popups or "Cancel" dialog buttons if they block the screen
            try {
                val cancelBtn = device.findObject(By.text("Cancel")) ?:
                                device.findObject(By.text("CANCEL")) ?:
                                device.findObject(By.res(targetPackageName, "cancel"))
                cancelBtn?.click()
                device.waitForIdle()
            } catch (e: Exception) {
                // Ignore if not present
            }

            // Step 1: Navigate to the Library Tab
            val libraryTab = device.wait(Until.findObject(By.res(targetPackageName, "nav_item_download")), 5000)
            libraryTab?.click()
            device.waitForIdle()

            // Step 2: Scroll through the Library List / Grid (fail-safe)
            try {
                val scrollableList = device.wait(Until.findObject(By.scrollable(true)), 5000)
                scrollableList?.let {
                    it.setGestureMargin(device.displayWidth / 10)
                    try {
                        it.fling(Direction.DOWN)
                    } catch (e: Exception) {
                        // Ignore scroll timing/event failures
                    }
                    device.waitForIdle()

                    try {
                        it.fling(Direction.DOWN)
                    } catch (e: Exception) {
                        // Ignore scroll timing/event failures
                    }
                    device.waitForIdle()
                    
                    try {
                        it.fling(Direction.UP)
                    } catch (e: Exception) {
                        // Ignore scroll timing/event failures
                    }
                    device.waitForIdle()
                }
            } catch (e: Exception) {
                // Ignore general gesture errors
            }
        }
    }
}
