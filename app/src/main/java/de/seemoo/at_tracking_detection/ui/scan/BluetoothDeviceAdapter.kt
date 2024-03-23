package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getPublicKey
import de.seemoo.at_tracking_detection.databinding.ItemScanResultBinding
import timber.log.Timber

class BluetoothDeviceAdapter:
    ListAdapter<ScanResult, BluetoothDeviceAdapter.ScanResultViewHolder>(BluetoothDeviceDiffCallback()) {

    class ScanResultViewHolder(private val binding: ItemScanResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(scanResult: ScanResult) {
            binding.scanResult = scanResult
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
        val scanResult: ScanResult = getItem(position)
        holder.bind(scanResult)

        holder.itemView.findViewById<MaterialCardView>(R.id.scan_result_item_card)
            .setOnClickListener {
                try {
                    val deviceAddress: String = getPublicKey(scanResult)
                    val directions = ScanFragmentDirections.actionScanToTrackingFragment(deviceAddress)
                    holder.itemView.findNavController()
                        .navigate(directions)
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
    }
}

class BluetoothDeviceDiffCallback: DiffUtil.ItemCallback<ScanResult>() {
    override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
        return oldItem.device.address == newItem.device.address
    }

    override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
        return (oldItem.device.address == newItem.device.address) && (oldItem.rssi == newItem.rssi)
    }
}