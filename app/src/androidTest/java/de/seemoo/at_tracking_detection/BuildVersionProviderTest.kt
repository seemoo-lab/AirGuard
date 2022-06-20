package de.seemoo.at_tracking_detection

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.seemoo.at_tracking_detection.util.DefaultBuildVersionProvider
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuildVersionProviderTest {

    @Test
    fun testDefaultVersionProvider() {
        assert(Build.VERSION.SDK_INT == DefaultBuildVersionProvider().sdkInt())
    }

    @Test
    fun testTestVersionProvider() {
        assert(10 == TestBuildVersionProvider(10).sdkInt())
    }
}