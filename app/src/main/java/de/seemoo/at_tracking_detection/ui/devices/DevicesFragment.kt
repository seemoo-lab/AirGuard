package de.seemoo.at_tracking_detection.ui.devices

import android.Manifest
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.tables.Device
import de.seemoo.at_tracking_detection.databinding.FragmentDevicesBinding
import de.seemoo.at_tracking_detection.ui.devices.filter.FilterDialogFragment
import de.seemoo.at_tracking_detection.ui.devices.filter.models.IgnoredFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.NotifiedFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.TimeRangeFilter
import de.seemoo.at_tracking_detection.util.Util
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import timber.log.Timber
import java.time.LocalDate


@AndroidEntryPoint
class DevicesFragment : Fragment() {

    private val devicesViewModel: DevicesViewModel by viewModels()

    private val safeArgs: DevicesFragmentArgs by navArgs()

    private lateinit var deviceAdapter: DeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentDevicesBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_devices, container, false)
        var swipeDirs: Int = ItemTouchHelper.LEFT
        var emptyListText = R.string.ignored_device_list_empty

        if (!safeArgs.showDevicesFound) {
            emptyListText = R.string.devices_list_empty
            swipeDirs = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            devicesViewModel.addOrRemoveFilter(IgnoredFilter.build())
        } else {
            val relevantTrackingStartDate = RiskLevelEvaluator.relevantTrackingDate.toLocalDate()
            devicesViewModel.addOrRemoveFilter(TimeRangeFilter.build(relevantTrackingStartDate, LocalDate.now()))
            devicesViewModel.addOrRemoveFilter(NotifiedFilter.build())
        }

        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback(swipeDirs))
        itemTouchHelper.attachToRecyclerView(binding.root.findViewById(R.id.devices_recycler_view))

        sharedElementReturnTransition =
            TransitionInflater.from(context).inflateTransition(android.R.transition.move)
                .setInterpolator(LinearOutSlowInInterpolator()).setDuration(500)

        deviceAdapter = DeviceAdapter(devicesViewModel, deviceItemListener)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.adapter = deviceAdapter
        binding.vm = devicesViewModel
        binding.emptyListText = getString(emptyListText)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        view.findViewById<RecyclerView>(R.id.devices_recycler_view)
            .doOnPreDraw { startPostponedEnterTransition() }
        val filterFab = view.findViewById<FloatingActionButton>(R.id.filter_fab)
        filterFab.setOnClickListener {
            FilterDialogFragment().show(childFragmentManager, DIALOG_TAG)
        }
        devicesViewModel.devices.observe(viewLifecycleOwner) {
            deviceAdapter.submitList(it)
        }
    }

    private val deviceItemListener =
        DeviceAdapter.OnClickListener { device: Device, materialCardView: MaterialCardView ->
            if (!Util.checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                return@OnClickListener
            }
            val directions: NavDirections =
                DevicesFragmentDirections.dashboardToDeviceDetailFragment(
                    device.address,
                    device.getFormattedDiscoveryDate()
                )
            val extras = FragmentNavigatorExtras(materialCardView to device.address)
            findNavController().navigate(directions, extras)
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

            private fun showRestoreDevice(device: Device) = Snackbar.make(
                view!!, getString(
                    R.string.devices_alter_removed, device.getDeviceNameWithId()
                ), Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.undo_button)) {
                Timber.d("Undo remove device!")
                devicesViewModel.setIgnoreFlag(device.address, false)
            }.show()

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val device =
                    deviceAdapter.currentList[viewHolder.bindingAdapterPosition]
                if (direction == ItemTouchHelper.LEFT) {
                    val editName = EditText(context)
                    editName.setText(device.getDeviceNameWithId())
                    AlertDialog.Builder(context)
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
                } else if (direction == ItemTouchHelper.RIGHT) {
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