package de.seemoo.at_tracking_detection.ui.devices

import android.Manifest
import android.graphics.Canvas
import android.os.Bundle
import android.text.InputFilter
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.marginBottom
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.navigation.NavDirections
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.databinding.FragmentDevicesBinding
import de.seemoo.at_tracking_detection.ui.devices.filter.FilterDialogFragment
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DateRangeFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DeviceTypeFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.IgnoredFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.LocationFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.NotifiedFilter
import de.seemoo.at_tracking_detection.ui.tracking.TrackingFragment
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import timber.log.Timber
import java.time.LocalDate

@AndroidEntryPoint
class DevicesFragment : Fragment() {

    private val safeArgs: DevicesFragmentArgs by navArgs()

    private var showDevicesFound: Boolean = true
    private var showAllDevices: Boolean = false
    private var deviceType: DeviceType? = null
    private var deviceType2: DeviceType? = null
    private var preselectNotifiedFilter: String = "UNSELECTED"
    private var preselectIgnoredFilter: String = "UNSELECTED"
    private var preselectRemoveDateRange: Boolean = false

    val devicesViewModel: DevicesViewModel by viewModels()

    private val dialogFragment = FilterDialogFragment()

    private lateinit var deviceAdapter: DeviceAdapter

    private var swipeDirs: Int = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize properties from SafeArgs
        this.showDevicesFound = safeArgs.showDevicesFound
        this.showAllDevices = safeArgs.showAllDevices
        this.deviceType = safeArgs.deviceType
        this.deviceType2 = safeArgs.deviceType2
        this.preselectNotifiedFilter = safeArgs.preselectNotifiedFilter
        this.preselectIgnoredFilter = safeArgs.preselectIgnoredFilter
        this.preselectRemoveDateRange = safeArgs.preselectRemoveDateRange

        super.onCreate(savedInstanceState)

        val emptyListText: Int
        var deviceInfoText = R.string.info_text_all_devices

        if (!showDevicesFound) {
            activity?.setTitle(R.string.title_ignored_devices)
            emptyListText = R.string.ignored_device_list_empty
            devicesViewModel.addOrRemoveFilter(IgnoredFilter())
            devicesViewModel.addOrRemoveFilter(
                DeviceTypeFilter(
                    DeviceManager.devices.map { it.deviceType }.toSet()
                )
            )
        } else {
            val relevantTrackingStartDate = RiskLevelEvaluator.relevantTrackingDateForRiskCalculation.toLocalDate()
            devicesViewModel.addOrRemoveFilter(
                DateRangeFilter(
                    relevantTrackingStartDate,
                    LocalDate.now()
                )
            )
            // If we show all devices immediately, we set the correct strings here
            if (showAllDevices) {
                deviceInfoText = R.string.info_text_all_devices
                emptyListText = R.string.empty_list_devices
            } else {
                // Only show tracker devices, for which a notification has been received
                devicesViewModel.addOrRemoveFilter(NotifiedFilter())
                deviceInfoText = R.string.info_text_only_trackers
                emptyListText = R.string.empty_list_trackers
            }
        }

        if (deviceType != null && deviceType != DeviceType.UNKNOWN) {
            if (deviceType2 != null && deviceType2 != DeviceType.UNKNOWN) {
                devicesViewModel.addOrRemoveFilter(DeviceTypeFilter(setOf(deviceType!!, deviceType2!!)))
            } else {
                devicesViewModel.addOrRemoveFilter(DeviceTypeFilter(setOf(deviceType!!)))
            }
        }

        // Apply location filter if locationId is provided
        val locationId = safeArgs.locationId
        if (locationId > 0) {
            devicesViewModel.addOrRemoveFilter(LocationFilter(locationId))
        }

        // Apply preselected filter states if provided
        applyPreselectFilters()

        devicesViewModel.emptyListText.value = getString(emptyListText)
        devicesViewModel.infoText.value = getString(deviceInfoText)

