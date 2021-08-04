package de.seemoo.at_tracking_detection.statistics.api

import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.relations.DeviceBeaconNotification
import de.seemoo.at_tracking_detection.statistics.api.models.Pong
import de.seemoo.at_tracking_detection.statistics.api.models.Token
import retrofit2.Response
import retrofit2.http.*

interface Api {
    @Headers("Authorization: Api-Key $API_KEY")
    @POST("donate_data")
    suspend fun donateData(
        @Header("token") token: String,
        @Body devices: List<DeviceBeaconNotification>
    ): Response<Void>

    @Headers("Authorization: Api-Key $API_KEY")
    @GET("get_token")
    suspend fun getToken(): Response<Token>

    @GET("ping")
    suspend fun ping(): Response<Pong>

    companion object {
        const val API_KEY = BuildConfig.API_KEY
    }
}