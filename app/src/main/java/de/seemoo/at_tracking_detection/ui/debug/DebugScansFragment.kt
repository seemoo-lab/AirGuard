package de.seemoo.at_tracking_detection.ui.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.databinding.FragmentDebugScansBinding
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@AndroidEntryPoint
class DebugScansFragment: Fragment() {

    private val viewModel: DebugScanViewModel by viewModels()
    private var binding: FragmentDebugScansBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_debug_scans, container, false)

//        binding?.lifecycleOwner = viewLifecycleOwner
//        binding?.vm = viewModel

        val scansView = binding?.root?.findViewById<ComposeView>(R.id.debug_scans_view)

        scansView?.setContent {
            MdcTheme {
                LastScanList()
            }
        }

        return binding?.root
    }

    @Composable
    private fun Test() {
        Text(
            text = stringResource(id = R.string.last_scans),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dp(8.0F))
                .wrapContentWidth(Alignment.CenterHorizontally)

        )
    }

    @Composable
    private fun DateText(scan: Scan) {
        Row {
            if (scan.startDate != null) {
                Text(text = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(scan.startDate))
            }
            if (scan.endDate != null) {
                Text(" - ")
                Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(scan.endDate))
            }
        }

    }

    @Composable
    private fun LastScanList() {
        Column {
            Test()
            LazyColumn {
                items(viewModel.scans) { scan ->
                    Row (Modifier.padding(horizontal = Dp(8.0F))) {
                        Column {
                            DateText(scan = scan)
                            Text(text = "| ${scan.duration} | Found: ${scan.noDevicesFound} | Mode: ${scan.scanMode} | ${scan.isManual}")
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

