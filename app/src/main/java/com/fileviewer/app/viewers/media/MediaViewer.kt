package com.fileviewer.app.viewers.media

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.source.ContentUriSource
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.LocalFileSource
import com.fileviewer.app.source.materialize
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.viewer.ViewerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ExoPlayer playback. The container is detected, but decoding depends on the device: on a
 * playback error this shows the error and the tracks the platform found, with Info and Hex.
 */
@Composable
fun MediaViewer(source: FileSource, kind: FileKind.Media, onSwitch: (ViewerId) -> Unit) {
    val uri by produceState<Result<Uri>?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                when (source) {
                    is LocalFileSource -> Uri.fromFile(source.file)
                    is ContentUriSource -> source.uri
                    else -> source.materialize().use { Uri.fromFile(it.file!!) }
                }
            }
        }
    }
    val u = uri ?: return Loading()
    u.onFailure { PlaybackError(source, it.message ?: "Couldn't read the file", onSwitch) }
    u.onSuccess { Player(source, it, kind, onSwitch) }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun Player(source: FileSource, uri: Uri, kind: FileKind.Media, onSwitch: (ViewerId) -> Unit) {
    val context = LocalContext.current
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var meta by remember(uri) { mutableStateOf(MediaMetadata.EMPTY) }
    var position by rememberSaveable(source.key) { mutableLongStateOf(0L) }
    val player = remember(uri) { ExoPlayer.Builder(context).build() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { position = player.currentPosition; player.pause() }
        }
        lifecycle.addObserver(observer)
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "${e.errorCodeName}: ${e.cause?.message ?: e.message}"
            }
            override fun onMediaMetadataChanged(m: MediaMetadata) { meta = m }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.seekTo(position)
        player.prepare()
        onDispose { lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    val err = error
    if (err != null) {
        PlaybackError(source, err, onSwitch)
        return
    }
    if (!kind.format.video) {
        AudioPlayer(source.name, meta, player)
        return
    }
    Column(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
        )
    }
}

@Composable
private fun PlaybackError(source: FileSource, message: String, onSwitch: (ViewerId) -> Unit) {
    val context = LocalContext.current
    val facts by produceState<Result<MediaProbe.Facts>?>(null, source) {
        value = withContext(Dispatchers.IO) { runCatching { MediaProbe.probe(context, source) } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("This device can't play this file", style = MaterialTheme.typography.titleMedium)
        Text(
            "The container was recognized, but playback depends on the decoders this phone has.",
            style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted, modifier = Modifier.padding(top = 6.dp),
        )
        Text(message, style = MaterialTheme.typography.bodySmall, color = Fv.palette.danger, modifier = Modifier.padding(top = 12.dp))
        facts?.getOrNull()?.tracks?.takeIf { it.isNotEmpty() }?.let { tracks ->
            Text("Tracks in this file", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            tracks.forEachIndexed { i, t -> Text("${i + 1}. $t", style = MaterialTheme.typography.bodyMedium) }
        }
        Row(Modifier.padding(top = 16.dp)) {
            TextButton(onClick = { onSwitch(ViewerId.Info) }) { Text("Show info") }
            TextButton(onClick = { onSwitch(ViewerId.Hex) }) { Text("Show hex") }
        }
    }
}
