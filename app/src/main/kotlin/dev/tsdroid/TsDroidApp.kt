package dev.tsdroid

import android.app.Application
import android.content.res.Resources
import android.util.Log
import dev.tsdroid.bridge.FileCache
import dev.tsdroid.data.migratePlaintextData
import java.io.File

class TsDroidApp : Application() {

    companion object {
        const val CHANNEL_ID_CONNECTION = "ts_connection"
        const val CHANNEL_ID_POKE = "ts_poke"

        /** Activities currently started — pokes only notify when none exist. */
        private var startedActivities = 0
        val isAppForeground: Boolean get() = startedActivities > 0

        init {
            System.loadLibrary("tslib_jni")
        }
    }

    override fun onCreate() {
        // 崩溃陷阱：未捕获异常的堆栈写入 filesDir/last_crash.txt，下次启动在
        // 界面上展示并可一键复制（无 adb 环境时也能拿到闪退原因）。
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                File(filesDir, "last_crash.txt").writeText(
                    buildString {
                        appendLine("thread=${thread.name}")
                        appendLine(Log.getStackTraceString(throwable))
                    },
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
        super.onCreate()
        migratePlaintextData(this)
        FileCache.clearOpenedCopies(this)
    }

    /** In the language picked in settings, for everything built from the application context. */
    override fun getResources(): Resources = AppLanguage.resources(this, super.getResources())
}
