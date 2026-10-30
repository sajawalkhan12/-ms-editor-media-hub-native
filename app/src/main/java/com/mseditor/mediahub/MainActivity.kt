
package com.mseditor.mediahub

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private val black = Color.rgb(5, 10, 13)
    private val panel = Color.rgb(9, 29, 37)
    private val emerald = Color.rgb(0, 210, 190)
    private val gold = Color.rgb(224, 183, 83)
    private val white = Color.WHITE

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText

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
            if (stroke != 0) {
                setStroke(dp(1), stroke)
            }
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
            setOnClickListener {
                showCategory("Settings")
            }
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

        val refresh = text("Refresh", 13f, white, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(panel, emerald)
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Supabase connection ke baad posts refresh honge.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val refreshParams = LinearLayout.LayoutParams(-2, dp(46))
        refreshParams.setMargins(dp(8), 0, 0, 0)
        searchRow.addView(refresh, refreshParams)
        root.addView(searchRow)

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(18))
        }

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(0, 0, 1f)
        )

        categories.drop(1).forEach { category ->
            addCategorySection(category)
        }

        search.addTextChangedListener(object :
            android.text.TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                filterCategories(s.toString())
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
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
    }

    private fun addCategorySection(category: String) {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panel)
            tag = category
        }

        val heading = text(category, 19f, emerald, true)
        section.addView(heading)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(25), dp(12), dp(25))
            background = rounded(black, Color.rgb(30, 70, 75))
        }

        card.addView(text("♫", 35f, gold, true))
        card.addView(text("Media posts yahan load honge", 14f, white))
        card.addView(text("Supabase connection pending", 11f, Color.LTGRAY))

        section.addView(card)

        val viewButton = text("View  →", 14f, gold, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(5))
            setOnClickListener {
                showCategory(category)
            }
        }
        section.addView(viewButton)

        section.setOnClickListener {
            showCategory(category)
        }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, 0, 0, dp(13))
        content.addView(section, params)
    }

    private fun filterCategories(query: String) {
        for (i in 0 until content.childCount) {
            val item = content.getChildAt(i)
            val category = item.tag?.toString() ?: ""
            item.visibility =
                if (category.contains(query, true) || query.isBlank())
                    View.VISIBLE else View.GONE
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
        page.addView(text(name, 25f, gold, true).apply {
            setPadding(0, dp(35), 0, dp(25))
        })

        page.addView(
            text("Media content Supabase connection ke baad yahan load hoga.",
                15f, white).apply {
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

    override fun onBackPressed() {
        showHome()
    }
}
