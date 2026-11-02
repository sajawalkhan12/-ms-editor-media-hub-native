
package com.mseditor.mediahub

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.DownloadManager
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.text.TextWatcher
import android.text.Editable
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class MainActivity : Activity() {

    private val black = Color.rgb(5, 10, 13)
    private val panel = Color.rgb(9, 29, 37)
    private val emerald = Color.rgb(0, 210, 190)
    private val gold = Color.rgb(224, 183, 83)
    private val white = Color.WHITE

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var postsContainer: LinearLayout
    private lateinit var refreshButton: Button

    private var allMediaItems = listOf<MediaItem>()
    private val exoPlayers = mutableListOf<ExoPlayer>()

    private val imageExecutor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var selectedCategory = "Home"

    private val categories = listOf(
        "Home",
        "Video Songs",
        "Video Prompt",
        "Image Prompt",
        "Assets",
        "Tutorials"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, stroke: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(14).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun releaseMediaPlayers() {
        exoPlayers.forEach { player ->
            try {
                player.stop()
                player.release()
            } catch (_: Exception) {
            }
        }
        exoPlayers.clear()
    }

    private fun showHome() {
        releaseMediaPlayers()
        selectedCategory = "Home"

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(black)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            setBackgroundColor(panel)
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        brand.addView(text("MS EDITOR MEDIA HUB", 19f, emerald, true))
        brand.addView(text("Video Songs • Tutorials • Prompts", 12f, gold))
        header.addView(brand)

        val settings = text("⚙", 27f, gold, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setOnClickListener { showNavigationMenu() }
        }

        header.addView(settings)
        root.addView(header)

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        search = EditText(this).apply {
            hint = "Search posts..."
            textSize = 15f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(panel, emerald)
        }

        searchRow.addView(
            search,
            LinearLayout.LayoutParams(0, dp(46), 1f)
        )

        refreshButton = Button(this).apply {
            text = "Refresh"
            textSize = 13f
            isAllCaps = false
            setTextColor(white)
            background = rounded(panel, emerald)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { loadMediaItems() }
        }

        val refreshParams = LinearLayout.LayoutParams(-2, dp(46))
        refreshParams.setMargins(dp(8), 0, 0, 0)
        searchRow.addView(refreshButton, refreshParams)
        root.addView(searchRow)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(black)
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(18))
        }

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(text("Latest Posts", 21f, gold, true))

        postsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(postsContainer)

        // Category tiles are created only after media has loaded, so counts are accurate.

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                filterCategories(s?.toString() ?: "")
                filterMediaItems(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(panel)
            setPadding(2, dp(8), 2, dp(8))
        }

        // Fixed navigation follows the approved PDF; Assets remains visible even when empty.
        val bottomItems = listOf(
            "⌂\nHome",
            "♫\nVideo Songs",
            "✧\nVideo Prompt",
            "▧\nImage Prompt",
            "▦\nAssets",
            "▷\nTutorials"
        )

        bottomItems.forEach { item ->
            val tab = text(item, 11f, emerald, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(7), dp(3), dp(7))
                setOnClickListener {
                    val category = item.substringAfter("\n")
                    if (category == "Home") showHome()
                    else showCategory(category)
                }
            }

            bottom.addView(tab, LinearLayout.LayoutParams(0, dp(58), 1f))
        }

        root.addView(bottom)
        setContentView(root)
        loadMediaItems()
    }

    private fun showNavigationMenu() {
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(panel)
        }
        fun section(title: String) {
            menu.addView(text(title, 13f, gold, true).apply {
                setPadding(0, dp(12), 0, dp(5))
            })
        }
        fun item(label: String, action: () -> Unit) {
            val row = text(label, 16f, white, true).apply {
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(black)
                setOnClickListener { action() }
            }
            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, dp(3), 0, dp(3))
            menu.addView(row, params)
        }
        section("CATEGORIES")
        categories.forEach { category ->
            item(category) {
                if (category == "Home") showHome() else showCategory(category)
            }
        }
        section("ADMIN & APPEARANCE")
        item("Admin Login") {
            Toast.makeText(this, "Admin Login is available on the connected website.", Toast.LENGTH_LONG).show()
        }
        item("Cover Image & Logo") {
            Toast.makeText(this, "Cover and Logo settings are available on the connected website.", Toast.LENGTH_LONG).show()
        }
        item("Upload Media") {
            Toast.makeText(this, "Admin media upload is available on the connected website.", Toast.LENGTH_LONG).show()
        }
        section("APP")
        item("Back to Home") { showHome() }
        item("Exit App") { finish() }
        val scroll = ScrollView(this).apply { addView(menu) }
        android.app.AlertDialog.Builder(this)
            .setTitle("Navigation")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun addCategorySection(category: String) {
        val count = allMediaItems.count { belongsToCategory(it, category) }
        // Never create a category card when it has no matching posts.
        if (count == 0) return

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panel)
            tag = category
        }

        section.addView(text(category, 19f, emerald, true))
        val description = "$count posts available"

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(20), dp(12), dp(20))
            background = rounded(black, Color.rgb(30, 70, 75))
        }

        card.addView(text("♫", 35f, gold, true))
        card.addView(text(description, 14f, white))
        section.addView(card)

        val viewButton = text("View  →", 14f, gold, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(5))
            setOnClickListener { showCategory(category) }
        }

        section.addView(viewButton)
        section.setOnClickListener { showCategory(category) }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, 0, 0, dp(13))
        content.addView(section, params)
    }

    private fun rebuildCategorySections() {
        if (!::content.isInitialized || !::postsContainer.isInitialized) return

        // Category tiles always live after Latest Posts and the post list.
        // Clear that entire tail so stale/untagged empty tiles cannot survive Refresh.
        val postsIndex = content.indexOfChild(postsContainer)
        if (postsIndex < 0) return
        while (content.childCount > postsIndex + 1) {
            content.removeViewAt(content.childCount - 1)
        }

        categories.drop(1).forEach { category ->
            addCategorySection(category)
        }
    }

    private fun filterCategories(query: String) {
        if (!::content.isInitialized) return

        for (i in 0 until content.childCount) {
            val item = content.getChildAt(i)
            val category = item.tag?.toString() ?: ""

            if (category.isNotBlank()) {
                item.visibility =
                    if (query.isBlank() || category.contains(query, true))
                        View.VISIBLE
                    else View.GONE
            }
        }
    }

    private fun loadMediaItems() {
        if (!::refreshButton.isInitialized ||
            !::postsContainer.isInitialized
        ) return

        refreshButton.isEnabled = false
        refreshButton.text = "Loading..."
        postsContainer.removeAllViews()
        postsContainer.addView(text("Loading posts...", 14f, Color.LTGRAY))

        SupabaseClient.getMediaItems { result ->
            if (isFinishing || isDestroyed) return@getMediaItems

            refreshButton.isEnabled = true
            refreshButton.text = "Refresh"

            result.onSuccess { items ->
                allMediaItems = items
                // Home shows the post feed only. Do not append the four category summary cards.
                filterMediaItems(search.text.toString())

                Toast.makeText(
                    this,
                    "Refresh complete: ${items.size} posts",
                    Toast.LENGTH_SHORT
                ).show()
            }

            result.onFailure { error ->
                postsContainer.removeAllViews()
                postsContainer.addView(
                    text(
                        "Posts load nahi huin:\n${error.message}",
                        13f,
                        Color.RED
                    )
                )
            }
        }
    }

    private fun filterMediaItems(query: String) {
        val filtered = allMediaItems.filter { item ->
            val matchesSearch = query.isBlank() ||
                item.title.contains(query, true) ||
                item.description.contains(query, true) ||
                item.mediaType.contains(query, true)

            val matchesCategory =
                selectedCategory == "Home" ||
                belongsToCategory(item, selectedCategory)

            matchesSearch && matchesCategory
        }

        showMediaItems(filtered)
    }

    private fun mediaUrl(path: String): String {
        if (path.isBlank()) return ""

        return if (
            path.startsWith("https://", true) ||
            path.startsWith("http://", true)
        ) path else SupabaseClient.getPublicMediaUrl(path)
    }

    private fun fileExtension(item: MediaItem): String {
        val path = item.externalUrl.ifBlank { item.storagePath }
            .trim()
            .substringBefore("?")
            .substringBefore("#")
            .lowercase()

        return path.substringAfterLast('.', "")
    }

    private fun youtubeVideoId(value: String): String {
        return try {
            val uri = Uri.parse(value.trim())
            val host = (uri.host ?: "").lowercase()
            val id = when {
                host.endsWith("youtu.be") -> uri.lastPathSegment ?: ""
                host.contains("youtube.com") -> when {
                    uri.path == "/watch" -> uri.getQueryParameter("v") ?: ""
                    else -> uri.pathSegments.let { parts ->
                        val index = parts.indexOfFirst { it in listOf("embed", "shorts", "live", "v") }
                        if (index >= 0 && index + 1 < parts.size) parts[index + 1] else ""
                    }
                }
                else -> ""
            }
            if (id.matches(Regex("[A-Za-z0-9_-]{11}"))) id else ""
        } catch (_: Exception) { "" }
    }

    private fun mediaKind(item: MediaItem): String {
        if (youtubeVideoId(item.externalUrl).isNotBlank()) return "video"
        return when (fileExtension(item)) {
            "mp4", "mov", "webm", "m4v", "mkv" -> "video"
            "mp3", "m4a", "wav", "aac", "ogg", "flac" -> "audio"
            "jpg", "jpeg", "png", "webp", "gif" -> "image"

            else -> {
                val type = item.mediaType.lowercase()

                when {
                    // Prompt records are text/content cards, not playable videos merely because
                    // their category name contains "video". Real video URLs were detected above.
                    type.contains("prompt") -> "text"

                    type.contains("video") ||
                    type.contains("movie") ||
                    type.contains("trailer") ||
                    type.contains("tutorial") -> "video"

                    type.contains("audio") ||
                    type == "song" ||
                    type.contains("audio song") -> "audio"

                    type.contains("image") -> "image"
                    else -> "text"
                }
            }
        }
    }

    private fun isImagePrompt(item: MediaItem): Boolean {
        val combined =
            "${item.mediaType} ${item.title} ${item.description} ${item.promptText}"

        val type = item.mediaType.trim().lowercase().replace('-', '_').replace(' ', '_')
        return type == "image_prompt" ||
            combined.contains("image prompt", true) ||
            (item.promptText.isNotBlank() &&
                !combined.contains("video prompt", true) &&
                mediaKind(item) != "video")
    }

    private fun isPrompt(item: MediaItem): Boolean {
        return item.mediaType.contains("prompt", true) ||
            item.title.contains("prompt", true) ||
            item.description.contains("prompt", true) ||
            item.promptText.isNotBlank()
    }

    private fun belongsToCategory(
        item: MediaItem,
        category: String
    ): Boolean {
        val type = item.mediaType.lowercase().replace('-', '_').replace(' ', '_')
        val title = item.title.lowercase()
        val description = item.description.lowercase()
        val prompt = item.promptText.lowercase()
        val combined = "$type $title $description $prompt"
        val kind = mediaKind(item)

        return when (category.lowercase()) {
            "video songs" ->
                (type.contains("song") || type == "video" ||
                 title.contains("song") || description.contains("song")) &&
                    (kind == "video" || youtubeVideoId(item.externalUrl).isNotBlank())

            "video prompt" ->
                type.contains("video_prompt") ||
                    combined.contains("video prompt") ||
                    (combined.contains("prompt") && kind == "video")

            "image prompt" ->
                isImagePrompt(item)

            "assets", "presets & assets" ->
                type in listOf("assets", "asset", "image", "movie", "trailer", "preset", "presets") ||
                    combined.contains("asset") || combined.contains("resource") ||
                    combined.contains("template") || kind == "image"

            "tutorials", "video tutorial" ->
                type.contains("tutorial") || combined.contains("tutorial") ||
                    combined.contains("how to") || combined.contains("guide")

            else -> type == category.lowercase().replace(' ', '_')
        }
    }

    private fun showMediaItems(items: List<MediaItem>) {
        releaseMediaPlayers()
        postsContainer.removeAllViews()

        if (items.isEmpty()) {
            postsContainer.addView(
                text("Abhi koi post available nahi hai.", 14f, white)
            )
            return
        }

        items.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = rounded(panel, emerald)
            }

            card.addView(
                text(
                    item.title.ifBlank { "Untitled Post" },
                    17f,
                    gold,
                    true
                )
            )

            card.addView(text("Type: ${item.mediaType}", 12f, emerald))

            val url = mediaUrl(
                item.externalUrl.ifBlank { item.storagePath }
            )
            val poster = mediaUrl(item.posterPath)
            val youtubeId = youtubeVideoId(item.externalUrl.ifBlank { url })

            if (isImagePrompt(item)) {
                // Prompt posts use their poster as the visible preview; source media may be absent.
                val preview = poster.ifBlank { url }
                if (preview.isNotBlank()) addImage(card, preview, 16f / 9f)
                addViewPromptButton(card, item)
            } else if (isPrompt(item) && mediaKind(item) == "text") {
                // Text prompt records still display their saved poster.
                if (poster.isNotBlank()) addImage(card, poster, 16f / 9f)
                if (item.description.isNotBlank()) card.addView(text(item.description, 13f, white))
                addViewPromptButton(card, item)
            } else {
                if (item.description.isNotBlank()) {
                    val description = text(item.description, 13f, white)
                    description.setPadding(0, dp(7), 0, dp(8))
                    card.addView(description)
                }

                when (mediaKind(item)) {
                    "audio" -> addNativeMediaPlayer(card, url, false)
                    "video" -> {
                        val thumbnail = when {
                            poster.isNotBlank() -> poster
                            youtubeId.isNotBlank() -> "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"
                            else -> ""
                        }
                        addVideoThumbnail(card, thumbnail, url, poster)
                    }
                    "image" -> addImage(card, url, 16f / 9f)
                    else -> if (url.isNotBlank()) addImage(card, url, 16f / 9f)
                }

                if (isPrompt(item)) addViewPromptButton(card, item)
            }

            addEngagementControls(card, item, url)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, 0, 0, dp(12))
            postsContainer.addView(card, params)
        }
    }

    private fun addViewPromptButton(
        card: LinearLayout,
        item: MediaItem
    ) {
        if (belongsToCategory(item, "Video Songs") ||
            belongsToCategory(item, "Tutorials")) return
        val button = Button(this).apply {
            text = "View Prompt"
            isAllCaps = false
            textSize = 14f
            setTextColor(white)
            background = rounded(black, gold)
            setOnClickListener {
                showPromptDialog(item)
            }
        }

        val params = LinearLayout.LayoutParams(-1, dp(48))
        params.setMargins(0, dp(10), 0, 0)
        card.addView(button, params)
    }

    // Show one 16:9 thumbnail first. The existing player is created only after a user taps it.
    // addNativeMediaPlayer() itself is intentionally unchanged to preserve working playback settings.
    private fun addVideoThumbnail(
        card: LinearLayout,
        thumbnailUrl: String,
        videoUrl: String,
        posterUrl: String = ""
    ) {
        val frame = object : FrameLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val height = width * 9 / 16
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            }
        }
        frame.layoutParams = LinearLayout.LayoutParams(-1, -2)
        frame.setBackgroundColor(Color.rgb(3, 12, 15))

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Video thumbnail. Tap to play."
        }
        frame.addView(imageView)

        val playHint = TextView(this).apply {
            text = "▶\nTap to play video"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(white)
            setBackgroundColor(Color.argb(125, 0, 0, 0))
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        frame.addView(playHint)

        if (thumbnailUrl.isNotBlank()) {
            imageExecutor.execute {
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(thumbnailUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 20000
                    connection.readTimeout = 20000
                    connection.instanceFollowRedirects = true
                    val code = connection.responseCode
                    if (code !in 200..299) throw Exception("Thumbnail HTTP $code")
                    val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    mainHandler.post {
                        if (!isFinishing && !isDestroyed && bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                            playHint.setBackgroundColor(Color.argb(90, 0, 0, 0))
                        }
                    }
                } catch (_: Exception) {
                    // Keep the dark 16:9 tap-to-play placeholder if the thumbnail cannot load.
                } finally {
                    connection?.disconnect()
                }
            }
        }

        frame.setOnClickListener {
            val insertAt = card.indexOfChild(frame)
            if (insertAt < 0) return@setOnClickListener
            card.removeView(frame)
            val oldCount = card.childCount
            addNativeMediaPlayer(card, videoUrl, true, posterUrl)
            if (card.childCount > oldCount) {
                val player = card.getChildAt(card.childCount - 1)
                card.removeView(player)
                card.addView(player, insertAt.coerceAtMost(card.childCount))
            }
        }

        card.addView(frame)
    }

    private fun addImage(
        card: LinearLayout,
        url: String,
        ratio: Float
    ) {
        if (url.isBlank()) {
            card.addView(text("Image URL nahi mili.", 13f, Color.RED))
            return
        }

        val frame = object : FrameLayout(this) {
            override fun onMeasure(
                widthMeasureSpec: Int,
                heightMeasureSpec: Int
            ) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val height = (width / ratio).toInt()
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                )
            }
        }

        frame.layoutParams = LinearLayout.LayoutParams(-1, -2)
        frame.setBackgroundColor(Color.rgb(3, 12, 15))

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Post image"
        }

        frame.addView(imageView)
        card.addView(frame)

        imageExecutor.execute {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                connection.doInput = true
                connection.instanceFollowRedirects = true

                val code = connection.responseCode
                if (code !in 200..299) throw Exception("Image HTTP $code")

                val bitmap = connection.inputStream.use {
                    BitmapFactory.decodeStream(it)
                }

                mainHandler.post {
                    if (!isFinishing && !isDestroyed && bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) {
                        imageView.setImageDrawable(null)
                        imageView.contentDescription = "Image could not load"
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun addNativeMediaPlayer(
        card: LinearLayout,
        url: String,
        isVideo: Boolean,
        posterUrl: String = ""
    ) {
        if (url.isBlank()) {
            card.addView(text(if (isVideo) "Video URL nahi mili." else "Audio URL nahi mili.", 13f, Color.RED))
            return
        }

        val youtubeId = if (isVideo) youtubeVideoId(url) else ""
        if (youtubeId.isNotBlank()) {
            val web = WebView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -1)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                webViewClient = WebViewClient()
                setBackgroundColor(Color.BLACK)
                loadDataWithBaseURL(
                    "https://www.youtube-nocookie.com",
                    """<html><body style="margin:0;background:#000"><iframe width="100%" height="100%" src="https://www.youtube-nocookie.com/embed/$youtubeId?playsinline=1&rel=0" title="YouTube video" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe></body></html>""",
                    "text/html", "UTF-8", null
                )
            }
            val videoFrame = object : FrameLayout(this) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val width = MeasureSpec.getSize(widthMeasureSpec)
                    val height = width * 9 / 16
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
                }
            }
            videoFrame.layoutParams = LinearLayout.LayoutParams(-1, -2)
            videoFrame.addView(web, FrameLayout.LayoutParams(-1, -1))
            card.addView(videoFrame)
            return
        }

        try {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)

            val mediaSourceFactory =
                DefaultMediaSourceFactory(httpDataSourceFactory)

            val player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

            exoPlayers.add(player)

            val playerView = PlayerView(this)
            playerView.useController = true
            playerView.controllerAutoShow = true
            playerView.setBackgroundColor(Color.BLACK)
            playerView.player = player

            if (isVideo) {
                val frame = object : FrameLayout(this) {
                    override fun onMeasure(
                        widthMeasureSpec: Int,
                        heightMeasureSpec: Int
                    ) {
                        val width = MeasureSpec.getSize(widthMeasureSpec)
                        val height = width * 9 / 16
                        super.onMeasure(
                            widthMeasureSpec,
                            MeasureSpec.makeMeasureSpec(
                                height,
                                MeasureSpec.EXACTLY
                            )
                        )
                    }
                }

                frame.layoutParams = LinearLayout.LayoutParams(-1, -2)
                frame.setBackgroundColor(Color.BLACK)

                playerView.layoutParams =
                    FrameLayout.LayoutParams(-1, -1)

                frame.addView(playerView)
                card.addView(frame)

            } else {
                playerView.layoutParams =
                    LinearLayout.LayoutParams(-1, dp(80))
                card.addView(playerView)
            }

            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    var details = error.message ?: "No message"

                    var cause: Throwable? = error.cause
                    while (cause != null) {
                        details += "\n${cause.javaClass.simpleName}: ${cause.message}"
                        cause = cause.cause
                    }

                    android.util.Log.e(
                        "MSMediaPlayer",
                        "Playback failed: ${error.errorCodeName}\n$details",
                        error
                    )

                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(
                            this@MainActivity,
                            "Playback error: ${error.errorCodeName}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })

            player.setMediaItem(ExoMediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = false

        } catch (error: Exception) {
            card.addView(
                text(
                    "Player start nahi hua: ${error.message}",
                    13f,
                    Color.RED
                )
            )
        }
    }

    private fun showPromptDialog(item: MediaItem) {
        val promptText = item.promptText.trim().ifBlank {
            item.description.trim().ifBlank { "Is post ka prompt text database mein nahi mila. Admin website par is post ka Prompt Text field check karein." }
        }

        val promptBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
            setBackgroundColor(panel)
        }

        val promptContent = TextView(this).apply {
            text = promptText
            textSize = 15f
            setTextColor(white)
            setTextIsSelectable(true)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        val promptScroll = ScrollView(this).apply {
            addView(promptContent)
        }

        promptBox.addView(
            promptScroll,
            LinearLayout.LayoutParams(-1, dp(300))
        )

        val copyButton = Button(this).apply {
            text = "Copy Prompt"
            isAllCaps = false
            setTextColor(white)
            background = rounded(black, gold)
        }

        promptBox.addView(
            copyButton,
            LinearLayout.LayoutParams(-1, dp(48))
        )

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(item.title.ifBlank { "Prompt" })
            .setView(promptBox)
            .setNegativeButton("Close", null)
            .create()

        copyButton.setOnClickListener {
            val clipboard =
                getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager

            clipboard.setPrimaryClip(
                ClipData.newPlainText("Prompt", promptText)
            )

            Toast.makeText(
                this,
                "Prompt copied",
                Toast.LENGTH_SHORT
            ).show()
        }

        dialog.show()
    }

    private fun showCategory(name: String) {
        releaseMediaPlayers()
        selectedCategory = name

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(black)
            setPadding(dp(16), dp(20), dp(16), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            text("MS EDITOR MEDIA HUB", 18f, emerald, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val back = text("← Home", 15f, gold, true).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { showHome() }
        }

        header.addView(back)
        page.addView(header)

        page.addView(
            text(name, 24f, gold, true).apply {
                setPadding(0, dp(18), 0, dp(12))
            }
        )

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val filtered = allMediaItems.filter {
            belongsToCategory(it, name)
        }

        if (filtered.isEmpty()) {
            list.addView(
                text("Is category mein abhi koi post nahi hai.", 14f, white)
            )
        } else {
            filtered.forEach { item ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = rounded(panel, emerald)
                }

                card.addView(text(item.title, 17f, gold, true))
                card.addView(text("Type: ${item.mediaType}", 12f, emerald))

                val url = mediaUrl(
                    item.externalUrl.ifBlank { item.storagePath }
                )
                val poster = mediaUrl(item.posterPath)

                if (isImagePrompt(item)) {
                    val preview = poster.ifBlank { url }
                    if (preview.isNotBlank()) addImage(card, preview, 16f / 9f)
                    addViewPromptButton(card, item)

                } else if (isPrompt(item) && mediaKind(item) == "text") {
                    if (poster.isNotBlank()) addImage(card, poster, 16f / 9f)
                    if (item.description.isNotBlank()) card.addView(text(item.description, 13f, white))
                    addViewPromptButton(card, item)

                } else {
                    if (item.description.isNotBlank()) {
                        card.addView(text(item.description, 13f, white))
                    }

                    when (mediaKind(item)) {
                        "video" -> {
                            val youtubeId = youtubeVideoId(item.externalUrl.ifBlank { url })
                            val thumbnail = when {
                                poster.isNotBlank() -> poster
                                youtubeId.isNotBlank() -> "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"
                                else -> ""
                            }
                            addVideoThumbnail(card, thumbnail, url, poster)
                        }
                        "audio" -> addNativeMediaPlayer(card, url, false)
                        "image" -> addImage(card, url, 16f / 9f)
                        else -> if (url.isNotBlank()) addImage(card, url, 16f / 9f)
                    }

                    if (isPrompt(item)) {
                        addViewPromptButton(card, item)
                    }
                }

                addEngagementControls(card, item, url)

                val params = LinearLayout.LayoutParams(-1, -2)
                params.setMargins(0, 0, 0, dp(12))
                list.addView(card, params)
            }
        }

        val homeButton = Button(this).apply {
            text = "← Back to Home"
            isAllCaps = false
            setTextColor(white)
            background = rounded(panel, gold)
            setOnClickListener { showHome() }
        }

        page.addView(homeButton)
        setContentView(page)
    }

    private fun visitorId(): String {
        val prefs = getSharedPreferences("media_hub_local", MODE_PRIVATE)
        var id = prefs.getString("visitor_id", null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("visitor_id", id).apply()
        }
        return id
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setTextColor(white)
            background = rounded(black, emerald)
            setPadding(dp(3), 0, dp(3), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun addEngagementControls(card: LinearLayout, item: MediaItem, mediaUrl: String) {
        val stats = text("♥ Likes: …     Comments: …", 12f, gold)
        stats.setPadding(0, dp(10), 0, dp(5))
        card.addView(stats)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun addAction(label: String, click: () -> Unit) {
            val button = actionButton(label, click)
            val p = LinearLayout.LayoutParams(0, dp(44), 1f)
            p.setMargins(dp(2), dp(3), dp(2), dp(3))
            actions.addView(button, p)
        }
        addAction("♥ Like") {
            SupabaseClient.toggleLike(item.id, visitorId()) { result ->
                result.onSuccess { loadEngagement(item, stats) }
                    .onFailure { Toast.makeText(this, "Like save nahi hua: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
        addAction("💬 Comment") { showComments(item, stats) }
        addAction("↗ Share") {
            // Prompt posts often have no downloadable media URL. Share their text
            // content and the public Media Hub page instead of disabling Share.
            val shareText = buildString {
                append(item.title.ifBlank { "MS Editor Media Hub" })
                if (item.description.isNotBlank()) {
                    append("\n\n")
                    append(item.description)
                }
                if (item.promptText.isNotBlank()) {
                    append("\n\nPrompt:\n")
                    append(item.promptText)
                }
                if (mediaUrl.isNotBlank()) {
                    append("\n\n")
                    append(mediaUrl)
                }
                append("\n\nMS Editor Media Hub:\n")
                append("https://sajawalkhan12.github.io/ms-editor-media-hub-/")
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, item.title.ifBlank { "MS Editor Media Hub" })
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            try {
                startActivity(Intent.createChooser(send, "Share post"))
            } catch (e: Exception) {
                Toast.makeText(this, "Share nahi ho saka: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        addAction("↓ Download") {
            if (mediaUrl.isBlank()) {
                Toast.makeText(this, "Download link available nahi hai.", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val request = DownloadManager.Request(Uri.parse(mediaUrl))
                        .setTitle(item.title.ifBlank { "MS Editor Media" })
                        .setDescription("Downloading media")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                    val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    manager.enqueue(request)
                    Toast.makeText(this, "Download shuru ho gaya.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Download nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        card.addView(actions)
        loadEngagement(item, stats)
    }

    private fun loadEngagement(item: MediaItem, stats: TextView) {
        SupabaseClient.getEngagement(item.id, visitorId()) { result ->
            result.onSuccess { data ->
                stats.text = "${if (data.likedByMe) "♥ Liked" else "♥ Like"} · ${data.likes} likes     💬 ${data.comments.size} comments"
            }.onFailure {
                stats.text = "Likes/comments load nahi huay"
                android.util.Log.e("MSMediaEngagement", "Load failed", it)
            }
        }
    }

    private fun showComments(item: MediaItem, stats: TextView) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
            setBackgroundColor(panel)
        }
        val list = TextView(this).apply {
            textSize = 14f
            setTextColor(white)
            setPadding(0, 0, 0, dp(10))
        }
        val name = EditText(this).apply {
            hint = "Aapka naam (optional)"
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
        }
        val message = EditText(this).apply {
            hint = "Comment likhein"
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            minLines = 2
        }
        layout.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, dp(190)))
        layout.addView(name)
        layout.addView(message)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Comments · ${item.title}")
            .setView(layout)
            .setNegativeButton("Close", null)
            .setPositiveButton("Post", null)
            .create()
        fun refreshComments() {
            SupabaseClient.getEngagement(item.id, visitorId()) { result ->
                result.onSuccess { data ->
                    list.text = if (data.comments.isEmpty()) "Abhi koi comment nahi." else
                        data.comments.joinToString("\n\n") { "${it.name}: ${it.text}" }
                    stats.text = "${if (data.likedByMe) "♥ Liked" else "♥ Like"} · ${data.likes} likes     💬 ${data.comments.size} comments"
                }.onFailure { list.text = "Comments load nahi huay: ${it.message}" }
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val comment = message.text.toString().trim()
                if (comment.isBlank()) {
                    message.error = "Comment likhein"
                    return@setOnClickListener
                }
                val visitorName = name.text.toString().trim().ifBlank { "Visitor" }.take(60)
                SupabaseClient.addComment(item.id, visitorName, comment) { result ->
                    result.onSuccess {
                        message.text.clear()
                        refreshComments()
                        Toast.makeText(this, "Comment post ho gaya.", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this, "Comment post nahi hua: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
        refreshComments()
    }

    override fun onDestroy() {
        releaseMediaPlayers()
        imageExecutor.shutdownNow()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        showHome()
    }
}
