package de.seemoo.at_tracking_detection.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment
import de.seemoo.at_tracking_detection.R

inline fun Context.startActivitySafe(intent: Intent, onError: () -> Unit = { showNotAppFound() }) {
    if (intent.resolveActivity(packageManager) != null)
        startActivity(intent)
    else
        onError()
}

fun Context.showNotAppFound() {
    val message = getString(R.string.app_handler_not_found)
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Fragment.startActivitySafe(intent: Intent, onError: (() -> Unit)? = null) {
    val context = requireContext()
    context.startActivitySafe(intent, onError ?: { context.showNotAppFound() })
}