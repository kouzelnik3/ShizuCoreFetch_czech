package xyz.siwane.shizucorefetch

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object StoreCacheManager {
    private const val PREFS_NAME = "store_network_cache"
    private const val KEY_APPS_LIST = "cached_apps_list"

    // حفظ البيانات الجديدة محلياً
    fun saveApps(context: Context, apps: List<AppModel>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = Gson().toJson(apps)
        prefs.edit().putString(KEY_APPS_LIST, jsonString).apply()
    }

    // جلب البيانات المحفوظة (إن وجدت)
    fun getCachedApps(context: Context): List<AppModel>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_APPS_LIST, null) ?: return null
        
        return try {
            val type = object : TypeToken<List<AppModel>>() {}.type
            Gson().fromJson(jsonString, type)
        } catch (e: Exception) {
            null
        }
    }
}
