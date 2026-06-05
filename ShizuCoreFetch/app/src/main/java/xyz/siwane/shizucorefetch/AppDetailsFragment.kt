package xyz.siwane.shizucorefetch

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.RoundedCornersTransformation
import io.noties.markwon.Markwon
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import xyz.siwane.shizucorefetch.databinding.FragmentAppDetailsBinding
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AppDetailsFragment : Fragment() {

    private var _binding: FragmentAppDetailsBinding? = null
    private val binding get() = _binding!!
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var markwon: Markwon
    
    private var currentPackageName: String = ""
    private var currentAppName: String = ""
    
    private lateinit var versionAdapter: VersionAdapter
    private val releaseList = mutableListOf<ReleaseModel>()

    companion object {
        fun newInstance(appId: String, appName: String, developer: String, iconUrl: String, desc: String, packageName: String = ""): AppDetailsFragment {
            val fragment = AppDetailsFragment()
            val args = Bundle()
            args.putString("APP_ID", appId)
            args.putString("APP_NAME", appName)
            args.putString("APP_DEVELOPER", developer)
            args.putString("APP_ICON_URL", iconUrl)
            args.putString("APP_DESC", desc)
            args.putString("APP_PACKAGE", packageName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        markwon = Markwon.create(requireContext())

        binding.toolbarDetails.setNavigationOnClickListener { 
            parentFragmentManager.popBackStack() 
        }

        val appId = arguments?.getString("APP_ID") ?: ""
        currentAppName = arguments?.getString("APP_NAME") ?: ""
        val appDeveloper = arguments?.getString("APP_DEVELOPER") ?: ""
        val appIconUrl = arguments?.getString("APP_ICON_URL") ?: ""
        val appDescription = arguments?.getString("APP_DESC") ?: ""
        
        currentPackageName = arguments?.getString("APP_PACKAGE") ?: ""
        if (currentPackageName.isEmpty()) {
            currentPackageName = getPackageNameFromSystem(currentAppName, appId)
        }

        binding.tvDetailsName.text = currentAppName
        val devPrefix = getString(R.string.store_developer_prefix)
        binding.tvDetailsDeveloper.text = "$devPrefix $appDeveloper"
        binding.tvDetailsReadme.text = getString(R.string.downloader_searching)
        
        binding.ivDetailsIcon.load(appIconUrl) { crossfade(true) }

        setupButtons(appDeveloper, appId, currentAppName)
        setupVersionsRecyclerView(appDeveloper, appId, currentAppName)
        setupSwipeRefresh(appDeveloper, currentAppName, appDescription)
        
        fetchStoreDataFromGithub(appDeveloper, currentAppName, appDescription)
        fetchReleasesFromGithub(appDeveloper, currentAppName)
    }

    private fun setupSwipeRefresh(appDeveloper: String, appName: String, appDescription: String) {
        binding.swipeRefreshDetails.setColorSchemeColors(android.graphics.Color.parseColor("#00d1b2"))
        binding.swipeRefreshDetails.setOnRefreshListener {
            binding.tvDetailsReadme.text = getString(R.string.downloader_searching)
            fetchStoreDataFromGithub(appDeveloper, appName, appDescription)
            fetchReleasesFromGithub(appDeveloper, appName)
        }
    }

    private fun fetchStoreDataFromGithub(developer: String, repoName: String, fallbackDesc: String) {
        thread {
            try {
                val isOfficialShizuku = developer.equals("rikkaapps", ignoreCase = true) && 
                                        repoName.equals("shizuku", ignoreCase = true)
                
                val apiUrl = if (isOfficialShizuku) {
                    "https://api.github.com/repos/elhizazi1/DrawixPRO/contents/shizuku_official_store.json"
                } else {
                    "https://api.github.com/repos/$developer/$repoName/contents/shizu_store.json"
                }

                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3.raw")
                connection.setRequestProperty("Cache-Control", "no-cache")

                val token = AuthManager.getToken(requireContext())
                if (!token.isNullOrEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    var jsonText = connection.inputStream.bufferedReader().readText().trim()
                    try {
                        val tempObj = JSONObject(jsonText)
                        if (tempObj.has("content") && tempObj.optString("encoding") == "base64") {
                            val base64Content = tempObj.getString("content").replace("\n", "")
                            jsonText = String(Base64.decode(base64Content, Base64.DEFAULT), Charsets.UTF_8).trim()
                        }
                    } catch (e: Exception) { }

                    val cleanJson = if (jsonText.startsWith("\uFEFF")) jsonText.substring(1) else jsonText
                    val jsonObject = JSONObject(cleanJson)
                    applyStoreData(jsonObject, developer, repoName, fallbackDesc)
                } else {
                    fetchReadmeFromGithub(developer, repoName, fallbackDesc)
                }
            } catch (e: Exception) {
                fetchReadmeFromGithub(developer, repoName, fallbackDesc)
            }
        }
    }

    private fun applyStoreData(jsonObject: JSONObject, developer: String, repoName: String, fallbackDesc: String) {
        val currentLang = java.util.Locale.getDefault().language
        var description = jsonObject.optString("detailed_description", "")
        var bannerUrl = jsonObject.optString("banner_url", "")
        var iconUrl = jsonObject.optString("icon_url", "")
        
        if (jsonObject.has("locales")) {
            val locales = jsonObject.optJSONObject("locales")
            if (locales != null && locales.has(currentLang)) {
                val localizedData = locales.optJSONObject(currentLang)
                if (localizedData != null) {
                    if (localizedData.has("detailed_description")) description = localizedData.optString("detailed_description", description)
                    if (localizedData.has("banner_url")) bannerUrl = localizedData.optString("banner_url", bannerUrl)
                    if (localizedData.has("icon_url")) iconUrl = localizedData.optString("icon_url", iconUrl)
                }
            }
        }
        
        val screenshots = mutableListOf<String>()
        if (jsonObject.has("screenshots")) {
            val arr = jsonObject.optJSONArray("screenshots")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    screenshots.add(arr.optString(i, ""))
                }
            }
        }
        
        mainHandler.post {
            if (!isAdded || _binding == null) return@post
            binding.swipeRefreshDetails.isRefreshing = false
            
            if (description.isNotEmpty()) {
                markwon.setMarkdown(binding.tvDetailsReadme, description)
            } else {
                fetchReadmeFromGithub(developer, repoName, fallbackDesc)
            }
            
            if (bannerUrl.isNotEmpty()) {
                binding.ivDetailsBanner.visibility = View.VISIBLE
                binding.ivDetailsBanner.load(buildImageUrl(developer, repoName, bannerUrl)) { crossfade(true) }
            }
            if (iconUrl.isNotEmpty()) {
                binding.ivDetailsIcon.load(buildImageUrl(developer, repoName, iconUrl)) { crossfade(true) }
            }
            
            if (screenshots.isNotEmpty()) {
                binding.tvScreenshotsTitle.visibility = View.VISIBLE
                binding.hsvScreenshots.visibility = View.VISIBLE
                binding.llScreenshots.removeAllViews()
                
                val density = resources.displayMetrics.density
                val heightPx = (280 * density).toInt()
                val marginPx = (8 * density).toInt()

                for (screenshot in screenshots) {
                    if (screenshot.isEmpty()) continue
                    val imageView = ImageView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, heightPx).apply {
                            marginEnd = marginPx
                        }
                        adjustViewBounds = true
                    }
                    binding.llScreenshots.addView(imageView)
                    imageView.load(buildImageUrl(developer, repoName, screenshot)) {
                        crossfade(true)
                        transformations(RoundedCornersTransformation(8f * density))
                    }
                }
            }
        }
    }

    private fun fetchReadmeFromGithub(developer: String, repoName: String, fallbackDesc: String) {
        thread {
            try {
                val apiUrl = "https://api.github.com/repos/$developer/$repoName/readme"
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3.raw")

                val token = AuthManager.getToken(requireContext())
                if (!token.isNullOrEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val readmeText = connection.inputStream.bufferedReader().readText()
                    mainHandler.post {
                        if (isAdded) {
                            binding.swipeRefreshDetails.isRefreshing = false
                            markwon.setMarkdown(binding.tvDetailsReadme, readmeText)
                        }
                    }
                } else {
                    mainHandler.post {
                        if (isAdded) {
                            binding.swipeRefreshDetails.isRefreshing = false
                            binding.tvDetailsReadme.text = fallbackDesc
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (isAdded) {
                        binding.swipeRefreshDetails.isRefreshing = false
                        binding.tvDetailsReadme.text = fallbackDesc
                    }
                }
            }
        }
    }

    private fun fetchReleasesFromGithub(developer: String, repoName: String) {
        thread {
            try {
                val apiUrl = "https://api.github.com/repos/$developer/$repoName/releases"
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                val token = AuthManager.getToken(requireContext())
                if (!token.isNullOrEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonArray = JSONArray(response)
                    val newReleases = mutableListOf<ReleaseModel>()
                    
                    for (i in 0 until jsonArray.length()) {
                        val releaseObj = jsonArray.getJSONObject(i)
                        val versionName = releaseObj.getString("tag_name")
                        val publishedAt = releaseObj.getString("published_at").substring(0, 10)
                        
                        val assets = releaseObj.getJSONArray("assets")
                        var downloadUrl = ""
                        var fileName = ""
                        
                        for (j in 0 until assets.length()) {
                            val asset = assets.getJSONObject(j)
                            val name = asset.getString("name")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                fileName = name
                                break
                            }
                        }
                        if (downloadUrl.isNotEmpty()) {
                            newReleases.add(ReleaseModel(versionName, publishedAt, downloadUrl, fileName))
                        }
                    }
                    
                    mainHandler.post {
                        if (!isAdded) return@post
                        binding.swipeRefreshDetails.isRefreshing = false
                        releaseList.clear()
                        releaseList.addAll(newReleases)
                        versionAdapter.notifyDataSetChanged()
                    }
                } else {
                    mainHandler.post { if (isAdded) binding.swipeRefreshDetails.isRefreshing = false }
                }
            } catch (e: Exception) {
                mainHandler.post { if (isAdded) binding.swipeRefreshDetails.isRefreshing = false }
            }
        }
    }

    private fun buildImageUrl(developer: String, repoName: String, path: String): String {
        if (path.startsWith("http")) return path
        return "https://cdn.jsdelivr.net/gh/$developer/$repoName/$path"
    }

    private fun setupVersionsRecyclerView(developer: String, appId: String, appName: String) {
        binding.rvVersions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVersions.isNestedScrollingEnabled = false
        
        versionAdapter = VersionAdapter(releaseList) { release ->
            installSpecificVersion(developer, appId, appName, release)
        }
        binding.rvVersions.adapter = versionAdapter
    }

    private fun getPackageNameFromSystem(appName: String, appId: String): String {
        val savedPkg = AppCacheManager.getSavedPackageFast(requireContext(), appId)
        if (savedPkg != null) return savedPkg

        val pm = requireContext().packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val cleanAppName = appName.replace(" ", "").lowercase()
        
        var partialMatch = ""
        for (appInfo in packages) {
            val pkgName = appInfo.packageName.lowercase()
            val label = pm.getApplicationLabel(appInfo).toString().replace(" ", "").lowercase()
            
            if (pkgName.contains(cleanAppName)) {
                AppCacheManager.savePackageName(requireContext(), appId, appInfo.packageName)
                return appInfo.packageName
            }
            if (label == cleanAppName) {
                AppCacheManager.savePackageName(requireContext(), appId, appInfo.packageName)
                return appInfo.packageName
            } else if (label.contains(cleanAppName) || cleanAppName.contains(label)) {
                partialMatch = appInfo.packageName
            }
        }
        if (partialMatch.isNotEmpty()) {
            AppCacheManager.savePackageName(requireContext(), appId, partialMatch)
        }
        return partialMatch
    }

    private fun setupButtons(developer: String, appId: String, appName: String) {
        if (currentPackageName.isEmpty()) {
            setUIForInstallState(developer, appId, appName)
            return
        }

        val pm = requireContext().packageManager
        val isInstalled = try {
            pm.getPackageInfo(currentPackageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        if (isInstalled) {
            setUIForOpenState(currentPackageName, developer, appId, appName)
        } else {
            setUIForInstallState(developer, appId, appName)
        }
    }

    private fun setUIForInstallState(developer: String, appId: String, appName: String) {
        binding.cvDetailsIcon.radius = 8f * resources.displayMetrics.density
        binding.pbDetailsProgress.visibility = View.GONE
        binding.btnDetailsUninstall.visibility = View.GONE
        binding.btnDetailsInstall.text = getString(R.string.install_button)
        binding.btnDetailsInstall.isEnabled = true
        binding.btnDetailsInstall.setOnClickListener { installApp(developer, appId, appName) }
    }

    private fun setUIForOpenState(packageName: String, developer: String, appId: String, appName: String) {
        binding.cvDetailsIcon.radius = 8f * resources.displayMetrics.density
        binding.pbDetailsProgress.visibility = View.GONE
        
        val isSystemApp = try {
            val appInfo = requireContext().packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: Exception) {
            false
        }

        if (isSystemApp) {
            binding.btnDetailsUninstall.visibility = View.GONE
        } else {
            binding.btnDetailsUninstall.visibility = View.VISIBLE
            binding.btnDetailsUninstall.isEnabled = true
            binding.btnDetailsUninstall.setOnClickListener { uninstallApp(packageName, developer, appId, appName) }
        }

        binding.btnDetailsInstall.text = getString(R.string.open_button)
        binding.btnDetailsInstall.isEnabled = true
        binding.btnDetailsInstall.setOnClickListener {
            val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(requireContext(), R.string.store_cannot_open, Toast.LENGTH_SHORT).show()
            }
        }

        val localVersion = VersionHelper.getLocalVersion(requireContext(), packageName)
        if (localVersion != null) {
            VersionHelper.checkUpdateAvailable(requireContext(), developer, appName, localVersion) { hasUpdate, _ ->
                if (isAdded && hasUpdate) {
                    binding.btnDetailsInstall.text = getString(R.string.update_button)
                    binding.btnDetailsInstall.setOnClickListener { installApp(developer, appId, appName) }
                }
            }
        }
    }

    private fun installApp(developer: String, appId: String, appName: String) {
        if (!ShizukuHelper.isShizukuRunning() || !ShizukuHelper.hasPermission()) {
            Toast.makeText(requireContext(), R.string.store_requesting_permission, Toast.LENGTH_SHORT).show()
            Shizuku.requestPermission(1001)
            return
        }

        binding.btnDetailsInstall.isEnabled = false
        binding.btnDetailsInstall.text = getString(R.string.installing_button)
        binding.cvDetailsIcon.radius = 36f * resources.displayMetrics.density
        binding.pbDetailsProgress.visibility = View.VISIBLE
        
        ApkDownloader.downloadLatestRelease(
            requireContext(), developer, appName,
            onProgress = { msg -> mainHandler.post { if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show() } },
            onResult = { success, path -> handleInstallResult(success, path, developer, appId, appName) }
        )
    }

    private fun installSpecificVersion(developer: String, appId: String, appName: String, release: ReleaseModel) {
        if (!ShizukuHelper.isShizukuRunning() || !ShizukuHelper.hasPermission()) {
            Toast.makeText(requireContext(), R.string.store_requesting_permission, Toast.LENGTH_SHORT).show()
            Shizuku.requestPermission(1001)
            return
        }

        binding.btnDetailsInstall.isEnabled = false
        binding.btnDetailsInstall.text = getString(R.string.installing_button)
        binding.cvDetailsIcon.radius = 36f * resources.displayMetrics.density
        binding.pbDetailsProgress.visibility = View.VISIBLE

        // عرض إشعار بدء التحميل للإصدار المحدد
        NotificationHelper.showDownloadProgress(requireContext(), appName, getString(R.string.downloader_downloading, release.fileName))

        thread {
            try {
                val apkFile = File(requireContext().cacheDir, release.fileName)
                val connection = URL(release.downloadUrl).openConnection() as HttpURLConnection
                connection.connect()

                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(apkFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.close()
                inputStream.close()
                
                mainHandler.post { handleInstallResult(true, apkFile.absolutePath, developer, appId, appName) }
            } catch (e: Exception) {
                mainHandler.post {
                    if (isAdded) handleInstallResult(false, getString(R.string.downloader_fail, e.message), developer, appId, appName)
                }
            }
        }
    }

    private fun handleInstallResult(success: Boolean, path: String?, developer: String, appId: String, appName: String) {
        if (!isAdded) return
        val safeContext = requireContext()
        if (success && path != null) {
            ShizukuInstaller.installApk(safeContext, path) { installSuccess, msg ->
                if (!isAdded) return@installApk
                Toast.makeText(safeContext, msg, Toast.LENGTH_LONG).show()
                if (installSuccess) {
                    val packageInfo = safeContext.packageManager.getPackageArchiveInfo(path, 0)
                    val extractedPackageName = packageInfo?.packageName

                    if (extractedPackageName != null) {
                        AppCacheManager.savePackageName(safeContext, appId, extractedPackageName)
                        currentPackageName = extractedPackageName
                        setUIForOpenState(currentPackageName, developer, appId, appName)
                    } else {
                        setUIForInstallState(developer, appId, appName)
                    }
                    NotificationHelper.showStatusNotification(safeContext, appName, safeContext.getString(R.string.installer_success), true)
                } else {
                    setUIForInstallState(developer, appId, appName)
                    NotificationHelper.showStatusNotification(safeContext, appName, safeContext.getString(R.string.installer_fail, msg), false)
                }
            }
        } else {
            Toast.makeText(safeContext, path ?: "Error", Toast.LENGTH_LONG).show()
            setUIForInstallState(developer, appId, appName)
            NotificationHelper.showStatusNotification(safeContext, appName, path ?: "Download failed", false)
        }
    }

    private fun uninstallApp(packageName: String, developer: String, appId: String, appName: String) {
        if (!ShizukuHelper.isShizukuRunning() || !ShizukuHelper.hasPermission()) {
            Toast.makeText(requireContext(), R.string.store_requesting_permission, Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnDetailsUninstall.isEnabled = false
        val safeContext = requireContext()
        
        ShizukuInstaller.uninstallApk(safeContext, packageName) { success, msg ->
            if (!isAdded) return@uninstallApk
            Toast.makeText(safeContext, msg, Toast.LENGTH_LONG).show()
            if (success) {
                currentPackageName = ""
                AppCacheManager.removePackageName(safeContext, appId)
                setUIForInstallState(developer, appId, appName)
                NotificationHelper.showStatusNotification(safeContext, appName, safeContext.getString(R.string.uninstall_success), true)
            } else {
                binding.btnDetailsUninstall.isEnabled = true
                NotificationHelper.showStatusNotification(safeContext, appName, safeContext.getString(R.string.uninstall_fail, msg), false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
