package de.seemoo.at_tracking_detection.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.MultiSelectListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A custom MultiSelectListPreference that uses Material3 styled dialogs
 * provides rounded corners, Material3 background colors, and font styling.
 */
class Material3MultiSelectListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : MultiSelectListPreference(context, attrs, defStyleAttr, defStyleRes) {

    override fun onClick() {
        // Don't call super.onClick() to prevent default dialog
        showMaterial3Dialog()
    }

    private fun showMaterial3Dialog() {
        if (entries == null || entryValues == null) {
            return
        }

        // Get current selections
        val currentValues = values ?: emptySet()
        val selectedItems = BooleanArray(entries.size) { index ->
            entryValues[index].toString() in currentValues
        }

        // Create a mutable copy to track changes
        val tempSelectedItems = selectedItems.clone()

        MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle ?: title)
            .setMultiChoiceItems(entries, tempSelectedItems) { _, which, isChecked ->
                tempSelectedItems[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                // Build the new set of selected values
                val newValues = mutableSetOf<String>()
                tempSelectedItems.forEachIndexed { index, isSelected ->
                    if (isSelected) {
                        newValues.add(entryValues[index].toString())
                    }
                }

                if (callChangeListener(newValues)) {
                    values = newValues
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

