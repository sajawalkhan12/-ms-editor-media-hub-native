
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
    val createdAt: String,
    val profileUsername: String = "",
    val profileDisplayName: String = "",
    val profileAvatarPath: String = ""
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

                    val parsed = parseMediaItems(response)
                    val ownerIds = parsed.map { it.createdBy }.filter { it.isNotBlank() }.distinct()
                    if (ownerIds.isEmpty()) parsed else {
                        val ids = ownerIds.joinToString(",")
                        val profileRows = JSONArray(restRequest("GET", "profiles?select=id,username,display_name,avatar_path&id=in.($ids)"))
                        val profileMap = mutableMapOf<String, JSONObject>()
                        for (index in 0 until profileRows.length()) {
                            val row = profileRows.getJSONObject(index)
                            profileMap[cleanJsonString(row, "id")] = row
                        }
                        parsed.map { item ->
                            val profile = profileMap[item.createdBy]
                            if (profile == null) item else item.copy(
                                profileUsername = cleanJsonString(profile, "username"),
                                profileDisplayName = cleanJsonString(profile, "display_name"),
                                profileAvatarPath = cleanJsonString(profile, "avatar_path")
                            )
                        }
                    }

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
                require(mediaType in listOf("video_songs", "video_tutorial", "video_prompt", "image_prompt", "presets_assets")) { "Category valid nahi hai." }
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



    fun isFollowing(token: String, followerId: String, followingId: String, callback: (Boolean) -> Unit) {
        executor.execute {
            val following = runCatching {
                val follower = URLEncoder.encode(followerId, "UTF-8")
                val target = URLEncoder.encode(followingId, "UTF-8")
                JSONArray(profileRequest(token, "GET", "user_follows?select=following_id&follower_id=eq.$follower&following_id=eq.$target")).length() > 0
            }.getOrDefault(false)
            mainHandler.post { callback(following) }
        }
    }

    fun toggleFollow(token: String, followerId: String, followingId: String, currentlyFollowing: Boolean, callback: (Result<Boolean>) -> Unit) {
        executor.execute {
            val result = runCatching {
                require(token.isNotBlank() && followerId.isNotBlank() && followingId.isNotBlank())
                require(followerId != followingId) { "Apne aap ko follow nahi kar sakte." }
                val follower = URLEncoder.encode(followerId, "UTF-8")
                val target = URLEncoder.encode(followingId, "UTF-8")
                if (currentlyFollowing) {
                    profileRequest(token, "DELETE", "user_follows?follower_id=eq.$follower&following_id=eq.$target")
                    false
                } else {
                    val body = JSONObject().put("follower_id", followerId).put("following_id", followingId).toString()
                    profileRequest(token, "POST", "user_follows", body)
                    true
                }
            }
            mainHandler.post { callback(result) }
        }
    }

    fun updateUserPost(token: String, userId: String, itemId: String, title: String, description: String, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = runCatching {
                require(token.isNotBlank() && userId.isNotBlank() && itemId.isNotBlank())
                val id = URLEncoder.encode(itemId, "UTF-8")
                val body = JSONObject().put("title", title.take(160)).put("description", description.take(2000)).toString()
                profileRequest(token, "PATCH", "media_items?id=eq.$id&created_by=eq.$userId", body)
                Unit
            }
            mainHandler.post { callback(result) }
        }
    }

    fun deleteUserPost(token: String, userId: String, item: MediaItem, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = runCatching {
                require(token.isNotBlank() && userId.isNotBlank()) { "Login session nahi mili." }
                require(item.id.isNotBlank() && item.createdBy == userId) { "Yeh post aapke account ki nahi hai." }
                require(item.storagePath.startsWith("user-posts/$userId/")) { "Sirf apni user post delete kar sakte hain." }

                // Remove only this user's object through the Storage API.
                val storageBody = JSONObject().put("prefixes", JSONArray().put(item.storagePath)).toString()
                profileRequest(token, "DELETE", "object/media", storageBody, storage = true)

                // Return the deleted row so an RLS-filtered/no-op delete is never reported as success.
                val id = URLEncoder.encode(item.id, "UTF-8")
                val encodedPath = URLEncoder.encode(item.storagePath, "UTF-8")
                val deletedRows = JSONArray(
                    profileRequest(
                        token,
                        "DELETE",
                        "media_items?id=eq.$id&created_by=eq.$userId&storage_path=eq." + encodedPath,
                        prefer = "return=representation"
                    )
                )
                if (deletedRows.length() == 0) {
                    throw Exception("Post database se delete nahi hui. Login dobara karein ya delete permission check karein.")
                }
                Unit
            }
            mainHandler.post { callback(result) }
        }
    }

    fun uploadUserPost(
        context: android.content.Context,
        token: String,
        userId: String,
        mediaUri: android.net.Uri,
        mediaKind: String,
        title: String,
        description: String,
        callback: (Result<Unit>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                require(token.isNotBlank() && userId.isNotBlank()) { "Login session nahi mili." }
                require(mediaKind == "image" || mediaKind == "video") { "Image ya video select karein." }
                val resolver = context.contentResolver
                val mime = resolver.getType(mediaUri) ?: "application/octet-stream"
                require(if (mediaKind == "image") mime.startsWith("image/") else mime.startsWith("video/")) {
                    "Selected file ka type sahi nahi hai."
                }
                val displayName = try {
                    resolver.query(mediaUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                } catch (_: Exception) { null } ?: if (mediaKind == "image") "image" else "video"
                val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val objectPath = "user-posts/$userId/${java.util.UUID.randomUUID()}_$safeName"
                val encoded = objectPath.split("/").joinToString("/") {
                    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
                val base = SupabaseConfig.SUPABASE_URL.trimEnd('/')
                val upload = URL("$base/storage/v1/object/media/$encoded").openConnection() as HttpURLConnection
                try {
                    upload.requestMethod = "POST"
                    upload.connectTimeout = 30000
                    upload.readTimeout = 180000
                    upload.doOutput = true
                    upload.setChunkedStreamingMode(64 * 1024)
                    upload.setRequestProperty("apikey", SupabaseConfig.SUPABASE_KEY.trim())
                    upload.setRequestProperty("Authorization", "Bearer $token")
                    upload.setRequestProperty("Content-Type", mime)
                    upload.setRequestProperty("x-upsert", "false")
                    resolver.openInputStream(mediaUri)?.use { input ->
                        upload.outputStream.use { output -> input.copyTo(output, 64 * 1024) }
                    } ?: throw Exception("Selected file read nahi ho saki.")
                    val uploadCode = upload.responseCode
                    val uploadResponse = (if (uploadCode in 200..299) upload.inputStream else upload.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (uploadCode !in 200..299) throw Exception("Storage HTTP $uploadCode: $uploadResponse")
                } finally { upload.disconnect() }

                val body = JSONObject()
                    .put("title", title.take(160))
                    .put("description", description.take(2000))
                    .put("media_type", if (mediaKind == "image") "image" else "movie")
                    .put("storage_path", objectPath)
                    .put("external_url", "")
                    .put("poster_path", "")
                    .put("created_by", userId)
                    .toString()
                val insert = URL("$base/rest/v1/media_items").openConnection() as HttpURLConnection
                try {
                    insert.requestMethod = "POST"
                    insert.connectTimeout = 30000
                    insert.readTimeout = 30000
                    insert.doOutput = true
                    insert.setRequestProperty("apikey", SupabaseConfig.SUPABASE_KEY.trim())
                    insert.setRequestProperty("Authorization", "Bearer $token")
                    insert.setRequestProperty("Content-Type", "application/json")
                    insert.setRequestProperty("Content-Profile", "public")
                    insert.setRequestProperty("Prefer", "return=minimal")
                    insert.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = insert.responseCode
                    val response = (if (code in 200..299) insert.inputStream else insert.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw Exception("Post record HTTP $code: $response")
                } finally { insert.disconnect() }
                Unit
            }
            mainHandler.post { callback(result) }
        }
    }

    private const val APPEARANCE_SETTINGS_PATH = "appearance/site-settings.json"

    private fun publicAppearanceUrl(path: String): String {
        val encoded = path.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return SupabaseConfig.SUPABASE_URL.trimEnd('/') +
            "/storage/v1/object/public/media/" + encoded
    }

    fun getAppLogo(callback: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val url = URL(publicAppearanceUrl(APPEARANCE_SETTINGS_PATH) + "?v=" + System.currentTimeMillis())
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.useCaches = false
                    connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_KEY.trim())
                    val code = connection.responseCode
                    if (code == 404) return@runCatching ""
                    if (code !in 200..299) throw Exception("Appearance settings HTTP $code")
                    val raw = connection.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(raw).optString("logo", "")
                } finally {
                    connection.disconnect()
                }
            }
            mainHandler.post { callback(result) }
        }
    }

    fun updateAppLogo(
        context: android.content.Context,
        session: AdminSession,
        imageUri: android.net.Uri,
        callback: (Result<String>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                require(session.accessToken.isNotBlank()) { "Admin session expire ho gayi. Dobara login karein." }
                val base = SupabaseConfig.SUPABASE_URL.trimEnd('/')
                val key = SupabaseConfig.SUPABASE_KEY.trim()
                val settingsUrl = URL(publicAppearanceUrl(APPEARANCE_SETTINGS_PATH) + "?v=" + System.currentTimeMillis())
                val settingsConnection = settingsUrl.openConnection() as HttpURLConnection
                val settings = try {
                    settingsConnection.requestMethod = "GET"
                    settingsConnection.connectTimeout = 15000
                    settingsConnection.readTimeout = 20000
                    settingsConnection.setRequestProperty("apikey", key)
                    val code = settingsConnection.responseCode
                    when {
                        code in 200..299 -> JSONObject(settingsConnection.inputStream.bufferedReader().use { it.readText() })
                        code == 404 -> JSONObject()
                        else -> throw Exception("Existing appearance settings HTTP $code")
                    }
                } finally {
                    settingsConnection.disconnect()
                }

                val resolver = context.contentResolver
                val mime = resolver.getType(imageUri) ?: "image/png"
                require(mime.startsWith("image/")) { "Sirf image file select karein." }
                val displayName = try {
                    resolver.query(imageUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                } catch (_: Exception) { null } ?: "app-logo.png"
                val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val logoPath = "appearance/${session.userId}-${System.currentTimeMillis()}-app-logo-$safeName"
                val encodedLogoPath = logoPath.split("/").joinToString("/") {
                    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
                val uploadImage = URL("$base/storage/v1/object/media/$encodedLogoPath").openConnection() as HttpURLConnection
                try {
                    uploadImage.requestMethod = "POST"
                    uploadImage.connectTimeout = 25000
                    uploadImage.readTimeout = 90000
                    uploadImage.doOutput = true
                    uploadImage.setRequestProperty("apikey", key)
                    uploadImage.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                    uploadImage.setRequestProperty("Content-Type", mime)
                    uploadImage.setRequestProperty("x-upsert", "false")
                    resolver.openInputStream(imageUri)?.use { input ->
                        uploadImage.outputStream.use { output -> input.copyTo(output, 64 * 1024) }
                    } ?: throw Exception("Logo image read nahi ho saki.")
                    val code = uploadImage.responseCode
                    val response = (if (code in 200..299) uploadImage.inputStream else uploadImage.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw Exception("Logo upload HTTP $code: $response")
                } finally {
                    uploadImage.disconnect()
                }

                val logoUrl = publicAppearanceUrl(logoPath)
                settings.put("logo", logoUrl)
                if (!settings.has("name")) settings.put("name", "MS Editor Media Hub")
                val settingsPath = APPEARANCE_SETTINGS_PATH.split("/").joinToString("/") {
                    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
                val saveSettings = URL("$base/storage/v1/object/media/$settingsPath").openConnection() as HttpURLConnection
                try {
                    saveSettings.requestMethod = "POST"
                    saveSettings.connectTimeout = 25000
                    saveSettings.readTimeout = 30000
                    saveSettings.doOutput = true
                    saveSettings.setRequestProperty("apikey", key)
                    saveSettings.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                    saveSettings.setRequestProperty("Content-Type", "application/json")
                    saveSettings.setRequestProperty("x-upsert", "true")
                    saveSettings.outputStream.use { it.write(settings.toString().toByteArray(Charsets.UTF_8)) }
                    val code = saveSettings.responseCode
                    val response = (if (code in 200..299) saveSettings.inputStream else saveSettings.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw Exception("Logo setting save HTTP $code: $response")
                } finally {
                    saveSettings.disconnect()
                }
                logoUrl
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

    data class UserProfile(
        val username: String = "",
        val displayName: String = "",
        val avatarPath: String = "",
        val coverPath: String = "",
        val bio: String = "",
        val location: String = "",
        val followers: Int = 0,
        val following: Int = 0
    )

    private fun profileRequest(token: String, method: String, path: String, body: String? = null, storage: Boolean = false, mime: String = "application/json", prefer: String? = null): String {
        val base = SupabaseConfig.SUPABASE_URL.trimEnd('/')
        val endpoint = if (storage) "$base/storage/v1/$path" else "$base/rest/v1/$path"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20000
            connection.readTimeout = 90000
            connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_KEY.trim())
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (!storage) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept-Profile", "public")
                connection.setRequestProperty("Content-Profile", "public")
                if (prefer != null) connection.setRequestProperty("Prefer", prefer)
                else if (body != null) connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
            } else if (body != null) {
                connection.setRequestProperty("Content-Type", mime)
                connection.setRequestProperty("x-upsert", "false")
            }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Exception("Profile request HTTP $code: $response")
            return response
        } finally { connection.disconnect() }
    }

    fun getUserProfile(token: String, userId: String, callback: (Result<UserProfile>) -> Unit) {
        executor.execute {
            val result = runCatching {
                require(token.isNotBlank() && userId.isNotBlank()) { "Login session nahi mili." }
                val id = URLEncoder.encode(userId, "UTF-8")
                val rows = JSONArray(profileRequest(token, "GET", "profiles?select=username,display_name,avatar_path,cover_path,bio,location&id=eq.$id"))
                val row = if (rows.length() > 0) rows.getJSONObject(0) else JSONObject()
                val followers = JSONArray(profileRequest(token, "GET", "user_follows?select=follower_id&following_id=eq.$id")).length()
                val following = JSONArray(profileRequest(token, "GET", "user_follows?select=following_id&follower_id=eq.$id")).length()
                UserProfile(
                    row.optString("username"), row.optString("display_name"),
                    row.optString("avatar_path"), row.optString("cover_path"),
                    row.optString("bio"), row.optString("location"), followers, following
                )
            }
            mainHandler.post { callback(result) }
        }
    }

    fun saveUserProfile(token: String, userId: String, bio: String, location: String, avatarPath: String, coverPath: String, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = runCatching {
                require(token.isNotBlank() && userId.isNotBlank()) { "Login session nahi mili." }
                val body = JSONObject()
                    .put("id", userId)
                    .put("bio", bio.take(500))
                    .put("location", location.take(100))
                    .put("avatar_path", avatarPath)
                    .put("cover_path", coverPath)
                profileRequest(token, "POST", "profiles?on_conflict=id", body.toString())
                Unit
            }
            mainHandler.post { callback(result) }
        }
    }

    fun uploadProfileImage(context: android.content.Context, token: String, userId: String, uri: android.net.Uri, kind: String, callback: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                require(kind == "avatar" || kind == "cover") { "Invalid profile image type." }
                val resolver = context.contentResolver
                val mime = resolver.getType(uri) ?: "image/jpeg"
                require(mime in listOf("image/jpeg", "image/png", "image/webp", "image/gif")) { "JPG, PNG, WEBP ya GIF image select karein." }
                val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
                val path = "$userId/$kind-${java.util.UUID.randomUUID()}.$ext"
                val encoded = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                val base = SupabaseConfig.SUPABASE_URL.trimEnd('/')
                val connection = URL("$base/storage/v1/object/profile-images/$encoded").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 20000
                    connection.readTimeout = 90000
                    connection.doOutput = true
                    connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_KEY.trim())
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", mime)
                    connection.setRequestProperty("x-upsert", "false")
                    resolver.openInputStream(uri)?.use { input -> connection.outputStream.use { output -> input.copyTo(output, 64 * 1024) } }
                        ?: throw Exception("Gallery image read nahi ho saki.")
                    val code = connection.responseCode
                    val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw Exception("Image upload HTTP $code: $response")
                } finally { connection.disconnect() }
                path
            }
            mainHandler.post { callback(result) }
        }
    }

    fun getProfileImageUrl(path: String): String {
        val clean = path.trim()
        if (clean.isBlank()) return ""
        if (clean.startsWith("https://") || clean.startsWith("http://")) return clean
        val encoded = clean.removePrefix("/").split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return SupabaseConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/public/profile-images/" + encoded
    }

}
