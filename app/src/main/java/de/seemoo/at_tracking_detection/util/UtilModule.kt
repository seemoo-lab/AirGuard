package de.seemoo.at_tracking_detection.util

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UtilModule {

    @Provides
    fun provideBuildVersionProvider(): BuildVersionProvider {
        return DefaultBuildVersionProvider()
    }
}