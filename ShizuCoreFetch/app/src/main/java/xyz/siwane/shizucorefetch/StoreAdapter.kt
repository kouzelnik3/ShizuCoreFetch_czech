package xyz.siwane.shizucorefetch

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import coil.load
import rikka.shizuku.Shizuku
import xyz.siwane.shizucorefetch.databinding.ItemAppBinding
import kotlin.concurrent.thread

class StoreAdapter(
    private var appList: List<AppModel>,
    private val onAppClick: (AppModel, String) -> Unit
) : RecyclerView.Adapter<StoreAdapter.AppViewHolder>() {

    private val allAppsBackup = ArrayList(appList)
    private var installedAppsCache: Map<String, String>? = null
    private var isCacheBuilding = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun filter(query: String) {
        val cleanQuery = query.lowercase().trim()
        appList = if (cleanQuery.isEmpty()) {
            allAppsBackup
        } else {
            allAppsBackup.filter {
                it.name.lowercase().contains(cleanQuery) ||
                it.developer.lowercase().contains(cleanQuery) ||
                it.description.lowercase().contains(cleanQuery)
            }
        }
        notifyDataSetChanged()
    }

    private fun buildCacheAsync(context: Context) {
        if (installedAppsCache != null || isCacheBuilding) return
        
        isCacheBuilding = true
        thread {
            val map = mutableMapOf<String, String>()
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (appInfo in packages) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    map[appInfo.packageName] = label
                }
            } catch (e: Exception) {
            }
            
            mainHandler.post {
                installedAppsCache = map
                isCacheBuilding = false
                notifyDataSetChanged()
            }
        }
    }

    fun refreshCache(context: Context) {
        installedAppsCache = null
        buildCacheAsync(context)
    }

    private fun getPackageName(context: Context, appItem: AppModel): String {
        val savedPkg = AppCacheManager.getSavedPackageFast(context, appItem.id)
        if (savedPkg != null) return savedPkg

        if (installedAppsCache == null) {
            buildCacheAsync(context)
            return ""
        }
        
        val cleanAppName = appItem.name.replace(" ", "").lowercase()
        for ((pkgName, label) in installedAppsCache!!) {
            val cleanPkgName = pkgName.lowercase()
            val cleanLabel = label.replace(" ", "").lowercase()
            
            if (cleanPkgName.contains(cleanAppName) || cleanLabel == cleanAppName || cleanLabel.contains(cleanAppName) || cleanAppName.contains(cleanLabel)) {
                AppCacheManager.savePackageName(context, appItem.id, pkgName)
                return pkgName
            }
        }
        return ""
    }

    inner class AppViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appItem = appList[position]
        val context = holder.itemView.context

        val density = context.resources.displayMetrics.density
        val radiusNormal = 8f * density
        val radiusCircle = 30f * density

        val currentPackageName = getPackageName(context, appItem)

        with(holder.binding) {
            tvAppName.text = appItem.name
            
            val devPrefix = context.getString(R.string.store_developer_prefix)
            tvAppDeveloper.text = "$devPrefix ${appItem.developer}"
            tvAppDescription.text = appItem.description

            ivAppIcon.load(appItem.iconUrl) { crossfade(true) }

            root.setOnClickListener { onAppClick(appItem, currentPackageName) }

            cvAppIcon.radius = radiusNormal
            pbDownloadProgress.visibility = View.GONE

            if (currentPackageName.isNotEmpty()) {
                val localVersion = VersionHelper.getLocalVersion(context, currentPackageName)
                
                btnInstall.isEnabled = true
                btnInstall.text = context.getString(R.string.open_button)
                
                btnInstall.setOnClickListener {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(currentPackageName)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    } else {
                        Toast.makeText(context, R.string.store_cannot_open, Toast.LENGTH_SHORT).show()
                    }
                }

                if (localVersion != null) {
                    VersionHelper.checkUpdateAvailable(context, appItem.developer, appItem.name, localVersion) { hasUpdate, _ ->
                        if (hasUpdate && tvAppName.text == appItem.name) {
                            btnInstall.text = context.getString(R.string.update_button)
                            btnInstall.setOnClickListener {
                                startDownloadAndInstall(context, appItem, holder.binding, radiusNormal, radiusCircle)
                            }
                        }
                    }
                }
            } else {
                btnInstall.isEnabled = true
                btnInstall.text = context.getString(R.string.install_button)

                btnInstall.setOnClickListener {
                    startDownloadAndInstall(context, appItem, holder.binding, radiusNormal, radiusCircle)
                }
            }
        }
    }

    private fun startDownloadAndInstall(context: Context, appItem: AppModel, binding: ItemAppBinding, radiusNormal: Float, radiusCircle: Float) {
        if (!ShizukuHelper.isShizukuRunning()) {
            Toast.makeText(context, R.string.store_shizuku_not_running, Toast.LENGTH_LONG).show()
            return
        }

        if (!ShizukuHelper.hasPermission()) {
            Toast.makeText(context, R.string.store_requesting_permission, Toast.LENGTH_SHORT).show()
            Shizuku.requestPermission(1001)
            return
        }

        binding.btnInstall.isEnabled = false
        binding.btnInstall.text = context.getString(R.string.installing_button)
        binding.cvAppIcon.radius = radiusCircle
        binding.pbDownloadProgress.visibility = View.VISIBLE
        
        Toast.makeText(context, context.getString(R.string.downloader_searching), Toast.LENGTH_SHORT).show()
        
        ApkDownloader.downloadLatestRelease(
            context,
            appItem.developer,
            appItem.name, 
            onProgress = { msg -> mainHandler.post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } },
            onResult = { isSuccess, resultPath ->
                mainHandler.post {
                    if (isSuccess && resultPath != null) {
                        ShizukuInstaller.installApk(context, resultPath) { installSuccess, installMsg ->
                            binding.cvAppIcon.radius = radiusNormal
                            binding.pbDownloadProgress.visibility = View.GONE
                            binding.btnInstall.isEnabled = true
                            
                            Toast.makeText(context, installMsg, Toast.LENGTH_LONG).show()
                            
                            if (installSuccess) {
                                val packageInfo = context.packageManager.getPackageArchiveInfo(resultPath, 0)
                                val extractedPackageName = packageInfo?.packageName

                                if (extractedPackageName != null) {
                                    AppCacheManager.savePackageName(context, appItem.id, extractedPackageName)
                                    
                                    binding.btnInstall.text = context.getString(R.string.open_button)
                                    binding.btnInstall.setOnClickListener {
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(extractedPackageName)
                                        if (launchIntent != null) {
                                            context.startActivity(launchIntent)
                                        } else {
                                            Toast.makeText(context, R.string.store_cannot_open, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                // إرسال إشعار ذكي بالنجاح
                                NotificationHelper.showStatusNotification(context, appItem.name, context.getString(R.string.installer_success), true)
                                refreshCache(context)
                                notifyDataSetChanged()
                            } else {
                                binding.btnInstall.text = context.getString(R.string.install_button)
                                // إرسال إشعار ذكي بالفشل
                                NotificationHelper.showStatusNotification(context, appItem.name, context.getString(R.string.installer_fail, installMsg), false)
                            }
                        }
                    } else {
                        binding.cvAppIcon.radius = radiusNormal
                        binding.pbDownloadProgress.visibility = View.GONE
                        binding.btnInstall.isEnabled = true
                        binding.btnInstall.text = context.getString(R.string.install_button)
                        Toast.makeText(context, resultPath ?: "Error", Toast.LENGTH_LONG).show()
                        
                        // إرسال إشعار ذكي بفشل التحميل
                        NotificationHelper.showStatusNotification(context, appItem.name, resultPath ?: "Download failed", false)
                    }
                }
            }
        )
    }

    override fun getItemCount(): Int = appList.size
}
