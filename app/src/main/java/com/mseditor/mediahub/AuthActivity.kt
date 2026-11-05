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
        page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(24),dp(22),dp(28));setBackgroundColor(black)}
        setContentView(ScrollView(this).apply{isFillViewport=true;addView(page)})
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(ImageView(this).apply{setImageResource(R.mipmap.ic_launcher);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(dp(42),dp(42)))
        head.addView(txt("MS EDITOR MEDIA HUB",18f,Color.WHITE,true).apply{setPadding(dp(9),0,0,0)})
        add(head,2);add(txt("WELCOME TO YOUR MEDIA HUB",12f,gold,true),18)
        add(txt("Sign up ya login karein. Account ke baghair app open nahi hogi.",14f,Color.LTGRAY),6)
        val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        tabs.addView(btn("SIGN UP",signup){signup=true;showPage()},LinearLayout.LayoutParams(0,dp(48),1f))
        tabs.addView(btn("LOGIN",!signup){signup=false;showPage()},LinearLayout.LayoutParams(0,dp(48),1f))
        add(tabs,22)
        add(txt(if(signup)"CREATE YOUR ACCOUNT" else "LOGIN TO YOUR ACCOUNT",20f,green,true),18)
        val methods=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("google" to "Google","phone" to "Phone","email" to "Email").forEach{(key,title)->
            methods.addView(btn(title,method==key){method=key;showPage()},LinearLayout.LayoutParams(0,dp(46),1f).apply{marginEnd=dp(5)})
        }
        add(methods,14)
        if(signup) signupFields() else loginFields()
    }

    private fun signupFields(){
        val first=field("First name");val last=field("Second name");val username=field("@username")
        add(first,14);add(last);add(username)
        if(method=="google"){
            add(txt("Google account se continue karein. Google provider Supabase mein enabled hona zaroori hai.",13f,Color.LTGRAY),12)
            add(btn("Continue with Google",true){openGoogle()},16);return
        }
        val identity=if(method=="phone")field("+92 Phone number",android.text.InputType.TYPE_CLASS_PHONE) else field("Email address",android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val pass=field("Create password",android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val confirm=field("Confirm password",android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        add(identity,14);add(pass);add(confirm)
        add(btn("CREATE ACCOUNT  →",true){
            val f=first.text.toString().trim();val l=last.text.toString().trim();val u=username.text.toString().trim().removePrefix("@")
            val id=identity.text.toString().trim();val p=pass.text.toString()
            if(f.isBlank()||l.isBlank()||!u.matches(Regex("[A-Za-z0-9_]{3,24}"))){toast("First name, second name aur valid @username bharein.");return@btn}
            if(id.isBlank()||p.length<6||p!=confirm.text.toString()){toast("Phone/email aur matching password (6+ characters) zaroori hai.");return@btn}
            val body=JSONObject().put("password",p).put("data",JSONObject().put("first_name",f).put("last_name",l).put("username",u).put("full_name","$f $l"))
            if(method=="phone")body.put("phone",id) else body.put("email",id)
            authRequest("POST","/auth/v1/signup",body){json->
                val token=json.optString("access_token")
                if(token.isNotBlank())saveSession(json) else toast("Account request bhej di. Email/SMS confirmation agar enabled hai to code verify karna hoga; phir Login karein.")
            }
        },18)
    }

    private fun loginFields(){
        if(method=="google"){add(btn("Login with Google",true){openGoogle()},18);return}
        val identity=if(method=="phone")field("+92 Phone number",android.text.InputType.TYPE_CLASS_PHONE) else field("Email address",android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password=field("Password",android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        add(identity,18);add(password)
        add(btn("LOGIN  →",true){
            val id=identity.text.toString().trim();val p=password.text.toString()
            if(id.isBlank()||p.isBlank()){toast("Phone/email aur password enter karein.");return@btn}
            val body=JSONObject().put("password",p)
            if(method=="phone")body.put("phone",id) else body.put("email",id)
            authRequest("POST","/auth/v1/token?grant_type=password",body){saveSession(it)}
        },18)
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
