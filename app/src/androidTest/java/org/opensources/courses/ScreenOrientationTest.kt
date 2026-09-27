package org.opensources.courses

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The screens are designed for portrait only (ADR 0027): the lock must stay in the manifest. */
@RunWith(AndroidJUnit4::class)
class ScreenOrientationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mainActivityIsLockedInPortrait() {
        val info =
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                PackageManager.ComponentInfoFlags.of(0),
            )

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)
    }
}
