package de.seemoo.at_tracking_detection.statistics.api

import android.os.Build.VERSION
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.relations.DeviceBeaconNotification
import de.seemoo.at_tracking_detection.statistics.api.models.Pong
import de.seemoo.at_tracking_detection.statistics.api.models.Token
import retrofit2.Response
import retrofit2.http.*
import java.util.*

interface Api {


    @Headers("Authorization: Api-Key $API_KEY")
    @POST("donate_data")
    suspend fun donateData(
        @Header("token") token: String,
        @Header("X-Timezone") timezone: String = TIME_ZONE,
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Body devices: List<DeviceBeaconNotification>
    ): Response<Void>

    @Headers("Authorization: Api-Key $API_KEY")
    @GET("get_token")
    suspend fun getToken(
        @Header("X-Timezone") timezone: String = TIME_ZONE,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): Response<Token>

    /**
     * Deletes the study data related to the given token.
     */
    @Headers("Authorization: Api-Key $API_KEY")
    @DELETE("delete_study_data")
    suspend fun deleteStudyData(
        @Header("token") token: String,
        @Header("X-Timezone") timezone: String = TIME_ZONE,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): Response<Void>

    @GET("ping")
    suspend fun ping(
        @Header("X-Timezone") timezone: String = TIME_ZONE,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): Response<Pong>

    companion object {
        const val API_KEY = BuildConfig.API_KEY
        val TIME_ZONE: String = TimeZone.getDefault().getDisplayName(TimeZone.getDefault().inDaylightTime(Date()), TimeZone.SHORT, Locale.US)
        val USER_AGENT = "AirGuard/${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}); Android ${VERSION.RELEASE}"
    }
}