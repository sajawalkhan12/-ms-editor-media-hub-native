package com.mseditor.mediahub

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class AuthActivity : Activity() {
    private val black = Color.rgb(5,10,13)
    private val panel = Color.rgb(9,29,37)
    private val green = Color.rgb(0,210,190)
    private val gold = Color.rgb(224,183,83)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var page: LinearLayout
    private var signup = true
    private var method = "email"

    private fun dp(v:Int) = (v*resources.displayMetrics.density).toInt()
    private fun bg(c:Int,s:Int=0)=GradientDrawable().apply { setColor(c);cornerRadius=dp(14).toFloat();if(s!=0)setStroke(dp(1),s) }
    private fun txt(t:String,size:Float=14f,color:Int=Color.WHITE,bold:Boolean=false):TextView {
        val view=TextView(this).apply { text=t;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD }
        AppTranslator.translate(t) { translated -> if (view.text.toString()==t) view.text=translated }
        return view
    }
    private fun field(h:String,type:Int=android.text.InputType.TYPE_CLASS_TEXT):EditText {
        val view=EditText(this).apply { hint=h;setSingleLine(true);textSize=15f;setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setPadding(dp(14),dp(13),dp(14),dp(13));background=bg(panel,green);inputType=type }
        AppTranslator.translate(h) { translated -> if (view.hint.toString()==h) view.hint=translated }
        return view
    }
    private fun btn(t:String,primary:Boolean=false,action:()->Unit):TextView {
        val view=TextView(this).apply { text=t;gravity=Gravity.CENTER;textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(if(primary)black else Color.WHITE);background=bg(if(primary)green else panel,if(primary)0 else gold);setPadding(dp(12),dp(14),dp(12),dp(14));setOnClickListener{action()} }
        AppTranslator.translate(t) { translated -> if (view.text.toString()==t) view.text=translated }
        return view
    }
    private fun add(v:android.view.View,top:Int=9){page.addView(v,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(top)})}

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.BLACK
        window.navigationBarColor=Color.BLACK
        val prefs=getSharedPreferences("app_settings",MODE_PRIVATE)
        AppTranslator.configure(this, prefs.getString("language_code","en").orEmpty())
        if(prefs.getString("language_code",null).isNullOrBlank()) showLanguagePage() else showPage()
    }

    override fun onResume() {
        super.onResume()
        val language = getSharedPreferences("app_settings", MODE_PRIVATE).getString("language_code", "en").orEmpty()
        AppTranslator.configure(this, language)
    }

    private fun showLanguagePage(){
        page=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER_HORIZONTAL
            setPadding(dp(22),dp(28),dp(22),dp(28))
            setBackgroundColor(black)
        }
        setContentView(ScrollView(this).apply{
            isFillViewport=true
            addView(page,android.view.ViewGroup.LayoutParams(-1,-1))
        })
        val head=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
        }
        head.addView(ImageView(this).apply{
            setImageResource(R.mipmap.ic_launcher)
            scaleType=ImageView.ScaleType.FIT_CENTER
        },LinearLayout.LayoutParams(dp(76),dp(76)))
        head.addView(txt("MS EDITOR MEDIA HUB",20f,Color.WHITE,true).apply{
            gravity=Gravity.CENTER
            setPadding(0,dp(10),0,0)
        })
        add(head,dp(18))
        add(txt("SELECT LANGUAGE",21f,gold,true),dp(24))
        add(txt("Choose your language to continue",15f,Color.LTGRAY),12)
        val languageNames=listOf(
            "English",
            "Urdu",
            "Hindi",
            "Sindhi",
            "Punjabi",
            "Arabic",
            "Bengali",
            "Pashto",
            "Persian (Farsi)",
            "Balochi",
            "Saraiki",
            "Kashmiri",
            "Gujarati",
            "Marathi",
            "Tamil",
            "Telugu",
            "Kannada",
            "Malayalam",
            "Odia",
            "Assamese",
            "Nepali",
            "Sinhala",
            "Chinese (Simplified)",
            "Chinese (Traditional)",
            "Japanese",
            "Korean",
            "Thai",
            "Vietnamese",
            "Indonesian",
            "Malay",
            "Filipino (Tagalog)",
            "Burmese",
            "Khmer",
            "Lao",
            "Mongolian",
            "Spanish",
            "French",
            "Portuguese",
            "Brazilian Portuguese",
            "Italian",
            "German",
            "Dutch",
            "Danish",
            "Swedish",
            "Norwegian",
            "Finnish",
            "Icelandic",
            "Polish",
            "Czech",
            "Slovak",
            "Hungarian",
            "Romanian",
            "Bulgarian",
            "Greek",
            "Albanian",
            "Croatian",
            "Serbian",
            "Bosnian",
            "Slovenian",
            "Macedonian",
            "Ukrainian",
            "Russian",
            "Belarusian",
            "Lithuanian",
            "Latvian",
            "Estonian",
            "Turkish",
            "Azerbaijani",
            "Kazakh",
            "Uzbek",
            "Kyrgyz",
            "Turkmen",
            "Tajik",
            "Armenian",
            "Georgian",
            "Hebrew",
            "Kurdish",
            "Amharic",
            "Somali",
            "Swahili",
            "Hausa",
            "Yoruba",
            "Igbo",
            "Zulu",
            "Xhosa",
            "Afrikaans",
            "Shona",
            "Kinyarwanda",
            "Lingala",
            "Malagasy",
            "Sesotho",
            "Setswana",
            "Wolof",
            "Oromo",
            "Tigrinya",
            "Fula",
            "Chichewa",
            "Twi",
            "Ewe",
            "Maori",
            "Samoan",
            "Tongan",
            "Fijian",
            "Haitian Creole",
            "Jamaican Patois",
            "Quechua",
            "Aymara",
            "Guarani",
            "Nahuatl",
            "Inuktitut",
            "Welsh",
            "Irish",
            "Scottish Gaelic",
            "Basque",
            "Catalan",
            "Galician",
            "Occitan",
            "Luxembourgish",
            "Esperanto"
        )
        val languageCodes=listOf(
            "en",
            "ur",
            "hi",
            "sd",
            "pa",
            "ar",
            "bn",
            "ps",
            "fa",
            "bal",
            "skr",
            "ks",
            "gu",
            "mr",
            "ta",
            "te",
            "kn",
            "ml",
            "or",
            "as",
            "ne",
            "si",
            "zh-CN",
            "zh-TW",
            "ja",
            "ko",
            "th",
            "vi",
            "id",
            "ms",
            "fil",
            "my",
            "km",
            "lo",
            "mn",
            "es",
            "fr",
            "pt",
            "pt-BR",
            "it",
            "de",
            "nl",
            "da",
            "sv",
            "no",
            "fi",
            "is",
            "pl",
            "cs",
            "sk",
            "hu",
            "ro",
            "bg",
            "el",
            "sq",
            "hr",
            "sr",
            "bs",
            "sl",
            "mk",
            "uk",
            "ru",
            "be",
            "lt",
            "lv",
            "et",
            "tr",
            "az",
            "kk",
            "uz",
            "ky",
            "tk",
            "tg",
            "hy",
            "ka",
            "he",
            "ku",
            "am",
            "so",
            "sw",
            "ha",
            "yo",
            "ig",
            "zu",
            "xh",
            "af",
            "sn",
            "rw",
            "ln",
            "mg",
            "st",
            "tn",
            "wo",
            "om",
            "ti",
            "ff",
            "ny",
            "tw",
            "ee",
            "mi",
            "sm",
            "to",
            "fj",
            "ht",
            "jam",
            "qu",
            "ay",
            "gn",
            "nah",
            "iu",
            "cy",
            "ga",
            "gd",
            "eu",
            "ca",
            "gl",
            "oc",
            "lb",
            "eo"
        )
        val supportedCodes=setOf("af","ar","be","bg","bn","ca","cs","cy","da","de","el","en","eo","es","et","fa","fi","fr","ga","gl","gu","he","hi","hr","ht","hu","id","is","it","ja","ka","kn","ko","lt","lv","mk","mr","ms","nl","no","pl","pt","ro","ru","sk","sl","sq","sv","sw","ta","te","th","tl","tr","uk","ur","vi","zh")
        val supportedIndices=languageCodes.indices.filter { AppTranslator.normalizeCode(languageCodes[it]) in supportedCodes }.distinctBy { AppTranslator.normalizeCode(languageCodes[it]) }
        val pickerNames=supportedIndices.map { languageNames[it] }
        val picker=Spinner(this).apply{
            adapter=ArrayAdapter(this@AuthActivity,android.R.layout.simple_spinner_dropdown_item,pickerNames)
            background=bg(panel,green)
        }
        add(picker,18)
        add(btn("CONTINUE",true){
            val position=picker.selectedItemPosition.coerceIn(supportedIndices.indices)
            val index=supportedIndices[position]
            getSharedPreferences("app_settings",MODE_PRIVATE).edit()
                .putString("language_code",languageCodes[index])
                .putString("language_name",languageNames[index]).apply()
            AppTranslator.configure(this,languageCodes[index])
            showPage()
        },22)
    }

    private fun showPage() = showSignUpPage()

    private fun showSignUpPage() {
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(black)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(page, android.view.ViewGroup.LayoutParams(-1, -1))
        })
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        head.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher); scaleType = ImageView.ScaleType.FIT_CENTER },
            LinearLayout.LayoutParams(dp(76), dp(76)))
        head.addView(txt("MS EDITOR MEDIA HUB", 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0)
        })
        add(head, dp(18))
        add(txt("SIGN UP", 21f, gold, true), dp(24))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = bg(Color.rgb(8, 24, 22), gold)
        }
        page.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val oldPage = page
        page = card
        val firstName = field("First Name")
        val lastName = field("Last Name")
        val username = field("Username")
        val email = field("Email", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = field("Password", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val confirm = field("Confirm Password", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        add(firstName, 2); add(lastName, 10); add(username, 10); add(email, 10); add(password, 10); add(confirm, 10)
        add(btn("SIGN UP", true) {
            val first = firstName.text.toString().trim()
            val last = lastName.text.toString().trim()
            val user = username.text.toString().trim().removePrefix("@")
            val mail = email.text.toString().trim()
            val pass = password.text.toString()
            val confirmPass = confirm.text.toString()
            if (first.isBlank()) { firstName.error = "First Name zaroori hai"; return@btn }
            if (last.isBlank()) { lastName.error = "Last Name zaroori hai"; return@btn }
            if (!user.matches(Regex("[A-Za-z0-9_]{3,24}"))) { username.error = "Username 3 se 24 letters, numbers ya underscore"; return@btn }
            if (mail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { email.error = "Valid email enter karein"; return@btn }
            if (pass.length < 6) { password.error = "Password kam az kam 6 characters"; return@btn }
            if (pass != confirmPass) { confirm.error = "Passwords match nahi karte"; return@btn }
            val metadata = JSONObject().put("first_name", first).put("last_name", last)
                .put("full_name", "$first $last").put("username", user)
            val body = JSONObject().put("email", mail).put("password", pass).put("data", metadata)
            authRequest("POST", "/auth/v1/signup", body) { json ->
                val access = json.optString("access_token")
                if (access.isBlank()) toast("Signup ho gaya. Email confirmation required ho to pehle email verify karein.")
                showLoginPage(mail)
            }
        }, 16)
        page = oldPage
        add(btn("Already have an account? LOGIN") { showLoginPage("") }, 14)
    }

    private fun showLoginPage(prefillEmail: String) {
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(black)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(page, android.view.ViewGroup.LayoutParams(-1, -1))
        })
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        head.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher); scaleType = ImageView.ScaleType.FIT_CENTER },
            LinearLayout.LayoutParams(dp(76), dp(76)))
        head.addView(txt("MS EDITOR MEDIA HUB", 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0)
        })
        add(head, dp(18))
        add(txt("LOGIN", 21f, gold, true), dp(24))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = bg(Color.rgb(8, 24, 22), green)
        }
        page.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val oldPage = page
        page = card
        val email = field("Email", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).apply { setText(prefillEmail) }
        val password = field("Password", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        add(email, 2); add(password, 12)
        add(btn("LOGIN", true) {
            val mail = email.text.toString().trim()
            val pass = password.text.toString()
            if (mail.isBlank() || pass.isBlank()) { toast("Email aur password enter karein."); return@btn }
            authRequest("POST", "/auth/v1/token?grant_type=password",
                JSONObject().put("email", mail).put("password", pass)) { saveSession(it) }
        }, 16)
        page = oldPage
        add(btn("New user? SIGN UP") { showSignUpPage() }, 14)
    }

    private fun authRequest(http:String,path:String,body:JSONObject,done:(JSONObject)->Unit){
        toast("Please wait...")
        worker.execute{
            val result=runCatching{
                val c=URL(SupabaseConfig.SUPABASE_URL.trimEnd('/')+path).openConnection() as HttpURLConnection
                try{
                    c.requestMethod=http;c.connectTimeout=25000;c.readTimeout=25000
                    c.setRequestProperty("apikey",SupabaseConfig.SUPABASE_KEY);c.setRequestProperty("Content-Type","application/json")
                    c.doOutput=true;c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
                    val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream
                    val raw=stream?.bufferedReader()?.use{it.readText()}?:"{}"
                    if(code !in 200..299){val e=JSONObject(raw);throw Exception(e.optString("msg").ifBlank{e.optString("message")}.ifBlank{e.optString("error_description")}.ifBlank{"Supabase HTTP $code"})}
                    JSONObject(raw)
                }finally{c.disconnect()}
            }
            runOnUiThread{result.onSuccess(done).onFailure{toast(it.message?:"Authentication failed.")}}
        }
    }

    private fun openGoogle(){
        val redirect=URLEncoder.encode("msmediahub://auth-callback","UTF-8")
        val url=SupabaseConfig.SUPABASE_URL.trimEnd('/')+"/auth/v1/authorize?provider=google&redirect_to=$redirect"
        try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(e:Exception){toast("Google login open nahi hua: ${e.message}")}
    }
    private fun saveSession(json:JSONObject){
        val token=json.optString("access_token");val user=json.optJSONObject("user")?:JSONObject()
        if(token.isBlank()){toast("Supabase ne session nahi diya. Email/SMS verification ya provider settings check karein.");return}
        getSharedPreferences("auth_session",MODE_PRIVATE).edit().putString("access_token",token).putString("refresh_token",json.optString("refresh_token")).putString("user_id",user.optString("id")).putString("login_method",method).apply()
        startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK));finish()
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
}
