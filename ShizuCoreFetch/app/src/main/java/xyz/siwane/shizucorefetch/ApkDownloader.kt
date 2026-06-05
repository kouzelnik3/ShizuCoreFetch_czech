package xyz.siwane.shizucorefetch

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object ApkDownloader {

    fun downloadLatestRelease(
        context: Context,
        developer: String,
        repoName: String,
        onProgress: (String) -> Unit,
        onResult: (Boolean, String?) -> Unit
    ) {
        thread {
            try {
                val apiUrl = "https://api.github.com/repos/$developer/$repoName/releases/latest"
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                val token = AuthManager.getToken(context)
                if (token != null) {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    postResult(onResult, false, context.getString(R.string.downloader_no_apk))
                    return@thread
                }

                val response = connection.inputStream.bufferedReader().readText()
                val jsonObject = JSONObject(response)
                val assets = jsonObject.getJSONArray("assets")

                var downloadUrl: String? = null
                var fileName: String? = null

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        fileName = name
                        break
                    }
                }

                if (downloadUrl == null || fileName == null) {
                    postResult(onResult, false, context.getString(R.string.downloader_no_apk))
                    return@thread
                }

                postProgress(onProgress, context.getString(R.string.downloader_downloading, fileName))
                // دفع إشعار التقدم الذكي بالنظام عند بدء سحب الحزمة
                NotificationHelper.showDownloadProgress(context, repoName, context.getString(R.string.downloader_downloading, fileName))

                val apkFile = File(context.cacheDir, fileName)
                val downloadConnection = URL(downloadUrl).openConnection() as HttpURLConnection
                downloadConnection.connect()

                val inputStream = downloadConnection.inputStream
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.close()
                inputStream.close()

                postProgress(onProgress, context.getString(R.string.downloader_success))
                postResult(onResult, true, apkFile.absolutePath)

            } catch (e: Exception) {
                postResult(onResult, false, context.getString(R.string.downloader_fail, e.message))
            }
        }
    }

    private fun postResult(onResult: (Boolean, String?) -> Unit, success: Boolean, result: String?) {
        Handler(Looper.getMainLooper()).post { onResult(success, result) }
    }

    private fun postProgress(onProgress: (String) -> Unit, message: String) {
        Handler(Looper.getMainLooper()).post { onProgress(message) }
    }
}
