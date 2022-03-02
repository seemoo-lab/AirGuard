package de.seemoo.at_tracking_detection.ui.devices.filter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Pair
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.databinding.DialogFilterBinding
import de.seemoo.at_tracking_detection.databinding.IncludeFilterChipBinding
import de.seemoo.at_tracking_detection.ui.devices.DevicesViewModel
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DeviceTypeFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.IgnoredFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.NotifiedFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DateRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@AndroidEntryPoint
class FilterDialogFragment :
    BottomSheetDialogFragment() {

    private val devicesViewModel: DevicesViewModel by viewModels({ requireParentFragment() })

    private var _binding: DialogFilterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFilterBinding.inflate(LayoutInflater.from(context))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.filterIgnoreChip.isChecked =
            devicesViewModel.activeFilter.containsKey(IgnoredFilter::class.toString())
        binding.filterNotifiedChip.isChecked =
            devicesViewModel.activeFilter.containsKey(NotifiedFilter::class.toString())
        getActiveTimeRange()?.let { setDateRangeText(it) }

        val defaultDeviceTypeFilter = DeviceTypeFilter.build(
            DeviceManager.devices.map { it.deviceType }.toSet()
        )
        val activeDeviceTypeFilter =
            devicesViewModel.activeFilter.getOrDefault(
                DeviceTypeFilter::class.toString(), defaultDeviceTypeFilter
            ) as DeviceTypeFilter

        for (device in DeviceManager.devices) {
            val chip =
                IncludeFilterChipBinding.inflate(LayoutInflater.from(context))
            chip.text = device.defaultDeviceName
            if (activeDeviceTypeFilter.contains(device.deviceType)) {
                chip.filterDeviceTypeChip.isChecked = true
            }
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

        binding.filterIgnoreChip.setOnClickListener {
            devicesViewModel.addOrRemoveFilter(
                IgnoredFilter.build(), !binding.filterIgnoreChip.isChecked
            )
        }
        binding.filterNotifiedChip.setOnClickListener {
            devicesViewModel.addOrRemoveFilter(
                NotifiedFilter.build(), !binding.filterNotifiedChip.isChecked
            )
        }
        binding.filterDateRangeInput.setOnClickListener {
            var datePickerBuilder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(getString(R.string.filter_date_range_picker_title))
                .setPositiveButtonText(getString(R.string.filter_apply))
            getActiveTimeRange()?.let {
                datePickerBuilder = datePickerBuilder.setSelection(it)
            }
            val datePicker = datePickerBuilder.build()
            datePicker.show(activity?.supportFragmentManager!!, DATE_RANGE_PICKER_TAG)
            datePicker.addOnPositiveButtonClickListener {
                setDateRangeText(it)
                devicesViewModel.addOrRemoveFilter(
                    DateRangeFilter.build(
                        toLocalDate(it.first),
                        toLocalDate(it.second)
                    )
                )
            }
        }
        binding.filterDateRange.setEndIconOnClickListener {
            binding.filterDateRangeInput.text?.clear()
            devicesViewModel.addOrRemoveFilter(DateRangeFilter.build(), true)
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