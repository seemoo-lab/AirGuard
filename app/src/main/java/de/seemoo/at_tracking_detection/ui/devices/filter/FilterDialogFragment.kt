package de.seemoo.at_tracking_detection.ui.devices.filter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.databinding.DialogFilterBinding
import de.seemoo.at_tracking_detection.databinding.IncludeFilterChipBinding
import de.seemoo.at_tracking_detection.ui.devices.DevicesViewModel
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DateRangeFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DeviceTypeFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.IgnoredFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.NotifiedFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@AndroidEntryPoint
class FilterDialogFragment : Fragment() {

    private val devicesViewModel: DevicesViewModel by viewModels({ requireParentFragment() })

    private var _binding: DialogFilterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFilterBinding.inflate(inflater, container, false)
        _binding?.viewModel = devicesViewModel
        _binding?.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        filterAdaptions()

        val expandButton = view.findViewById<MaterialButton>(R.id.filter_expand_button)
        val headerSummary = view.findViewById<View>(R.id.filter_summary_text)

        val toggleListener = View.OnClickListener {
            val value = devicesViewModel.filterIsExpanded.value ?: false
            devicesViewModel.filterIsExpanded.postValue(!value)

            val rotation = if (!value) 180f else 0f
            expandButton.animate().rotation(rotation).setDuration(200).start()
        }

        expandButton.setOnClickListener(toggleListener)
        headerSummary.setOnClickListener(toggleListener)

        val sortButton = view.findViewById<MaterialButton>(R.id.filter_sort_button)
        sortButton.setOnClickListener {
            SortPopupWindow(requireContext(), devicesViewModel).show(it)
        }
    }

    private fun showSortMenu(v: View) {
        val popup = PopupMenu(requireContext(), v)

        popup.menu.setGroupCheckable(0, true, true)

        val nameItem = popup.menu.add(0, 1, 0, getString(R.string.filter_sort_by_name))
        val lastSeenItem = popup.menu.add(0, 2, 0, getString(R.string.filter_sort_by_last_seen))
        val firstDiscoveredItem = popup.menu.add(0, 3, 0, getString(R.string.filter_sort_by_first_discovered))

        // Check the currently selected sort option
        when (devicesViewModel.getCurrentSort()) {
            DevicesViewModel.SortOption.NAME -> nameItem.isChecked = true
            DevicesViewModel.SortOption.LAST_SEEN -> lastSeenItem.isChecked = true
            DevicesViewModel.SortOption.FIRST_DISCOVERED -> firstDiscoveredItem.isChecked = true
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> devicesViewModel.setSortOption(DevicesViewModel.SortOption.NAME)
                2 -> devicesViewModel.setSortOption(DevicesViewModel.SortOption.LAST_SEEN)
                3 -> devicesViewModel.setSortOption(DevicesViewModel.SortOption.FIRST_DISCOVERED)
            }
            true
        }
        popup.show()
    }

    private fun filterAdaptions() {
        binding.filterIgnoreChip.isChecked =
            devicesViewModel.activeFilter.containsKey(IgnoredFilter::class.toString())
        binding.filterNotifiedChip.isChecked =
            devicesViewModel.activeFilter.containsKey(NotifiedFilter::class.toString())
        getActiveTimeRange()?.let { setDateRangeText(it) }

        val defaultDeviceTypeFilter = DeviceTypeFilter(
            DeviceManager.devices.map { it.deviceType }.toSet()
        )
        val activeDeviceTypeFilter =
            devicesViewModel.activeFilter.getOrDefault(
                DeviceTypeFilter::class.toString(), defaultDeviceTypeFilter
            ) as DeviceTypeFilter

        // Create and add device type filter chips
        binding.filterDeviceTypes.removeAllViews()

        DeviceManager.devices.forEach { device ->
            val chip = IncludeFilterChipBinding.inflate(LayoutInflater.from(context))
            chip.text = device.defaultDeviceName
            val isChecked = activeDeviceTypeFilter.contains(device.deviceType)
            chip.filterDeviceTypeChip.isChecked = isChecked
            chip.filterDeviceTypeChip.id = (device.deviceType.toString() + ".chip").hashCode()

            chip.filterDeviceTypeChip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    activeDeviceTypeFilter.add(device.deviceType)
                } else {
                    activeDeviceTypeFilter.remove(device.deviceType)
                }
                devicesViewModel.addOrRemoveFilter(activeDeviceTypeFilter)
            }
            binding.filterDeviceTypes.addView(chip.root)
        }

        // Set click listeners for filter chips
        binding.filterIgnoreChip.setOnClickListener {
            devicesViewModel.addOrRemoveFilter(
                IgnoredFilter(), !binding.filterIgnoreChip.isChecked
            )
        }
        binding.filterNotifiedChip.setOnClickListener {
            devicesViewModel.addOrRemoveFilter(
                NotifiedFilter(), !binding.filterNotifiedChip.isChecked
            )
        }

        binding.filterDateRangeInput.setOnClickListener {
            openDateRangePicker()
        }
        binding.filterDateRange.setStartIconOnClickListener {
            openDateRangePicker()
        }

        binding.filterDateRange.setEndIconOnClickListener {
            binding.filterDateRangeInput.text?.clear()
            devicesViewModel.addOrRemoveFilter(DateRangeFilter(), remove = true)
        }
    }

    private fun openDateRangePicker() {
        var datePickerBuilder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.filter_date_range_picker_title))
            .setPositiveButtonText(getString(R.string.filter_apply))
        getActiveTimeRange()?.let {
            datePickerBuilder = datePickerBuilder.setSelection(it)
        }
        val datePicker = datePickerBuilder.build()
        datePicker.show(childFragmentManager, DATE_RANGE_PICKER_TAG)
        datePicker.addOnPositiveButtonClickListener {
            setDateRangeText(it)
            val newFilter = DateRangeFilter(
                toLocalDate(it.first),
                toLocalDate(it.second)
            )
            devicesViewModel.addOrRemoveFilter(newFilter)
        }
    }

    private fun getActiveTimeRange(): Pair<Long, Long>? {
        val hasActiveTimeRangeFilter =
            devicesViewModel.activeFilter.containsKey(DateRangeFilter::class.toString())
        return if (hasActiveTimeRangeFilter) {
            val filter =
                devicesViewModel.activeFilter[DateRangeFilter::class.toString()] as DateRangeFilter
            filter.getTimeRangePair()
        } else {
            null
        }
    }

    private fun setDateRangeText(selectedRange: Pair<Long, Long>) {
        binding.filterDateRangeInput.setText(
            getString(
                R.string.filter_from_until_text,
                toLocalDate(selectedRange.first),
                toLocalDate(selectedRange.second)
            )
        )
    }

    private fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DATE_RANGE_PICKER_TAG =
            "de.seemoo.at_tracking_detection.DATE_RANGE_PICKER"
    }
}