package com.mseditor.mediahub

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Free on-device UI text translation.
 * Source UI strings are English. Models are downloaded on demand and cached by ML Kit.
 */
object AppTranslator {
    private data class Pending(val source: String, val callback: (String) -> Unit)
    private val pending = mutableListOf<Pending>()
    private var translator: Translator? = null
    private var languageCode = "en"
    private var modelReady = false
    private var loading = false

    fun normalizeCode(code: String): String = when (code.lowercase()) {
        "fil" -> "tl"
        "pt-br" -> "pt"
        "zh-cn", "zh-tw" -> "zh"
        else -> code.lowercase()
    }

    fun configure(context: Context, requestedCode: String) {
        val targetCode = normalizeCode(requestedCode)
        if (targetCode == languageCode && (targetCode == "en" || modelReady || loading)) return
        translator?.close()
        translator = null
        modelReady = false
        loading = false
        languageCode = targetCode
        synchronized(pending) { pending.clear() }
        if (targetCode == "en") return

        val target = TranslateLanguage.fromLanguageTag(targetCode) ?: return
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(target)
            .build()
        val client = Translation.getClient(options)
        translator = client
        loading = true
        // Translation is free; the language model download uses device data.
        client.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
            .addOnSuccessListener {
                if (translator !== client) return@addOnSuccessListener
                modelReady = true
                loading = false
                val queued = synchronized(pending) { pending.toList().also { pending.clear() } }
                queued.forEach { translateNow(client, it.source, it.callback) }
            }
            .addOnFailureListener {
                if (translator === client) {
                    loading = false
                    val queued = synchronized(pending) { pending.toList().also { pending.clear() } }
                    queued.forEach { it.callback(it.source) }
                }
            }
    }

    fun translate(source: String, callback: (String) -> Unit) {
        if (source.isBlank() || languageCode == "en") {
            callback(source)
            return
        }
        val client = translator
        if (client != null && modelReady) {
            translateNow(client, source, callback)
        } else if (loading) {
            synchronized(pending) {
                if (pending.size < 500) pending.add(Pending(source, callback)) else callback(source)
            }
        } else {
            callback(source)
        }
    }

    private fun translateNow(client: Translator, source: String, callback: (String) -> Unit) {
        client.translate(source)
            .addOnSuccessListener { callback(it) }
            .addOnFailureListener { callback(source) }
    }
}
