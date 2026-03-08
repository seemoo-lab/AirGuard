package de.seemoo.at_tracking_detection.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A custom ListPreference that uses Material3 styled dialogs
 * provides rounded corners, Material3 background colors, and font styling
 */
class Material3ListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : ListPreference(context, attrs, defStyleAttr, defStyleRes) {

    override fun onClick() {
        // Don't call super.onClick() to prevent default dialog
        showMaterial3Dialog()
    }

    private fun showMaterial3Dialog() {
        if (entries == null || entryValues == null) {
            return
        }

        val currentIndex = findIndexOfValue(value)

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(entries, currentIndex) { dialog, which ->
                val selectedValue = entryValues[which].toString()
                if (callChangeListener(selectedValue)) {
                    value = selectedValue
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

