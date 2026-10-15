package dev.tsdroid

import android.app.Application
import android.content.Context
import dev.tsdroid.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TsDroidApp : Application() {

    companion object {
        const val CHANNEL_ID_CONNECTION = "ts_connection"

        init {
            System.loadLibrary("tslib_jni")
        }

        /**
         * The language the process is starting in, read while it is still coming up.
         *
         * `attachBaseContext` needs it *before* anything can be inflated, which used to mean blocking
         * the main thread on the first DataStore read of the process as its very first act — before
         * the activity, the theme, or the first frame existed. The read is started here instead; an
         * activity only blocks on it if it somehow loses the race.
         */
        @Volatile
        private var startupLanguage: String? = null

        fun preloadLanguage(context: Context) {
            val appContext = context.applicationContext
            // Process-lifetime scope: nothing to cancel, and losing this read would only mean the
            // activity falls back to reading it itself.
            CoroutineScope(Dispatchers.IO).launch {
                startupLanguage = SettingsStore(appContext).language.first()
            }
        }

        /** What [preloadLanguage] found, or null while that read is still running. */
        fun preloadedLanguage(): String? = startupLanguage
    }

    override fun onCreate() {
        super.onCreate()
        preloadLanguage(this)
    }
}
