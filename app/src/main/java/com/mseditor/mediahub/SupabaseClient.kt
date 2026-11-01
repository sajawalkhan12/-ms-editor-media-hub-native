
package com.mseditor.mediahub

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class MediaItem(
    val id: String,
    val title: String,
    val description: String,
    val promptText: String,
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

                val baseUrl = SupabaseConfig.SUPABASE_URL.trim().trimEnd('/')
                val apiKey = SupabaseConfig.SUPABASE_KEY.trim()

                require(baseUrl.startsWith("https://")) {
                    "Supabase URL sahi nahi hai."
                }

                require(apiKey.isNotBlank()) {
                    "Supabase publishable key khali hai."
                }

                val endpoint =
                    "$baseUrl/rest/v1/media_items?select=*&order=created_at.desc"

                val connection =
                    URL(endpoint).openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    connection.useCaches = false

                    connection.setRequestProperty("apikey", apiKey)
                    connection.setRequestProperty(
                        "Authorization",
                        "Bearer $apiKey"
                    )
                    connection.setRequestProperty(
                        "Accept",
                        "application/json"
                    )
                    connection.setRequestProperty(
                        "Accept-Profile",
                        "public"
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
                                ?: "No error details returned"

                        throw Exception(
                            "Supabase HTTP $responseCode: $errorText"
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


    data class MediaComment(val name: String, val text: String)
    data class EngagementSummary(val likes: Int, val likedByMe: Boolean, val comments: List<MediaComment>)

    private fun restRequest(method: String, path: String, body: String? = null): String {
        val base = SupabaseConfig.SUPABASE_URL.trimEnd('/')
        val key = SupabaseConfig.SUPABASE_KEY.trim()
        val connection = URL("$base/rest/v1/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20000
            connection.readTimeout = 20000
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept-Profile", "public")
            connection.setRequestProperty("Content-Profile", "public")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Prefer", "return=minimal")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception("Supabase HTTP $code: $response")
            return response
        } finally {
            connection.disconnect()
        }
    }

    fun getEngagement(mediaId: String, visitorId: String, callback: (Result<EngagementSummary>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val id = URLEncoder.encode(mediaId, "UTF-8")
                val likesJson = restRequest("GET", "media_likes?select=visitor_id&media_id=eq.$id")
                val likes = JSONArray(likesJson)
                var liked = false
                for (i in 0 until likes.length()) {
                    if (likes.getJSONObject(i).optString("visitor_id") == visitorId) liked = true
                }
                val commentsJson = restRequest("GET", "media_comments?select=visitor_name,comment_text&media_id=eq.$id&order=created_at.asc")
                val commentsArray = JSONArray(commentsJson)
                val comments = mutableListOf<MediaComment>()
                for (i in 0 until commentsArray.length()) {
                    val row = commentsArray.getJSONObject(i)
                    comments.add(MediaComment(row.optString("visitor_name", "Visitor"), row.optString("comment_text", "")))
                }
                EngagementSummary(likes.length(), liked, comments)
            }
            mainHandler.post { callback(result) }
        }
    }

    fun toggleLike(mediaId: String, visitorId: String, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val id = URLEncoder.encode(mediaId, "UTF-8")
                val who = URLEncoder.encode(visitorId, "UTF-8")
                val path = "media_likes?select=id&media_id=eq.$id&visitor_id=eq.$who"
                val existing = JSONArray(restRequest("GET", path))
                if (existing.length() > 0) {
                    restRequest("DELETE", "media_likes?media_id=eq.$id&visitor_id=eq.$who")
                } else {
                    val body = JSONObject().put("media_id", mediaId).put("visitor_id", visitorId).toString()
                    restRequest("POST", "media_likes", body)
                }
                Unit
            }
            mainHandler.post { callback(result) }
        }
    }

    fun addComment(mediaId: String, visitorName: String, comment: String, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val body = JSONObject()
                    .put("media_id", mediaId)
                    .put("visitor_name", visitorName.take(60))
                    .put("comment_text", comment.take(1000))
                    .toString()
                restRequest("POST", "media_comments", body)
                Unit
            }
            mainHandler.post { callback(result) }
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
                    id = item.optString("id", ""),
                    title = item.optString("title", ""),
                    description = item.optString("description", ""),
                    promptText = item.optString("prompt_text", "").ifBlank {
                        item.optString("prompt", "").ifBlank {
                            item.optString("prompt_content", "").ifBlank {
                                item.optString("promptText", "")
                            }
                        }
                    },
                    mediaType = item.optString("media_type", ""),
                    storagePath = item.optString("storage_path", ""),
                    externalUrl = item.optString("external_url", ""),
                    posterPath = item.optString("poster_path", ""),
                    createdBy = item.optString("created_by", ""),
                    createdAt = item.optString("created_at", "")
                )
            )
        }

        return items
    }

    fun getPublicMediaUrl(path: String): String {

        val cleanPath = path.trim()

        if (cleanPath.isBlank()) return ""

        // Already a complete URL: do not build it again.
        if (
            cleanPath.startsWith("https://", true) ||
            cleanPath.startsWith("http://", true)
        ) {
            return cleanPath
        }

        val encodedPath = cleanPath
            .removePrefix("/")
            .split("/")
            .joinToString("/") { part ->
                URLEncoder.encode(part, "UTF-8")
                    .replace("+", "%20")
            }

        return SupabaseConfig.SUPABASE_URL.trimEnd('/') +
            "/storage/v1/object/public/media/" +
            encodedPath
    }
}
