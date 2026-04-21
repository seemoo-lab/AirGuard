package de.seemoo.at_tracking_detection.ui.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val devicesViewModel: DevicesViewModel,
    private val onClickListener: OnClickListener,
    private val onLongClickListener: OnLongClickListener? = null
) :
    ListAdapter<BaseDevice, DeviceAdapter.DeviceViewHolder>(Companion) {

    class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(baseDevice: BaseDevice, devicesViewModel: DevicesViewModel, isSelected: Boolean) {
            binding.deviceBeaconCount = devicesViewModel.getDeviceBeaconsCount(baseDevice.address)
            binding.baseDevice = baseDevice
            binding.isClickable = true
            binding.executePendingBindings()

            val card = binding.root as? MaterialCardView
            val beaconBadgeCard = binding.tvBeaconCount.parent as? MaterialCardView
            if (card != null) {
                if (isSelected) {
                    val selectionColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSecondaryContainer)
                    card.setCardBackgroundColor(selectionColor)
                    val strokePx = (2 * card.context.resources.displayMetrics.density).toInt()
                    card.strokeWidth = strokePx
                    card.strokeColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSecondaryContainer)
                    beaconBadgeCard?.setCardBackgroundColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimaryContainer))
                    binding.tvBeaconCount.setTextColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnPrimaryContainer))
                } else {
                    val surfaceColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceContainerHigh)
                    card.setCardBackgroundColor(surfaceColor)
                    card.strokeWidth = 0
                    beaconBadgeCard?.setCardBackgroundColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorSecondaryContainer))
                    binding.tvBeaconCount.setTextColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSecondaryContainer))
                }
            }
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
        val isSelected = devicesViewModel.selectedDevices.value?.contains(baseDevice.address) == true
        holder.bind(baseDevice, devicesViewModel, isSelected)
        holder.itemView.transitionName = baseDevice.address
        val cardView = holder.itemView.findViewById<MaterialCardView>(R.id.device_item_card)
        holder.itemView.setOnClickListener {
            if (devicesViewModel.isSelectionMode.value == true) {
                devicesViewModel.toggleDeviceSelection(baseDevice.address)
                notifyItemChanged(position)
            } else {
                onClickListener.onClick(baseDevice, cardView)
            }
        }
        holder.itemView.setOnLongClickListener {
            devicesViewModel.toggleDeviceSelection(baseDevice.address)
            notifyItemChanged(position)
            onLongClickListener?.onLongClick(baseDevice)
            true
        }
    }

    class OnClickListener(val clickListener: (BaseDevice, MaterialCardView) -> Unit) {
        fun onClick(baseDevice: BaseDevice, cardView: MaterialCardView) =
            clickListener(baseDevice, cardView)
    }

    class OnLongClickListener(val longClickListener: (BaseDevice) -> Unit) {
        fun onLongClick(baseDevice: BaseDevice) = longClickListener(baseDevice)
    }

    companion object : DiffUtil.ItemCallback<BaseDevice>() {
        override fun areContentsTheSame(oldItem: BaseDevice, newItem: BaseDevice): Boolean =
            oldItem == newItem

        override fun areItemsTheSame(oldItem: BaseDevice, newItem: BaseDevice): Boolean =
            oldItem.deviceId == newItem.deviceId
    }
}