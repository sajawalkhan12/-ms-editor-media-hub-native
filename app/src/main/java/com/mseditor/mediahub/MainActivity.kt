
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
            setOnClickListener { showCategory("Settings") }
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

        categories.drop(1).forEach { addCategorySection(it) }

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

        val bottomItems = listOf(
            "⌂\nHome",
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

    private fun addCategorySection(category: String) {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panel)
            tag = category
        }

        section.addView(text(category, 19f, emerald, true))

        val count = allMediaItems.count {
            belongsToCategory(it, category)
        }

        val description = if (count == 0) {
            "Open category to view posts"
        } else {
            "$count posts available"
        }

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

    private fun mediaKind(item: MediaItem): String {
        return when (fileExtension(item)) {
            "mp4", "mov", "webm", "m4v", "mkv" -> "video"
            "mp3", "m4a", "wav", "aac", "ogg", "flac" -> "audio"
            "jpg", "jpeg", "png", "webp", "gif" -> "image"

            else -> {
                val type = item.mediaType.lowercase()

                when {
                    type.contains("video") ||
                    type.contains("movie") ||
                    type.contains("trailer") ||
                    type.contains("tutorial") -> "video"

                    type.contains("audio") ||
                    type == "song" ||
                    type.contains("audio song") -> "audio"

                    type.contains("image") -> "image"
                    type.contains("prompt") -> "text"
                    else -> "text"
                }
            }
        }
    }

    private fun isImagePrompt(item: MediaItem): Boolean {
        val combined =
            "${item.mediaType} ${item.title} ${item.description}"

        return combined.contains("prompt", true) &&
            (mediaKind(item) == "image" || mediaKind(item) == "text")
    }

    private fun isPrompt(item: MediaItem): Boolean {
        return item.mediaType.contains("prompt", true) ||
            item.title.contains("prompt", true) ||
            item.description.contains("prompt", true)
    }

    private fun belongsToCategory(
        item: MediaItem,
        category: String
    ): Boolean {
        val type = item.mediaType.lowercase()
        val title = item.title.lowercase()
        val description = item.description.lowercase()
        val combined = "$type $title $description"
        val kind = mediaKind(item)

        return when (category.lowercase()) {
            "video songs" ->
                (type.contains("song") || title.contains("song") ||
                 description.contains("song")) && kind == "video"

            "video prompt" ->
                combined.contains("prompt") && kind == "video"

            "image prompt" ->
                isImagePrompt(item)

            "assets" ->
                combined.contains("asset") ||
                    combined.contains("resource") ||
                    combined.contains("template")

            "tutorials" ->
                combined.contains("tutorial") ||
                    combined.contains("how to") ||
                    combined.contains("guide")

            else -> false
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

            if (isImagePrompt(item)) {
                if (url.isNotBlank()) {
                    addImage(card, url, 16f / 9f)
                }

                addViewPromptButton(card, item)

            } else {
                if (item.description.isNotBlank()) {
                    val description = text(item.description, 13f, white)
                    description.setPadding(0, dp(7), 0, dp(8))
                    card.addView(description)
                }

                when (mediaKind(item)) {
                    "audio" -> addNativeMediaPlayer(card, url, false)
                    "video" -> addNativeMediaPlayer(card, url, true, poster)
                    "image" -> addImage(card, url, 16f / 9f)
                    else -> {
                        if (url.isNotBlank()) {
                            addImage(card, url, 16f / 9f)
                        }
                    }
                }

                if (isPrompt(item)) {
                    addViewPromptButton(card, item)
                }
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
            card.addView(
                text(
                    if (isVideo) "Video file URL nahi mili."
                    else "Audio file URL nahi mili.",
                    13f,
                    Color.RED
                )
            )
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
        val promptText = item.description.ifBlank {
            "Is post mein prompt text nahi mila."
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
                    if (url.isNotBlank()) {
                        addImage(card, url, 16f / 9f)
                    }

                    addViewPromptButton(card, item)

                } else {
                    if (item.description.isNotBlank()) {
                        card.addView(text(item.description, 13f, white))
                    }

                    when (mediaKind(item)) {
                        "video" -> addNativeMediaPlayer(card, url, true, poster)
                        "audio" -> addNativeMediaPlayer(card, url, false)
                        "image" -> addImage(card, url, 16f / 9f)
                        else -> {
                            if (url.isNotBlank()) {
                                addImage(card, url, 16f / 9f)
                            }
                        }
                    }

                    if (isPrompt(item)) {
                        addViewPromptButton(card, item)
                    }
                }

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
            if (mediaUrl.isBlank()) {
                Toast.makeText(this, "Share link available nahi hai.", Toast.LENGTH_SHORT).show()
            } else {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${item.title}\n$mediaUrl")
                }
                startActivity(Intent.createChooser(send, "Share media"))
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
