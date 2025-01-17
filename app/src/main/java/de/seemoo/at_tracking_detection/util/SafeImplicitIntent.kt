package de.seemoo.at_tracking_detection.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment
import de.seemoo.at_tracking_detection.R

inline fun Context.startActivitySafe(intent: Intent, onError: () -> Unit = { showNotAppFound() }) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        onError()
    }
}

fun Context.showNotAppFound() {
    val message = getString(R.string.app_handler_not_found)
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

inline fun Fragment.startActivitySafe(
    intent: Intent,
    onError: () -> Unit = { context?.showNotAppFound() }
) {
    context?.startActivitySafe(intent, onError)
}