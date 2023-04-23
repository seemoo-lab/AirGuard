package de.seemoo.at_tracking_detection.ui.scan

import android.Manifest
import android.bluetooth.le.ScanResult
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.FragmentManager
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.ItemScanResultBinding
import de.seemoo.at_tracking_detection.ui.scan.dialog.PlaySoundDialogFragment
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungDevice.Companion.getPublicKey

class BluetoothDeviceAdapter constructor(private val fragmentManager: FragmentManager) :
    ListAdapter<ScanResult, BluetoothDeviceAdapter.ScanResultViewHolder>(Companion) {

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

        holder.itemView.findViewById<ImageView>(R.id.scan_result_play_sound).setOnClickListener {
        // TODO: both of these are not called, checked with Debugger

        holder.itemView.findViewById<ImageView>(R.id.scan_signal_strength).setOnClickListener() {
            val directions = ScanFragmentDirections.actionScanToScanDistance() // TODO: add argument here for public key
            holder.itemView.findNavController().navigate(directions) // TODO: This does not work for some reason
        }

        holder.itemView.findViewById<ImageView>(R.id.scan_result_play_sound).setOnClickListener() {
            val hasAllPermissions =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S || Utility.checkAndRequestPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            if (hasAllPermissions) {
                PlaySoundDialogFragment(scanResult).show(fragmentManager, null)
            }
        }
    }

    companion object : DiffUtil.ItemCallback<ScanResult>() {
        override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean =
            oldItem == newItem

        override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean =
            getPublicKey(oldItem) == getPublicKey(newItem)
    }

}