package de.seemoo.at_tracking_detection.ui.tracking // Or your adapters package

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.R

// Data class to hold the simplified info for one beacon row
data class BeaconPreviewItem(
    val id: Int, // Unique ID for DiffUtil (can use beacon ID or hash)
    val timeText: String,
    val locationText: String
)

class BeaconPreviewAdapter : ListAdapter<BeaconPreviewItem, BeaconPreviewAdapter.BeaconViewHolder>(BeaconDiffCallback()) {

    class BeaconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeTextView: TextView = itemView.findViewById(R.id.beacon_time_textview)
        private val locationTextView: TextView = itemView.findViewById(R.id.beacon_location_textview)

        fun bind(item: BeaconPreviewItem) {
            timeTextView.text = item.timeText
            locationTextView.text = item.locationText
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BeaconViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_beacon_preview, parent, false)
        return BeaconViewHolder(view)
    }

    override fun onBindViewHolder(holder: BeaconViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }
}

class BeaconDiffCallback : DiffUtil.ItemCallback<BeaconPreviewItem>() {
    override fun areItemsTheSame(oldItem: BeaconPreviewItem, newItem: BeaconPreviewItem): Boolean {
        return oldItem.id == newItem.id // Use a unique ID per beacon
    }

    override fun areContentsTheSame(oldItem: BeaconPreviewItem, newItem: BeaconPreviewItem): Boolean {
        return oldItem == newItem // Rely on data class equals()
    }
}