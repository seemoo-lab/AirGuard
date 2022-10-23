package de.seemoo.at_tracking_detection.database.models

import androidx.room.*
import java.time.LocalDateTime

@Entity(tableName = "location", indices = [Index(value = ["latitude", "longitude"], unique = true)])
data class Location(
    @PrimaryKey(autoGenerate = true) val locationId: Int,
    @ColumnInfo(name = "firstDiscovery") val firstDiscovery: LocalDateTime,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "latitude") val latitude: Double,
) {
    constructor(
        firstDiscovery: LocalDateTime,
        longitude: Double,
        latitude: Double,
    ): this(
        0, // TODO: does this add an actual 0 to the database???
        firstDiscovery,
        longitude,
        latitude,
    )
}