package de.seemoo.at_tracking_detection.ui.devices.filter

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.util.Pair
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
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
import de.seemoo.at_tracking_detection.ui.devices.filter.models.FavoriteFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

@AndroidEntryPoint
class FilterDialogFragment : Fragment() {

    private val devicesViewModel: DevicesViewModel by viewModels({ requireParentFragment() })

    private var _binding: DialogFilterBinding? = null
    private val binding get() = _binding!!
    private val baseTextEndPaddingByChipId = mutableMapOf<Int, Float>()
    private val baseTextStartPaddingByChipId = mutableMapOf<Int, Float>()
    private val currentChipIconResByChipId = mutableMapOf<Int, Int?>()
    private var suppressDeviceTypeChipCallbacks = false

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
            TransitionManager.beginDelayedTransition(binding.root as ViewGroup, AutoTransition())

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
        val timesSeenItem = popup.menu.add(0, 4, 0, getString(R.string.filter_sort_by_times_seen))

        // Check the currently selected sort option
        when (devicesViewModel.getCurrentSort()) {
            DevicesViewModel.SortOption.NAME -> nameItem.isChecked = true
            DevicesViewModel.SortOption.LAST_SEEN -> lastSeenItem.isChecked = true
            DevicesViewModel.SortOption.FIRST_DISCOVERED -> firstDiscoveredItem.isChecked = true
            DevicesViewModel.SortOption.TIMES_SEEN -> timesSeenItem.isChecked = true
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
            configureDeviceTypeChip(chip.filterDeviceTypeChip, isChecked)

            chip.filterDeviceTypeChip.setOnCheckedChangeListener { _, isChecked ->
                applyDeviceTypeChipState(chip.filterDeviceTypeChip, isChecked, animate = true)
                if (suppressDeviceTypeChipCallbacks) {
                    return@setOnCheckedChangeListener
                }
                if (isChecked) {
                    activeDeviceTypeFilter.add(device.deviceType)
                } else {
                    activeDeviceTypeFilter.remove(device.deviceType)
                }
                devicesViewModel.addOrRemoveFilter(activeDeviceTypeFilter)
            }
            chip.filterDeviceTypeChip.setOnLongClickListener {
                val isOnlyOneSelected = activeDeviceTypeFilter.deviceTypes.size == 1 &&
                        activeDeviceTypeFilter.contains(device.deviceType)

                if (isOnlyOneSelected) {
                    // If this is already the only selected type, select all types again
                    DeviceManager.devices.forEach { d ->
                        activeDeviceTypeFilter.add(d.deviceType)
                    }
                } else {
                    // Exclusively select only this device type
                    activeDeviceTypeFilter.deviceTypes.clear()
                    activeDeviceTypeFilter.add(device.deviceType)
                }

                // Update all chip checked states in the chip group in one pass.
                val chipGroup = binding.filterDeviceTypes
                suppressDeviceTypeChipCallbacks = true
                for (i in 0 until chipGroup.childCount) {
                    val childChip = chipGroup.getChildAt(i) as? Chip
                    if (isOnlyOneSelected) {
                        if (childChip != null && !childChip.isChecked) {
                            childChip.isChecked = true
                            applyDeviceTypeChipState(childChip, isChecked = true, animate = true)
                        }
                    } else {
                        val shouldBeChecked = childChip?.id == chip.filterDeviceTypeChip.id
                        if (childChip != null && childChip.isChecked != shouldBeChecked) {
                            childChip.isChecked = shouldBeChecked
                            applyDeviceTypeChipState(childChip, shouldBeChecked, animate = true)
                        }
                    }
                }
                suppressDeviceTypeChipCallbacks = false
                devicesViewModel.addOrRemoveFilter(activeDeviceTypeFilter)
                true
            }
            binding.filterDeviceTypes.addView(chip.root)
        }

        // Set click listeners for filter chips
        configureAnimatedFilterChip(binding.filterIgnoreChip)
        configureAnimatedFilterChip(binding.filterNotifiedChip)
        configureAnimatedFilterChip(binding.filterFavoriteChip)

        binding.filterIgnoreChip.setOnClickListener {
            devicesViewModel.cycleIgnoredFilterState()
        }
        binding.filterNotifiedChip.setOnClickListener {
            devicesViewModel.cycleNotifiedFilterState()
        }
        binding.filterFavoriteChip.setOnClickListener {
            devicesViewModel.cycleFavoriteFilterState()
        }

