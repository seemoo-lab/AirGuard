package de.seemoo.at_tracking_detection.ui.settings

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Location
import de.seemoo.at_tracking_detection.database.models.Notification
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.LocationRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class RawDataExportFragment : Fragment() {

    @Inject lateinit var deviceRepository:      DeviceRepository
    @Inject lateinit var beaconRepository:      BeaconRepository
    @Inject lateinit var locationRepository:    LocationRepository
    @Inject lateinit var notificationRepository: NotificationRepository

    // --- State ---
    private var fromDateTime: LocalDateTime = LocalDateTime.now().minusDays(7).with(LocalTime.MIDNIGHT)
    private var toDateTime:   LocalDateTime = LocalDateTime.now()
    private var minDateTime:  LocalDateTime = LocalDateTime.now().minusYears(10)

    // --- Views ---
    private lateinit var presetChipGroup:  ChipGroup
    private lateinit var fromInputLayout:  TextInputLayout
    private lateinit var toInputLayout:    TextInputLayout
    private lateinit var fromEditText:     TextInputEditText
    private lateinit var toEditText:       TextInputEditText
    private lateinit var rangeErrorText:   View
    private lateinit var exportButton:     MaterialButton
    private lateinit var progressBar:      View

    private lateinit var displayFormatter: DateTimeFormatter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_raw_data_export, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        displayFormatter = if (DateFormat.is24HourFormat(requireContext()))
            DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm")
        else
            DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a")

        presetChipGroup  = view.findViewById(R.id.preset_chip_group)
        fromInputLayout  = view.findViewById(R.id.from_date_input_layout)
        toInputLayout    = view.findViewById(R.id.to_date_input_layout)
        fromEditText     = view.findViewById(R.id.from_date_edit_text)
        toEditText       = view.findViewById(R.id.to_date_edit_text)
        rangeErrorText   = view.findViewById(R.id.range_error_text)
        exportButton     = view.findViewById(R.id.export_raw_data_button)
        progressBar      = view.findViewById(R.id.raw_data_export_progress_bar)

        updateDateFields()

        view.findViewById<Chip>(R.id.chip_last_3_days).setOnClickListener  { applyPreset(3) }
        view.findViewById<Chip>(R.id.chip_last_7_days).setOnClickListener  { applyPreset(7) }
        view.findViewById<Chip>(R.id.chip_last_14_days).setOnClickListener { applyPreset(14) }
        view.findViewById<Chip>(R.id.chip_last_month).setOnClickListener   { applyPreset(30) }
        view.findViewById<Chip>(R.id.chip_all_time).setOnClickListener     { applyAllTime() }

        fromEditText.setOnClickListener           { showDateTimePicker(isFrom = true) }
        fromInputLayout.setEndIconOnClickListener { showDateTimePicker(isFrom = true) }
        toEditText.setOnClickListener             { showDateTimePicker(isFrom = false) }
        toInputLayout.setEndIconOnClickListener   { showDateTimePicker(isFrom = false) }

        exportButton.setOnClickListener { startExport() }

        loadMinDateTime()
    }

    private fun loadMinDateTime() {
        lifecycleScope.launch(Dispatchers.IO) {
            val earliest = beaconRepository.getEarliestAnyDate()
                ?: LocalDateTime.now().minusYears(10)
            withContext(Dispatchers.Main) {
                minDateTime = earliest
                if (fromDateTime.isBefore(minDateTime)) {
                    fromDateTime = minDateTime
                    updateDateFields()
                    validateRange()
                }
            }
        }
    }

    private fun applyPreset(days: Long) {
        toDateTime   = LocalDateTime.now()
        fromDateTime = LocalDate.now().minusDays(days).atStartOfDay()
            .let { if (it.isBefore(minDateTime)) minDateTime else it }
        updateDateFields()
        validateRange()
    }

    private fun applyAllTime() {
        toDateTime   = LocalDateTime.now()
        fromDateTime = minDateTime
        updateDateFields()
        validateRange()
    }

    private fun showDateTimePicker(isFrom: Boolean) {
        val current = if (isFrom) fromDateTime else toDateTime

        val lowerBoundMs = if (isFrom)
            minDateTime.toLocalDate().toUtcMidnightMillis()
        else
            fromDateTime.toLocalDate().toUtcMidnightMillis()

        val upperBoundMs = if (isFrom)
            toDateTime.toLocalDate().toUtcMidnightMillis()
        else
            LocalDate.now().toUtcMidnightMillis()

        val constraints = CalendarConstraints.Builder()
            .setStart(lowerBoundMs)
            .setEnd(upperBoundMs)
            .setValidator(
                CompositeDateValidator.allOf(listOf(
                    DateValidatorPointForward.from(lowerBoundMs),
                    DateValidatorPointBackward.before(upperBoundMs + DAY_MS)
                ))
            )
            .build()

        val titleRes = if (isFrom) R.string.raw_data_export_select_start_date
                       else       R.string.raw_data_export_select_end_date

        MaterialDatePicker.Builder.datePicker()
            .setTitleText(titleRes)
            .setSelection(current.toLocalDate().toUtcMidnightMillis())
            .setCalendarConstraints(constraints)
            .build()
            .also { picker ->
                picker.addOnPositiveButtonClickListener { selectedMs ->
                    val date = Instant.ofEpochMilli(selectedMs)
                        .atOffset(ZoneOffset.UTC).toLocalDate()
                    showTimePicker(isFrom, date)
                }
                picker.show(parentFragmentManager, "datePicker_$isFrom")
            }
    }

    private fun showTimePicker(isFrom: Boolean, date: LocalDate) {
        val current = if (isFrom) fromDateTime else toDateTime
        val is24h   = DateFormat.is24HourFormat(requireContext())
        val titleRes = if (isFrom) R.string.raw_data_export_select_start_time
                       else       R.string.raw_data_export_select_end_time

        MaterialTimePicker.Builder()
            .setTitleText(titleRes)
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTimeFormat(if (is24h) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .build()
            .also { picker ->
                picker.addOnPositiveButtonClickListener {
                    val newDateTime = LocalDateTime.of(date, LocalTime.of(picker.hour, picker.minute))
                    if (isFrom) fromDateTime = newDateTime else toDateTime = newDateTime
                    presetChipGroup.clearCheck()
                    updateDateFields()
                    validateRange()
                }
                picker.show(parentFragmentManager, "timePicker_$isFrom")
            }
    }

    private fun updateDateFields() {
        fromEditText.setText(fromDateTime.format(displayFormatter))
        toEditText.setText(toDateTime.format(displayFormatter))
    }

    private fun validateRange(): Boolean {
        val valid = fromDateTime.isBefore(toDateTime)
        rangeErrorText.visibility      = if (valid) View.GONE else View.VISIBLE
        fromInputLayout.isErrorEnabled = !valid
        toInputLayout.isErrorEnabled   = !valid
        if (!valid) {
            fromInputLayout.error = " "
            toInputLayout.error   = " "
        } else {
            fromInputLayout.error = null
            toInputLayout.error   = null
        }
        exportButton.isEnabled = valid
        return valid
    }

    private fun showLoading(show: Boolean) {
        exportButton.visibility = if (show) View.GONE else View.VISIBLE
        progressBar.visibility  = if (show) View.VISIBLE else View.GONE
    }

    private fun startExport() {
        if (!validateRange()) return
        // Open the system file picker; the actual generation only starts in onActivityResult
        val fromStr = fromDateTime.format(FILE_NAME_FORMATTER)
        val toStr   = toDateTime.format(FILE_NAME_FORMATTER)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE,
                "AirGuard Raw Data Export $fromStr to $toStr.zip")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CREATE_FILE_REQUEST_CODE) return

        val uri = data?.data ?: return // User canceled the picker

        val from = fromDateTime
        val to   = toDateTime
        val ctx  = requireContext().applicationContext

        showLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { outStream ->
                    ZipOutputStream(outStream).use { zip ->
                        generateAndStreamZip(zip, from, to)
                    }
                } ?: error("Could not open output stream for URI")

                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, R.string.raw_data_export_success, Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error writing raw data export ZIP")
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx,
                        ctx.getString(R.string.raw_data_export_save_error, e.message),
                        Toast.LENGTH_LONG).show()
                    showLoading(false)
                }
            }
        }
    }

    private suspend fun generateAndStreamZip(zip: ZipOutputStream, from: LocalDateTime, to: LocalDateTime) {
        Timber.d("Generating raw data export ZIP [%s → %s]", from, to)

        // Read all queries in parallel
        val (beacons, locations, notifications, devices) = coroutineScope {
            val dB = async { beaconRepository.getBeaconsInRange(from, to) }
            val dL = async { locationRepository.getLocationsForBeaconsInRange(from, to) }
            val dN = async { notificationRepository.getNotificationsInRange(from, to) }
            val dD = async { deviceRepository.getDevicesForBeaconsInRange(from, to) }
            listOf(dB.await(), dL.await(), dN.await(), dD.await())
        }

        @Suppress("UNCHECKED_CAST") // TODO: unelegant solution, maybe find better one than this cast
        Timber.d("Export counts – devices:%d beacons:%d locations:%d notifications:%d",
            (devices as List<BaseDevice>).size, (beacons as List<Beacon>).size,
            (locations as List<Location>).size, (notifications as List<Notification>).size)

        writeZipEntry(zip, "devices.csv")       { w -> writeDevicesRows(w, devices) }
        writeZipEntry(zip, "beacons.csv")       { w -> writeBeaconsRows(w, beacons) }
        writeZipEntry(zip, "locations.csv")     { w -> writeLocationsRows(w, locations) }
        writeZipEntry(zip, "notifications.csv") { w -> writeNotificationsRows(w, notifications) }
    }

    /** Opens a zip entry, provides a BufferedWriter for row writing, then flushes and closes the entry. */
    private inline fun writeZipEntry(zip: ZipOutputStream, name: String, block: (BufferedWriter) -> Unit) {
        zip.putNextEntry(ZipEntry(name))
        // Wrap zip stream in a BufferedWriter for efficient char→byte conversion.
        // Do not close the writer (that would close the ZipOutputStream).
        val writer = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8))
        block(writer)
        writer.flush()   // flush buffer → ZipOutputStream before closeEntry()
        zip.closeEntry()
    }

    private fun writeDevicesRows(w: BufferedWriter, devices: List<BaseDevice>) {
        // Hint: This needs to be updated every time the device table changes
        w.write(csvRow("deviceId","uniqueId","address","name","ignore","hearted",
            "connectable","payloadData","firstDiscovery","lastSeen",
            "notificationSent","lastNotificationSent","deviceType",
            "subDeviceType","riskLevel","safeTracker","alternativeIdentifier",
            "comment","matchedUsing15MinAlgo"))
        w.newLine()
        devices.forEach { d ->
            w.write(csvRow(
                d.deviceId.toString(), d.uniqueId ?: "", d.address, d.name ?: "",
                d.ignore.toString(), d.hearted.toString(),
                d.connectable?.toString() ?: "",
                d.payloadData?.let { "%02X".format(it) } ?: "",
                d.firstDiscovery.format(ISO_FORMATTER), d.lastSeen.format(ISO_FORMATTER),
                d.notificationSent.toString(),
                d.lastNotificationSent?.format(ISO_FORMATTER) ?: "",
                d.deviceType?.name ?: "", d.subDeviceType, d.riskLevel.toString(),
                d.safeTracker.toString(), d.alternativeIdentifier ?: "",
                d.comment ?: "", d.matchedUsing15MinAlgo.toString()
            ))
            w.newLine()
        }
    }

    private fun writeBeaconsRows(w: BufferedWriter, beacons: List<Beacon>) {
        // Hint: This needs to be updated every time the beacons table changes
        w.write(csvRow("beaconId","receivedAt","rssi","deviceAddress",
            "locationId","manufacturerData","serviceUUIDs","connectionState"))
        w.newLine()
        beacons.forEach { b ->
            w.write(csvRow(
                b.beaconId.toString(), b.receivedAt.format(ISO_FORMATTER),
                b.rssi.toString(), b.deviceAddress,
                b.locationId?.toString() ?: "",
                b.manufacturerData?.joinToString("") { "%02X".format(it) } ?: "",
                b.serviceUUIDs?.joinToString(";") ?: "",
                b.connectionState
            ))
            w.newLine()
        }
    }

    private fun writeLocationsRows(w: BufferedWriter, locations: List<Location>) {
        // Hint: This needs to be updated every time the locations table changes
        w.write(csvRow("locationId","name","firstDiscovery","lastSeen",
            "longitude","latitude","altitude","accuracy"))
        w.newLine()
        locations.forEach { l ->
            w.write(csvRow(
                l.locationId.toString(), l.name ?: "",
                l.firstDiscovery.format(ISO_FORMATTER), l.lastSeen.format(ISO_FORMATTER),
                l.longitude.toString(), l.latitude.toString(),
                l.altitude?.toString() ?: "", l.accuracy?.toString() ?: ""
            ))
            w.newLine()
        }
    }

    private fun writeNotificationsRows(w: BufferedWriter, notifications: List<Notification>) {
        // Hint: This needs to be updated every time the notifications table changes
        w.write(csvRow("notificationId","deviceAddress","falseAlarm",
            "dismissed","clicked","createdAt","sensitivity"))
        w.newLine()
        notifications.forEach { n ->
            w.write(csvRow(
                n.notificationId.toString(), n.deviceAddress,
                n.falseAlarm.toString(),
                n.dismissed?.toString() ?: "", n.clicked?.toString() ?: "",
                n.createdAt.format(ISO_FORMATTER), n.sensitivity.toString()
            ))
            w.newLine()
        }
    }

    /** RFC-4180 compliant field escaping. */
    private fun csvRow(vararg fields: String): String = fields.joinToString(",") { field ->
        val escaped = field.replace("\"", "\"\"")
        if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\""
        else escaped
    }

    private fun LocalDate.toUtcMidnightMillis(): Long =
        atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    companion object {
        private const val CREATE_FILE_REQUEST_CODE = 2001
        private const val DAY_MS = 86_400_000L
        private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private val FILE_NAME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
    }
}
