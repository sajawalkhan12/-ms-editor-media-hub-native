package com.mseditor.mediahub

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object PushTokenRegistrar {
    private val executor = Executors.newSingleThreadExecutor()

    fun unregister(context: Context, fcmToken: String) {
        if (fcmToken.isBlank()) return
        val prefs = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", "").orEmpty()
        val userId = prefs.getString("user_id", "").orEmpty()
        if (accessToken.isBlank() || userId.isBlank()) return
        executor.execute {
            runCatching {
                val tokenQuery = java.net.URLEncoder.encode(fcmToken, "UTF-8")
                val userQuery = java.net.URLEncoder.encode(userId, "UTF-8")
                val endpoint = SupabaseConfig.SUPABASE_URL.trimEnd('/') +
                    "/rest/v1/user_push_tokens?fcm_token=eq.$tokenQuery&user_id=eq.$userQuery"
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "DELETE"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_KEY.trim())
                    connection.setRequestProperty("Authorization", "Bearer $accessToken")
                    connection.setRequestProperty("Prefer", "return=minimal")
                    val code = connection.responseCode
                    if (code !in 200..299) Log.w("PushTokenRegistrar", "Token removal HTTP $code")
                } finally { connection.disconnect() }
            }.onFailure { Log.w("PushTokenRegistrar", "Token removal failed", it) }
        }
    }

    fun register(context: Context, fcmToken: String) {
        if (fcmToken.isBlank()) return
        val prefs = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", "").orEmpty()
        val userId = prefs.getString("user_id", "").orEmpty()
        if (accessToken.isBlank() || userId.isBlank()) return

        executor.execute {
            runCatching {
                val endpoint = SupabaseConfig.SUPABASE_URL.trimEnd('/') +
                    "/rest/v1/user_push_tokens?on_conflict=fcm_token"
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.doOutput = true
                    connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_KEY.trim())
                    connection.setRequestProperty("Authorization", "Bearer $accessToken")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                    val body = JSONObject()
                        .put("user_id", userId)
                        .put("fcm_token", fcmToken)
                        .put("device_name", Build.MODEL.ifBlank { "Android" })
                        .put("updated_at", java.time.Instant.now().toString())
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        Log.w("PushTokenRegistrar", "Token registration HTTP $code: $error")
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { Log.w("PushTokenRegistrar", "Token registration failed", it) }
        }
    }
}
