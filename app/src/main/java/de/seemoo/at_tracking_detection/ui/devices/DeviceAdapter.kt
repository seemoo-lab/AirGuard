package de.seemoo.at_tracking_detection.ui.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.databinding.ItemDeviceBinding

class DeviceAdapter constructor(
    private val devicesViewModel: DevicesViewModel,
    private val onClickListener: OnClickListener
) :
    ListAdapter<BaseDevice, DeviceAdapter.DeviceViewHolder>(Companion) {

    class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(baseDevice: BaseDevice, devicesViewModel: DevicesViewModel) {
            binding.deviceBeaconCount = devicesViewModel.getDeviceBeaconsCount(baseDevice.address)
            binding.baseDevice = baseDevice
            binding.isClickable = true
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding: ItemDeviceBinding =
            ItemDeviceBinding.inflate(layoutInflater, parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val baseDevice: BaseDevice = getItem(position)
        holder.bind(baseDevice, devicesViewModel)
        holder.itemView.transitionName = baseDevice.address
        val cardView = holder.itemView.findViewById<MaterialCardView>(R.id.device_item_card)
        holder.itemView.setOnClickListener { onClickListener.onClick(baseDevice, cardView) }
    }

    class OnClickListener(val clickListener: (BaseDevice, MaterialCardView) -> Unit) {
        fun onClick(baseDevice: BaseDevice, cardView: MaterialCardView) =
            clickListener(baseDevice, cardView)
    }

    companion object : DiffUtil.ItemCallback<BaseDevice>() {
        override fun areContentsTheSame(oldItem: BaseDevice, newItem: BaseDevice): Boolean =
            oldItem == newItem

        override fun areItemsTheSame(oldItem: BaseDevice, newItem: BaseDevice): Boolean =
            oldItem.deviceId == newItem.deviceId
    }
}