package de.seemoo.at_tracking_detection.ui.devices.filter.models

import androidx.core.util.Pair
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import java.time.LocalDate
import java.time.ZoneId

class DateRangeFilter(
    private val fromDate: LocalDate? = null,
    private val untilDate: LocalDate? = null
) : Filter() {
    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        if (fromDate == null && untilDate == null) return baseDevices

        return baseDevices.filter { device ->
            val deviceDate = device.lastSeen
            val fromMatch = fromDate?.atStartOfDay()?.let { deviceDate.isAfter(it) } ?: true
            val untilMatch = untilDate?.atTime(23, 59)?.let { deviceDate.isBefore(it) } ?: true

            fromMatch && untilMatch
        }
    }

    fun getTimeRangePair(): Pair<Long, Long>? {
        if (fromDate == null || untilDate == null) return null
        return Pair(toMilli(fromDate), toMilli(untilDate))
    }

    private fun toMilli(localDate: LocalDate): Long =
        localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}