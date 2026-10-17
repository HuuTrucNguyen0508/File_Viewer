package com.fileviewer.app.viewers.code

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONObject

/**
 * One-time TextMate setup: bundled grammars from assets/textmate/languages.json and the two
 * "drafting" themes. Started at app launch so the first code file opens without waiting.
 */
object TextMate {
    const val LIGHT = "drafting-light"
    const val DARK = "drafting-dark"

    private var ready: Deferred<Map<String, String>>? = null

    /** Starts loading in the background; safe to call repeatedly. */
    @Synchronized
    fun start(context: Context): Deferred<Map<String, String>> = ready ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
        val app = context.applicationContext
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(app.assets))
        val themes = ThemeRegistry.getInstance()
        for (name in listOf(LIGHT, DARK)) {
            val path = "textmate/themes/$name.json"
            val stream = FileProviderRegistry.getInstance().tryGetInputStream(path)
            themes.loadTheme(ThemeModel(IThemeSource.fromInputStream(stream, path, null), name).apply { isDark = name == DARK })
        }
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        val json = app.assets.open("textmate/languages.json").bufferedReader().use { it.readText() }
        val langs = JSONObject(json).getJSONArray("languages")
        (0 until langs.length()).associate { i -> langs.getJSONObject(i).let { it.getString("name") to it.getString("scopeName") } }
    }.also { ready = it }

    /** Waits for setup; returns grammar id -> scope name. */
    suspend fun scopes(context: Context): Map<String, String> = start(context).await()

    fun applyTheme(dark: Boolean) {
        ThemeRegistry.getInstance().setTheme(if (dark) DARK else LIGHT)
    }
}
