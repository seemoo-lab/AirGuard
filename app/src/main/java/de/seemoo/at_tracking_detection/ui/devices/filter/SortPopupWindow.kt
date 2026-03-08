package de.seemoo.at_tracking_detection.ui.devices.filter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.ui.devices.DevicesViewModel

class SortPopupWindow(
    private val context: Context,
    private val viewModel: DevicesViewModel
) {

    fun show(anchor: View) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.popup_window_sort, null)

        // Configure PopupWindow
        val popupWindow = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // Focusable
        )
        // Transparent background required for card elevation/shadow to show correctly
        popupWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        popupWindow.elevation = 10f

        // Bind Views
        val optionName = view.findViewById<View>(R.id.sort_option_name)
        val checkName = view.findViewById<ImageView>(R.id.check_name)

        val optionLastSeen = view.findViewById<View>(R.id.sort_option_last_seen)
        val checkLastSeen = view.findViewById<ImageView>(R.id.check_last_seen)

        val optionFirstDiscovered = view.findViewById<View>(R.id.sort_option_first_discovered)
        val checkFirstDiscovered = view.findViewById<ImageView>(R.id.check_first_discovered)

        val optionTimesSeen = view.findViewById<View>(R.id.sort_option_times_seen)
        val checkTimesSeen = view.findViewById<ImageView>(R.id.check_times_seen)

        // Set Initial State
        val currentSort = viewModel.getCurrentSort()
        checkName.visibility = if (currentSort == DevicesViewModel.SortOption.NAME) View.VISIBLE else View.INVISIBLE
        checkLastSeen.visibility = if (currentSort == DevicesViewModel.SortOption.LAST_SEEN) View.VISIBLE else View.INVISIBLE
        checkFirstDiscovered.visibility = if (currentSort == DevicesViewModel.SortOption.FIRST_DISCOVERED) View.VISIBLE else View.INVISIBLE
        checkTimesSeen.visibility = if (currentSort == DevicesViewModel.SortOption.TIMES_SEEN) View.VISIBLE else View.INVISIBLE

        // Set Listeners
        optionName.setOnClickListener {
            viewModel.setSortOption(DevicesViewModel.SortOption.NAME)
            popupWindow.dismiss()
        }

        optionLastSeen.setOnClickListener {
            viewModel.setSortOption(DevicesViewModel.SortOption.LAST_SEEN)
            popupWindow.dismiss()
        }

        optionFirstDiscovered.setOnClickListener {
            viewModel.setSortOption(DevicesViewModel.SortOption.FIRST_DISCOVERED)
            popupWindow.dismiss()
        }

        optionTimesSeen.setOnClickListener {
            viewModel.setSortOption(DevicesViewModel.SortOption.TIMES_SEEN)
            popupWindow.dismiss()
        }

        // Aligns the Pop Up to the right side of the dialog filer
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = view.measuredWidth
        val offsetX = anchor.width - popupWidth

        popupWindow.showAsDropDown(anchor, offsetX, 0)
    }
}