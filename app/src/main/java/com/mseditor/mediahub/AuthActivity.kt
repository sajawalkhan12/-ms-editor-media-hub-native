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
    private fun txt(t:String,size:Float=14f,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply { text=t;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD }
    private fun field(h:String,type:Int=android.text.InputType.TYPE_CLASS_TEXT)=EditText(this).apply { hint=h;setSingleLine(true);textSize=15f;setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setPadding(dp(14),dp(13),dp(14),dp(13));background=bg(panel,green);inputType=type }
    private fun btn(t:String,primary:Boolean=false,action:()->Unit)=TextView(this).apply { text=t;gravity=Gravity.CENTER;textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(if(primary)black else Color.WHITE);background=bg(if(primary)green else panel,if(primary)0 else gold);setPadding(dp(12),dp(14),dp(12),dp(14));setOnClickListener{action()} }
    private fun add(v:android.view.View,top:Int=9){page.addView(v,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(top)})}

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK;showPage()}
    private fun showPage(){
        page=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER_HORIZONTAL
            setPadding(dp(22),dp(28),dp(22),dp(28))
            setBackgroundColor(black)
        }
        setContentView(ScrollView(this).apply{
            isFillViewport=true
            addView(page, ScrollView.LayoutParams(-1,-1))
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
        add(txt("SIGN UP",21f,gold,true),dp(24))

        val signupCard=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(16),dp(16),dp(18))
            background=bg(Color.rgb(8,24,22),gold)
        }
        page.addView(signupCard,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        val oldPage=page
        page=signupCard
        val signupEmail=field("Email",android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val signupUsername=field("Username")
        val signupPassword=field("Password",android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        add(signupEmail,2);add(signupUsername,12);add(signupPassword,12)
        add(btn("SIGN UP",true){
            val email=signupEmail.text.toString().trim()
            val username=signupUsername.text.toString().trim().removePrefix("@")
            val password=signupPassword.text.toString()
            if(email.isBlank()||!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){
                toast("Valid email address enter karein.");return@btn
            }
            if(!username.matches(Regex("[A-Za-z0-9_]{3,24}"))){
                toast("Username 3 se 24 letters, numbers ya underscore ka hona chahiye.");return@btn
            }
            if(password.length<6){toast("Password kam az kam 6 characters ka hona chahiye.");return@btn}
            val body=JSONObject().put("email",email).put("password",password)
                .put("data",JSONObject().put("username",username))
            authRequest("POST","/auth/v1/signup",body){json->
                val token=json.optString("access_token")
                if(token.isNotBlank())saveSession(json)
                else toast("Signup request bhej di. Email confirmation enabled ho to email verify karke Login karein.")
            }
        },16)
        page=oldPage

        add(txt("LOGIN",21f,gold,true),24)
        val loginCard=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(16),dp(16),dp(18))
            background=bg(Color.rgb(8,24,22),green)
        }
        page.addView(loginCard,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        page=loginCard
        val loginEmail=field("Email",android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val loginPassword=field("Password",android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        add(loginEmail,2);add(loginPassword,12)
        add(btn("LOGIN",true){
            val email=loginEmail.text.toString().trim()
            val password=loginPassword.text.toString()
            if(email.isBlank()||password.isBlank()){
                toast("Email aur password enter karein.");return@btn
            }
            val body=JSONObject().put("email",email).put("password",password)
            authRequest("POST","/auth/v1/token?grant_type=password",body){saveSession(it)}
        },16)
        page=oldPage
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