        sharedElementReturnTransition =
            TransitionInflater.from(context).inflateTransition(android.R.transition.move)
                .setInterpolator(LinearOutSlowInInterpolator()).setDuration(500)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentDevicesBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_devices, container, false)

        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback(swipeDirs))
        itemTouchHelper.attachToRecyclerView(binding.root.findViewById(R.id.devices_recycler_view))

        deviceAdapter = DeviceAdapter(devicesViewModel, deviceItemListener)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.adapter = deviceAdapter
        binding.vm = devicesViewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        val recyclerView = view.findViewById<RecyclerView>(R.id.devices_recycler_view)
        val filterContainer = view.findViewById<View>(R.id.filter_fragment)

        recyclerView.doOnPreDraw { startPostponedEnterTransition() }

        ViewCompat.setOnApplyWindowInsetsListener(filterContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply Top Padding ONLY to the container (Fixes overlap)
            v.updatePadding(top = bars.top)

            // Handle Sibling (RecyclerView) padding logic
            val navView = requireActivity().findViewById<BottomNavigationView>(R.id.main_nav_view)
            val navHeight = if (navView != null && navView.height > 0)
                navView.height + navView.marginBottom
            else
                bars.bottom + (88 * resources.displayMetrics.density).toInt()

            recyclerView.updatePadding(bottom = navHeight)

            v.post {
                recyclerView.updatePadding(top = v.height + v.marginBottom)
            }

            // Stop system from adding unnecessary padding to te dialog filter
            WindowInsetsCompat.CONSUMED
        }

        // Update Recycler Padding when Filter Expands/Collapses
        filterContainer.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            recyclerView.updatePadding(top = v.height + v.marginBottom)
        }

        devicesViewModel.devices.observe(viewLifecycleOwner) {
            deviceAdapter.submitList(it)
            updateTexts()
        }

        // Update empty list text based on whether filters are active
        devicesViewModel.showAllDevicesButton.observe(viewLifecycleOwner) { showButton ->
            if (!showButton) {
                devicesViewModel.emptyListText.value = getString(R.string.empty_list_no_devices)
            }
        }

        val showAllButton = view.findViewById<Button>(R.id.show_all_devices_button)
        showAllButton.setOnClickListener {
            // Reset filter states to UNSELECTED
            devicesViewModel.ignoredFilterState.value = DevicesViewModel.FilterState.UNSELECTED
            devicesViewModel.notifiedFilterState.value = DevicesViewModel.FilterState.UNSELECTED

            // Remove filters
            devicesViewModel.addOrRemoveFilter(NotifiedFilter(), true)
            devicesViewModel.addOrRemoveFilter(IgnoredFilter(), true)
            devicesViewModel.addOrRemoveFilter(DateRangeFilter(LocalDate.now(), LocalDate.now()), true)

            // Add device type filter to show all devices
            devicesViewModel.addOrRemoveFilter(
                DeviceTypeFilter(
                    DeviceManager.devices.map { it.deviceType }.toSet()
                )
            )
        }

        //Adding the Filter fragment to the view
        val transaction = childFragmentManager.beginTransaction().replace(R.id.filter_fragment, dialogFragment)
        transaction.commit()

    }

    private val deviceItemListener: DeviceAdapter.OnClickListener
        get() = DeviceAdapter.OnClickListener { baseDevice: BaseDevice, materialCardView: MaterialCardView ->
            if (!Utility.checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                return@OnClickListener
            }
            val directions: NavDirections =
                DevicesFragmentDirections
                    .actionNavigationDevicesToTrackingFragment(
                        baseDevice.address
                    )
            val extras = FragmentNavigatorExtras(materialCardView to baseDevice.address)
            findNavController().navigate(directions, extras)
        }


    private fun updateTexts() {
        if (safeArgs.locationId > 0) {
            (requireActivity() as AppCompatActivity).supportActionBar?.title =
                getString(R.string.found_at_location)
        } else if (devicesViewModel.activeFilter.containsKey(NotifiedFilter::class.toString())) {
            // Only shows trackers
            (requireActivity() as AppCompatActivity).supportActionBar?.title =
                getString(R.string.tracker_detected)
            devicesViewModel.infoText.value = getString(R.string.info_text_only_trackers)
            devicesViewModel.emptyListText.value = getString(R.string.empty_list_trackers)
        } else {
            (requireActivity() as AppCompatActivity).supportActionBar?.title =
                getString(R.string.title_devices)
            devicesViewModel.infoText.value = getString(R.string.info_text_all_devices)
            devicesViewModel.emptyListText.value = getString(R.string.empty_list_devices)
        }
    }

    private fun swipeToDeleteCallback(swipeDirs: Int) = object : ItemTouchHelper.SimpleCallback(0, swipeDirs) {
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            val itemView = viewHolder.itemView
            val context = itemView.context
            val editColor = ContextCompat.getColor(context, R.color.md_theme_secondaryContainer)
            val heartColor = ContextCompat.getColor(context, R.color.md_theme_tertiaryContainer)
            val editIconTint = ContextCompat.getColor(context, R.color.md_theme_onSecondaryContainer)
            val heartIconTint = ContextCompat.getColor(context, R.color.md_theme_onTertiaryContainer)
            val editBackground = editColor.toDrawable()
            val heartBackground = heartColor.toDrawable()
            val editIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_edit_24)?.mutate()
            editIcon?.let { DrawableCompat.setTint(it, editIconTint) }
            val heartIcon = ContextCompat.getDrawable(context, R.drawable.ic_heart_filled)?.mutate()
            heartIcon?.let { DrawableCompat.setTint(it, heartIconTint) }
            var transitionX = dX
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                when {
                    dX > 0 -> {
                        val limitedDx = dX / 4f
                        if (heartIcon != null) {
                            val iconMargin = (itemView.height - heartIcon.intrinsicHeight) / 2
                            val iconTop = itemView.top + (itemView.height - heartIcon.intrinsicHeight) / 2
                            val iconBottom = iconTop + heartIcon.intrinsicHeight
                            val iconLeft = itemView.left + iconMargin
                            val iconRight = itemView.left + iconMargin + heartIcon.intrinsicWidth
                            heartIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            heartBackground.setBounds(itemView.left, itemView.top, (itemView.left + limitedDx).toInt(), itemView.bottom)
                            heartBackground.draw(c)
                            heartIcon.draw(c)
                        }
                        transitionX = limitedDx
                    }
                    dX < 0 -> {
                        val limitedDx = dX / 4f
                        if (editIcon != null) {
                            val iconMargin = (itemView.height - editIcon.intrinsicHeight) / 2
                            val iconTop = itemView.top + (itemView.height - editIcon.intrinsicHeight) / 2
                            val iconBottom = iconTop + editIcon.intrinsicHeight
                            val iconLeft = itemView.right - iconMargin - editIcon.intrinsicWidth
                            val iconRight = itemView.right - iconMargin
                            editIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            editBackground.setBounds((itemView.right + limitedDx).toInt(), itemView.top, itemView.right, itemView.bottom)
                            editBackground.draw(c)
                            editIcon.draw(c)
                        }
                        transitionX = limitedDx
                    }
                }
            }
            super.onChildDraw(c, recyclerView, viewHolder, transitionX, dY, actionState, isCurrentlyActive)
        }
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val device = deviceAdapter.currentList[viewHolder.bindingAdapterPosition]
            if (direction == ItemTouchHelper.LEFT) {
                val editName = EditText(context).apply {
                    maxLines = 1
                    filters = arrayOf(InputFilter.LengthFilter(TrackingFragment.MAX_CHARACTER_LIMIT))
                    setText(device.getDeviceNameWithID())
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setIcon(R.drawable.ic_baseline_edit_24)
                    .setTitle(getString(R.string.devices_edit_title)).setView(editName)
                    .setNegativeButton(getString(R.string.cancel_button), null)
                    .setPositiveButton(R.string.ok_button) { _, _ ->
                        val newName = editName.text.toString()
                        if (newName.isNotEmpty()) {
                            device.name = newName
                            devicesViewModel.update(device)
                            Timber.d("Renamed device to ${device.name}")
                        } else {
                            Toast.makeText(context, R.string.device_name_cannot_be_empty, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setOnDismissListener { deviceAdapter.notifyItemChanged(viewHolder.bindingAdapterPosition) }
                    .show()
            } else if (direction == ItemTouchHelper.RIGHT) {
                devicesViewModel.setHeartState(device.address, !device.hearted)
                deviceAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun applyPreselectFilters() {
        // remove the date range filter
        if (preselectRemoveDateRange) {
            val keysToRemove = devicesViewModel.activeFilter.filter { (_, f) -> f is DateRangeFilter }.keys
            keysToRemove.forEach { devicesViewModel.activeFilter.remove(it) }
            devicesViewModel.updateFilterSummaryText()
            devicesViewModel.updateVisibleList()
        }

        // Apply preselected notified filter state
        when (preselectNotifiedFilter) {
            "INCLUDING" -> {
                devicesViewModel.notifiedFilterState.value = DevicesViewModel.FilterState.INCLUDING
                devicesViewModel.activeFilter[NotifiedFilter::class.toString()] = NotifiedFilter(filterFor = true)
            }
            "EXCLUDING" -> {
                devicesViewModel.notifiedFilterState.value = DevicesViewModel.FilterState.EXCLUDING
                devicesViewModel.activeFilter[NotifiedFilter::class.toString()] = NotifiedFilter(filterFor = false)
            }
            else -> {
                // default, do nothing
            }
        }

        // Apply preselected ignored filter state
        when (preselectIgnoredFilter) {
            "INCLUDING" -> {
                devicesViewModel.ignoredFilterState.value = DevicesViewModel.FilterState.INCLUDING
                devicesViewModel.activeFilter[IgnoredFilter::class.toString()] = IgnoredFilter(filterFor = true)
            }
            "EXCLUDING" -> {
                devicesViewModel.ignoredFilterState.value = DevicesViewModel.FilterState.EXCLUDING
                devicesViewModel.activeFilter[IgnoredFilter::class.toString()] = IgnoredFilter(filterFor = false)
            }
            else -> {
                // default, do nothing
            }
        }

        // Update the filter summary text and visible list if any preselections were made
        if (preselectNotifiedFilter != "UNSELECTED" || preselectIgnoredFilter != "UNSELECTED") {
            devicesViewModel.updateFilterSummaryText()
            devicesViewModel.updateVisibleList()
        }
    }

    companion object {
        private const val DIALOG_TAG = "de.seemoo.at_tracking_detection.filter_dialog"
    }
}