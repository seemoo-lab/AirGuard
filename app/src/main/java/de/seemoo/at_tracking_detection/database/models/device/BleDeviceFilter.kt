package de.seemoo.at_tracking_detection.database.models.device

import java.util.*

class BleDeviceFilter private constructor(
    val services: ArrayList<UUID>,
    val characteristics: ArrayList<ServiceCharacteristicFilter>
) {

    data class ServiceCharacteristicFilter(
        val service: UUID,
        val characteristic: UUID,
        val value: String
    ) {}


    data class Builder(
        private val services: ArrayList<UUID> = arrayListOf(),
        private val characteristics: ArrayList<ServiceCharacteristicFilter> = arrayListOf()
    ) {

        fun setCharacteristic(serviceUUID: UUID, characteristic: UUID, value: String) = apply {
            this.characteristics.add(
                ServiceCharacteristicFilter(
                    serviceUUID,
                    characteristic,
                    value
                )
            )
        }

        fun setAccessCharacteristic(uuid: UUID, value: String) = apply {
            this.characteristics.add(
                ServiceCharacteristicFilter(
                    GENERIC_ACCESS_SERVICE,
                    uuid,
                    value
                )
            )
        }

        fun setAppearance(appearance: String) = apply {
            this.characteristics.add(
                ServiceCharacteristicFilter(
                    GENERIC_ACCESS_SERVICE,
                    APPEARANCE_CHARACTERISTIC,
                    appearance
                )
            )
        }

        fun setDeviceName(name: String) = apply {
            this.characteristics.add(
                ServiceCharacteristicFilter(
                    GENERIC_ACCESS_SERVICE,
                    DEVICE_NAME_CHARACTERISTIC,
                    name
                )
            )
        }

        fun setService(uuid: UUID) = apply { this.services.add(uuid) }

        fun build(): BleDeviceFilter {
            return BleDeviceFilter(services, characteristics)
        }
    }

    companion object {
        private val GENERIC_ACCESS_SERVICE = UUID.fromString("1800")
        private val DEVICE_NAME_CHARACTERISTIC = UUID.fromString("00002A00-0000-8000-00805F9B34FB")
        private val APPEARANCE_CHARACTERISTIC = UUID.fromString("00002A01-0000-8000-00805F9B34FB")
    }
}