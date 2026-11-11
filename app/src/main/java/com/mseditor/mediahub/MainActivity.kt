
package com.mseditor.mediahub

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
    private var splashRunnable: Runnable? = null
    private var hiddenAdminTapCount = 0
    private var hiddenAdminTapReset: Runnable? = null

    private var isProfileVisible = false
    private var selectedCategory = "Home"
    private var adminSession: SupabaseClient.AdminSession? = null
    private val PICK_MEDIA_FILE = 4101
    private val PICK_POSTER_FILE = 4102
    private val PICK_LOGO_FILE = 4103
    private val PICK_PROFILE_COVER = 4104
    private val PICK_PROFILE_AVATAR = 4105
    private val PICK_USER_POST_FILE = 4106
    private var selectedUserPostUri: Uri? = null
    private var selectedUserPostType: String = ""
    private var userPostSelectedLabel: TextView? = null
    private var profileAvatarPath = ""
    private var profileCoverPath = ""
    private var profileAccessToken = ""
    private var profileUserId = ""
    private var profileBioField: EditText? = null
    private var profileLocationField: EditText? = null
    private var returnToUploadPostAfterImageEdit = false
    private var uploadCoverPreview: ImageView? = null
    private var selectedUploadUri: Uri? = null
    private var selectedPosterUri: Uri? = null

    private val categories = listOf(
        "Home",
        "Video Songs",
        "Video Tutorial",
        "Video Prompt",
        "Image Prompt",
        "Presets & Assets"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSplashScreen()
    }

    private fun showSplashScreen() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        splash.addView(logo, LinearLayout.LayoutParams(dp(270), dp(270)))
        val radar = RadarScannerView(this)
        val radarParams = LinearLayout.LayoutParams(dp(150), dp(150))
        radarParams.topMargin = dp(24)
        splash.addView(radar, radarParams)
        val status = text("SCANNING APP...", 12f, emerald, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
            setPadding(0, dp(12), 0, 0)
        }
        splash.addView(status)
        setContentView(splash)
        splashRunnable?.let { mainHandler.removeCallbacks(it) }
        splashRunnable = Runnable {
            if (!isFinishing && !isDestroyed) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                val loggedIn = getSharedPreferences("auth_session", MODE_PRIVATE)
                    .getString("access_token", null).orEmpty().isNotBlank()
                if (loggedIn) showHome() else {
                    startActivity(Intent(this, AuthActivity::class.java))
                    finish()
                }
            }
        }
        mainHandler.postDelayed(splashRunnable!!, 2200L)
    }

    private inner class RadarScannerView(context: Context) : View(context) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = emerald; alpha = 150 }
        private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = emerald; alpha = 75 }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = emerald; alpha = 230 }
        private var angle = 0f
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f
            val radius = (minOf(width, height) / 2f) - dp(5)
            val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            for (fraction in floatArrayOf(0.34f, 0.67f, 1f)) canvas.drawCircle(cx, cy, radius * fraction, ringPaint)
            canvas.drawLine(cx - radius, cy, cx + radius, cy, ringPaint)
            canvas.drawLine(cx, cy - radius, cx, cy + radius, ringPaint)
            canvas.drawArc(bounds, angle, 48f, true, sweepPaint)
            val radians = Math.toRadians(angle.toDouble())
            canvas.drawLine(cx, cy, cx + (radius * kotlin.math.cos(radians)).toFloat(), cy + (radius * kotlin.math.sin(radians)).toFloat(), linePaint)
            canvas.drawCircle(cx, cy, dp(3).toFloat(), linePaint)
            angle = (angle + 5f) % 360f
            postInvalidateDelayed(32L)
        }
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

    private fun addExpandableDescription(
        parent: LinearLayout,
        value: String,
        topPadding: Int = 0,
        bottomPadding: Int = 0
    ) {
        val description = text(value, 13f, white).apply {
            setPadding(0, dp(topPadding), 0, dp(bottomPadding))
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val more = text("More", 12f, gold, true).apply {
            setPadding(0, dp(2), 0, dp(5))
            visibility = View.GONE
        }

        parent.addView(description)
        parent.addView(more)

        description.post {
            val layout = description.layout
            if (layout != null && layout.lineCount > 0 &&
                layout.getEllipsisCount(layout.lineCount - 1) > 0
            ) {
                more.visibility = View.VISIBLE
            }
        }

        more.setOnClickListener {
            val expanded = description.maxLines == 3
            if (expanded) {
                description.maxLines = Int.MAX_VALUE
                description.ellipsize = null
                more.text = "Less"
            } else {
                description.maxLines = 3
                description.ellipsize = android.text.TextUtils.TruncateAt.END
                more.text = "More"
            }
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
        isProfileVisible = false
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
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandTitle = text("MS EDITOR MEDIA HUB", 16f, emerald, true)
        val brandLogo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            visibility = View.VISIBLE
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "MS Editor Media Hub logo"
        }
        brandRow.addView(brandLogo, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            rightMargin = dp(7)
        })
        brandRow.addView(brandTitle, LinearLayout.LayoutParams(0, -2, 1f))
        brand.addView(brandRow)
        brand.addView(text("Video Songs • Tutorials • Prompts", 12f, gold))
        // Hidden admin access: tap the brand header 7 times within 2.5 seconds.
        brandRow.setOnClickListener {
            hiddenAdminTapCount++
            hiddenAdminTapReset?.let { mainHandler.removeCallbacks(it) }
            if (hiddenAdminTapCount >= 7) {
                hiddenAdminTapCount = 0
                hiddenAdminTapReset = null
                showAdminLogin()
            } else {
                hiddenAdminTapReset = Runnable { hiddenAdminTapCount = 0 }
                mainHandler.postDelayed(hiddenAdminTapReset!!, 2500L)
            }
        }
        header.addView(brand)
        loadHeaderLogo(brandLogo)

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

        // Bottom navigation: replace Assets with a direct User Profile shortcut.
        val bottomItems = listOf(
            "⌂\nHome",
            "♫\nVideo Songs",
            "▷\nVideo Tutorial",
            "✧\nVideo Prompt",
            "▧\nImage Prompt",
            "♙\nUser Profile"
        )

        bottomItems.forEach { item ->
            val tab = text(item, 11f, emerald, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(7), dp(3), dp(7))
                setOnClickListener {
                    val destination = item.substringAfter("\n")
                    when (destination) {
                        "Home" -> showHome()
                        "User Profile" -> showProfilePage()
                        else -> showCategory(destination)
                    }
                }
            }

            bottom.addView(tab, LinearLayout.LayoutParams(0, dp(58), 1f))
        }

        root.addView(bottom)
        setContentView(root)
        loadMediaItems()
    }

    private fun loadHeaderLogo(target: ImageView) {
        SupabaseClient.getAppLogo { result ->
            if (isFinishing || isDestroyed) return@getAppLogo
            val logoUrl = result.getOrNull().orEmpty()
            if (logoUrl.isBlank()) {
                return@getAppLogo
            }
            imageExecutor.execute {
                val bitmap = runCatching {
                    val connection = URL(logoUrl).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 15000
                        connection.readTimeout = 20000
                        connection.instanceFollowRedirects = true
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
                mainHandler.post {
                    if (!isFinishing && !isDestroyed && bitmap != null) {
                        target.setImageBitmap(bitmap)
                        target.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun chooseAppLogo() {
        if (adminSession == null) {
            Toast.makeText(this, "Logo badalne ke liye Admin Login karein.", Toast.LENGTH_LONG).show()
            showAdminLogin()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, PICK_LOGO_FILE)
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
        if (adminSession != null) {
            section("ADMIN")
            item("Open Admin Page") { showAdminPage() }
            item("Admin Sign Out") {
                adminSession = null
                Toast.makeText(this, "Admin sign out ho gaya.", Toast.LENGTH_SHORT).show()
                showNavigationMenu()
            }
            section("ADMIN TOOLS")
            item("Change App Logo") { chooseAppLogo() }
            item("Cover Image & Logo") { openAdminWebsite("Admin appearance settings") }
            item("Upload Media") { showUploadForm() }
        }
        section("ACCOUNT")
        item("🌐 MS Editor Media Hub") { openAdminWebsite("MS Editor Media Hub") }
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

    private fun showAdminLogin() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(black)
        }
        page.addView(text("MS EDITOR MEDIA HUB", 16f, gold, true))
        page.addView(text("ADMIN LOGIN", 25f, emerald, true).apply {
            setPadding(0, dp(22), 0, dp(8))
        })
        page.addView(text("Yeh private admin page hai. Sirf approved admin account ko access milega.", 14f, Color.LTGRAY))
        val email = EditText(this).apply {
            hint = "Admin email"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(panel, emerald)
        }
        val password = EditText(this).apply {
            hint = "Password"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(panel, emerald)
        }
        page.addView(email, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        page.addView(password, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val login = actionButton("ADMIN LOGIN") { }
        page.addView(login, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(20) })
        page.addView(actionButton("Back to Home") { showHome() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        })
        login.setOnClickListener {
            val enteredEmail = email.text.toString().trim()
            val enteredPassword = password.text.toString()
            if (enteredEmail.isBlank()) {
                email.error = "Admin email enter karein"
                return@setOnClickListener
            }
            if (enteredPassword.isBlank()) {
                password.error = "Password enter karein"
                return@setOnClickListener
            }
            login.isEnabled = false
            login.text = "Checking..."
            SupabaseClient.signInAndCheckAdmin(enteredEmail, enteredPassword) { result ->
                if (isFinishing || isDestroyed) return@signInAndCheckAdmin
                login.isEnabled = true
                login.text = "ADMIN LOGIN"
                result.onSuccess { session ->
                    adminSession = session
                    Toast.makeText(this, "Admin verification successful.", Toast.LENGTH_SHORT).show()
                    showAdminPage()
                }.onFailure { error ->
                    Toast.makeText(this, error.message ?: "Admin login failed.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showAdminPage() {
        if (adminSession == null) { showAdminLogin(); return }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
            setBackgroundColor(black)
        }
        page.addView(text("ADMIN DASHBOARD", 23f, emerald, true))
        page.addView(text("Verified admin account", 14f, gold).apply { setPadding(0, dp(8), 0, dp(18)) })
        page.addView(text("Your account passed Supabase email/password sign-in and the is_media_admin permission check.", 14f, white))
        page.addView(actionButton("＋ Upload Media (In App)") { showUploadForm() }, LinearLayout.LayoutParams(-1, dp(50)))
        val logoParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) }
        page.addView(actionButton("▧ Change App Logo") { chooseAppLogo() }, logoParams)
        val webTools = actionButton("Open Full Admin Tools (Website)") { openAdminWebsite("Admin tools") }
        val webParams = LinearLayout.LayoutParams(-1, dp(50))
        webParams.topMargin = dp(10)
        page.addView(webTools, webParams)
        val signOut = actionButton("Admin Sign Out") { adminSession = null; showHome(); Toast.makeText(this, "Admin sign out ho gaya.", Toast.LENGTH_SHORT).show() }
        val signParams = LinearLayout.LayoutParams(-1, dp(50)); signParams.topMargin = dp(10); page.addView(signOut, signParams)
        val back = actionButton("Back to Home") { showHome() }
        val backParams = LinearLayout.LayoutParams(-1, dp(50)); backParams.topMargin = dp(10); page.addView(back, backParams)
        setContentView(page)
    }


    private fun showUploadForm() {
        if (adminSession == null) {
            Toast.makeText(this, "Upload ke liye pehle Admin Login karein.", Toast.LENGTH_LONG).show()
            showAdminLogin()
            return
        }
        selectedUploadUri = null
        selectedPosterUri = null
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(22))
            setBackgroundColor(black)
        }
        page.addView(text("UPLOAD MEDIA", 23f, emerald, true))
        page.addView(text("Native upload · Website ke saath shared", 13f, gold).apply {
            setPadding(0, dp(6), 0, dp(16))
        })
        fun edit(hintValue: String, multiline: Boolean = false) = EditText(this).apply {
            hint = hintValue
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(panel, emerald)
            if (multiline) { minLines = 3; gravity = Gravity.TOP } else setSingleLine(true)
        }
        val titleField = edit("Media title")
        val descriptionField = edit("Description (optional)", true)
        val promptField = edit("Image Prompt / Video Prompt yahan likhein", true).apply {
            visibility = View.GONE
        }
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Video Songs", "Video Tutorial", "Video Prompt", "Image Prompt", "Presets & Assets"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    promptField.visibility = if (position == 2 || position == 3) View.VISIBLE else View.GONE
                    promptField.hint = if (position == 2) "Video Prompt" else "Image Prompt"
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        val mediaLabel = text("No media file selected", 13f, Color.LTGRAY)
        val posterLabel = text("Poster: optional", 13f, Color.LTGRAY)
        val status = text("Ready to upload.", 13f, gold)
        fun add(view: View, top: Int = 9) {
            val p = LinearLayout.LayoutParams(-1, -2)
            p.topMargin = dp(top)
            page.addView(view, p)
        }
        add(titleField, 12)
        add(descriptionField)
        add(promptField)
        add(text("Category", 13f, gold), 12)
        add(category)
        add(actionButton("Choose Media File") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, PICK_MEDIA_FILE)
        }, 14)
        add(mediaLabel, 3)
        add(actionButton("Choose Poster / Thumbnail (Optional)") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
            }, PICK_POSTER_FILE)
        }, 10)
        add(posterLabel, 3)
        lateinit var uploadButton: Button
        uploadButton = actionButton("Upload to Supabase") {
            val session = adminSession
            val mediaUri = selectedUploadUri
            if (session == null) { status.text = "Admin session nahi mili. Dobara login karein."; return@actionButton }
            if (mediaUri == null) { status.text = "Pehle media file select karein."; return@actionButton }
            val titleValue = titleField.text.toString().trim()
            if (titleValue.isBlank()) { titleField.error = "Title zaroori hai"; return@actionButton }
            val mediaType = when (category.selectedItemPosition) {
                0 -> "video_songs"
                1 -> "video_tutorial"
                2 -> "video_prompt"
                3 -> "image_prompt"
                else -> "presets_assets"
            }
            val promptValue = promptField.text.toString().trim()
            if ((mediaType == "video_prompt" || mediaType == "image_prompt") && promptValue.isBlank()) {
                promptField.error = "Prompt zaroor likhein"
                return@actionButton
            }
            uploadButton.isEnabled = false
            uploadButton.text = "Uploading..."
            status.text = "File upload ho rahi hai. App band na karein..."
            SupabaseClient.uploadMedia(this, session, mediaUri, selectedPosterUri,
                titleValue, descriptionField.text.toString().trim(), mediaType, promptValue) { result ->
                if (isFinishing || isDestroyed) return@uploadMedia
                uploadButton.isEnabled = true
                uploadButton.text = "Upload to Supabase"
                result.onSuccess {
                    status.text = "Upload successful! Website aur app dono par available hai."
                    selectedUploadUri = null
                    selectedPosterUri = null
                    mediaLabel.text = "No media file selected"
                    posterLabel.text = "Poster: optional"
                    titleField.text.clear()
                    descriptionField.text.clear()
                    promptField.text.clear()
                    Toast.makeText(this, "Media upload ho gaya.", Toast.LENGTH_LONG).show()
                }.onFailure { e ->
                    status.text = "Upload failed: ${e.message}"
                    Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        add(uploadButton, 16)
        add(status, 10)
        add(actionButton("Back to Admin Dashboard") { showAdminPage() }, 18)
        add(actionButton("Back to Home") { showHome() }, 8)
        setContentView(ScrollView(this).apply { setBackgroundColor(black); addView(page) })
    }



        private fun showUserUploadPostPage() {
        val prefs = getSharedPreferences("auth_session", MODE_PRIVATE)
        val token = prefs.getString("access_token", "").orEmpty()
        val userId = prefs.getString("user_id", "").orEmpty()
        profileAccessToken = token
        profileUserId = userId
        if (token.isBlank() || userId.isBlank()) {
            startActivity(Intent(this, AuthActivity::class.java)); finish(); return
        }
        selectedUserPostUri = null
        selectedUserPostType = ""
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(black)
        }
        page.addView(actionButton("‹ Back to Profile") { showProfilePage() }, LinearLayout.LayoutParams(-1, dp(44)))
        page.addView(text("UPLOAD POST", 24f, emerald, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        page.addView(text("Apni post aur profile details yahan save karein.", 13f, gold).apply { setPadding(0, 0, 0, dp(12)) })

        fun formBox(title: String): LinearLayout {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(16))
                background = rounded(panel, gold)
            }
            box.addView(text(title, 17f, gold, true).apply { setPadding(0, 0, 0, dp(12)) })
            page.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            return box
        }
        fun field(hintText: String, multiline: Boolean = false): EditText =
            EditText(this).apply {
                hint = hintText
                if (multiline) { minLines = 3; gravity = Gravity.TOP } else setSingleLine(true)
                setTextColor(white); setHintTextColor(Color.LTGRAY)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(black, emerald)
            }
        fun addBoxButton(box: LinearLayout, label: String, action: () -> Unit) {
            box.addView(actionButton(label, action), LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        }

        val postBox = formBox("BOX 1 — POST UPLOAD")
        val titleField = field("Name / Title")
        val descriptionField = field("Description", true)
        val linkField = field("All Media Video Link Paste")
        val selectedLabel = text("Gallery se abhi koi media select nahi hua.", 12f, Color.LTGRAY)
        val status = text("Post ki type aur privacy select karein.", 12f, gold)
        var selectedVisibility = "public"
        val visibilityGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val publicChoice = RadioButton(this).apply { id = View.generateViewId(); text = "Public Post"; setTextColor(white); isChecked = true }
        val privateChoice = RadioButton(this).apply { id = View.generateViewId(); text = "Private Post"; setTextColor(white) }
        visibilityGroup.addView(publicChoice); visibilityGroup.addView(privateChoice)
        visibilityGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedVisibility = if (checkedId == privateChoice.id) "private" else "public"
        }
        postBox.addView(visibilityGroup)
        postBox.addView(text("Note: Private Post mein pasted external link app mein hidden rahega, lekin original link provider par public ho sakta hai. Zyada privacy ke liye file upload karein.", 11f, Color.LTGRAY))
        addBoxButton(postBox, "🎬  Video Upload") {
            selectedUserPostType = "video"
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "video/*"
            }, PICK_USER_POST_FILE)
        }
        addBoxButton(postBox, "🖼  Image Upload") {
            selectedUserPostType = "image"
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
            }, PICK_USER_POST_FILE)
        }
        postBox.addView(selectedLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        postBox.addView(linkField, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        postBox.addView(descriptionField, LinearLayout.LayoutParams(-1, dp(94)).apply { topMargin = dp(10) })
        postBox.addView(titleField, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        addBoxButton(postBox, "💾  Post Save") {
            val title = titleField.text.toString().trim()
            if (title.isBlank()) { titleField.error = "Name / Title zaroori hai"; return@addBoxButton }
            val link = linkField.text.toString().trim()
            val uri = selectedUserPostUri
            if (uri == null && link.isBlank()) {
                status.text = "Video/Image upload karein ya media link paste karein."
                return@addBoxButton
            }
            status.text = "Post save ho rahi hai..."
            val done: (Result<Unit>) -> Unit = { result ->
                if (!isFinishing && !isDestroyed) {
                    result.onSuccess {
                        Toast.makeText(this, "Post save ho gayi.", Toast.LENGTH_LONG).show()
                        selectedUserPostUri = null
                        showProfilePage()
                    }.onFailure { error ->
                        status.text = "Post save nahi hui: ${error.message}"
                        Toast.makeText(this, "Post save nahi hui: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            if (uri != null) {
                SupabaseClient.uploadUserPost(this, token, userId, uri, selectedUserPostType,
                    title, descriptionField.text.toString().trim(), selectedVisibility, done)
            } else {
                SupabaseClient.uploadUserPostLink(token, userId, link, title,
                    descriptionField.text.toString().trim(), selectedVisibility, done)
            }
        }
        postBox.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val profileBox = formBox("BOX 2 — PROFILE SETTINGS")
        val coverPreview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(black)
            contentDescription = "Cover image preview"
        }
        uploadCoverPreview = coverPreview
        profileBox.addView(coverPreview, LinearLayout.LayoutParams(-1, dp(100)))
        addBoxButton(profileBox, "Cover Image — Gallery se") {
            returnToUploadPostAfterImageEdit = true
            openProfileImagePicker(PICK_PROFILE_COVER)
        }
        addBoxButton(profileBox, "Profile Image — Gallery se") {
            returnToUploadPostAfterImageEdit = true
            openProfileImagePicker(PICK_PROFILE_AVATAR)
        }
        val fullNameField = field("Full Name")
        val usernameField = field("Username (@username)")
        profileBox.addView(fullNameField, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        profileBox.addView(usernameField, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        addBoxButton(profileBox, "💾  Save Profile") {
            val fullName = fullNameField.text.toString().trim().replace("@", "").trim()
            val usernameValue = usernameField.text.toString().trim().removePrefix("@").trim()
            if (fullName.isBlank()) { fullNameField.error = "Full Name zaroori hai"; return@addBoxButton }
            if (usernameValue.isBlank() || !usernameValue.matches(Regex("[A-Za-z0-9_.]{3,30}"))) {
                usernameField.error = "Username 3–30 characters, letters/numbers/._"
                return@addBoxButton
            }
            SupabaseClient.saveUserIdentity(token, userId, fullName, usernameValue) { result ->
                if (isFinishing || isDestroyed) return@saveUserIdentity
                result.onSuccess {
                    Toast.makeText(this, "Profile save ho gaya.", Toast.LENGTH_LONG).show()
                    showProfilePage()
                }.onFailure { error ->
                    Toast.makeText(this, "Profile save nahi hua: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(black); addView(page) })
        SupabaseClient.getUserProfile(token, userId) { result ->
            if (isFinishing || isDestroyed) return@getUserProfile
            result.onSuccess { profile ->
                profileAvatarPath = profile.avatarPath
                profileCoverPath = profile.coverPath
                fullNameField.setText(profile.displayName)
                usernameField.setText(profile.username)
                loadProfileImage(coverPreview, profileCoverPath)
            }
        }
    }

    private fun confirmUserLogout() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Kya aap apne account se logout karna chahte hain?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Logout") { _, _ -> logoutUser() }
            .show()
    }

    private fun logoutUser() {
        val token = getSharedPreferences("auth_session", MODE_PRIVATE)
            .getString("access_token", "").orEmpty()
        imageExecutor.execute {
            try {
                if (token.isNotBlank()) {
                    val connection = URL(
                        SupabaseConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/logout"
                    ).openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_KEY.trim())
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.responseCode
                    } finally {
                        connection.disconnect()
                    }
                }
            } catch (_: Exception) {
                // Clear this device's session even if the server token has expired.
            }
            getSharedPreferences("auth_session", MODE_PRIVATE).edit().clear().apply()
            mainHandler.post {
                Toast.makeText(this, "Aap logout ho gaye hain.", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this, AuthActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
        }
    }

    private fun showProfilePage(retryOnAuthError: Boolean = true) {
        isProfileVisible = true
        val prefs = getSharedPreferences("auth_session", MODE_PRIVATE)
        profileAccessToken = prefs.getString("access_token", "").orEmpty()
        profileUserId = prefs.getString("user_id", "").orEmpty()
        if (profileAccessToken.isBlank() || profileUserId.isBlank()) {
            startActivity(Intent(this, AuthActivity::class.java)); finish(); return
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(22)); setBackgroundColor(black)
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(actionButton("‹ Back") { showHome() }, LinearLayout.LayoutParams(-2, dp(42)))
        toolbar.addView(text("PROFILE", 18f, gold, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(42), 1f))
        toolbar.addView(text("⋮", 25f, white, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(8), 0)
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Profile Options")
                    .setItems(arrayOf("Back to Home", "Logout")) { _, which ->
                        if (which == 0) showHome() else confirmUserLogout()
                    }
                    .show()
            }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        page.addView(toolbar)

        val coverFrame = FrameLayout(this).apply { background = rounded(panel, gold); clipToOutline = true }
        val coverView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(panel); contentDescription = "Profile cover image"
        }
        coverFrame.addView(coverView, FrameLayout.LayoutParams(-1, -1))
        page.addView(coverFrame, LinearLayout.LayoutParams(-1, dp(154)).apply { topMargin = dp(8) })

        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(8))
        }
        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(panel); setStroke(dp(2), emerald) }
            clipToOutline = true; contentDescription = "Profile picture"
        }
        identity.addView(avatar, LinearLayout.LayoutParams(dp(82), dp(82)))
        val identityText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        val displayName = text("Your Name", 18f, white, true)
        val username = text("@username", 13f, Color.LTGRAY)
        identityText.addView(displayName); identityText.addView(username)
        identity.addView(identityText, LinearLayout.LayoutParams(0, -2, 1f)); page.addView(identity)

        val profileBioText = text("", 13f, Color.LTGRAY).apply { setPadding(dp(8), dp(4), dp(8), dp(2)); visibility = View.GONE }
        val profileLocationText = text("", 12f, Color.LTGRAY).apply { setPadding(dp(8), dp(2), dp(8), dp(8)); visibility = View.GONE }
        page.addView(profileBioText); page.addView(profileLocationText)

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(4), dp(12), dp(4), dp(12)); background = rounded(panel)
        }
        fun stat(value: String, label: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(text(value, 19f, white, true).apply { gravity = Gravity.CENTER })
            addView(text(label, 12f, Color.LTGRAY).apply { gravity = Gravity.CENTER })
        }
        val postsStat = stat("0", "Posts")
        val followersStat = stat("0", "Followers"); val followingStat = stat("0", "Following")
        stats.addView(postsStat, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(followersStat, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(followingStat, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(stats)

        postsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var memberPosts = emptyList<MediaItem>()
        var currentPostFilter = "new"
        fun renderProfilePosts() {
            val visible = when (currentPostFilter) {
                "old" -> memberPosts.filter { it.visibility == "public" }.sortedBy { it.createdAt }
                "private" -> memberPosts.filter { it.visibility == "private" }.sortedByDescending { it.createdAt }
                else -> memberPosts.filter { it.visibility == "public" }.sortedByDescending { it.createdAt }
            }
            showMediaItems(visible)
        }
        fun profileOption(label: String, filter: String) {
            page.addView(actionButton(label) {
                currentPostFilter = filter
                renderProfilePosts()
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(7) })
        }
        profileOption("＋  New Post", "new")
        profileOption("▦  Old Post", "old")
        profileOption("🔒  Private Post", "private")
        page.addView(actionButton("⬆  Upload Post mein jao") { showUserUploadPostPage() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(7) })
        page.addView(postsContainer, LinearLayout.LayoutParams(-1, -2))

        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(black); addView(page) })
        SupabaseClient.getMediaItems(profileAccessToken) { mediaResult ->
            if (!isFinishing && !isDestroyed) mediaResult.onSuccess { items ->
                allMediaItems = items
                memberPosts = items.filter {
                    it.createdBy == profileUserId &&
                        (it.storagePath.startsWith("user-posts/") ||
                         it.storagePath.startsWith("private-user-posts/") ||
                         (it.externalUrl.isNotBlank() && it.mediaType.equals("movie", true)))
                }
                (postsStat.getChildAt(0) as? TextView)?.text = memberPosts.size.toString()
                renderProfilePosts()
            }.onFailure { error ->
                if (error.message.orEmpty().contains("HTTP 401") && retryOnAuthError) {
                    SupabaseClient.refreshSession(this) { refreshResult ->
                        if (isFinishing || isDestroyed) return@refreshSession
                        refreshResult.onSuccess {
                            showProfilePage(false)
                        }.onFailure { refreshError ->
                            Toast.makeText(this, "Session refresh nahi hui: ${refreshError.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Profile posts load nahi huin: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        SupabaseClient.getUserProfile(profileAccessToken, profileUserId) { result ->
            if (isFinishing || isDestroyed) return@getUserProfile
            result.onSuccess { profile ->
                profileAvatarPath = profile.avatarPath
                profileCoverPath = profile.coverPath
                displayName.text = profile.displayName.replace("@", "").ifBlank { "Your Name" }
                username.text = "@" + profile.username.trim().removePrefix("@")
                if (profile.bio.isNotBlank()) {
                    profileBioText.text = profile.bio; profileBioText.visibility = View.VISIBLE
                }
                if (profile.location.isNotBlank()) {
                    profileLocationText.text = "📍 " + profile.location; profileLocationText.visibility = View.VISIBLE
                }
                loadProfileImage(avatar, profileAvatarPath)
                loadProfileImage(coverView, profileCoverPath)
                (followersStat.getChildAt(0) as? TextView)?.text = profile.followers.toString()
                (followingStat.getChildAt(0) as? TextView)?.text = profile.following.toString()
            }
        }
    }

private fun openProfileImagePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, requestCode)
    }

    private fun loadProfileImage(target: ImageView, path: String) {
        if (path.isBlank()) return
        imageExecutor.execute {
            val bitmap = runCatching {
                val connection = URL(SupabaseClient.getProfileImageUrl(path)).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally { connection.disconnect() }
            }.getOrNull()
            mainHandler.post {
                if (!isFinishing && !isDestroyed && bitmap != null) target.setImageBitmap(bitmap)
            }
        }
    }

    private fun uploadSelectedProfileImage(uri: Uri, kind: String) {
        Toast.makeText(this, "Image upload ho rahi hai...", Toast.LENGTH_SHORT).show()
        SupabaseClient.uploadProfileImage(this, profileAccessToken, profileUserId, uri, kind) { result ->
            if (isFinishing || isDestroyed) return@uploadProfileImage
            result.onSuccess { path ->
                if (kind == "avatar") profileAvatarPath = path else profileCoverPath = path
                SupabaseClient.saveProfileImages(
                    profileAccessToken, profileUserId, profileAvatarPath, profileCoverPath
                ) { saved ->
                    saved.onSuccess {
                        Toast.makeText(this, if (kind == "avatar") "DP update ho gayi." else "Cover image update ho gayi.", Toast.LENGTH_LONG).show()
                        if (returnToUploadPostAfterImageEdit) {
                            returnToUploadPostAfterImageEdit = false
                            if (kind == "cover") uploadCoverPreview?.let { loadProfileImage(it, profileCoverPath) }
                        } else {
                            showProfilePage()
                        }
                    }.onFailure { e ->
                        Toast.makeText(this, "Image upload ho gayi lekin profile save nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.onFailure { e ->
                Toast.makeText(this, "Image upload nahi hui: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        when (requestCode) {
            PICK_MEDIA_FILE -> {
                selectedUploadUri = uri
                Toast.makeText(this, "Media file select ho gayi.", Toast.LENGTH_SHORT).show()
            }
            PICK_POSTER_FILE -> {
                selectedPosterUri = uri
                Toast.makeText(this, "Poster select ho gaya.", Toast.LENGTH_SHORT).show()
            }
            PICK_PROFILE_COVER -> uploadSelectedProfileImage(uri, "cover")
            PICK_PROFILE_AVATAR -> uploadSelectedProfileImage(uri, "avatar")
            PICK_USER_POST_FILE -> {
                selectedUserPostUri = uri
                userPostSelectedLabel?.text = if (selectedUserPostType == "image") "Image selected: " + (uri.lastPathSegment ?: "Gallery image") else "Video selected: " + (uri.lastPathSegment ?: "Gallery video")
                Toast.makeText(this, if (selectedUserPostType == "image") "Image select ho gayi." else "Video select ho gayi.", Toast.LENGTH_SHORT).show()
            }
            PICK_LOGO_FILE -> {
                val session = adminSession
                if (session == null) {
                    Toast.makeText(this, "Admin session nahi mili. Dobara login karein.", Toast.LENGTH_LONG).show()
                    showAdminLogin()
                } else {
                    Toast.makeText(this, "Logo upload ho raha hai...", Toast.LENGTH_SHORT).show()
                    SupabaseClient.updateAppLogo(this, session, uri) { result ->
                        if (isFinishing || isDestroyed) return@updateAppLogo
                        result.onSuccess {
                            Toast.makeText(this, "App logo save ho gaya. Home par naya logo dikh jayega.", Toast.LENGTH_LONG).show()
                            showHome()
                        }.onFailure { error ->
                            Toast.makeText(this, "Logo save nahi hua: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun openAdminWebsite(label: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sajawalkhan12.github.io/ms-editor-media-hub-/"))
        try { startActivity(intent) } catch (_: Exception) { Toast.makeText(this, "$label open nahi ho saka.", Toast.LENGTH_LONG).show() }
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

        val sessionToken = if (isProfileVisible) getSharedPreferences("auth_session", MODE_PRIVATE).getString("access_token", null) else null
        SupabaseClient.getMediaItems(sessionToken) { result ->
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
                if (selectedCategory == "Home") {
                    item.visibility == "public"
                } else {
                    item.visibility == "public" && belongsToCategory(item, selectedCategory)
                }

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
        val isVideoPrompt = type.contains("video_prompt") ||
            type == "video_prompt" ||
            combined.contains("video prompt", true)
        return !isVideoPrompt && (
            type == "image_prompt" ||
                combined.contains("image prompt", true) ||
                (item.promptText.isNotBlank() && mediaKind(item) != "video")
            )
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
        // User-created posts belong to Home/Profile only, never to admin-managed categories.
        val isUserPost = item.createdBy.isNotBlank() &&
            (item.storagePath.startsWith("user-posts/") ||
             item.storagePath.startsWith("private-user-posts/") ||
             item.externalUrl.isNotBlank())
        if (isUserPost || item.visibility != "public") return false
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
                !isPrompt(item) && (
                    type in listOf("assets", "asset", "image", "movie", "trailer", "preset", "presets") ||
                        combined.contains("asset") || combined.contains("resource") ||
                        combined.contains("template") || kind == "image"
                    )

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

            val isMemberPost = item.createdBy.isNotBlank() &&
                (item.storagePath.startsWith("user-posts/") ||
                 item.storagePath.startsWith("private-user-posts/") ||
                 item.externalUrl.isNotBlank())
            if (isMemberPost) {
                val authorRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, dp(10))
                }
                val authorAvatar = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(black)
                        setStroke(dp(1), emerald)
                    }
                    clipToOutline = true
                    contentDescription = "Post owner profile photo"
                }
                authorRow.addView(authorAvatar, LinearLayout.LayoutParams(dp(42), dp(42)))
                if (item.profileAvatarPath.isNotBlank()) loadProfileImage(authorAvatar, item.profileAvatarPath)
                else authorAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
                val authorName = item.profileDisplayName.trim().ifBlank {
                    item.profileUsername.trim().removePrefix("@")
                }.ifBlank { "User" }
                val usernameLabel = "@" + item.profileUsername.trim().removePrefix("@").ifBlank { "user" }
                authorRow.addView(text("$authorName\n$usernameLabel", 13f, white, true)
                    .apply { setPadding(dp(9), 0, dp(8), 0) }, LinearLayout.LayoutParams(0, -2, 1f))

                val sessionPrefs = getSharedPreferences("auth_session", MODE_PRIVATE)
                val viewerId = sessionPrefs.getString("user_id", "").orEmpty()
                val viewerToken = sessionPrefs.getString("access_token", "").orEmpty()
                if (viewerId.isNotBlank() && viewerId != item.createdBy) {
                    val followButton = Button(this).apply {
                        text = "Follow"
                        textSize = 11f
                        isAllCaps = false
                        minWidth = 0; minimumWidth = 0
                        minHeight = 0; minimumHeight = 0
                        setPadding(dp(9), 0, dp(9), 0)
                        setTextColor(white)
                        background = rounded(black, emerald)
                        isEnabled = false
                    }
                    var isFollowing = false
                    SupabaseClient.isFollowing(viewerToken, viewerId, item.createdBy) { followed ->
                        if (!isFinishing && !isDestroyed) {
                            isFollowing = followed
                            followButton.text = if (followed) "Following" else "Follow"
                            followButton.isEnabled = true
                        }
                    }
                    followButton.setOnClickListener {
                        followButton.isEnabled = false
                        SupabaseClient.toggleFollow(viewerToken, viewerId, item.createdBy, isFollowing) { result ->
                            if (isFinishing || isDestroyed) return@toggleFollow
                            result.onSuccess { nowFollowing ->
                                isFollowing = nowFollowing
                                followButton.text = if (nowFollowing) "Following" else "Follow"
                            }.onFailure { error ->
                                Toast.makeText(this, "Follow update nahi hua: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                            followButton.isEnabled = true
                        }
                    }
                    authorRow.addView(followButton, LinearLayout.LayoutParams(-2, dp(38)))
                }
                if (viewerId.isNotBlank() && viewerId == item.createdBy) {
                    val more = text("⋮", 26f, gold, true).apply {
                        gravity = Gravity.CENTER
                        setPadding(dp(10), 0, dp(4), 0)
                        setOnClickListener { showUserPostMenu(item) }
                        contentDescription = "Edit or delete your post"
                    }
                    authorRow.addView(more, LinearLayout.LayoutParams(dp(38), dp(42)))
                }
                card.addView(authorRow)
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

            val isPrivateStoredMedia = item.visibility == "private" && item.storagePath.startsWith("private-user-posts/")
            val url = if (isPrivateStoredMedia) "" else mediaUrl(item.externalUrl.ifBlank { item.storagePath })
            val poster = mediaUrl(item.posterPath)
            val youtubeId = youtubeVideoId(item.externalUrl.ifBlank { url })
            if (item.visibility == "private") {
                card.addView(text("🔒 PRIVATE POST — Sirf aap dekh sakte hain", 12f, gold, true)
                    .apply { setPadding(0, dp(6), 0, dp(6)) })
            }
            if (isPrivateStoredMedia) {
                card.addView(actionButton("🔒 View Private Media") {
                    val prefs = getSharedPreferences("auth_session", MODE_PRIVATE)
                    val token = prefs.getString("access_token", "").orEmpty()
                    SupabaseClient.getSignedPrivateMediaUrl(item.storagePath, token) { signed ->
                        if (isFinishing || isDestroyed) return@getSignedPrivateMediaUrl
                        signed.onSuccess { signedUrl ->
                            when (mediaKind(item)) {
                                "video" -> addVideoThumbnail(card, "", signedUrl, "")
                                "audio" -> addNativeMediaPlayer(card, signedUrl, false)
                                "image" -> addImage(card, signedUrl, 16f / 9f)
                                else -> addImage(card, signedUrl, 16f / 9f)
                            }
                        }.onFailure { error ->
                            Toast.makeText(this, "Private media open nahi hui: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
            } else

            if (isImagePrompt(item)) {
                // Prompt posts use their poster as the visible preview; source media may be absent.
                val preview = poster.ifBlank { url }
                if (preview.isNotBlank()) addImage(card, preview, 16f / 9f)
                addViewPromptButton(card, item)
            } else if (isPrompt(item) && mediaKind(item) == "text") {
                // Text prompt records still display their saved poster.
                if (poster.isNotBlank()) addImage(card, poster, 16f / 9f)
                if (item.description.isNotBlank()) addExpandableDescription(card, item.description)
                addViewPromptButton(card, item)
            } else {
                if (item.description.isNotBlank()) {
                    addExpandableDescription(card, item.description, 7, 8)
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
            if (adminSession != null) {
                val deleteButton = actionButton("🗑 Delete Post") {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Delete post?")
                        .setMessage("Yeh post website aur app dono se delete hogi. Kya aap sure hain?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            val session = adminSession
                            if (session == null) {
                                Toast.makeText(this, "Admin dobara login karein.", Toast.LENGTH_LONG).show()
                            } else {
                                SupabaseClient.deleteMedia(session, item) { result ->
                                    result.onSuccess {
                                        Toast.makeText(this, "Post delete ho gayi.", Toast.LENGTH_SHORT).show()
                                        loadMediaItems()
                                    }.onFailure {
                                        Toast.makeText(this, "Delete failed: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }.show()
                }
                val deleteParams = LinearLayout.LayoutParams(-1, dp(44))
                deleteParams.topMargin = dp(6)
                card.addView(deleteButton, deleteParams)
            }

            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, 0, 0, dp(12))
            postsContainer.addView(card, params)
        }
    }

    private fun refreshCurrentFeedAfterUserPostChange() {
        SupabaseClient.getMediaItems { result ->
            if (isFinishing || isDestroyed) return@getMediaItems
            result.onSuccess { items ->
                allMediaItems = items
                if (isProfileVisible) showProfilePage()
                else if (::postsContainer.isInitialized) {
                    val query = if (::search.isInitialized) search.text.toString() else ""
                    filterMediaItems(query)
                }
            }.onFailure { error ->
                Toast.makeText(this, "Posts refresh nahi huin: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showUserPostMenu(item: MediaItem) {
        val prefs = getSharedPreferences("auth_session", MODE_PRIVATE)
        val token = prefs.getString("access_token", "").orEmpty()
        val userId = prefs.getString("user_id", "").orEmpty()
        if (token.isBlank() || userId != item.createdBy) {
            Toast.makeText(this, "Sirf apni post edit ya delete kar sakte hain.", Toast.LENGTH_LONG).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Your Post")
            .setItems(arrayOf("✏ Edit Post", "🗑 Delete Post")) { _, which ->
                if (which == 0) editUserPost(item, token, userId)
                else android.app.AlertDialog.Builder(this)
                    .setTitle("Delete post?")
                    .setMessage("Yeh post permanently delete ho jayegi. Kya aap sure hain?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        SupabaseClient.deleteUserPost(token, userId, item) { result ->
                            if (isFinishing || isDestroyed) return@deleteUserPost
                            result.onSuccess {
                                refreshCurrentFeedAfterUserPostChange()
                                Toast.makeText(this, "Aapki post delete ho gayi.", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Toast.makeText(this, "Delete failed: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.show()
            }.show()
    }

    private fun editUserPost(item: MediaItem, token: String, userId: String) {
        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
        }
        val titleField = EditText(this).apply {
            setText(item.title)
            hint = "Post title"
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            background = rounded(panel, emerald)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val descriptionField = EditText(this).apply {
            setText(item.description)
            hint = "Description"
            minLines = 3
            gravity = Gravity.TOP
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            background = rounded(panel, emerald)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        editor.addView(titleField, LinearLayout.LayoutParams(-1, dp(48)))
        editor.addView(descriptionField, LinearLayout.LayoutParams(-1, dp(100)).apply { topMargin = dp(10) })
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Edit Your Post")
            .setView(editor)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleField.text.toString().trim()
                if (title.isBlank()) {
                    titleField.error = "Title zaroori hai"
                    return@setOnClickListener
                }
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
                SupabaseClient.updateUserPost(token, userId, item.id, title, descriptionField.text.toString().trim()) { result ->
                    if (isFinishing || isDestroyed) return@updateUserPost
                    result.onSuccess {
                        dialog.dismiss()
                        Toast.makeText(this, "Post update ho gayi.", Toast.LENGTH_SHORT).show()
                        refreshCurrentFeedAfterUserPostChange()
                    }.onFailure { error ->
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        Toast.makeText(this, "Edit failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
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
                    if (item.description.isNotBlank()) addExpandableDescription(card, item.description)
                    addViewPromptButton(card, item)

                } else {
                    if (item.description.isNotBlank()) {
                        addExpandableDescription(card, item.description)
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
            if (item.visibility != "public") {
                Toast.makeText(this, "Private post ka public share link available nahi hai.", Toast.LENGTH_LONG).show()
            } else {
                val postUrl = "https://sajawalkhan12.github.io/ms-editor-media-hub-/?post=${Uri.encode(item.id)}"
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Post URL", postUrl))
                Toast.makeText(this, "Post ka website link copy ho gaya.", Toast.LENGTH_SHORT).show()
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
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
        layout.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, dp(220)))
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
                    list.removeAllViews()
                    if (data.comments.isEmpty()) {
                        list.addView(text("Abhi koi comment nahi.", 14f, Color.LTGRAY))
                    } else {
                        data.comments.forEach { comment ->
                            val row = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(dp(8), dp(8), dp(8), dp(8))
                                background = rounded(black)
                            }
                            row.addView(text("${comment.name}: ${comment.text}", 14f, white))
                            if (adminSession != null) {
                                val delete = actionButton("🗑 Delete Comment") {
                                    val session = adminSession
                                    if (session == null) {
                                        Toast.makeText(this, "Admin dobara login karein.", Toast.LENGTH_LONG).show()
                                    } else {
                                        android.app.AlertDialog.Builder(this)
                                            .setTitle("Delete comment?")
                                            .setMessage("Kya aap is comment ko delete karna chahte hain?")                                            .setNegativeButton("Cancel", null)
                                            .setPositiveButton("Delete") { _, _ ->
                                                SupabaseClient.deleteComment(session, comment.id) { deleted ->
                                                    deleted.onSuccess {
                                                        Toast.makeText(this, "Comment delete ho gaya.", Toast.LENGTH_SHORT).show()
                                                        refreshComments()
                                                    }.onFailure {
                                                        Toast.makeText(this, "Comment delete nahi hua: ${it.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }.show()
                                    }
                                }
                                row.addView(delete)
                            }
                            val params = LinearLayout.LayoutParams(-1, -2)
                            params.setMargins(0, 0, 0, dp(7))
                            list.addView(row, params)
                        }
                    }
                    stats.text = "${if (data.likedByMe) "♥ Liked" else "♥ Like"} · ${data.likes} likes     💬 ${data.comments.size} comments"
                }.onFailure {
                    list.removeAllViews()
                    list.addView(text("Comments load nahi huay: ${it.message}", 13f, Color.RED))
                }
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