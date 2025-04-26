package de.seemoo.at_tracking_detection.ui.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Location
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.LocationRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.databinding.FragmentExportDeviceBinding
import de.seemoo.at_tracking_detection.util.Utility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
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

    private lateinit var mapView: MapView
    private var isMapReady = false

    private lateinit var binding: FragmentExportDeviceBinding

    private lateinit var beaconPreviewAdapter: BeaconPreviewAdapter
    private lateinit var beaconsRecyclerView: RecyclerView

    private val PAGE_WIDTH = 1200
    private val PAGE_HEIGHT = 2000
    private val MARGIN = 50f
    private val FOOTER_HEIGHT = 50f // Space reserved for footer
    private val TEXT_SIZE_NORMAL = 24f
    private val TEXT_SIZE_HEADER = 36f
    private val LINE_SPACING = 10f // Extra space between lines
    private val NORMAL_LINE_HEIGHT = TEXT_SIZE_NORMAL + LINE_SPACING
    private val MAP_BOTTOM_MARGIN = 20f
    private val SECTION_SPACING = 40f // Space between logical sections

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

        beaconPreviewAdapter = BeaconPreviewAdapter()

        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        beaconsRecyclerView = binding.beaconsRecyclerview // Use binding
        setupRecyclerView()
        observeViewModel()

        viewModel.loadDevice(deviceAddress, requireContext())

        mapView = view.findViewById(R.id.map_preview)
        Utility.basicMapSetup(mapView)

        mapView.setMultiTouchControls(false)
        mapView.setBuiltInZoomControls(false)
        mapView.setOnTouchListener { _, _ -> true }

        view.post {
            setupMapContent()
        }

        exportDocumentButton = view.findViewById<Button>(R.id.create_document_button)
        progressBar = view.findViewById(R.id.progress_bar)

        exportDocumentButton.setOnClickListener {
            if (isMapReady) {
                checkPermissionsAndGeneratePdf()
            } else {
                Toast.makeText(context, getString(R.string.export_trackers_map_still_loading), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (!isMapReady) setupMapContent()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    private fun setupRecyclerView() {
        beaconsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = beaconPreviewAdapter
            // setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        viewModel.beaconPreviewList.observe(viewLifecycleOwner) { beaconList ->
            beaconPreviewAdapter.submitList(beaconList)
        }
    }

    private fun setupMapContent() {
        lifecycleScope.launch {
            val locations = locationRepository.getLocationsForBeacon(deviceAddress!!)
            val geoPoints = locations.map { GeoPoint(it.latitude, it.longitude) }

            // Add markers
            val iconDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_location_on_45_black)
            iconDrawable?.setTint(Color.BLACK)
            geoPoints.forEach { point ->
                Marker(mapView).apply {
                    position = point
                    icon = iconDrawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setInfoWindow(null)
                    mapView.overlays.add(this)
                }
            }

            // Set zoom and center
            if (geoPoints.isNotEmpty()) {
                val boundingBox = BoundingBox.fromGeoPointsSafe(geoPoints)
                mapView.post {
                    mapView.zoomToBoundingBox(boundingBox, false, 100)
                    mapView.invalidate()
                    isMapReady = true
                }
            } else {
                mapView.controller.setZoom(2.0)
                isMapReady = true
            }
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
        val device = deviceRepository.getDevice(deviceAddress!!) ?: run {
            Timber.e("Device not found in database: $deviceAddress")
            throw IllegalStateException("Device $deviceAddress not found for PDF export.")
        }
        val beacons = beaconRepository.getDeviceBeacons(device.address)
        val locations = locationRepository.getLocationsForBeacon(device.address)

        val outputStream = ByteArrayOutputStream()
        val pdfDocument = PdfDocument()

        try {
            Timber.d("Starting PDF layout calculation (Pass 1)...")
            val totalPages = calculateTotalPages(device, beacons, locations)
            Timber.d("Calculated total pages: $totalPages")
            if (totalPages <= 0) {
                Timber.w("Calculation resulted in zero or negative pages. Aborting.")
                return@withContext ByteArray(0) // Return empty if no content leads to pages
            }

            Timber.d("Starting PDF rendering (Pass 2)...")
            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas
            var yPos = MARGIN

            // Common Paints
            val textPaint = Paint().apply { color = Color.BLACK; textSize = TEXT_SIZE_NORMAL; isAntiAlias = true }
            val boldTextPaint = Paint(textPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val linkPaint = Paint(textPaint).apply { color = Color.BLUE; isUnderlineText = true }
            val headerPaint = Paint().apply { textSize = TEXT_SIZE_HEADER; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

            val drawablePageHeight = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT - LINE_SPACING // Usable height for content

            val trackerFollowing = ExportDeviceViewModel.isTrackerFollowing(device)
            headerPaint.color = if (trackerFollowing) Color.RED else Color.BLUE
            val headerText = if (trackerFollowing) getString(R.string.export_trackers_following) else getString(R.string.export_trackers_not_following)
            canvas.drawText(headerText, MARGIN, yPos + TEXT_SIZE_HEADER, headerPaint)
            yPos += TEXT_SIZE_HEADER + SECTION_SPACING

            // --- Basic Device Information ---
            val deviceTypeStr = DeviceManager.deviceTypeToString(device.deviceType ?: DeviceType.UNKNOWN)
            val lastSeenDate = device.lastSeen.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
            val firstSeenDate = device.firstDiscovery.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
            val detectionCount = beacons.size
            val uniqueLocationCount = locations.size

            canvas.drawText(getString(R.string.export_trackers_basic_info_title), MARGIN, yPos, boldTextPaint)
            yPos += NORMAL_LINE_HEIGHT

            canvas.drawText(getString(R.string.export_trackers_tracker_type, deviceTypeStr), MARGIN, yPos, textPaint)
            yPos += NORMAL_LINE_HEIGHT
            if (device.deviceType != DeviceType.SAMSUNG_TRACKER && device.deviceType != DeviceType.SAMSUNG_FIND_MY_MOBILE) {
                canvas.drawText(getString(R.string.export_trackers_mac, device.address), MARGIN, yPos, textPaint)
                yPos += NORMAL_LINE_HEIGHT
            }
            canvas.drawText(getString(R.string.export_trackers_first_seen, firstSeenDate), MARGIN, yPos, textPaint)
            yPos += NORMAL_LINE_HEIGHT
            canvas.drawText(getString(R.string.export_trackers_last_seen, lastSeenDate), MARGIN, yPos, textPaint)
            yPos += NORMAL_LINE_HEIGHT
            canvas.drawText(getString(R.string.export_trackers_detections, detectionCount), MARGIN, yPos, textPaint)
            yPos += NORMAL_LINE_HEIGHT
            canvas.drawText(getString(R.string.export_trackers_unique_locations, uniqueLocationCount), MARGIN, yPos, textPaint)
            yPos += SECTION_SPACING

            // --- Map Section ---
            val mapBitmap = createMapBitmap()
            val mapHeight = (mapBitmap.height * (PAGE_WIDTH - 2 * MARGIN) / mapBitmap.width)
            val mapRect = Rect(MARGIN.toInt(), yPos.toInt(), (PAGE_WIDTH - MARGIN).toInt(), (yPos + mapHeight).toInt())
            if (yPos + mapHeight > drawablePageHeight) {
                Timber.d("Map doesn't fit on page $currentPageNumber, creating new page.")
                drawPageFooter(canvas, currentPageNumber, totalPages, textPaint) // Add footer to current page
                pdfDocument.finishPage(currentPage) // Finish current page
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                yPos = MARGIN // Reset yPos for new page
            }

            canvas.drawText(getString(R.string.export_trackers_map_title), MARGIN, yPos, boldTextPaint)
            yPos += NORMAL_LINE_HEIGHT
            canvas.drawBitmap(mapBitmap, null, mapRect, null) // Draw map scaled into rect
            yPos += mapHeight + SECTION_SPACING
            mapBitmap.recycle()

            // --- Beacon Details Section ---
            canvas.drawText(getString(R.string.export_trackers_detections_title), MARGIN, yPos, boldTextPaint)
            yPos += NORMAL_LINE_HEIGHT

            for (beacon in beacons) {
                val beaconLocation = locations.find { it.locationId == beacon.locationId }

                val beaconConnectionState: ConnectionState = Utility.getConnectionStateFromString(beacon.connectionState)
                val show15MinuteWarning = beaconConnectionState !in DeviceManager.unsafeConnectionState

                val beaconEntryHeight = getBeaconEntryHeight(beaconLocation, show15MinuteWarning)

                // Check if the *entire* beacon entry fits on the current page
                if (yPos + beaconEntryHeight > drawablePageHeight) {
                    Timber.d("Beacon entry doesn't fit on page $currentPageNumber, creating new page.")
                    drawPageFooter(canvas, currentPageNumber, totalPages, textPaint)
                    pdfDocument.finishPage(currentPage)
                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    yPos = MARGIN // Reset yPos
                    canvas.drawText(getString(R.string.export_trackers_detections_title_continued), MARGIN, yPos, boldTextPaint)
                    yPos += NORMAL_LINE_HEIGHT
                }

                // Draw beacon details
                val beaconDate = beacon.receivedAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
                canvas.drawText(getString(R.string.export_trackers_time, beaconDate), MARGIN, yPos, textPaint)
                yPos += NORMAL_LINE_HEIGHT

                val locationText = if (beaconLocation != null) {
                    getString(R.string.export_trackers_location, beaconLocation.latitude.toString(), beaconLocation.longitude.toString())
                } else {
                    getString(R.string.export_trackers_location_unknown)
                }
                canvas.drawText(locationText, MARGIN, yPos, textPaint)
                yPos += NORMAL_LINE_HEIGHT

                if (beaconLocation != null) {
                    val googleMapsLink = "https://maps.google.com/?q=${beaconLocation.latitude},${beaconLocation.longitude}"
                    canvas.drawText(getString(R.string.export_trackers_map, googleMapsLink), MARGIN, yPos, linkPaint)
                    yPos += NORMAL_LINE_HEIGHT
                }

                if (show15MinuteWarning) {
                    canvas.drawText(getString(R.string.export_trackers_15_minute_warning), MARGIN, yPos, textPaint)
                    yPos += NORMAL_LINE_HEIGHT
                }

                canvas.drawText(getString(R.string.export_trackers_signal_strength, beacon.rssi), MARGIN, yPos, textPaint)
                yPos += NORMAL_LINE_HEIGHT + LINE_SPACING // Add extra spacing between beacon entries
            }

            // --- Final Footer ---
            drawPageFooter(canvas, currentPageNumber, totalPages, textPaint) // Draw footer on the last page

            pdfDocument.finishPage(currentPage) // Finish the last page

            // --- Write to Output Stream ---
            pdfDocument.writeTo(outputStream)
            Timber.d("PDF rendering finished successfully.")
            outputStream.toByteArray()
        } catch (e: Exception) {
            Timber.e(e, "Error generating PDF content")
            throw e
        } finally {
            pdfDocument.close()
            outputStream.close()
            Timber.d("PDF Document closed.")
        }
    }

    private suspend fun createMapBitmap(): Bitmap = withContext(Dispatchers.Main) {
        // Ensure map view has valid dimensions
        if (mapView.width <= 0 || mapView.height <= 0) {
            Timber.w("MapView dimensions are invalid, returning placeholder bitmap")
            // Return a small placeholder or throw an error
            return@withContext createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        }

        Timber.d("Creating map bitmap with dimensions: ${mapView.width}x${mapView.height}")
        val bitmap = createBitmap(mapView.width, mapView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        mapView.draw(canvas)
        Timber.d("Map drawing onto bitmap canvas complete.")
        bitmap
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int, totalPages: Int, paint: Paint) {
        val footerText = getString(R.string.export_trackers_created_by)
        val pageNumberText = getString(R.string.export_page_number, pageNumber, totalPages)

        val footerY = PAGE_HEIGHT - MARGIN / 2 // Position slightly above the bottom margin
        val pageNumberY = footerY

        canvas.drawText(footerText, MARGIN, footerY, paint)

        // Draw "Page X / Y" on the right
        val pageNumWidth = paint.measureText(pageNumberText)
        val pageNumberX = PAGE_WIDTH - MARGIN - pageNumWidth
        canvas.drawText(pageNumberText, pageNumberX, pageNumberY, paint)
        Timber.v("Drew footer for page $pageNumber / $totalPages")
    }

    private fun getBeaconEntryHeight(beaconLocation: Location?, show15MinuteWarning: Boolean): Float {
        var lines = 3 // Time, Location Text, Signal Strength
        if (beaconLocation != null) {
            lines += 1 // Add line for Google Maps link
        }
        if (show15MinuteWarning) {
            lines += 1 // Add line for 15-minute warning
        }
        return lines * NORMAL_LINE_HEIGHT
    }

    private suspend fun calculateTotalPages(
        device: BaseDevice,
        beacons: List<Beacon>,
        locations: List<Location>
    ): Int = withContext(Dispatchers.Default) { // Use Default dispatcher for calculations
        var pageCount = 1
        var yPos = MARGIN
        val drawablePageHeight = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT - LINE_SPACING

        // --- Simulate Header ---
        yPos += TEXT_SIZE_HEADER + SECTION_SPACING

        // --- Simulate Basic Info ---
        // Approximate height: Title line + 5 lines of info + section spacing
        var basicInfoHeight = NORMAL_LINE_HEIGHT // Title
        basicInfoHeight += if (device.deviceType != DeviceType.SAMSUNG_TRACKER && device.deviceType != DeviceType.SAMSUNG_FIND_MY_MOBILE) 5 * NORMAL_LINE_HEIGHT else 4 * NORMAL_LINE_HEIGHT
        basicInfoHeight += SECTION_SPACING
        yPos += basicInfoHeight


        // --- Simulate Map ---
        val mapBitmap = createMapBitmap() // Need to create it to get dimensions
        val mapHeight = (mapBitmap.height * (PAGE_WIDTH - 2 * MARGIN) / mapBitmap.width).toFloat()
        mapBitmap.recycle()

        val mapSectionHeight = NORMAL_LINE_HEIGHT + mapHeight + SECTION_SPACING // Title + Map + Spacing

        if (yPos + mapSectionHeight > drawablePageHeight) {
            pageCount++
            yPos = MARGIN // Reset for new page
        }
        yPos += mapSectionHeight


        // --- Simulate Beacon Details ---
        val beaconListHeaderHeight = NORMAL_LINE_HEIGHT + LINE_SPACING // "Detections:" title + spacing
        if (yPos + beaconListHeaderHeight > drawablePageHeight) { // Check if header itself needs new page
            pageCount++
            yPos = MARGIN
        }
        yPos += beaconListHeaderHeight

        for (beacon in beacons) {
            val beaconLocation = locations.find { it.locationId == beacon.locationId }
            val beaconConnectionState: ConnectionState = Utility.getConnectionStateFromString(beacon.connectionState)
            val show15MinuteWarning = beaconConnectionState !in DeviceManager.unsafeConnectionState
            val entryHeight = getBeaconEntryHeight(beaconLocation, show15MinuteWarning) + LINE_SPACING // Add spacing between entries

            if (yPos + entryHeight > drawablePageHeight) {
                pageCount++
                yPos = MARGIN // Reset for new page
                // Add height for potential "Detections (Continued)" header on new page
                yPos += NORMAL_LINE_HEIGHT
            }
            yPos += entryHeight
        }

        pageCount
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1001
        private const val CREATE_FILE_REQUEST_CODE = 1002
        private var pdfBytes: ByteArray? = null
    }
}