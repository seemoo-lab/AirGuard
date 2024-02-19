package de.seemoo.at_tracking_detection.ui.devices

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.databinding.FragmentDevicesBinding
import de.seemoo.at_tracking_detection.ui.devices.filter.FilterDialogFragment
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DeviceTypeFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.IgnoredFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.NotifiedFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DateRangeFilter
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import timber.log.Timber
import java.time.LocalDate


@AndroidEntryPoint
abstract class DevicesFragment(
    var showDevicesFound: Boolean = true,
    var showAllDevices: Boolean = false,
    var deviceType: DeviceType?=null,
    var deviceType2: DeviceType?=null,
) : Fragment() {

    private val devicesViewModel: DevicesViewModel by viewModels()

    private val dialogFragment = FilterDialogFragment()

    private lateinit var deviceAdapter: DeviceAdapter

    private var swipeDirs: Int = ItemTouchHelper.LEFT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up the view model here since this is only called when the Fragment gets created
        // `onCreateView` is called every time the fragment is shown. Therefore it would not
        // be good to handle the view model here. The View model should keep some state

        val emptyListText: Int
        var deviceInfoText = R.string.info_text_all_devices


        if (!showDevicesFound) {
            activity?.setTitle(R.string.title_ignored_devices)
            emptyListText = R.string.ignored_device_list_empty
            swipeDirs = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            devicesViewModel.addOrRemoveFilter(IgnoredFilter.build())
            devicesViewModel.addOrRemoveFilter(
                DeviceTypeFilter.build(
                    DeviceManager.devices.map { it.deviceType }.toSet()
                )
            )
        } else {
            val relevantTrackingStartDate = RiskLevelEvaluator.relevantTrackingDateForRiskCalculation.toLocalDate()
            devicesViewModel.addOrRemoveFilter(
                DateRangeFilter.build(
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
                devicesViewModel.addOrRemoveFilter(NotifiedFilter.build())
                deviceInfoText = R.string.info_text_only_trackers
                emptyListText = R.string.empty_list_trackers
            }
        }

        if (deviceType != null && deviceType != DeviceType.UNKNOWN) {
            if (deviceType2 != null && deviceType2 != DeviceType.UNKNOWN) {
                devicesViewModel.addOrRemoveFilter(DeviceTypeFilter.build(setOf(deviceType!!, deviceType2!!)))
            } else {
                devicesViewModel.addOrRemoveFilter(DeviceTypeFilter.build(setOf(deviceType!!)))
            }
        }

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
        view.findViewById<RecyclerView>(R.id.devices_recycler_view)
            .doOnPreDraw { startPostponedEnterTransition() }

        devicesViewModel.devices.observe(viewLifecycleOwner) {
            deviceAdapter.submitList(it)
            updateTexts()
        }

        val showAllButton = view.findViewById<Button>(R.id.show_all_devices_button)
        showAllButton.setOnClickListener {
            devicesViewModel.addOrRemoveFilter(NotifiedFilter.build(), true)
            devicesViewModel.addOrRemoveFilter(IgnoredFilter.build(), true)
            devicesViewModel.addOrRemoveFilter(
                DeviceTypeFilter.build(
                    DeviceManager.devices.map { it.deviceType }.toSet()
                )
            )
        }

        //Adding the Filter fragment to the view
        val transaction = childFragmentManager.beginTransaction().replace(R.id.filter_fragment, dialogFragment)
        transaction.commit()

    }

    abstract val deviceItemListener: DeviceAdapter.OnClickListener


    private fun updateTexts() {
        if (devicesViewModel.activeFilter.containsKey(NotifiedFilter::class.toString())) {
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

    private fun swipeToDeleteCallback(swipeDirs: Int) =
        object :
            ItemTouchHelper.SimpleCallback(0, swipeDirs) {
            private val deleteBackground = ColorDrawable(Color.RED)
            private val editBackground = ColorDrawable(Color.GRAY)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView

                val deleteIcon =
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_baseline_delete_24)
                val editIcon =
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_baseline_edit_24)

                var transitionX = dX

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    when {
                        dX > 0 -> {
                            val iconMargin = (itemView.height - deleteIcon!!.intrinsicHeight) / 2
                            val iconTop =
                                itemView.top + (itemView.height - deleteIcon.intrinsicHeight) / 2
                            val iconBottom = itemView.bottom - deleteIcon.intrinsicHeight
                            val iconLeft = itemView.left + iconMargin
                            val iconRight = itemView.left + iconMargin + deleteIcon.intrinsicWidth
                            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            deleteBackground.setBounds(
                                itemView.left,
                                itemView.top,
                                itemView.left + dX.toInt(),
                                itemView.bottom
                            )
                            deleteBackground.draw(c)
                            deleteIcon.draw(c)
                        }
                        dX < 0 -> {
                            val iconMargin = (itemView.height - editIcon!!.intrinsicHeight) / 2
                            val iconTop =
                                itemView.top + (itemView.height - editIcon.intrinsicHeight) / 2
                            val iconBottom = iconTop + editIcon.intrinsicHeight
                            val iconLeft = itemView.right - iconMargin - editIcon.intrinsicWidth
                            val iconRight = itemView.right - iconMargin
                            editIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            editBackground.setBounds(
                                itemView.right + dX.toInt(),
                                itemView.top,
                                itemView.right,
                                itemView.bottom
                            )
                            transitionX /= 4
                            editBackground.draw(c)
                            editIcon.draw(c)
                        }
                    }
                }

                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    transitionX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )

            }

            private fun showRestoreDevice(baseDevice: BaseDevice) = Snackbar.make(
                view!!, getString(
                    R.string.devices_alter_removed, baseDevice.getDeviceNameWithID()
                ), Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.undo_button)) {
                Timber.d("Undo remove device!")
                devicesViewModel.setIgnoreFlag(baseDevice.address, false)
            }.show()

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val device =
                    deviceAdapter.currentList[viewHolder.bindingAdapterPosition]
                if (direction == ItemTouchHelper.LEFT) {
                    val editName = EditText(context)
                    editName.setText(device.getDeviceNameWithID())
                    MaterialAlertDialogBuilder(requireContext())
                        .setIcon(R.drawable.ic_baseline_edit_24)
                        .setTitle(getString(R.string.devices_edit_title)).setView(editName)
                        .setNegativeButton(getString(R.string.cancel_button), null)
                        .setPositiveButton(R.string.ok_button) { _, _ ->
                            device.name = editName.text.toString()
                            devicesViewModel.update(device)
                            Timber.d("Renamed device to ${device.name}")
                        }
                        .setOnDismissListener {
                            deviceAdapter.notifyItemChanged(viewHolder.bindingAdapterPosition)
                        }
                        .show()
                } else if (direction == ItemTouchHelper.RIGHT && device.ignore) {
                    devicesViewModel.setIgnoreFlag(device.address, false)
                    showRestoreDevice(device)
                    Timber.d("Removed device ${device.address} from ignored list")
                }
            }
        }

    companion object {
        private const val DIALOG_TAG = "de.seemoo.at_tracking_detection.filter_dialog"
    }
}