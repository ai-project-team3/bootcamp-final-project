package com.example.finalproject_demo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.finalproject_demo.demo.Art
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Rebuild a saved generated character's rig off the UI thread; its PNG stays visible throughout. */
@Composable
fun StoryCharacterImage(art: Art.Img, modifier: Modifier, motion: RigMotion?) {
    val animated = motion != null && art.rig != null && !motionFrozen
    LaunchedEffect(art.name, art.rig, animated) {
        if (!animated || !art.name.startsWith("local:")) return@LaunchedEffect
        val png = withContext(Dispatchers.IO) {
            runCatching { File(art.name.removePrefix("local:")).readBytes() }.getOrNull()
        }
        if (png != null) RigCache.prefetch(art.name, png, RigHint.of(art.rig))
    }
    val rig = if (animated) RigCache.peek(art.name) else null
    if (rig != null && motion != null) RigView(rig, motion, modifier)
    else AssetImage(art.name, modifier) { ArtView(art.fallback, modifier) }
}
