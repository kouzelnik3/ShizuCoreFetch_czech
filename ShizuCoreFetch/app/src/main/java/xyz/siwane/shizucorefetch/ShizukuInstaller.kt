package xyz.siwane.shizucorefetch

import android.content.Context
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuInstaller {

    // 1. دالة التثبيت الصامت
    fun installApk(context: Context, apkFilePath: String, onResult: (Boolean, String) -> Unit) {
        if (!ShizukuHelper.hasPermission()) {
            postResult(onResult, false, context.getString(R.string.installer_no_permission))
            return
        }

        Thread {
            try {
                val command = "pm install -r \"$apkFilePath\""
                val commandArray = arrayOf("sh", "-c", command)
                
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                
                newProcessMethod.isAccessible = true
                val process = newProcessMethod.invoke(null, commandArray, null, null) as Process
                process.waitFor()
                
                val isSuccess = process.exitValue() == 0
                val reader = BufferedReader(InputStreamReader(
                    if (isSuccess) process.inputStream else process.errorStream
                ))
                val output = reader.readText().trim()

                val message = if (isSuccess) {
                    context.getString(R.string.installer_success)
                } else {
                    context.getString(R.string.installer_fail, output)
                }

                postResult(onResult, isSuccess, message)

            } catch (e: Exception) {
                postResult(onResult, false, context.getString(R.string.installer_error, e.message))
            }
        }.start()
    }

    // 2. دالة إلغاء التثبيت الصامت
    fun uninstallApk(context: Context, packageName: String, onResult: (Boolean, String) -> Unit) {
        if (!ShizukuHelper.hasPermission()) {
            postResult(onResult, false, context.getString(R.string.installer_no_permission))
            return
        }

        Thread {
            try {
                val command = "pm uninstall $packageName"
                val commandArray = arrayOf("sh", "-c", command)
                
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                
                newProcessMethod.isAccessible = true
                val process = newProcessMethod.invoke(null, commandArray, null, null) as Process
                process.waitFor()
                
                val isSuccess = process.exitValue() == 0
                val reader = BufferedReader(InputStreamReader(
                    if (isSuccess) process.inputStream else process.errorStream
                ))
                val output = reader.readText().trim()

                val message = if (isSuccess) {
                    context.getString(R.string.uninstall_success)
                } else {
                    context.getString(R.string.uninstall_fail, output)
                }
                
                postResult(onResult, isSuccess, message)

            } catch (e: Exception) {
                postResult(onResult, false, context.getString(R.string.installer_error, e.message))
            }
        }.start()
    }

    // 3. دالة معالجة النتائج وإرسالها للواجهة الرئيسية
    private fun postResult(onResult: (Boolean, String) -> Unit, success: Boolean, message: String) {
        Handler(Looper.getMainLooper()).post {
            onResult(success, message)
        }
    }
}
