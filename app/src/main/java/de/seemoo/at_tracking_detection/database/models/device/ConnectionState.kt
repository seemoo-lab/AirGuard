package de.seemoo.at_tracking_detection.database.models.device

enum class ConnectionState {
    OFFLINE, PREMATURE_OFFLINE, OVERMATURE_OFFLINE, CONNECTED, UNKNOWN;

    override fun toString(): String {
        return name
    }
}