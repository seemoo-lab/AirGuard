package de.seemoo.at_tracking_detection.ui.devices

import android.os.Bundle
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.LiveData
import androidx.navigation.NavDirections
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.tables.Device
import de.seemoo.at_tracking_detection.databinding.FragmentDevicesBinding
import de.seemoo.at_tracking_detection.ui.devices.adapter.DeviceAdapter
import timber.log.Timber

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
        val deviceList: LiveData<List<Device>>
        if (safeArgs.showDevicesFound) {
            deviceList = devicesViewModel.devices
        } else {
            deviceList = devicesViewModel.ignoredDevices
            val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
            itemTouchHelper.attachToRecyclerView(binding.root.findViewById(R.id.devices_recycler_view))
        }

        sharedElementReturnTransition =
            TransitionInflater.from(context).inflateTransition(android.R.transition.move)
                .setInterpolator(LinearOutSlowInInterpolator()).setDuration(500)

        deviceAdapter = DeviceAdapter(devicesViewModel, deviceItemListener)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.adapter = deviceAdapter
        binding.vm = devicesViewModel
        deviceList.observe(viewLifecycleOwner, { devices ->
            deviceAdapter.submitList(devices)
            devicesViewModel.deviceListEmpty.postValue(devices.isEmpty())
        })



        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        view.findViewById<RecyclerView>(R.id.devices_recycler_view)
            .doOnPreDraw { startPostponedEnterTransition() }

        if (!safeArgs.showDevicesFound) {
            val textView = view.findViewById<TextView>(R.id.empty_list_text)
            textView?.setText(R.string.ignored_device_list_empty)
        }
    }

    private val deviceItemListener =
        DeviceAdapter.OnClickListener { device: Device, materialCardView: MaterialCardView ->
            val directions: NavDirections =
                DevicesFragmentDirections.dashboardToDeviceDetailFragment(
                    device.address,
                    device.getFormattedDiscoveryDate()
                )
            val extras = FragmentNavigatorExtras(materialCardView to device.address)
            findNavController().navigate(directions, extras)
        }

    private val swipeToDeleteCallback =
        object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val deviceAddress =
                    deviceAdapter.currentList[viewHolder.bindingAdapterPosition].address
                devicesViewModel.removeDeviceIgnoreFlag(deviceAddress)
                Timber.d("Removed device $deviceAddress from ignored list")
            }

        }
}