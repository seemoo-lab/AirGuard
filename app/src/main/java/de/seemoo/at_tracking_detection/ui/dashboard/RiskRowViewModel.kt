package de.seemoo.at_tracking_detection.ui.dashboard

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


class RiskRowViewModel constructor(
    val text: String,
    val image: Drawable
) {}