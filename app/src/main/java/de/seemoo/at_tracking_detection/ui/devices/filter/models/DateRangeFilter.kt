package de.seemoo.at_tracking_detection.ui.devices.filter.models

import androidx.core.util.Pair
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DateRangeFilter(
    private val fromDate: LocalDateTime? = null,
    private val untilDate: LocalDateTime? = null
) : Filter() {
    // Secondary constructor for callers that still work with LocalDate (e.g. date picker)
    constructor(fromLocalDate: LocalDate?, untilLocalDate: LocalDate?) : this(
        fromLocalDate?.atStartOfDay(),
        untilLocalDate?.atTime(23, 59)
    )

    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        if (fromDate == null && untilDate == null) return baseDevices

        return baseDevices.filter { device ->
            val deviceDate = device.lastSeen
            val fromMatch = fromDate?.let { !deviceDate.isBefore(it) } ?: true
            val untilMatch = untilDate?.let { !deviceDate.isAfter(it) } ?: true

            fromMatch && untilMatch
        }
    }

    fun getTimeRangePair(): Pair<Long, Long>? {
        if (fromDate == null || untilDate == null) return null
        return Pair(toMilli(fromDate.toLocalDate()), toMilli(untilDate.toLocalDate()))
    }

    private fun toMilli(localDate: LocalDate): Long =
        localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}