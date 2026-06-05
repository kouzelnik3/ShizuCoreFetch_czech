package xyz.siwane.shizucorefetch

import android.content.Context
import android.content.pm.PackageManager

object AppCacheManager {
    private const val PREFS_NAME = "InstalledAppsCache"

    fun savePackageName(context: Context, appId: String, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(appId, packageName).apply()
    }

    fun removePackageName(context: Context, appId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(appId).apply()
    }

    // الدالة السحرية الجديدة: تفحص الذاكرة الدائمة بسرعة البرق
    fun getSavedPackageFast(context: Context, appId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPackage = prefs.getString(appId, null)
        
        if (savedPackage != null) {
            return try {
                context.packageManager.getPackageInfo(savedPackage, 0)
                savedPackage // نجاح: التطبيق ما زال مثبتاً
            } catch (e: PackageManager.NameNotFoundException) {
                removePackageName(context, appId) // المستخدم حذفه يدوياً
                null
            }
        }
        return null
    }

    fun getPackageName(context: Context, appName: String, appId: String): String {
        // الفحص السريع أولاً
        val saved = getSavedPackageFast(context, appId)
        if (saved != null) return saved

        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val cleanAppName = appName.replace(" ", "").lowercase()
            
            for (appInfo in packages) {
                val pkgName = appInfo.packageName.lowercase()
                val label = pm.getApplicationLabel(appInfo).toString().replace(" ", "").lowercase()
                
                if (pkgName.contains(cleanAppName)) {
                    savePackageName(context, appId, appInfo.packageName)
                    return appInfo.packageName
                }
                
                if (label.isNotEmpty() && cleanAppName.isNotEmpty()) {
                    if (label == cleanAppName || label.contains(cleanAppName) || cleanAppName.contains(label)) {
                        savePackageName(context, appId, appInfo.packageName)
                        return appInfo.packageName
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return ""
    }
}
