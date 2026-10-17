package com.fileviewer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fileviewer.app.browser.FolderScreen
import com.fileviewer.app.browser.HomeScreen
import com.fileviewer.app.browser.RecentsStore
import com.fileviewer.app.browser.SharedListScreen
import com.fileviewer.app.source.LocalFileSource
import com.fileviewer.app.ui.theme.FileViewerTheme
import com.fileviewer.app.viewer.ViewerScreen
import java.io.File

class MainActivity : ComponentActivity() {
    private val state: AppState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!state.handledLaunchIntent) {
            state.handledLaunchIntent = true
            handle(intent)
        }
        setContent { FileViewerTheme { AppRoot(state) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val sources = runCatching { IntentSources.from(intent, contentResolver) }.getOrDefault(emptyList())
        state.openExternal(sources)
    }
}

@Composable
private fun AppRoot(state: AppState) {
    val context = LocalContext.current
    val recents = remember { RecentsStore(context) }
    val saveable = rememberSaveableStateHolder()
    BackHandler(enabled = state.stack.size > 1) { state.back() }

    fun openFile(file: File) {
        recents.add(isUri = false, value = file.absolutePath, name = file.name)
        state.push(Screen.Viewer(LocalFileSource(file)))
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val screen = state.current
        saveable.SaveableStateProvider("${state.stack.size}:$screen") {
            when (screen) {
                Screen.Home -> HomeScreen(
                    recents = recents,
                    onFolder = { state.push(Screen.Folder(it.path)) },
                    onOpenPath = ::openFile,
                    onOpenUri = { uri, persisted ->
                        val source = IntentSources.source(uri, null, context.contentResolver)
                        if (persisted) recents.add(isUri = true, value = uri.toString(), name = source.name)
                        state.push(Screen.Viewer(source))
                    },
                )
                is Screen.Folder -> FolderScreen(
                    dir = File(screen.path),
                    state = state,
                    onBack = { state.back() },
                    onFolder = { state.push(Screen.Folder(it.path)) },
                    onOpen = ::openFile,
                )
                is Screen.Viewer -> ViewerScreen(screen.source, onBack = { state.back() }, onOpen = { state.push(Screen.Viewer(it)) })
                is Screen.Shared -> SharedListScreen(screen.sources, onBack = { state.back() }, onOpen = { state.push(Screen.Viewer(it)) })
            }
        }
    }
}
