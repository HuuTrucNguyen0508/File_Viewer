package com.fileviewer.app.viewers.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.fileviewer.app.ui.theme.Fv
import kotlinx.coroutines.delay

@Composable
fun AudioControls(player: Player) {
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var duration by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                playing = p.isPlaying
                position = p.currentPosition
                duration = p.duration.coerceAtLeast(0)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, playing) {
        while (playing) { position = player.currentPosition; delay(250) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Slider(
            value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = { position = it.toLong(); player.seekTo(position) },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = duration > 0,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(MediaProbe.formatDuration(position), style = MaterialTheme.typography.labelLarge, color = Fv.palette.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) { Icon(Icons.Outlined.Replay10, "Back 10 seconds") }
                FilledIconButton(onClick = {
                    if (player.isPlaying) player.pause() else {
                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                        player.play()
                    }
                }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play") }
                IconButton(onClick = { player.seekTo((player.currentPosition + 10_000).coerceAtMost(duration)) }) { Icon(Icons.Outlined.Forward10, "Forward 10 seconds") }
            }
            Text(MediaProbe.formatDuration(duration), style = MaterialTheme.typography.labelLarge, color = Fv.palette.muted)
        }
    }
}
