package de.seemoo.at_tracking_detection.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.LocationRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class FoundTrackersExport : Fragment() {
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var beaconRepository: BeaconRepository
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var notificationRepository: NotificationRepository

    private lateinit var exportDocumentButton: Button
    private lateinit var progressBar: ProgressBar

    data class PageContext(
        val page: PdfDocument.Page,
        val canvas: Canvas,
        val yPos: Float
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_found_trackers_export, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        exportDocumentButton = view.findViewById(R.id.create_document_button)
        progressBar = view.findViewById(R.id.progress_bar)

        val exportDocumentButton = view.findViewById<Button>(R.id.create_document_button)
        exportDocumentButton.setOnClickListener {
            checkPermissionsAndGeneratePdf()
        }
    }

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

    private suspend fun generatePdfContent(): ByteArray = withContext(Dispatchers.IO) {
        Timber.d("Generating PDF content")
        val outputStream = ByteArrayOutputStream()
        val pdfDocument = PdfDocument()
        try {
            val initialPage = createNewPage(pdfDocument)
            var pageContext = PageContext(initialPage, initialPage.canvas, 50f)

            val devicesWithNotifications = notificationRepository.getAllNotifications()
                .mapNotNull { notification ->
                    deviceRepository.getDevice(notification.deviceAddress)
                }.distinctBy { it.address }

            devicesWithNotifications.forEach { device ->
                pageContext = drawDeviceHeader(device, pageContext, pdfDocument)
                pageContext = drawNotifications(device, pageContext, pdfDocument)
                pageContext = drawBeacons(device, pageContext, pdfDocument)
                pageContext = pageContext.copy(yPos = pageContext.yPos + 30f)
            }

            pdfDocument.finishPage(pageContext.page)
            pdfDocument.writeTo(outputStream)
            outputStream.toByteArray()
        } finally {
            pdfDocument.close()
            outputStream.close()
        }
    }

    private fun createNewPage(pdfDocument: PdfDocument): PdfDocument.Page {
        Timber.d("Creating new PDF page")
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        return pdfDocument.startPage(pageInfo)
    }

    private fun drawDeviceHeader(
        device: BaseDevice,
        context: PageContext,
        pdfDocument: PdfDocument
    ): PageContext {
        Timber.d("Drawing device header for ${device.name ?: device.address}")
        var (currentPage, currentCanvas, currentYPos) = context

        val paint = Paint().apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }

        var yPos = currentYPos
        if (yPos > 800) {
            pdfDocument.finishPage(currentPage)
            currentPage = createNewPage(pdfDocument)
            currentCanvas = currentPage.canvas
            yPos = 50f
        }

        currentCanvas.drawText(
            "Device: ${device.name ?: device.address}",
            50f,
            yPos,
            paint
        )
        return PageContext(currentPage, currentCanvas, yPos + 30f)
    }

    private fun drawNotifications(
        device: BaseDevice,
        context: PageContext,
        pdfDocument: PdfDocument
    ): PageContext {
        Timber.d("Drawing notifications for ${device.name ?: device.address}")
        var (currentPage, currentCanvas, currentYPos) = context

        val paint = Paint().apply {
            textSize = 12f
            color = 0xFF666666.toInt()
        }

        val notifications = notificationRepository.notificationForDevice(device)

        var yPos = currentYPos
        if (notifications.isNotEmpty()) {
            currentCanvas.drawText("Notifications:", 50f, yPos, paint)
            yPos += 20f
        }

        notifications.forEach { notification ->
            if (yPos > 750) {
                pdfDocument.finishPage(currentPage)
                currentPage = createNewPage(pdfDocument)
                currentCanvas = currentPage.canvas
                yPos = 50f
            }
            currentCanvas.drawText(
                "• ${notification.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}",
                60f,
                yPos,
                paint
            )
            yPos += 20f
        }
        return PageContext(currentPage, currentCanvas, yPos)
    }

    private fun drawBeacons(
        device: BaseDevice,
        context: PageContext,
        pdfDocument: PdfDocument
    ): PageContext {
        Timber.d("Drawing beacons for ${device.name ?: device.address}")
        var (currentPage, currentCanvas, currentYPos) = context

        val headerPaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val textPaint = Paint().apply {
            textSize = 12f
            color = 0xFF444444.toInt()
        }

        val beacons = beaconRepository.getDeviceBeacons(device.address)

        var yPos = currentYPos
        if (beacons.isNotEmpty()) {
            currentCanvas.drawText("Beacon Data:", 50f, yPos, headerPaint)
            yPos += 25f
        }

        beacons.forEach { beacon ->
            if (yPos > 750) {
                pdfDocument.finishPage(currentPage)
                currentPage = createNewPage(pdfDocument)
                currentCanvas = currentPage.canvas
                yPos = 50f
            }
            currentCanvas.drawText(
                "▸ ${beacon.receivedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}",
                50f,
                yPos,
                textPaint
            )
            yPos += 20f
            currentCanvas.drawText("   RSSI: ${beacon.rssi} dBm", 60f, yPos, textPaint)
            currentCanvas.drawText("   State: ${beacon.connectionState}", 60f, yPos + 15f, textPaint)
            yPos += 35f

            beacon.locationId?.let { locationId ->
                locationRepository.getLocationWithId(locationId)?.let { location ->
                    currentCanvas.drawText("   Location:", 60f, yPos, textPaint)
                    currentCanvas.drawText("     Lat: ${"%.6f".format(location.latitude)}", 70f, yPos + 15f, textPaint)
                    currentCanvas.drawText("     Lon: ${"%.6f".format(location.longitude)}", 70f, yPos + 30f, textPaint)
                    currentCanvas.drawText("     Alt: ${location.altitude?.let { "%.1f m".format(it) } ?: "N/A"}",
                        70f, yPos + 45f, textPaint)
                    currentCanvas.drawText("     Accuracy: ${location.accuracy?.let { "%.1f m".format(it) } ?: "N/A"}",
                        70f, yPos + 60f, textPaint)
                    yPos += 80f
                } ?: run {
                    currentCanvas.drawText("   No location data", 60f, yPos, textPaint)
                    yPos += 20f
                }
            } ?: run {
                currentCanvas.drawText("   No location data", 60f, yPos, textPaint)
                yPos += 20f
            }
            yPos += 15f
        }
        return PageContext(currentPage, currentCanvas, yPos)
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

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1001
        private const val CREATE_FILE_REQUEST_CODE = 1002
        private var pdfBytes: ByteArray? = null
    }

}