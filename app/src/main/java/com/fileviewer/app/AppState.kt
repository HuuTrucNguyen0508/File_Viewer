package com.fileviewer.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fileviewer.app.browser.SortBy
import com.fileviewer.app.source.FileSource

sealed interface Screen {
    data object Home : Screen
    data class Folder(val path: String) : Screen
    data class Viewer(val source: FileSource) : Screen
    data class Shared(val sources: List<FileSource>) : Screen
}

/** The back stack and browser preferences. Survives rotation; not process death. */
class AppState : ViewModel() {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var sortBy by mutableStateOf(SortBy.Name)
    var descending by mutableStateOf(false)
    var showHidden by mutableStateOf(false)

    /** The launch intent is handled once per ViewModel, not again on rotation. */
    var handledLaunchIntent = false

    val current: Screen get() = stack.last()

    fun push(screen: Screen) { stack.add(screen) }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Opened from another app: one file goes straight to its viewer, several to a list. */
    fun openExternal(sources: List<FileSource>) {
        if (sources.isEmpty()) return
        stack.clear()
        stack.add(Screen.Home)
        stack.add(if (sources.size == 1) Screen.Viewer(sources.single()) else Screen.Shared(sources))
    }
}
