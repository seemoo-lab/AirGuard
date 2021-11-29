package de.seemoo.at_tracking_detection.database.repository

import androidx.annotation.WorkerThread
import de.seemoo.at_tracking_detection.database.daos.DeviceDao
import de.seemoo.at_tracking_detection.database.relations.DeviceBeaconNotification
import de.seemoo.at_tracking_detection.database.tables.Device
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject

@WorkerThread
class DeviceRepository @Inject constructor(private val deviceDao: DeviceDao) {
    val devices: Flow<List<Device>> = deviceDao.getAll()

    val totalCount: Flow<Int> = deviceDao.getTotalCount()

    fun totalDeviceCountChange(since: LocalDateTime): Flow<Int> =
        deviceDao.getTotalCountChange(since)

    fun devicesCurrentlyMonitored(since: LocalDateTime): Flow<Int> =
        deviceDao.getCurrentlyMonitored(since)

    val ignoredDevices: Flow<List<Device>> = deviceDao.getIgnored()

    val ignoredDevicesSync: List<Device> = deviceDao.getIgnoredSync()

    fun getDevice(deviceAddress: String): Device? = deviceDao.getByAddress(deviceAddress)

    @WorkerThread
    suspend fun getDeviceBeaconsSince(dateTime: String?): List<DeviceBeaconNotification> {
        return if (dateTime != null) {
            deviceDao.getDeviceBeaconsSince(LocalDateTime.parse(dateTime))
        } else {
            deviceDao.getDeviceBeacons()
        }
    }

    @WorkerThread
    suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    @WorkerThread
    suspend fun update(device: Device) {
        deviceDao.update(device)
    }

    @WorkerThread
    suspend fun setIgnoreFlag(deviceAddress: String, state: Boolean) {
        deviceDao.setIgnoreFlag(deviceAddress, state)
    }
}