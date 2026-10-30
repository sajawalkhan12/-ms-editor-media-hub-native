
package com.mseditor.mediahub

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class MediaItem(
    val id: String,
    val title: String,
    val description: String,
    val mediaType: String,
    val storagePath: String,
    val externalUrl: String,
    val posterPath: String,
    val createdBy: String,
    val createdAt: String
)

object SupabaseClient {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getMediaItems(
        callback: (Result<List<MediaItem>>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val endpoint =
                    SupabaseConfig.SUPABASE_URL +
                    "/rest/v1/media_items?select=*&order=created_at.desc"

                val connection =
                    URL(endpoint).openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 20000
                    connection.readTimeout = 20000
                    connection.setRequestProperty(
                        "apikey",
                        SupabaseConfig.SUPABASE_KEY
                    )
                    connection.setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    val responseCode = connection.responseCode

                    val response = if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use {
                            it.readText()
                        }
                    } else {
                        val errorText =
                            connection.errorStream
                                ?.bufferedReader()
                                ?.use { it.readText() }
                                ?: "Unknown server error"

                        throw Exception(
                            "Supabase error $responseCode: $errorText"
                        )
                    }

                    parseMediaItems(response)
                } finally {
                    connection.disconnect()
                }
            }

            mainHandler.post {
                callback(result)
            }
        }
    }

    private fun parseMediaItems(
        response: String
    ): List<MediaItem> {

        val jsonArray = JSONArray(response)
        val items = mutableListOf<MediaItem>()

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)

            items.add(
                MediaItem(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    description = item.optString("description"),
                    mediaType = item.optString("media_type"),
                    storagePath = item.optString("storage_path"),
                    externalUrl = item.optString("external_url"),
                    posterPath = item.optString("poster_path"),
                    createdBy = item.optString("created_by"),
                    createdAt = item.optString("created_at")
                )
            )
        }

        return items
    }

    fun getPublicMediaUrl(path: String): String {
        if (path.isBlank()) return ""

        val encodedPath = path.split("/")
            .joinToString("/") {
                java.net.URLEncoder.encode(
                    it,
                    "UTF-8"
                ).replace("+", "%20")
            }

        return SupabaseConfig.SUPABASE_URL +
            "/storage/v1/object/public/media/" +
            encodedPath
    }
}
