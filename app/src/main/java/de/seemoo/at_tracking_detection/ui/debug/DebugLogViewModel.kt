package de.seemoo.at_tracking_detection.ui.debug

import android.text.Editable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.bipi.tressence.file.FileLoggerTree
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DebugLogViewModel @Inject constructor(): ViewModel() {
    private var fullLogText: List<String>
    var logText: MutableLiveData<String> = MutableLiveData()
    var filterText: MutableLiveData<String> = MutableLiveData()

    private val logFile: File

    init {
        val trees = Timber.forest()
        val fileLogTree: FileLoggerTree = trees.firstOrNull { it is FileLoggerTree } as FileLoggerTree
        val filename = fileLogTree.getFileName(0)
        val file = File(filename)
        logFile = file

        fullLogText = if (file.exists()) {
            file.readLines()
        }else {
            arrayListOf("No log file found")
        }

        logText.postValue(fullLogText.joinToString("\n"))

    }

    fun filterChanged(s: Editable) {
        val filteredLines =
            fullLogText.filter { it.lowercase().contains(s.toString().lowercase()) }
        logText.postValue(filteredLines.joinToString("\n"))
    }

    fun clearLogs() {
        logFile.delete()
        logFile.createNewFile()
        fullLogText = arrayListOf("")
        logText.postValue("")
    }
}