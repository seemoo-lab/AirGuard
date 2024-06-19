package de.seemoo.at_tracking_detection.ui.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.databinding.ItemScanResultBinding
import timber.log.Timber

class BluetoothDeviceAdapter:
    ListAdapter<ScanResultWrapper, BluetoothDeviceAdapter.ScanResultViewHolder>(BluetoothDeviceDiffCallback()) {

    class ScanResultViewHolder(private val binding: ItemScanResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(wrappedScanResult: ScanResultWrapper) {
            binding.wrappedScanResult = wrappedScanResult
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanResultViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding: ItemScanResultBinding =
            ItemScanResultBinding.inflate(layoutInflater, parent, false)
        return ScanResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
        val wrappedScanResult: ScanResultWrapper = getItem(position)
        holder.bind(wrappedScanResult)

        holder.itemView.findViewById<MaterialCardView>(R.id.scan_result_item_card)
            .setOnClickListener {
                try {
                    val deviceAddress: String = wrappedScanResult.uniqueIdentifier
                    val deviceType: DeviceType = wrappedScanResult.deviceType
                    val deviceTypeString = DeviceManager.deviceTypeToString(deviceType)

                    val directions = ScanFragmentDirections.actionScanToTrackingFragment(
                        deviceAddress = deviceAddress,
                        deviceTypeAsString = deviceTypeString
                    )
                    holder.itemView.findNavController()
                        .navigate(directions)
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
    }
}

class BluetoothDeviceDiffCallback: DiffUtil.ItemCallback<ScanResultWrapper>() {
    override fun areItemsTheSame(oldItem: ScanResultWrapper, newItem: ScanResultWrapper): Boolean {
        return oldItem.uniqueIdentifier == newItem.uniqueIdentifier
    }

    override fun areContentsTheSame(oldItem: ScanResultWrapper, newItem: ScanResultWrapper): Boolean {
        return (oldItem.uniqueIdentifier == newItem.uniqueIdentifier) &&
                (oldItem.rssiValue == newItem.rssiValue) &&
                (oldItem.deviceName.get() == newItem.deviceName.get()) &&
                (oldItem.advertisedName.get() == newItem.advertisedName.get()) &&
                (oldItem.appearance.get() == newItem.appearance.get()) &&
                (oldItem.manufacturer.get() == newItem.manufacturer.get())
        //return return (oldItem.uniqueIdentifier == newItem.uniqueIdentifier) && (oldItem.rssiValue == newItem.rssiValue)
        // return (oldItem.uniqueIdentifier == newItem.uniqueIdentifier) && (oldItem.rssiValue == newItem.rssiValue)  && (oldItem.deviceName.get() == newItem.deviceName.get()) && (oldItem.advertisedName.get() == newItem.advertisedName.get()) && (oldItem.appearance.get() == newItem.appearance.get()) && (oldItem.manufacturer.get() == newItem.manufacturer.get())
    }
}