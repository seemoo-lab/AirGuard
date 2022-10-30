package de.seemoo.at_tracking_detection.database.models

import androidx.room.*
import java.time.LocalDateTime

@Entity(tableName = "location", indices = [Index(value = ["latitude", "longitude"], unique = true)])
data class Location(
    @PrimaryKey(autoGenerate = true) val locationId: Int,
    @ColumnInfo(name = "name") var name: String?,
    @ColumnInfo(name = "firstDiscovery") val firstDiscovery: LocalDateTime,
    @ColumnInfo(name = "lastSeen") var lastSeen: LocalDateTime,
    @ColumnInfo(name = "longitude") var longitude: Double,
    @ColumnInfo(name = "latitude") var latitude: Double,
    @ColumnInfo(name = "accuracy") var accuracy: Float?,
) {
    constructor(
        firstDiscovery: LocalDateTime,
        longitude: Double,
        latitude: Double,
        accuracy: Float?
    ): this(
        0, // TODO: does this add an actual 0 to the database???
        null,
        firstDiscovery, // firstDiscovery
        firstDiscovery, // lastSeen
        longitude,
        latitude,
        accuracy
    )
}