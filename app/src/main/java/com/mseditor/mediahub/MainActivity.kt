
package com.mseditor.mediahub

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

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
    private var audioPlayer: MediaPlayer? = null
    private var currentAudioButton: Button? = null

    private val imageExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

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

    private fun rounded(
        color: Int,
        stroke: Int = 0
    ): GradientDrawable {
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

    private fun showHome() {

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

            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Refresh started",
                    Toast.LENGTH_SHORT
                ).show()
                loadMediaItems()
            }
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

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        content.addView(text("Latest Posts", 21f, gold, true))

        postsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(postsContainer)

        categories.drop(1).forEach { category ->
            addCategorySection(category)
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                val query = s?.toString() ?: ""
                filterCategories(query)
                filterMediaItems(query)
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
                    if (category == "Home") {
                        showHome()
                    } else {
                        showCategory(category)
                    }
                }
            }

            bottom.addView(
                tab,
                LinearLayout.LayoutParams(0, dp(58), 1f)
            )
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

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(25), dp(12), dp(25))
            background = rounded(black, Color.rgb(30, 70, 75))
        }

        card.addView(text("♫", 35f, gold, true))
        card.addView(text("Open category to view posts", 14f, white))
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
        for (i in 0 until content.childCount) {
            val item = content.getChildAt(i)
            val category = item.tag?.toString() ?: ""

            if (category.isNotBlank()) {
                item.visibility =
                    if (category.contains(query, true) || query.isBlank()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
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

                Toast.makeText(
                    this,
                    "Refresh complete: ${items.size} posts",
                    Toast.LENGTH_SHORT
                ).show()

                filterMediaItems(search.text.toString())
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

                Toast.makeText(
                    this,
                    "Refresh failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun filterMediaItems(query: String) {

        val filtered = allMediaItems.filter { item ->
            item.title.contains(query, true) ||
            item.description.contains(query, true) ||
            item.mediaType.contains(query, true)
        }

        showMediaItems(filtered)
    }

    private fun mediaUrl(path: String): String {
        if (path.isBlank()) return ""

        return if (
            path.startsWith("https://", true) ||
            path.startsWith("http://", true)
        ) {
            path
        } else {
            SupabaseClient.getPublicMediaUrl(path)
        }
    }

    private fun mediaKind(item: MediaItem): String {

        val type = item.mediaType.lowercase()
        val url = (item.externalUrl.ifBlank { item.storagePath }).lowercase()

        if (
            type.contains("audio") ||
            type.contains("song") ||
            url.endsWith(".mp3") ||
            url.endsWith(".m4a") ||
            url.endsWith(".wav") ||
            url.endsWith(".aac")
        ) return "audio"

        if (
            type.contains("image") ||
            url.endsWith(".jpg") ||
            url.endsWith(".jpeg") ||
            url.endsWith(".png") ||
            url.endsWith(".webp")
        ) return "image"

        if (
            type.contains("video") ||
            type.contains("movie") ||
            type.contains("trailer") ||
            type.contains("tutorial") ||
            url.endsWith(".mp4") ||
            url.endsWith(".mov") ||
            url.endsWith(".webm")
        ) return "video"

        return "text"
    }

    private fun showMediaItems(items: List<MediaItem>) {

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

            card.addView(
                text("Type: ${item.mediaType}", 12f, emerald)
            )

            if (item.description.isNotBlank()) {
                val description = text(item.description, 13f, white)
                description.setPadding(0, dp(7), 0, dp(8))
                card.addView(description)
            }

            val url = mediaUrl(item.externalUrl.ifBlank { item.storagePath })

            when (mediaKind(item)) {
                "audio" -> addAudioPlayer(card, url)
                "video" -> addVideoPlayer(card, url)
                "image" -> addImage(card, url)
                else -> {
                    if (url.isNotBlank()) {
                        addImage(card, url)
                    }
                }
            }

            // Optional poster/thumbnail
            val poster = mediaUrl(item.posterPath)
            if (poster.isNotBlank() && mediaKind(item) != "image") {
                val label = text("Post thumbnail", 11f, gold)
                label.setPadding(0, dp(8), 0, dp(4))
                card.addView(label)
                addImage(card, poster, dp(130))
            }

            if (
                item.mediaType.contains("prompt", true) ||
                item.description.contains("prompt", true)
            ) {
                val copyButton = Button(this).apply {
                    text = "Copy Text"
                    isAllCaps = false
                    setTextColor(white)
                    background = rounded(black, gold)
                    setOnClickListener {
                        val clipboard =
                            getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager

                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "Post text",
                                item.description
                            )
                        )

                        Toast.makeText(
                            this@MainActivity,
                            "Text copied",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                card.addView(copyButton)
            }

            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, 0, 0, dp(12))
            postsContainer.addView(card, params)
        }
    }

    private fun addImage(
        card: LinearLayout,
        url: String,
        height: Int = 210
    ) {
        if (url.isBlank()) return

        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, height)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(3, 12, 15))
            contentDescription = "Post image"
        }

        card.addView(imageView)

        imageExecutor.execute {
            try {
                val connection =
                    URL(url).openConnection() as HttpURLConnection

                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                connection.doInput = true

                val bitmap = connection.inputStream.use {
                    BitmapFactory.decodeStream(it)
                }

                connection.disconnect()

                if (bitmap != null) {
                    mainHandler.post {
                        if (!isFinishing && !isDestroyed) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) {
                        imageView.setImageDrawable(null)
                    }
                }
            }
        }
    }

    private fun addAudioPlayer(card: LinearLayout, url: String) {

        if (url.isBlank()) {
            card.addView(text("Audio file URL nahi mili.", 13f, Color.RED))
            return
        }

        val playButton = Button(this).apply {
            text = "▶ Play Song"
            isAllCaps = false
            setTextColor(white)
            background = rounded(black, emerald)
        }

        card.addView(playButton)

        playButton.setOnClickListener {

            if (audioPlayer?.isPlaying == true &&
                currentAudioButton === playButton
            ) {
                audioPlayer?.pause()
                playButton.text = "▶ Resume Song"
                return@setOnClickListener
            }

            releaseAudio()

            playButton.isEnabled = false
            playButton.text = "Loading audio..."

            try {
                val player = MediaPlayer()
                audioPlayer = player
                currentAudioButton = playButton

                player.setDataSource(url)

                player.setOnPreparedListener {
                    if (isFinishing || isDestroyed) {
                        it.release()
                        return@setOnPreparedListener
                    }

                    playButton.isEnabled = true
                    playButton.text = "Ⅱ Pause Song"
                    it.start()
                }

                player.setOnCompletionListener {
                    playButton.text = "▶ Play Song"
                    releaseAudio()
                }

                player.setOnErrorListener { _, _, _ ->
                    playButton.isEnabled = true
                    playButton.text = "▶ Play Song"
                    releaseAudio()
                    Toast.makeText(
                        this,
                        "Audio play nahi ho saka. URL check karein.",
                        Toast.LENGTH_LONG
                    ).show()
                    true
                }

                player.prepareAsync()

            } catch (e: Exception) {
                playButton.isEnabled = true
                playButton.text = "▶ Play Song"
                releaseAudio()

                Toast.makeText(
                    this,
                    "Audio error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun releaseAudio() {
        try {
            audioPlayer?.release()
        } catch (_: Exception) {
        }

        audioPlayer = null
        currentAudioButton = null
    }

    private fun addVideoPlayer(card: LinearLayout, url: String) {

        if (url.isBlank()) {
            card.addView(text("Video file URL nahi mili.", 13f, Color.RED))
            return
        }

        val videoView = VideoView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(220))
            setBackgroundColor(Color.BLACK)
        }

        card.addView(videoView)

        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)

        try {
            videoView.setVideoURI(Uri.parse(url))
            videoView.setOnPreparedListener { player ->
                player.isLooping = false
            }

            videoView.setOnErrorListener { _, _, _ ->
                Toast.makeText(
                    this,
                    "Video play nahi ho saka. File URL check karein.",
                    Toast.LENGTH_LONG
                ).show()
                true
            }
        } catch (e: Exception) {
            card.addView(
                text("Video error: ${e.message}", 12f, Color.RED)
            )
        }
    }

    private fun showCategory(name: String) {

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(30), dp(20), dp(20))
            setBackgroundColor(black)
        }

        page.addView(text("MS EDITOR MEDIA HUB", 20f, emerald, true))

        page.addView(
            text(name, 25f, gold, true).apply {
                setPadding(0, dp(35), 0, dp(25))
            }
        )

        page.addView(
            text(
                "Category filtering ka agla step mein connection hoga.",
                15f,
                white
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        val back = text("← Back to Home", 17f, emerald, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(25), dp(20), dp(25))
            setOnClickListener { showHome() }
        }

        page.addView(back)
        setContentView(page)
    }

    override fun onDestroy() {
        releaseAudio()
        imageExecutor.shutdownNow()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        showHome()
    }
}
