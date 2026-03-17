package de.seemoo.at_tracking_detection.ui.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.ItemScanSeparatorBinding

class ScanSeparatorAdapter : RecyclerView.Adapter<ScanSeparatorAdapter.SeparatorViewHolder>() {

    private var isVisible = false

    /** Show or hide the separator row with an insert/remove animation. */
    fun setVisible(visible: Boolean) {
        if (visible == isVisible) return
        isVisible = visible
        if (visible) notifyItemInserted(0) else notifyItemRemoved(0)
    }

    /**
     * Returns 0 or 1.
     * Its purpose is to indicate whether the Separator should be shown or not
     */
    override fun getItemCount(): Int = if (isVisible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeparatorViewHolder {
        val binding = ItemScanSeparatorBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SeparatorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SeparatorViewHolder, position: Int) = Unit

    class SeparatorViewHolder(
        binding: ItemScanSeparatorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.infoButton.setOnClickListener {
                MaterialAlertDialogBuilder(itemView.context)
                    .setTitle(R.string.low_risk_trackers)
                    .setMessage(R.string.explanation_safe_trackers)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }
}
