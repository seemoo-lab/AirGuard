package de.seemoo.at_tracking_detection.util.ble

import kotlin.math.ceil

object DbmToPercent {


    /**
     * Code example from Linux WiFi driver converted to Kotlin
     * https://www.intuitibits.com/2016/03/23/dbm-to-percent-conversion/
     * https://github.com/torvalds/linux/blob/9ff9b0d392ea08090cd1780fb196f36dbb586529/drivers/net/wireless/intel/ipw2x00/ipw2200.c#L4321
     *
     * Example in PHP
     * function signal_quality_perc_quad($rssi, $perfect_rssi=-20, $worst_rssi=-85) {
            $nominal_rssi=($perfect_rssi – $worst_rssi);
            $signal_quality =
            (100 *
            ($perfect_rssi – $worst_rssi) *
            ($perfect_rssi – $worst_rssi) –
            ($perfect_rssi – $rssi) *
            (15 * ($perfect_rssi – $worst_rssi) + 62 * ($perfect_rssi – $rssi))) / (($perfect_rssi – $worst_rssi) * ($perfect_rssi – $worst_rssi));

            if ($signal_quality > 100) {
            $signal_quality = 100;
            } else if ($signal_quality < 1) {
            $signal_quality = 0;
            }
            return ceil($signal_quality);
            }

            function qualtable($min_rssi=-100, $max_rssi=-1, $perfect_rssi=-20, $worst_rssi=-80) {
            for ($rssi=$min_rssi; $rssi <= $max_rssi; $rssi++) {
            echo "$rssi ".signal_quality_perc_quad($rssi, $perfect_rssi, $worst_rssi)."\n";
        }
        }
     *
     */
    fun convert(rssi:Double, perfectRssi:Double=-20.0, worstRssi:Double=-90.0): Int {
        val nominalRssi = perfectRssi - worstRssi
        var signalQuality = (100 *
                (nominalRssi) *
                (nominalRssi) -
                (perfectRssi - rssi) *
                (15 * (nominalRssi) + 62 * (perfectRssi - rssi))) / ((nominalRssi) * (nominalRssi))

        if (signalQuality > 100) {
            signalQuality = 100.0
        } else if (signalQuality < 1) {
            signalQuality = 1.0
        }

        return ceil(signalQuality).toInt()
    }
}