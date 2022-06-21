package de.seemoo.at_tracking_detection

import de.seemoo.at_tracking_detection.util.BuildVersionProvider

class TestBuildVersionProvider (private val sdk: Int): BuildVersionProvider {
    override fun sdkInt(): Int {
        return sdk
    }
}