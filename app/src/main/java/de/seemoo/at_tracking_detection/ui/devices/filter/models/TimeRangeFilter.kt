package de.seemoo.at_tracking_detection.ui.devices.filter.models

import androidx.core.util.Pair
import de.seemoo.at_tracking_detection.database.tables.Device
import java.time.LocalDate
import java.time.ZoneId

class TimeRangeFilter : Filter() {
    override fun apply(devices: List<Device>): List<Device> {
        return devices.filter {
            untilDate?.atStartOfDay()?.isAfter(it.lastSeen) ?: true && fromDate?.atStartOfDay()
                ?.isBefore(it.lastSeen) ?: true
        }
    }

    fun getTimeRangePair(): Pair<Long, Long>? {
        val anyNull = listOf(fromDate, untilDate).any { it == null }
        return if (anyNull) {
            null
        } else {
            Pair(toMilli(fromDate!!), toMilli(untilDate!!))
        }
    }

    private fun toMilli(localDate: LocalDate): Long =
        localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

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