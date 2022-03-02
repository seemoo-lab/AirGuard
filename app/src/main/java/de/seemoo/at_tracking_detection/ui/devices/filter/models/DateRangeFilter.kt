package de.seemoo.at_tracking_detection.ui.devices.filter.models

import androidx.core.util.Pair
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import java.time.LocalDate
import java.time.ZoneId

class DateRangeFilter : Filter() {
    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        return baseDevices.filter { device ->
            var untilMatch = true
            val untilDate = untilDate
            val fromDate = fromDate
            if (untilDate != null) {
                untilMatch = device.lastSeen.isBefore(untilDate.atTime(23, 59))
            }
            var fromMatch = true
            if (fromDate != null) {
                fromMatch = device.lastSeen.isAfter(fromDate.atStartOfDay())
            }

            return@filter untilMatch && fromMatch
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
            return DateRangeFilter()
        }
    }
}