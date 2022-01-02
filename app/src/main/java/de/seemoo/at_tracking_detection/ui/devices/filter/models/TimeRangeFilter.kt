package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.tables.Device
import java.time.LocalDate

class TimeRangeFilter : Filter() {
    override fun apply(devices: List<Device>): List<Device> {
        return devices.filter {
            untilDate?.atStartOfDay()?.isAfter(it.lastSeen) ?: true && fromDate?.atStartOfDay()
                ?.isBefore(it.lastSeen) ?: true
        }
    }

    companion object {
        private var fromDate: LocalDate? = null
        private var untilDate: LocalDate? = null

        fun build(from: LocalDate? = null, until: LocalDate? = null): Filter {
            fromDate = from
            untilDate = until
            return TimeRangeFilter()
        }
    }
}