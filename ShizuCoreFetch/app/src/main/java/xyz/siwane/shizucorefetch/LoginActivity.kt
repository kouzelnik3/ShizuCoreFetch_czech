package xyz.siwane.shizucorefetch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import xyz.siwane.shizucorefetch.databinding.ActivityLoginBinding
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var authWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // تفعيل الـ Edge-to-Edge الذكي
        EdgeToEdgeHelper.setup(this, binding.root)

        // التعديل الأول: التحقق من أن التوكن ليس Null وليس نصاً فارغاً
        val token = AuthManager.getToken(this)
        if (!token.isNullOrEmpty()) {
            navigateToHome()
            return
        }

        // التعديل الثاني: التحقق مما إذا كان هناك أمر ببدء تسجيل الدخول فوراً
        if (intent.getBooleanExtra("auto_start_github", false)) {
            startNativeGitHubLogin()
        }

        // زر تسجيل الدخول
        binding.btnLoginGithub.setOnClickListener {
            startNativeGitHubLogin()
        }

        // زر الدخول كزائر
        binding.btnGuestLogin.setOnClickListener {
            navigateToHome()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (authWebView != null) {
                    if (authWebView!!.canGoBack()) {
                        authWebView!!.goBack()
                    } else {
                        closeAuthWebView()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun startNativeGitHubLogin() {
        binding.btnLoginGithub.isEnabled = false
        binding.btnGuestLogin.isEnabled = false // تعطيل زر الزائر أثناء التحميل
        Toast.makeText(this, "Connecting to GitHub...", Toast.LENGTH_SHORT).show()
        
        val authUrl = Uri.parse("https://github.com/login/oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", Constants.GITHUB_CLIENT_ID)
            .appendQueryParameter("scope", "repo")
            .appendQueryParameter("redirect_uri", Constants.GITHUB_REDIRECT_URI)
            .build()
            .toString()

        authWebView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            elevation = 100f 
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    
                    if (url.startsWith(Constants.GITHUB_REDIRECT_URI)) {
                        val code = request?.url?.getQueryParameter("code")
                        if (code != null) {
                            Toast.makeText(this@LoginActivity, "Authenticating safely...", Toast.LENGTH_SHORT).show()
                            exchangeCodeForTokenSafely(code)
                        } else {
                            Toast.makeText(this@LoginActivity, "Login failed.", Toast.LENGTH_SHORT).show()
                            binding.btnGuestLogin.isEnabled = true
                        }
                        closeAuthWebView()
                        return true
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }
            }
        }

        (binding.root as ViewGroup).addView(authWebView)
        authWebView?.loadUrl(authUrl)
    }

    private fun closeAuthWebView() {
        authWebView?.let {
            (binding.root as ViewGroup).removeView(it)
            it.destroy()
            authWebView = null
        }
        binding.btnLoginGithub.isEnabled = true
        binding.btnGuestLogin.isEnabled = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val uri = intent.data
        if (uri != null && uri.scheme == "shizufetch") {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                binding.btnLoginGithub.isEnabled = false
                binding.btnGuestLogin.isEnabled = false
                Toast.makeText(this, "Authenticating safely...", Toast.LENGTH_SHORT).show()
                exchangeCodeForTokenSafely(code)
            }
            intent.data = null
        }
    }

    private fun exchangeCodeForTokenSafely(code: String) {
        thread {
            try {
                val tokenUrl = URL(Constants.GAS_URL)
                val connection = tokenUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true

                val postData = "code=$code"
                
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(postData)
                writer.flush()
                writer.close()

                if (connection.responseCode == HttpURLConnection.HTTP_OK || connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonObject = JSONObject(response)
                    
                    if (jsonObject.has("access_token")) {
                        val token = jsonObject.getString("access_token")
                        mainHandler.post {
                            AuthManager.saveToken(this@LoginActivity, token)
                            navigateToHome()
                        }
                    } else {
                        mainHandler.post {
                            binding.btnLoginGithub.isEnabled = true
                            binding.btnGuestLogin.isEnabled = true
                            Toast.makeText(this@LoginActivity, "Failed to get token from server", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    mainHandler.post {
                        binding.btnLoginGithub.isEnabled = true
                        binding.btnGuestLogin.isEnabled = true
                        Toast.makeText(this@LoginActivity, "Server Error: ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    binding.btnLoginGithub.isEnabled = true
                    binding.btnGuestLogin.isEnabled = true
                    Toast.makeText(this@LoginActivity, "Network Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}