        // Observe filter states to update chip appearance
        devicesViewModel.ignoredFilterState.value?.let { state ->
            updateChipAppearance(binding.filterIgnoreChip, state, animate = false)
        }
        devicesViewModel.ignoredFilterState.observe(viewLifecycleOwner) { state ->
            updateChipAppearance(binding.filterIgnoreChip, state, animate = true)
        }
        devicesViewModel.notifiedFilterState.value?.let { state ->
            updateChipAppearance(binding.filterNotifiedChip, state, animate = false)
        }
        devicesViewModel.notifiedFilterState.observe(viewLifecycleOwner) { state ->
            updateChipAppearance(binding.filterNotifiedChip, state, animate = true)
        }
        devicesViewModel.favoriteFilterState.value?.let { state ->
            updateChipAppearance(binding.filterFavoriteChip, state, animate = false)
        }
        devicesViewModel.favoriteFilterState.observe(viewLifecycleOwner) { state ->
            updateChipAppearance(binding.filterFavoriteChip, state, animate = true)
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

    private fun configureDeviceTypeChip(chip: Chip, isChecked: Boolean) {
        configureAnimatedFilterChip(chip)
        applyChipVisualState(
            chip = chip,
            targetIconRes = if (isChecked) R.drawable.ic_baseline_check_24 else null,
            animate = false
        )
    }

    private fun configureAnimatedFilterChip(chip: Chip) {
        chip.gravity = Gravity.CENTER
        chip.textAlignment = View.TEXT_ALIGNMENT_CENTER
        chip.chipIcon = chip.chipIcon?.mutate()
        baseTextStartPaddingByChipId[chip.id] = chip.textStartPadding
        baseTextEndPaddingByChipId[chip.id] = chip.textEndPadding
        currentChipIconResByChipId[chip.id] = R.drawable.ic_baseline_check_24
        chip.post {
            val stableWidth = calculateStableChipWidth(chip)
            chip.minWidth = stableWidth
            chip.layoutParams = chip.layoutParams.apply {
                width = stableWidth
            }
        }
    }

    private fun applyDeviceTypeChipState(chip: Chip, isChecked: Boolean, animate: Boolean) {
        applyChipVisualState(
            chip = chip,
            targetIconRes = if (isChecked) R.drawable.ic_baseline_check_24 else null,
            animate = animate
        )
    }

    private fun applyChipVisualState(chip: Chip, targetIconRes: Int?, animate: Boolean) {
        val baseTextStartPadding = baseTextStartPaddingByChipId[chip.id] ?: chip.textStartPadding
        val baseTextEndPadding = baseTextEndPaddingByChipId[chip.id] ?: chip.textEndPadding
        val iconSpace = getChipIconSpace(chip)
        val hasIcon = targetIconRes != null
        // Checked: center in the space right of the icon. Unchecked: center across full chip width.
        val targetTextStartPadding: Float
        val targetTextEndPadding: Float
        if (hasIcon) {
            targetTextStartPadding = baseTextStartPadding
            targetTextEndPadding = baseTextEndPadding
        } else {
            // Shift text left by half icon space while keeping start+end padding sum stable.
            val desiredShift = iconSpace / 2f
            val newStartPadding = (baseTextStartPadding - desiredShift).coerceAtLeast(0f)
            val appliedShift = baseTextStartPadding - newStartPadding
            targetTextStartPadding = newStartPadding
            targetTextEndPadding = baseTextEndPadding + appliedShift
        }

        if (animate) {
            chip.animate().cancel()
            chip.animate().alpha(0.7f).setDuration(CHIP_STATE_ANIMATION_DURATION_MS / 2).withEndAction {
                chip.animate().alpha(1f).setDuration(CHIP_STATE_ANIMATION_DURATION_MS / 2).start()
            }.start()

            ValueAnimator.ofFloat(chip.textStartPadding, targetTextStartPadding).apply {
                duration = CHIP_STATE_ANIMATION_DURATION_MS
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    chip.textStartPadding = animator.animatedValue as Float
                }
            }.start()

            ValueAnimator.ofFloat(chip.textEndPadding, targetTextEndPadding).apply {
                duration = CHIP_STATE_ANIMATION_DURATION_MS
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    chip.textEndPadding = animator.animatedValue as Float
                }
            }.start()
        } else {
            chip.textStartPadding = targetTextStartPadding
            chip.textEndPadding = targetTextEndPadding
        }

