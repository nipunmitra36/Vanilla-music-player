package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class JamendoResponse(
    @Json(name = "headers") val headers: JamendoHeaders,
    @Json(name = "results") val results: List<JamendoTrack>
)

@JsonClass(generateAdapter = true)
data class JamendoHeaders(
    @Json(name = "status") val status: String,
    @Json(name = "code") val code: Int,
    @Json(name = "error_message") val errorMessage: String,
    @Json(name = "warnings") val warnings: String,
    @Json(name = "results_count") val resultsCount: Int
)

@JsonClass(generateAdapter = true)
data class JamendoTrack(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "duration") val duration: Int,
    @Json(name = "artist_id") val artistId: String,
    @Json(name = "artist_name") val artistName: String,
    @Json(name = "artist_idstr") val artistIdStr: String,
    @Json(name = "album_name") val albumName: String,
    @Json(name = "album_id") val albumId: String,
    @Json(name = "license_ccurl") val licenseCcurl: String,
    @Json(name = "position") val position: Int,
    @Json(name = "releasedate") val releaseDate: String,
    @Json(name = "album_image") val albumImage: String,
    @Json(name = "audio") val audio: String,
    @Json(name = "audiodownload") val audioDownload: String,
    @Json(name = "prourl") val proUrl: String,
    @Json(name = "shorturl") val shortUrl: String,
    @Json(name = "shareurl") val shareUrl: String,
    @Json(name = "image") val image: String
)

interface JamendoApiService {
    @GET("v3.0/tracks/")
    suspend fun getTracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 30,
        @Query("hasimage") hasImage: Boolean = true,
        @Query("tags") tags: String? = null,
        @Query("search") search: String? = null,
        @Query("boost") boost: String? = "popularity_week"
    ): JamendoResponse
}
