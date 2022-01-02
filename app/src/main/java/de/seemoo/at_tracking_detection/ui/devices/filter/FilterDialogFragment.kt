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
import de.seemoo.at_tracking_detection.databinding.DialogFilterBinding
import de.seemoo.at_tracking_detection.ui.devices.DevicesViewModel
import de.seemoo.at_tracking_detection.ui.devices.filter.models.IgnoredFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.NotifiedFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.TimeRangeFilter
import timber.log.Timber
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
        devicesViewModel.selectedTimeRange?.let {
            setDateRangeText(it)
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
            devicesViewModel.selectedTimeRange?.let {
                datePickerBuilder = datePickerBuilder.setSelection(it)
            }
            val datePicker = datePickerBuilder.build()
            datePicker.show(activity?.supportFragmentManager!!, DATE_RANGE_PICKER_TAG)
            datePicker.addOnPositiveButtonClickListener {
                setDateRangeText(it)
                devicesViewModel.selectedTimeRange = it
                devicesViewModel.addOrRemoveFilter(
                    TimeRangeFilter.build(
                        toLocalDate(it.first),
                        toLocalDate(it.second)
                    )
                )
            }
        }
        binding.filterDateRange.setEndIconOnClickListener {
            binding.filterDateRangeInput.text?.clear()
            devicesViewModel.addOrRemoveFilter(TimeRangeFilter.build(), true)
        }
    }

    private fun setDateRangeText(selectedRange: Pair<Long, Long>) {
        binding.filterDateRangeInput.setText(
            getString(
                R.string.filter_from_until_text,
                toLocalDate(selectedRange.first),
                toLocalDate(selectedRange.first)
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