        animateChipIcon(chip, targetIconRes, animate)
    }

    private fun animateChipIcon(chip: Chip, targetIconRes: Int?, animate: Boolean) {
        val currentIconRes = currentChipIconResByChipId[chip.id]
        val targetIconAlpha = if (targetIconRes != null) 255 else 0

        if (!animate) {
            if (targetIconRes != null && currentIconRes != targetIconRes) {
                chip.setChipIconResource(targetIconRes)
                chip.chipIcon = chip.chipIcon?.mutate()
            }
            chip.chipIcon?.alpha = targetIconAlpha
            currentChipIconResByChipId[chip.id] = targetIconRes
            return
        }

        if (currentIconRes != null && targetIconRes != null && currentIconRes != targetIconRes) {
            var hasSwappedIcon = false
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = CHIP_STATE_ANIMATION_DURATION_MS
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedFraction
                    if (fraction < 0.5f) {
                        chip.chipIcon?.alpha = ((1f - (fraction * 2f)) * 255f).toInt()
                    } else {
                        if (!hasSwappedIcon) {
                            chip.setChipIconResource(targetIconRes)
                            chip.chipIcon = chip.chipIcon?.mutate()
                            hasSwappedIcon = true
                        }
                        chip.chipIcon?.alpha = (((fraction - 0.5f) * 2f) * 255f).toInt()
                    }
                }
                start()
            }
            currentChipIconResByChipId[chip.id] = targetIconRes
            return
        }

        if (targetIconRes != null && currentIconRes != targetIconRes) {
            chip.setChipIconResource(targetIconRes)
            chip.chipIcon = chip.chipIcon?.mutate()
            chip.chipIcon?.alpha = 0
        }

        ValueAnimator.ofInt(chip.chipIcon?.alpha ?: targetIconAlpha, targetIconAlpha).apply {
            duration = CHIP_STATE_ANIMATION_DURATION_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animator ->
                chip.chipIcon?.alpha = animator.animatedValue as Int
            }
            start()
        }
        currentChipIconResByChipId[chip.id] = targetIconRes
    }

    private fun calculateStableChipWidth(chip: Chip): Int {
        val baseTextStartPadding = baseTextStartPaddingByChipId[chip.id] ?: chip.textStartPadding
        val baseTextEndPadding = baseTextEndPaddingByChipId[chip.id] ?: chip.textEndPadding
        val iconSpace = getChipIconSpace(chip)
        val textWidth = chip.paint.measureText(chip.text?.toString().orEmpty())

        val checkedWidth =
            chip.chipStartPadding + iconSpace + baseTextStartPadding + textWidth + baseTextEndPadding + chip.chipEndPadding
        return ceil(checkedWidth.toDouble()).toInt()
    }

    private fun getChipIconSpace(chip: Chip): Float {
        val iconWidth = chip.chipIcon?.intrinsicWidth?.takeIf { it > 0 }?.toFloat()
            ?: 0f
        return iconWidth + chip.iconStartPadding + chip.iconEndPadding
    }

    private fun updateChipAppearance(chip: Chip, state: DevicesViewModel.FilterState, animate: Boolean) {
        when (state) {
            DevicesViewModel.FilterState.UNSELECTED -> {
                chip.isChecked = false
                applyChipVisualState(chip, targetIconRes = null, animate = animate)
                chip.paintFlags = chip.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            DevicesViewModel.FilterState.INCLUDING -> {
                chip.isChecked = true
                applyChipVisualState(chip, targetIconRes = R.drawable.ic_baseline_check_24, animate = animate)
                chip.paintFlags = chip.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            DevicesViewModel.FilterState.EXCLUDING -> {
                chip.isChecked = true
                applyChipVisualState(chip, targetIconRes = R.drawable.ic_baseline_close_24, animate = animate)
                chip.paintFlags = chip.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CHIP_STATE_ANIMATION_DURATION_MS = 150L
        private const val DATE_RANGE_PICKER_TAG =
            "de.seemoo.at_tracking_detection.DATE_RANGE_PICKER"
    }
}