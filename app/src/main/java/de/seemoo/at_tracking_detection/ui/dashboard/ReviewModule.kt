package de.seemoo.at_tracking_detection.ui.dashboard

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReviewModule {

    @Provides
    @Singleton
    fun provideReviewController(@ApplicationContext context: Context): ReviewController {
        return ReviewController(context)
    }
}