package dev.tsdroid

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import dev.tsdroid.data.SettingsStore
import java.util.Locale
import java.util.WeakHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The language picked in settings, applied to the whole process.
 *
 * A context's resources follow the system locale. The activity, the application and the service
 * each hand out resources in the chosen language instead, so notifications, toasts and messages
 * built outside the screens (for example by a ViewModel through the application) match them.
 */
object AppLanguage {

    @Volatile
    private var tag: String? = null

    private class Localized(val tag: String, val base: Configuration, val resources: Resources)

    private val localized = WeakHashMap<Resources, Localized>()

    /** The chosen language tag, read from settings the first time the process needs it. */
    fun tag(context: Context): String =
        tag ?: runBlocking(Dispatchers.IO) { SettingsStore(context).language.first() }.also(::use)

    /**
     * Stores [newTag] and switches the process to it. Contexts that hand out [resources] follow at
     * once; the activity has to be recreated.
     */
    suspend fun set(context: Context, newTag: String) {
        // Switched before the setting is written, so whatever reacts to the new setting already
        // gets resources in the new language.
        use(newTag)
        SettingsStore(context).setLanguage(newTag)
    }

    /** [base] with its locale replaced by the chosen language. */
    fun configuration(context: Context, base: Configuration): Configuration =
        Configuration(base).apply { setLocales(LocaleList(Locale.forLanguageTag(tag(context)))) }

    /**
     * [base], the resources of [context], in the chosen language. Rebuilt when the language or the
     * base configuration (dark mode, screen size…) changes.
     */
    fun resources(context: Context, base: Resources): Resources {
        val tag = tag(context)
        synchronized(localized) {
            localized[base]?.let { if (it.tag == tag && it.base == base.configuration) return it.resources }
            val resources = context.createConfigurationContext(configuration(context, base.configuration)).resources
            localized[base] = Localized(tag, Configuration(base.configuration), resources)
            return resources
        }
    }

    private fun use(newTag: String) {
        tag = newTag
        Locale.setDefault(Locale.forLanguageTag(newTag))
    }
}
