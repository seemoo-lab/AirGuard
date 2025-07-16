package de.seemoo.at_tracking_detection.ui.scan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice

class SuperScanAdapter(
    private var devices: List<BaseDevice>,
    private val onItemClicked: (BaseDevice) -> Unit
) : RecyclerView.Adapter<SuperScanAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suspected_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
        holder.itemView.setOnClickListener { onItemClicked(device) }
    }

    override fun getItemCount() = devices.size

    fun updateData(newDevices: List<BaseDevice>) {
        this.devices = newDevices
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceLogo: ImageView = itemView.findViewById(R.id.device_logo)
        private val deviceName: TextView = itemView.findViewById(R.id.device_name)
        private val deviceAddress: TextView = itemView.findViewById(R.id.device_address)

        fun bind(device: BaseDevice) {
            deviceName.text = device.getDeviceNameWithID()
            deviceAddress.text = device.address
            deviceLogo.setImageDrawable(device.getDrawable())
        }
    }
}
