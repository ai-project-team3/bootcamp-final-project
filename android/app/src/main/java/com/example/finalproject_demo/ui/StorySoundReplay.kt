package com.example.finalproject_demo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.playStorySound
import kotlinx.coroutines.launch

/** Only the reopened story reader uses this control; playback belongs to its composition. */
@Composable
internal fun BoxScope.StorySoundReplay(d: Director, book: SavedStoryBook, page: Int) {
    if (page == 0 || book.soundClipId == null) return
    val scope = rememberCoroutineScope()
    var playing by remember(book.id) { mutableStateOf(false) }
    var missing by remember(book.id) { mutableStateOf(false) }
    Box(Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 12.dp)) {
        PillButton(if (missing) "소리가 남아 있지 않아" else if (playing) "🔊 듣고 있어…" else "🔊 내가 만든 소리", Sun, Ink, 15) {
            if (!playing) scope.launch {
                playing = true
                try { missing = !d.playStorySound(book) }
                finally { playing = false }
            }
        }
    }
}
