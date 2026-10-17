package com.fileviewer.app.viewers.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.fileviewer.app.detect.Family
import com.fileviewer.app.ui.theme.Fv

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AudioPlayer(name: String, metadata: MediaMetadata, player: Player) {
    val palette = Fv.palette
    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text(metadata.title?.toString() ?: name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        val byline = listOfNotNull(metadata.artist, metadata.albumTitle).joinToString(", ")
        if (byline.isNotBlank()) Text(byline, style = MaterialTheme.typography.bodyMedium, color = palette.muted, modifier = Modifier.padding(horizontal = 16.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth - 64.dp, maxHeight - 24.dp, 320.dp).coerceAtLeast(1.dp)
            Box(Modifier.size(side).background(palette.surface), contentAlignment = Alignment.Center) {
                val artwork = metadata.artworkData
                if (artwork != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(artwork).size(1024).build(),
                        contentDescription = "Cover art",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Outlined.MusicNote, "Audio", tint = palette.family(Family.Media), modifier = Modifier.size(side / 3))
                }
            }
        }
        AudioControls(player)
    }
}
