package de.seemoo.at_tracking_detection

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.seemoo.at_tracking_detection.util.ble.DbmToPercent
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
class DbmToPercentTest {

    @Test
    fun testDbmToPercent() {
        for (i in -100..-1) {
            val signalPercentage = DbmToPercent.convert(i.toDouble())
            Timber.d("${i}dbm = ${signalPercentage}%")
        }
    }
}