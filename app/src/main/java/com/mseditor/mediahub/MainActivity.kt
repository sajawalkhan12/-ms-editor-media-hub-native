package com.mseditor.mediahub

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val black = Color.rgb(10, 12, 12)
    private val emerald = Color.rgb(0, 190, 120)
    private val gold = Color.rgb(224, 183, 83)
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun showHome() {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(20, 28, 20, 20)
        root.setBackgroundColor(black)

        val title = TextView(this).apply {
            text = "MS EDITOR"
            textSize = 30f
            setTextColor(emerald)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "MEDIA HUB"
            textSize = 18f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 28)
        }
        root.addView(subtitle)

        val categories = listOf(
            "Video Tutorial",
            "Video Songs",
            "Video Prompt",
            "Image Prompt",
            "Audio Songs"
        )

        categories.forEach { category ->
            val card = TextView(this).apply {
                text = "▶   $category"
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(18, 20, 18, 20)
                setBackgroundColor(Color.rgb(24, 38, 32))
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    showCategory(category)
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 12)
            root.addView(card, params)
        }

        val admin = TextView(this).apply {
            text = "Admin"
            textSize = 16f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(12, 18, 12, 18)
            setOnClickListener {
                showCategory("Admin")
            }
        }
        root.addView(admin)

        setContentView(root)
    }

    private fun showCategory(name: String) {
        root.removeAllViews()

        val heading = TextView(this).apply {
            text = name
            textSize = 26f
            setTextColor(emerald)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }
        root.addView(heading)

        val message = TextView(this).apply {
            text = "Content Supabase connection ke baad yahan load hoga."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(10, 20, 10, 30)
        }
        root.addView(message)

        val back = TextView(this).apply {
            text = "← Back to Home"
            textSize = 18f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(10, 20, 10, 20)
            setOnClickListener {
                showHome()
            }
        }
        root.addView(back)
    }
}
