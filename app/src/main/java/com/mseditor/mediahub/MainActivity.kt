
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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import android.util.Log
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
    private val mediaWebViews = mutableListOf<WebView>()
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
        window.statusBarColor = black
        window.navigationBarColor = black
        showHome()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun rounded(
        color: Int,
        radius: Int = 14,
        strokeColor: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun text(
        value: String,
        size: Float = 14f,
        color: Int = white
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
    }

    private fun makeButton(
        label: String,
        backgroundColor: Int = emerald,
        textColor: Int = black
    ): Button {
        return Button(this).apply {
            text = label
            setTextColor(textColor)
            textSize = 13f
            isAllCaps = false
            background = rounded(backgroundColor, 12)
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }
    }

    private fun releaseMediaPlayers() {
        mediaWebViews.forEach { webView ->
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.removeAllViews()
                webView.destroy()
            } catch (e: Exception) {
                Log.e("MSMediaHub", "WebView release error", e)
            }
        }
        mediaWebViews.clear()
    }

    private fun showHome() {
        releaseMediaPlayers()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(black)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(8))
            setBackgroundColor(panel)
        }

        val brand = text("MS EDITOR MEDIA HUB", 20f, gold)
        brand.typeface = Typeface.DEFAULT_BOLD
        header.addView(brand)

        header.addView(text("Premium Media • Prompts • Tutorials", 12f, emerald))

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        search = EditText(this).apply {
            hint = "Search posts..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(white)
            textSize = 14f
            setSingleLine(true)
            background = rounded(black, 12, emerald)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        searchRow.addView(
            search,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        refreshButton = makeButton("Refresh")
        refreshButton.setOnClickListener {
            loadMediaItems()
        }

        searchRow.addView(
            refreshButton,
            LinearLayout.LayoutParams(
                dp(100),
                dp(48)
            ).apply {
                marginStart = dp(8)
            }
        )

        header.addView(searchRow)

        root.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(20))
        }

        content.addView(text("LATEST POSTS", 17f, gold).apply {
            typeface = Typeface.DEFAULT_BOLD
        })

        postsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(postsContainer)

        content.addView(text("EXPLORE CATEGORIES", 17f, gold).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(20), 0, dp(10))
        })

        addCategorySection(content)

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setBackgroundColor(panel)
        }

        listOf("Home", "Videos", "Prompts", "Assets").forEach { item ->
            val button = makeButton(item, black, emerald)
            button.setOnClickListener {
                when (item) {
                    "Home" -> showHome()
                    "Videos" -> showCategory("Video Songs")
                    "Prompts" -> showCategory("Video Prompt")
                    "Assets" -> showCategory("Assets")
                }
            }

            bottom.addView(
                button,
                LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
            )
        }

        root.addView(bottom)
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                filterMediaItems(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        loadMediaItems()
    }

    private fun addCategorySection(parent: LinearLayout) {
        categories.forEach { category ->
            val button = makeButton(category, panel, emerald)
            button.textSize = 14f
            button.gravity = Gravity.START or Gravity.CENTER_VERTICAL

            parent.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48)
                ).apply {
                    bottomMargin = dp(7)
                }
            )

            button.setOnClickListener {
                showCategory(category)
            }
        }
    }

    private fun filterMediaItems(query: String) {
        val filtered = allMediaItems.filter {
            it.title.contains(query, true) ||
            it.description.contains(query, true) ||
            it.mediaType.contains(query, true)
        }
        showMediaItems(filtered)
    }

    private fun loadMediaItems() {
        refreshButton.isEnabled = false
        refreshButton.text = "Loading..."

        postsContainer.removeAllViews()
        postsContainer.addView(text("Loading posts...", 14f, emerald))

        SupabaseClient.getMediaItems { result ->
            runOnUiThread {
                refreshButton.isEnabled = true
                refreshButton.text = "Refresh"

                if (result.isSuccess) {
                    allMediaItems = result.getOrDefault(emptyList())
                    showMediaItems(allMediaItems)
                } else {
                    postsContainer.removeAllViews()
                    postsContainer.addView(
                        text(
                            "Unable to load posts. Please check your internet connection.",
                            14f,
                            gold
                        )
                    )
                    Log.e(
                        "MSMediaHub",
                        "Supabase error",
                        result.exceptionOrNull()
                    )
                }
            }
        }
    }

    private fun mediaUrl(item: MediaItem): String {
        val external = item.externalUrl.trim()

        if (external.startsWith("https://") ||
            external.startsWith("http://")) {
            return external
        }

        return SupabaseClient.getPublicMediaUrl(item.storagePath)
    }

    private fun posterUrl(item: MediaItem): String {
        val path = item.posterPath.trim()

        if (path.isBlank()) return ""

        if (path.startsWith("https://") ||
            path.startsWith("http://")) {
            return path
        }

        return SupabaseClient.getPublicMediaUrl(path)
    }

    private fun fileExtension(item: MediaItem): String {
        val path = if (item.externalUrl.isNotBlank()) {
            item.externalUrl
        } else {
            item.storagePath
        }

        return path.substringBefore("?")
            .substringAfterLast(".", "")
            .lowercase()
    }

    private fun mediaKind(item: MediaItem): String {
        return when (fileExtension(item)) {
            "mp4", "mov", "m4v", "webm", "3gp", "mkv" -> "video"
            "mp3", "wav", "m4a", "aac", "ogg" -> "audio"
            "jpg", "jpeg", "png", "webp", "gif" -> "image"
            else -> {
                val type = item.mediaType.lowercase()
                when {
                    type.contains("video") -> "video"
                    type.contains("audio") -> "audio"
                    type.contains("image") -> "image"
                    else -> "other"
                }
            }
        }
    }

    private fun belongsToCategory(
        item: MediaItem,
        category: String
    ): Boolean {
        val type = item.mediaType.lowercase()
        val kind = mediaKind(item)

        return when (category) {
            "Home" -> true
            "Video Songs" -> kind == "video"
            "Video Prompt" -> type.contains("video prompt")
            "Image Prompt" -> type.contains("image prompt")
            "Assets" -> type.contains("asset") || type.contains("image")
            "Tutorials" -> type.contains("tutorial")
            else -> true
        }
    }

    private fun showMediaItems(items: List<MediaItem>) {
        releaseMediaPlayers()
        postsContainer.removeAllViews()

        if (items.isEmpty()) {
            postsContainer.addView(
                text("No posts found.", 14f, gold)
            )
            return
        }

        items.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(panel, 16)
            }

            val title = text(item.title, 17f, gold)
            title.typeface = Typeface.DEFAULT_BOLD
            card.addView(title)

            card.addView(
                text(item.mediaType, 11f, emerald)
            )

            val url = mediaUrl(item)
            val poster = posterUrl(item)

            card.addView(
                text(item.description, 13f, white)
            )

            when (mediaKind(item)) {
                "video" -> addHtmlMediaPlayer(
                    card,
                    url,
                    true,
                    poster
                )

                "audio" -> addHtmlMediaPlayer(
                    card,
                    url,
                    false
                )

                "image" -> addImage(
                    card,
                    url,
                    16f / 9f
                )

                else -> {
                    if (url.isNotBlank()) {
                        addImage(card, url, 16f / 9f)
                    }
                }
            }

            if (item.description.isNotBlank()) {
                val copyButton = makeButton(
                    "Copy Description",
                    gold,
                    black
                )

                copyButton.setOnClickListener {
                    val clipboard =
                        getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "Media description",
                            item.description
                        )
                    )

                    Toast.makeText(
                        this,
                        "Copied",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                card.addView(
                    copyButton,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(46)
                    ).apply {
                        topMargin = dp(8)
                    }
                )
            }

            postsContainer.addView(
                card,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(14)
                }
            )
        }
    }

    private fun addImage(
        card: LinearLayout,
        url: String,
        ratio: Float
    ) {
        if (url.isBlank()) return

        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        frame.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(200)
        ).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        }

        frame.viewTreeObserver.addOnGlobalLayoutListener {
            val width = frame.width
            if (width > 0) {
                val params = frame.layoutParams
                val height = (width / ratio).toInt()
                if (params.height != height) {
                    params.height = height
                    frame.layoutParams = params
                }
            }
        }

        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }

        frame.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        card.addView(frame)

        imageExecutor.execute {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(url).openConnection()
                    as HttpURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.instanceFollowRedirects = true
                connection.connect()

                val bitmap = connection.inputStream.use {
                    BitmapFactory.decodeStream(it)
                }

                if (bitmap != null) {
                    mainHandler.post {
                        if (!isFinishing && !isDestroyed) {
                            image.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MSMediaHub", "Image loading error", e)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun addHtmlMediaPlayer(
        card: LinearLayout,
        url: String,
        isVideo: Boolean,
        posterUrl: String = ""
    ) {
        if (url.isBlank()) {
            card.addView(
                text("Media URL is missing.", 13f, gold)
            )
            return
        }

        val safeUrl = escapeHtml(url)
        val safePoster = escapeHtml(posterUrl)

        val webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)

            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.mediaPlaybackRequiresUserGesture = true

            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)

                    Log.e(
                        "MSMediaHub",
                        "Player loading error: ${error?.description}"
                    )
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?
                ) {
                    super.onReceivedHttpError(
                        view,
                        request,
                        errorResponse
                    )

                    Log.e(
                        "MSMediaHub",
                        "Player HTTP error: ${errorResponse?.statusCode}"
                    )
                }
            }
        }

        mediaWebViews.add(webView)

        val mediaHtml = if (isVideo) {
            val posterAttribute = if (safePoster.isNotBlank()) {
                """poster="$safePoster""""
            } else {
                ""
            }

            """
            <video
                controls
                playsinline
                preload="metadata"
                $posterAttribute
            >
                <source src="$safeUrl" type="video/mp4">
                Your browser does not support HTML5 video.
            </video>
            """
        } else {
            """
            <audio controls preload="metadata">
                <source src="$safeUrl">
                Your browser does not support audio playback.
            </audio>
            """
        }

        val playerHeight = if (isVideo) {
            dp(220)
        } else {
            dp(85)
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta
                    name="viewport"
                    content="width=device-width, initial-scale=1.0,
                    maximum-scale=1.0"
                >
                <style>
                    * {
                        box-sizing: border-box;
                    }

                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        overflow: hidden;
                        background: #000000;
                    }

                    video {
                        display: block;
                        width: 100%;
                        height: 100%;
                        object-fit: contain;
                        background: #000000;
                    }

                    audio {
                        display: block;
                        width: 100%;
                        margin-top: 12px;
                    }
                </style>
            </head>
            <body>
                $mediaHtml
            </body>
            </html>
        """.trimIndent()

        if (isVideo) {
            val frame = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
            }

            frame.viewTreeObserver.addOnGlobalLayoutListener {
                val width = frame.width
                if (width > 0) {
                    val params = frame.layoutParams
                    val height = (width * 9f / 16f).toInt()

                    if (params.height != height) {
                        params.height = height
                        frame.layoutParams = params
                    }
                }
            }

            frame.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            card.addView(
                frame,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    playerHeight
                ).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                }
            )
        } else {
            card.addView(
                webView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    playerHeight
                ).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                }
            )
        }

        webView.loadDataWithBaseURL(
            "https://mgjrrgsbddppffnvnmgs.supabase.co/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun showCategory(category: String) {
        selectedCategory = category
        releaseMediaPlayers()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(black)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(panel)
        }

        val back = makeButton("Back", black, emerald)
        back.setOnClickListener {
            showHome()
        }

        header.addView(back)

        val heading = text(category, 18f, gold)
        heading.typeface = Typeface.DEFAULT_BOLD

        header.addView(
            heading,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(12)
            }
        )

        root.addView(header)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(20))
        }

        scroll.addView(list)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        val filtered = allMediaItems.filter {
            belongsToCategory(it, category)
        }

        if (filtered.isEmpty()) {
            list.addView(text("No posts in this category.", 14f, gold))
        } else {
            filtered.forEach { item ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = rounded(panel, 16)
                }

                val title = text(item.title, 17f, gold)
                title.typeface = Typeface.DEFAULT_BOLD
                card.addView(title)

                card.addView(
                    text(item.description, 13f, white)
                )

                val url = mediaUrl(item)
                val poster = posterUrl(item)

                when (mediaKind(item)) {
                    "video" -> addHtmlMediaPlayer(
                        card,
                        url,
                        true,
                        poster
                    )

                    "audio" -> addHtmlMediaPlayer(
                        card,
                        url,
                        false
                    )

                    "image" -> addImage(
                        card,
                        url,
                        16f / 9f
                    )

                    else -> {
                        if (url.isNotBlank()) {
                            addImage(card, url, 16f / 9f)
                        }
                    }
                }

                list.addView(
                    card,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(14)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        releaseMediaPlayers()

        try {
            imageExecutor.shutdownNow()
        } catch (e: Exception) {
            Log.e("MSMediaHub", "Executor shutdown error", e)
        }

        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (selectedCategory != "Home") {
            showHome()
        } else {
            super.onBackPressed()
        }
    }
}
