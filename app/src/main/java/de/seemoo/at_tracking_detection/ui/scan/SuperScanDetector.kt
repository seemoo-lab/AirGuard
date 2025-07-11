package de.seemoo.at_tracking_detection.ui.scan

import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.TrackingNetwork
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.LocationRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime

class SuperScanDetector(
    private val beaconRepository: BeaconRepository,
    private val deviceRepository: DeviceRepository,
    private val locationRepository: LocationRepository
) {
    /**
     * Mode 1: Detects if a tracker from a single tracking network has been observed
     * at multiple locations over a specific period.
     *
     * This is like the normal scan, except it considers that a device might change its identity
     * within the same tracking network (e.g. AirTag to AirPods)
     *
     * @param daysToScan The number of past days to scan for tracker activity.
     * @param durationMinutes The required continuous tracking duration in minutes.
     * @param minLocations The minimum number of distinct locations a tracker must be seen at during the continuous period.
     * @param intervalMinutes The maximum time gap allowed between beacon sightings for the tracking to be considered continuous. // TODO: add tolerance
     * @return A list of devices that are suspected to be advanced trackers.
     */
    suspend fun checkTrackersWithIdentitySwitching(daysToScan: Long, durationMinutes: Long, minLocations: Int, intervalMinutes: Long = 15): List<BaseDevice> {
        Timber.d("Checking for trackers with identity switching over the last $daysToScan days, with a minimum duration of $durationMinutes minutes and at least $minLocations locations.")

        val suspectedDevices = mutableListOf<BaseDevice>()
        val scanStartTime = LocalDateTime.now().minusDays(daysToScan)

        // Fetch all beacons within the scan window and group them by device
        val allBeacons = beaconRepository.getBeaconsSince(scanStartTime).first()
        val beaconsByDevice = allBeacons.groupBy { it.deviceAddress }

        for ((deviceAddress, beacons) in beaconsByDevice) {
            val device = deviceRepository.getDevice(deviceAddress) ?: continue
            val trackingNetwork = device.deviceType?.getTrackingNetwork()

            // We only check devices that belong to a known tracking network
            if (trackingNetwork == null || trackingNetwork == TrackingNetwork.UNKNOWN) {
                continue
            }

            val sortedBeacons = beacons.sortedBy { it.receivedAt }
            if (sortedBeacons.size < 2) continue

            var sessionStartTime = sortedBeacons.first().receivedAt
            var currentSessionLocations = mutableSetOf<Int>()
            sortedBeacons.first().locationId?.let { currentSessionLocations.add(it) }

            // Iterate through beacons to find continuous tracking sessions
            for (i in 1 until sortedBeacons.size) {
                val prevBeacon = sortedBeacons[i - 1]
                val currentBeacon = sortedBeacons[i]
                val timeDiffMinutes = Duration.between(prevBeacon.receivedAt, currentBeacon.receivedAt).toMinutes()

                if (timeDiffMinutes <= intervalMinutes) {
                    // The session is continuous
                    currentBeacon.locationId?.let { currentSessionLocations.add(it) }
                } else {
                    // The session is broken, check if the previous session meets the criteria
                    val sessionDuration = Duration.between(sessionStartTime, prevBeacon.receivedAt)
                    if (sessionDuration.toMinutes() >= durationMinutes && currentSessionLocations.size >= minLocations) {
                        suspectedDevices.add(device)
                        break // Device is suspect, move to the next one
                    }
                    // Start a new session
                    sessionStartTime = currentBeacon.receivedAt
                    currentSessionLocations = mutableSetOf()
                    currentBeacon.locationId?.let { currentSessionLocations.add(it) }
                }
            }

            // Check the last session after the loop finishes
            val lastBeacon = sortedBeacons.last()
            val lastSessionDuration = Duration.between(sessionStartTime, lastBeacon.receivedAt)
            if (!suspectedDevices.contains(device) && lastSessionDuration.toMinutes() >= durationMinutes && currentSessionLocations.size >= minLocations) {
                suspectedDevices.add(device)
            }
        }

        Timber.d("Suspected devices: $suspectedDevices")

        return suspectedDevices
    }

    /**
     * Mode 2: Detects trackers that may only be active when moving.
     * This mode checks if a device has been seen at multiple new locations within a given duration.
     *
     * @param daysToScan The number of past days to scan for tracker activity.
     * @param durationMinutes The time window in minutes to look for new locations. // TODO: we need bigger duration for this mode / Alternative: get rid of durationHours and only consider daysToScan
     * @param minLocations The minimum number of new locations a tracker must be seen at within the duration.
     * @return A list of devices that are suspected to be motion-activated trackers.
     */
    suspend fun checkTrackersWithMotionSensor(daysToScan: Long, durationMinutes: Long, minLocations: Int): List<BaseDevice> {
        Timber.d("Checking for motion-activated trackers over the last $daysToScan days, with a minimum duration of $durationMinutes minutes and at least $minLocations locations.")

        val suspectedDevices = mutableListOf<BaseDevice>()
        val scanStartTime = LocalDateTime.now().minusDays(daysToScan)

        // Get all devices and filter them by the scan start time
        val allDevices = deviceRepository.devices.first().filter { it.lastSeen >= scanStartTime }

        for (device in allDevices) {
            // Get all locations for the device within the scan window, sorted by discovery time
            val deviceLocations = locationRepository.getLocationsForBeaconSince(device.address, scanStartTime)
                .sortedBy { it.firstDiscovery }

            if (deviceLocations.size < minLocations) {
                continue
            }

            // Use a sliding window to check if minLocations were visited within durationHours
            for (i in 0..deviceLocations.size - minLocations) {
                val window = deviceLocations.subList(i, i + minLocations)
                val firstLocationTime = window.first().firstDiscovery
                val lastLocationTime = window.last().firstDiscovery
                val timeDiff = Duration.between(firstLocationTime, lastLocationTime)

                if (timeDiff.toHours() <= durationMinutes) {
                    if (!suspectedDevices.any { it.address == device.address }) {
                        suspectedDevices.add(device)
                    }
                    break // Found a match for this device, move to the next one
                }
            }
        }

        Timber.d("Suspected motion-activated devices: $suspectedDevices")

        return suspectedDevices
    }

    /**
     * Mode 3: Detects trackers that might be switching tracking networks by looking for any
     * continuous tracking activity over a given period within a larger timeframe.
     *
     * @param daysToScan The number of past days to scan for tracker activity.
     * @param durationMinutes The required continuous tracking duration in minutes.
     * @param minLocations The minimum number of distinct locations a tracker must be seen at during the continuous period.
     * @param intervalMinutes The maximum time gap allowed between beacon sightings for the tracking to be considered continuous.
     * @return A boolean indicating if a potential network-switching tracker is detected.
     */
    suspend fun checkNetworkSwitchingTrackers(daysToScan: Long, durationMinutes: Long, minLocations: Int, intervalMinutes: Long): Boolean {
        Timber.d("Checking for network-switching trackers over the last $daysToScan days, with a minimum duration of $durationMinutes minutes and at least $minLocations locations.")

        val scanStartTime = LocalDateTime.now().minusDays(daysToScan)
        val sortedBeacons = beaconRepository.getBeaconsSince(scanStartTime).first().sortedBy { it.receivedAt }

        if (sortedBeacons.size < 2) {
            Timber.d("Not enough beacons found to determine network-switching trackers.")
            return false
        }

        var sessionStartTime = sortedBeacons.first().receivedAt
        var currentSessionLocations = mutableSetOf<Int>()
        sortedBeacons.first().locationId?.let { currentSessionLocations.add(it) }

        // Iterate through all beacons to find a continuous tracking session from any device
        for (i in 1 until sortedBeacons.size) {
            val prevBeacon = sortedBeacons[i - 1]
            val currentBeacon = sortedBeacons[i]
            val timeDiffMinutes = Duration.between(prevBeacon.receivedAt, currentBeacon.receivedAt).toMinutes()

            if (timeDiffMinutes <= intervalMinutes) {
                // The session is continuous
                currentBeacon.locationId?.let { currentSessionLocations.add(it) }
            } else {
                // The session is broken, check if the previous session meets the criteria
                val sessionDuration = Duration.between(sessionStartTime, prevBeacon.receivedAt)
                if (sessionDuration.toMinutes() >= durationMinutes && currentSessionLocations.size >= minLocations) {
                    Timber.d("Network-switching tracker detected. (Variant 1)")
                    return true // A suspicious tracking session was found
                }
                // Start a new session
                sessionStartTime = currentBeacon.receivedAt
                currentSessionLocations = mutableSetOf()
                currentBeacon.locationId?.let { currentSessionLocations.add(it) }
            }
        }

        // Check the last session after the loop finishes
        val lastBeacon = sortedBeacons.last()
        val lastSessionDuration = Duration.between(sessionStartTime, lastBeacon.receivedAt)
        if (lastSessionDuration.toMinutes() >= durationMinutes && currentSessionLocations.size >= minLocations) {
            Timber.d("Network-switching tracker detected. (Variant 2)")
            return true
        }

        Timber.d("No network-switching trackers detected.")
        return false
    }
}