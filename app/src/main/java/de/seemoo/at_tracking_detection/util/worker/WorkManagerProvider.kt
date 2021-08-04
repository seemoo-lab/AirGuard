package de.seemoo.at_tracking_detection.util.worker

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerProvider @Inject constructor(@ApplicationContext context: Context) {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    val workManager by lazy {
        val configuration = Configuration.Builder().apply {
            setMinimumLoggingLevel(android.util.Log.DEBUG)
            setWorkerFactory(workerFactory)
        }.build()

        Timber.d("WorkManager initialize...")
        try {
            WorkManager.initialize(context, configuration)
        } catch (e: IllegalStateException) {
            Timber.e("WorkManager already initialized... skipping!")
        }
        WorkManager.getInstance(context).also {
            Timber.d("WorkManager setup done: %s", it)
        }
    }
}