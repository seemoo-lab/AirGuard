package de.seemoo.at_tracking_detection.ui.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.LocationRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.databinding.FragmentExportDeviceBinding
import de.seemoo.at_tracking_detection.ui.settings.FoundTrackersExport
import de.seemoo.at_tracking_detection.ui.settings.FoundTrackersExport.PageContext
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.risk.RiskLevel
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator.Companion.checkRiskLevelForDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@AndroidEntryPoint
class ExportDeviceFragment: Fragment() {
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var beaconRepository: BeaconRepository
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var notificationRepository: NotificationRepository

    private val viewModel: ExportDeviceViewModel by viewModels()
    private val safeArgs: ExportDeviceFragmentArgs by navArgs()

    private var deviceAddress: String? = null

    private lateinit var exportDocumentButton: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var binding: FragmentExportDeviceBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_export_device,
            container,
            false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = viewModel

        deviceAddress = safeArgs.deviceAddress

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        exportDocumentButton = view.findViewById<Button>(R.id.create_document_button)
        progressBar = view.findViewById(R.id.progress_bar)

        exportDocumentButton.setOnClickListener {
            checkPermissionsAndGeneratePdf()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startPdfGeneration()
                } else {
                    Toast.makeText(context, "Permission required to save PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkPermissionsAndGeneratePdf() {
        Timber.d("Checking permissions for PDF generation")
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startPdfGeneration()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    showPermissionRationale()
                }
                else -> {
                    requestPermissions(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        STORAGE_PERMISSION_CODE
                    )
                }
            }
        } else {
            startPdfGeneration()
        }
    }

    private fun startPdfGeneration() {
        showLoading(true)
        Timber.d("Starting PDF generation")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfBytes = generatePdfContent()

                withContext(Dispatchers.Main) {
                    showSaveDialog(pdfBytes)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                }
            }
        }
    }

    private fun showSaveDialog(pdfBytes: ByteArray) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "TrackingReport_${System.currentTimeMillis()}.pdf")
        }
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)

        // Store PDF bytes temporarily
        Companion.pdfBytes = pdfBytes
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        requireContext().contentResolver.openOutputStream(uri)?.use {
                            it.write(pdfBytes)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "PDF saved successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        exportDocumentButton.visibility = if (show) View.GONE else View.VISIBLE
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(requireContext())
            .setTitle("Storage Permission Needed")
            .setMessage("This permission is required to save PDF reports")
            .setPositiveButton("OK") { _, _ ->
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun generatePdfContent(): ByteArray = withContext(Dispatchers.IO) {
        Timber.d("Generating PDF content")
        val outputStream = ByteArrayOutputStream()
        val pdfDocument = PdfDocument()
        val device = deviceRepository.getDevice(deviceAddress!!) ?: throw IllegalStateException("Device not found")
        try {
            // val initialPage = createNewPage(pdfDocument)
            val pageInfo = PdfDocument.PageInfo.Builder(1200, 2000, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            var pageContext = PageContext(page, canvas, 50f)
            var yPos = 50f

            // Determine if the tracker is following
            val useLocation = SharedPrefs.useLocationInTrackingDetection
            val deviceRiskLevel = checkRiskLevelForDevice(device!!, useLocation) //TODO: Handle null cases
            val trackerFollowing = deviceRiskLevel != RiskLevel.LOW

            val headerPaint = Paint().apply {
                color = if (trackerFollowing) Color.RED else Color.BLUE
                textSize = 36f
            }
            canvas.drawText(
                if (trackerFollowing) getString(R.string.export_trackers_following) else getString(R.string.export_trackers_not_following),
                50f, 60f, headerPaint
            )
            yPos += 80f

            // --- Basic Device Information ---
            val trackerType = DeviceManager.deviceTypeToString(device.deviceType!!) // TODO: Handle potential null deviceType
            val lastSeenDate = device.lastSeen.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
            val firstSeenDate = device.firstDiscovery.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
            val detectionCount = beaconRepository.getDeviceBeaconsCount(device.address)
            val locations = locationRepository.getLocationsForBeacon(device.address)
            val uniqueLocations = locations.size

            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 24f
            }

            // Basic Info
            canvas.drawText(getString(R.string.export_trackers_tracker_type, trackerType), 50f, yPos, textPaint)
            yPos += 40
            if (device.deviceType != DeviceType.SAMSUNG_TRACKER && device.deviceType != DeviceType.SAMSUNG_FIND_MY_MOBILE) {
                // Identification does not work via MAC address on Samsung devices, therefore hide it
                canvas.drawText(getString(R.string.export_trackers_mac, device.address), 50f, yPos, textPaint)
                yPos += 40
            }
            canvas.drawText(getString(R.string.export_trackers_last_seen, lastSeenDate), 50f, yPos, textPaint)
            yPos += 40
            canvas.drawText(getString(R.string.export_trackers_first_seen, firstSeenDate), 50f, yPos, textPaint)
            yPos += 40
            canvas.drawText(getString(R.string.export_trackers_detections, detectionCount), 50f, yPos, textPaint)
            yPos += 40
            canvas.drawText(getString(R.string.export_trackers_unique_locations, uniqueLocations), 50f, yPos, textPaint)
            yPos += 60 // Add extra space before the next section

            // --- Map Placeholder ---
            // TODO: Map
            canvas.drawText("[Map Placeholder]", 50f, yPos, textPaint) // Placeholder for the map
            yPos += 60 // Add extra space before the next section

            // --- Beacons ---
            val beacons = beaconRepository.getDeviceBeacons(device.address)

            for (beacon in beacons) {
                val beaconDate = beacon.receivedAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
                val beaconLocation = locations.find { it.locationId == beacon.locationId }

                // 1. Time
                canvas.drawText(getString(R.string.export_trackers_time, beaconDate), 50f, yPos, textPaint)
                yPos += 30

                // 2. Location
                val locationText = if (beaconLocation != null) {
                    getString(R.string.export_trackers_location, beaconLocation.latitude.toString(), beaconLocation.longitude.toString())
                } else {
                    getString(R.string.export_trackers_location_unknown)
                }
                canvas.drawText(locationText, 50f, yPos, textPaint)
                yPos += 30

                // 3. Google Maps Link
                if (beaconLocation != null && beaconLocation.locationId != 0) {
                    // TODO: make Link clickable
                    val googleMapsLink = "https://maps.google.com/?q=${beaconLocation.latitude},${beaconLocation.longitude}"
                    canvas.drawText(getString(R.string.export_trackers_map, googleMapsLink), 50f, yPos, textPaint)
                    yPos += 30
                }

                // 4. Signal Strength
                val rssi = beacon.rssi
                canvas.drawText(getString(R.string.export_trackers_signal_strength, rssi), 50f, yPos, textPaint)
                yPos += 40
            }

            // --- Footer ---
            // Draw footer at a fixed position near the bottom
            canvas.drawText(getString(R.string.export_trackers_created_by), 50f, 1900f, textPaint)

            pdfDocument.finishPage(pageContext.page)
            pdfDocument.writeTo(outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            Timber.e(e, "Error generating PDF content")
            throw e
        } finally {
            pdfDocument.close()
            outputStream.close()
        }
    }

//    private fun generatePdfToUri(device: BaseDevice, uri: Uri) {
//        val context = context ?: return // Handle detached state
//
////        val mapView = MapView(context).apply {
////            setTileSource(TileSourceFactory.MAPNIK)
////            minZoomLevel = 4.0
////            maxZoomLevel = 19.0
////            controller.setZoom(12.0)
////
////            // TODO: replace with existing logic
////
////            // Add markers for each location
////            locations.forEach { location ->
////                val marker = Marker(this).apply {
////                    position = GeoPoint(location.latitude, location.longitude)
////                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
////                }
////                overlays.add(marker)
////            }
////        }
////
////        // Measure and layout map view
////        mapView.measure(
////            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
////            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
////        )
////        mapView.layout(0, 0, 1000, 600)
////
////        // Draw map to canvas
////        mapView.draw(canvas.apply { translate(50f, yPos + 40f) })
////        yPos += 640f
//
//    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1001
        private const val CREATE_FILE_REQUEST_CODE = 1002
        private var pdfBytes: ByteArray? = null
    }
}