
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


    data class MediaComment(val id: String, val name: String, val text: String)
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
                val commentsJson = restRequest("GET", "media_comments?select=id,visitor_name,comment_text&media_id=eq.$id&order=created_at.asc")
                val commentsArray = JSONArray(commentsJson)
                val comments = mutableListOf<MediaComment>()
                for (i in 0 until commentsArray.length()) {
                    val row = commentsArray.getJSONObject(i)
                    comments.add(MediaComment(row.optString("id", ""), row.optString("visitor_name", "Visitor"), row.optString("comment_text", "")))
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

    private fun adminRequest(session: AdminSession, method: String, path: String, body: String? = null, storage: Boolean = false): String {
        val base = SupabaseConfig.SUPABASE_URL.trimEnd('/')
        val key = SupabaseConfig.SUPABASE_KEY.trim()
        val endpoint = if (storage) "$base/storage/v1/$path" else "$base/rest/v1/$path"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 25000
            connection.readTimeout = 25000
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("Accept", "application/json")
            if (!storage) {
                connection.setRequestProperty("Content-Profile", "public")
                connection.setRequestProperty("Accept-Profile", "public")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception("Admin request HTTP $code: $response")
            return response
        } finally { connection.disconnect() }
    }

    fun deleteMedia(session: AdminSession, item: MediaItem, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = runCatching {
                require(session.accessToken.isNotBlank()) { "Admin session expire ho gayi. Dobara login karein." }
                val id = URLEncoder.encode(item.id, "UTF-8")
                // Delete the database row first; if RLS rejects it, the media files remain untouched.
                adminRequest(session, "DELETE", "media_items?id=eq.$id")
                val paths = listOf(item.storagePath, item.posterPath)
                    .filter { it.isNotBlank() && !it.startsWith("http://") && !it.startsWith("https://") }
                paths.forEach { rawPath ->
                    val encoded = rawPath.removePrefix("/").split("/").joinToString("/") {
                        URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                    }
                    runCatching { adminRequest(session, "DELETE", "object/media/$encoded", storage = true) }
                }
                Unit
            }
            mainHandler.post { callback(result) }
        }
    }

    fun deleteComment(session: AdminSession, commentId: String, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = runCatching {
                require(session.accessToken.isNotBlank()) { "Admin session expire ho gayi. Dobara login karein." }
                require(commentId.isNotBlank()) { "Comment ID nahi mila." }
                val id = URLEncoder.encode(commentId, "UTF-8")
                adminRequest(session, "DELETE", "media_comments?id=eq.$id")
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

    private fun cleanJsonString(item: JSONObject, key: String): String {
        val value = item.opt(key)
        if (value == null || value == JSONObject.NULL) return ""
        val text = value.toString().trim()
        return if (text.equals("null", ignoreCase = true)) "" else text
    }

    private fun parseMediaItems(
        response: String
    ): List<MediaItem> {

        val jsonArray = JSONArray(response)
        val items = mutableListOf<MediaItem>()

        for (i in 0 until jsonArray.length()) {

            val item = jsonArray.getJSONObject(i)
            val promptText = cleanJsonString(item, "prompt_text")
                .ifBlank {
                    cleanJsonString(item, "prompt")
                }
                .ifBlank {
                    cleanJsonString(item, "prompt_content")
                }
                .ifBlank {
                    cleanJsonString(item, "promptText")
                }

            items.add(
                MediaItem(
                    id = cleanJsonString(item, "id"),
                    title = cleanJsonString(item, "title"),
                    description = cleanJsonString(item, "description"),
                    promptText = promptText,
                    mediaType = cleanJsonString(item, "media_type"),
                    storagePath = cleanJsonString(item, "storage_path"),
                    externalUrl = cleanJsonString(item, "external_url"),
                    posterPath = cleanJsonString(item, "poster_path"),
                    createdBy = cleanJsonString(item, "created_by"),
                    createdAt = cleanJsonString(item, "created_at")
                )
            )
        }

        return items
    }


    data class AdminSession(val accessToken: String, val userId: String)

    fun signInAndCheckAdmin(
        email: String,
        password: String,
        callback: (Result<AdminSession>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val base = SupabaseConfig.SUPABASE_URL.trim().trimEnd('/')
                val key = SupabaseConfig.SUPABASE_KEY.trim()
                require(email.isNotBlank()) { "Email enter karein." }
                require(password.isNotBlank()) { "Password enter karein." }
                val authConnection = URL("$base/auth/v1/token?grant_type=password")
                    .openConnection() as HttpURLConnection
                val authJson: JSONObject
                try {
                    authConnection.requestMethod = "POST"
                    authConnection.connectTimeout = 20000
                    authConnection.readTimeout = 20000
                    authConnection.doOutput = true
                    authConnection.setRequestProperty("apikey", key)
                    authConnection.setRequestProperty("Content-Type", "application/json")
                    authConnection.setRequestProperty("Accept", "application/json")
                    val body = JSONObject().put("email", email.trim()).put("password", password).toString()
                    authConnection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = authConnection.responseCode
                    val stream = if (code in 200..299) authConnection.inputStream else authConnection.errorStream
                    val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (code !in 200..299) {
                        throw Exception("Sign-in failed (HTTP $code): " + try {
                            JSONObject(raw).optString("msg").ifBlank { JSONObject(raw).optString("message") }.ifBlank { "Email/password check karein." }
                        } catch (_: Exception) { "Email/password check karein." })
                    }
                    authJson = JSONObject(raw)
                } finally {
                    authConnection.disconnect()
                }

                val token = authJson.optString("access_token")
                val userId = authJson.optJSONObject("user")?.optString("id").orEmpty()
                require(token.isNotBlank() && userId.isNotBlank()) { "Supabase session details nahi mile." }

                val rpc = URL("$base/rest/v1/rpc/is_media_admin").openConnection() as HttpURLConnection
                try {
                    rpc.requestMethod = "POST"
                    rpc.connectTimeout = 20000
                    rpc.readTimeout = 20000
                    rpc.doOutput = true
                    rpc.setRequestProperty("apikey", key)
                    rpc.setRequestProperty("Authorization", "Bearer $token")
                    rpc.setRequestProperty("Content-Type", "application/json")
                    rpc.setRequestProperty("Accept", "application/json")
                    rpc.setRequestProperty("Content-Profile", "public")
                    rpc.setRequestProperty("Accept-Profile", "public")
                    rpc.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                    val code = rpc.responseCode
                    val stream = if (code in 200..299) rpc.inputStream else rpc.errorStream
                    val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (code !in 200..299) throw Exception("Admin permission check failed (HTTP $code): $raw")
                    val allowed = raw.trim().equals("true", true) ||
                        (try { JSONObject(raw).optBoolean("is_media_admin") } catch (_: Exception) { false })
                    if (!allowed) throw Exception("Login ho gaya, lekin is account ko Media Admin permission nahi mili.")
                } finally {
                    rpc.disconnect()
                }
                AdminSession(token, userId)
            }
            mainHandler.post { callback(result) }
        }
    }


    fun uploadMedia(
        context: android.content.Context,
        session: AdminSession,
        mediaUri: android.net.Uri,
        posterUri: android.net.Uri?,
        title: String,
        description: String,
        mediaType: String,
        callback: (Result<Unit>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                require(mediaType in listOf("movie", "trailer", "image", "audio")) { "Category valid nahi hai." }
                val base = SupabaseConfig.SUPABASE_URL.trimEnd('/')
                val key = SupabaseConfig.SUPABASE_KEY.trim()
                fun uploadObject(uri: android.net.Uri, folder: String): String {
                    val resolver = context.contentResolver
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    val displayName = try {
                        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    } catch (_: Exception) { null } ?: "upload.bin"
                    val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val objectPath = "$folder/${java.util.UUID.randomUUID()}_$safeName"
                    val encoded = objectPath.split("/").joinToString("/") {
                        URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                    }
                    val connection = URL("$base/storage/v1/object/media/$encoded").openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 30000
                        connection.readTimeout = 180000
                        connection.doOutput = true
                        connection.setChunkedStreamingMode(64 * 1024)
                        connection.setRequestProperty("apikey", key)
                        connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                        connection.setRequestProperty("Content-Type", mime)
                        connection.setRequestProperty("x-upsert", "false")
                        resolver.openInputStream(uri)?.use { input ->
                            connection.outputStream.use { output -> input.copyTo(output, 64 * 1024) }
                        } ?: throw Exception("Selected file read nahi ho saki.")
                        val code = connection.responseCode
                        val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                            ?.bufferedReader()?.use { it.readText() } ?: ""
                        if (code !in 200..299) throw Exception("Storage HTTP $code: $raw")
                    } finally { connection.disconnect() }
                    return objectPath
                }
                val mediaPath = uploadObject(mediaUri, "media")
                val posterPath = if (posterUri != null) uploadObject(posterUri, "posters") else ""
                val body = JSONObject()
                    .put("title", title)
                    .put("description", description)
                    .put("media_type", mediaType)
                    .put("storage_path", mediaPath)
                    .put("external_url", "")
                    .put("poster_path", posterPath)
                    .put("created_by", session.userId)
                    .toString()
                val insert = URL("$base/rest/v1/media_items").openConnection() as HttpURLConnection
                try {
                    insert.requestMethod = "POST"
                    insert.connectTimeout = 30000
                    insert.readTimeout = 30000
                    insert.doOutput = true
                    insert.setRequestProperty("apikey", key)
                    insert.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                    insert.setRequestProperty("Content-Type", "application/json")
                    insert.setRequestProperty("Content-Profile", "public")
                    insert.setRequestProperty("Prefer", "return=minimal")
                    insert.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = insert.responseCode
                    val raw = (if (code in 200..299) insert.inputStream else insert.errorStream)
                        ?.bufferedReader()?.use { it.readText() } ?: ""
                    if (code !in 200..299) throw Exception("Media record HTTP $code: $raw")
                } finally { insert.disconnect() }
                Unit
            }
            mainHandler.post { callback(result) }
        }
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
