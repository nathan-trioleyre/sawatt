package app.sawatt

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * The language chosen in Me → Language, or null to follow the phone.
 *
 * From Android 13 the choice is the system's per-app language, so it stays in sync with
 * Settings → Apps → SaWatt → Language, and the activity handles the change without being recreated.
 * Older versions keep it in preferences: [LocalizedContent] gives the screens resources in that
 * language, and [localized] does the same for the workout service and the exporter.
 */
object AppLanguage {
    /** Languages the app is translated into, as language tags. */
    val supported = listOf("en", "fr")

    private const val PREFS = "language"
    private const val KEY_TAG = "tag"

    private val usesSystemSetting = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The stored choice below Android 13; null until first read. */
    private var legacyTag: MutableStateFlow<String?>? = null

    fun current(context: Context): String? {
        val language = if (usesSystemSetting) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.takeUnless { it.isEmpty }?.get(0)?.language
        } else {
            legacyTag(context).value
        }
        return language?.takeIf { it in supported }
    }

    fun set(activity: Activity, tag: String?) {
        if (usesSystemSetting) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(activity).edit { putString(KEY_TAG, tag) }
            legacyTag(activity).value = tag
        }
    }

    /** Below Android 13, [context] with its resources in the chosen language; [context] itself otherwise. */
    fun localized(context: Context): Context =
        if (usesSystemSetting) context else localized(context, legacyTag(context).value, context.resources.configuration)

    private fun localized(base: Context, tag: String?, configuration: Configuration): Context {
        val resources = base.createConfigurationContext(Configuration(configuration).apply { setLocale(localeOf(tag)) }).resources
        // A wrapper, not the configuration context itself, so that base keeps starting activities.
        return object : ContextWrapper(base) {
            override fun getResources(): Resources = resources
        }
    }

    /**
     * Below Android 13, switches the activity's own resources to the chosen language and recomposes
     * [content], without recreating the activity. Updating the resources themselves, rather than
     * providing other ones, also reaches menus and dialogs, which compose in their own windows.
     */
    @Composable
    fun LocalizedContent(content: @Composable () -> Unit) {
        if (usesSystemSetting) return content()
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val tag by legacyTag(context).collectAsState()
        val localized = remember(configuration, tag) {
            val locale = localeOf(tag)
            // Dates and numbers are formatted with the default locale.
            Locale.setDefault(locale)
            listOf(context.resources, context.applicationContext.resources).forEach { resources ->
                @Suppress("DEPRECATION") // The only way to change the locale in place before Android 13.
                resources.updateConfiguration(Configuration(resources.configuration).apply { setLocale(locale) }, resources.displayMetrics)
            }
            // A new configuration so that every string read again.
            Configuration(configuration).apply { setLocale(locale) }
        }
        CompositionLocalProvider(LocalConfiguration provides localized, content = content)
    }

    /** With no choice, the phone's language: resources may already carry an older choice. */
    private fun localeOf(tag: String?): Locale = tag?.let(Locale::forLanguageTag) ?: Resources.getSystem().configuration.locales[0]

    private fun legacyTag(context: Context): MutableStateFlow<String?> =
        legacyTag ?: MutableStateFlow(prefs(context).getString(KEY_TAG, null)).also { legacyTag = it }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
