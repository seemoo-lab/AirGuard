package de.seemoo.at_tracking_detection.ui.devices.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.tables.Device
import de.seemoo.at_tracking_detection.databinding.IncludeDeviceItemBinding
import de.seemoo.at_tracking_detection.ui.devices.DevicesViewModel

class DeviceAdapter constructor(
    private val devicesViewModel: DevicesViewModel,
    private val onClickListener: OnClickListener
) :
    ListAdapter<Device, DeviceAdapter.DeviceViewHolder>(Companion) {

    class DeviceViewHolder(private val binding: IncludeDeviceItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device, devicesViewModel: DevicesViewModel) {
            binding.deviceBeaconCount = devicesViewModel.getDeviceBeaconsCount(device.address)
            binding.device = device
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding: IncludeDeviceItemBinding =
            IncludeDeviceItemBinding.inflate(layoutInflater, parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device: Device = getItem(position)
        holder.bind(device, devicesViewModel)
        holder.itemView.transitionName = device.address
        val cardView = holder.itemView.findViewById<MaterialCardView>(R.id.device_item_card)
        holder.itemView.setOnClickListener { onClickListener.onClick(device, cardView) }
    }

    class OnClickListener(val clickListener: (Device, MaterialCardView) -> Unit) {
        fun onClick(device: Device, cardView: MaterialCardView) = clickListener(device, cardView)
    }

    companion object : DiffUtil.ItemCallback<Device>() {
        override fun areContentsTheSame(oldItem: Device, newItem: Device): Boolean =
            oldItem == newItem

        override fun areItemsTheSame(oldItem: Device, newItem: Device): Boolean =
            oldItem.deviceId == newItem.deviceId
    }

}