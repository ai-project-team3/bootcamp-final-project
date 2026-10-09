package com.example.finalproject_demo.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.ui.FeelPrefs
import com.example.finalproject_demo.ui.KidFont
import com.example.finalproject_demo.ui.motionFrozen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CurtainOpening(d: Director, onStart: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var running by rememberSaveable { mutableStateOf(false) }
    var time by rememberSaveable { mutableFloatStateOf(0f) }
    var rawPull by remember { mutableFloatStateOf(0f) }
    var releasedPull by rememberSaveable { mutableFloatStateOf(0f) }
    var introduced by remember { mutableStateOf(time > OpeningMotion.voiceAt) }
    var voiceDone by remember { mutableStateOf(time > OpeningMotion.voiceAt) }
    var voiceWait by remember { mutableFloatStateOf(0f) }
    val currentStart by rememberUpdatedState(onStart)
    val soundOn by rememberUpdatedState(FeelPrefs.soundOn)
    val audio = remember(context) { OpeningAudio(context) }
    val art by produceState<OpeningArt?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { OpeningArt(context) }.onFailure {
                android.util.Log.w("OttoOpening", "Opening art could not be decoded", it)
            }.getOrNull()
        }
    }
    DisposableEffect(owner, audio) {
        audio.setActive(active)
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            audio.setActive(active)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); audio.close() }
    }
    LaunchedEffect(FeelPrefs.soundOn) {
        if (!FeelPrefs.soundOn) { audio.silence(); if (introduced) voiceDone = true }
    }
    LaunchedEffect(running, active, motionFrozen, art) {
        if (!running || !active || motionFrozen || art == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (time < OpeningMotion.duration) {
            val now = withFrameNanos { it }
            val delta = ((now - previous) / 1_000_000f).coerceIn(0f, 64f)
            previous = now
            val before = time
            time = OpeningMotion.advance(time, delta, voiceDone)
            if (time >= OpeningMotion.voiceAt && !introduced) {
                introduced = true
                if (soundOn) audio.introduce { voiceDone = true } else voiceDone = true
            }
            if (introduced && !voiceDone) {
                voiceWait += delta
                // A missing/failed decoder must not trap a child. Background time is excluded.
                if (voiceWait > 5000f) { audio.silence(); voiceDone = true }
            }
            if (soundOn && OpeningMotion.hops.any { before < it.start && time >= it.start }) audio.hop()
        }
        audio.silence(); currentStart()
    }
    fun start() {
        if (!running && art != null) {
            releasedPull = OpeningMotion.pull(rawPull)
            rawPull = 0f
            running = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(Color(0xFF861E1B))) {
        val placement = roomOpeningPlacement(maxWidth, maxHeight)
        val camera = openingCamera(time, maxWidth.value, maxHeight.value, placement)
        val density = LocalDensity.current.density
        if (art != null) OpeningScene(d, art!!, time, OpeningMotion.pull(rawPull), releasedPull)
        if (!running) {
            Column(Modifier.align(Alignment.Center).padding(end = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (rawPull > 0f) {
                    if (OpeningMotion.canRelease(rawPull)) "좋아! 이제 손을 놓아 봐!" else "조금 더 아래로 쭈우욱!"
                } else if (art == null) "무대를 준비하고 있어요" else "줄을 당겨 봐!",
                    fontFamily = KidFont, color = Color(0xFFFFF1D2), fontSize = 28.sp)
                if (rawPull == 0f && art != null) Text("아래로 쭈우욱!", fontFamily = KidFont, color = Color(0xFFFFE4B5), fontSize = 18.sp)
            }
            // Touch bounds follow the actual knot, including during the resisted stretch.
            val k = placement.stage.width() / 1200f * camera.scale
            val ropeX = (placement.stage.left + placement.stage.width() * 1091f / 1200f) * camera.scale + camera.x
            val top = (placement.stage.top + placement.stage.width() * 160f / 1200f) * camera.scale + camera.y
            val ropeWidth = (164f * k).coerceAtLeast(64f)
            if (art != null) Box(Modifier.offset((ropeX - ropeWidth / 2).dp, top.coerceAtLeast(0f).dp)
                .width(ropeWidth.dp).height((320f * k).coerceAtLeast(80f).dp)
                .testTag("opening-rope")
                .semantics { contentDescription = "아래로 당겨서 커튼 열기"; role = Role.Button; onClick("커튼 열기") { start(); true } }
                .pointerInput(art, k) {
                    detectDragGestures(
                        onDragStart = { rawPull = 0f },
                        onDragEnd = { if (OpeningMotion.canRelease(rawPull)) start() else rawPull = 0f },
                        onDragCancel = { rawPull = 0f },
                        onDrag = { change, amount -> change.consume(); rawPull = (rawPull + amount.y / (density * k)).coerceAtLeast(0f) },
                    )
                })
        }
        TextButton(onClick = { audio.silence(); currentStart() },
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).heightIn(min = 48.dp)
                .background(Color(0xDDFFF8E8), RoundedCornerShape(24.dp))) {
            Text("건너뛰기", fontFamily = KidFont, color = Color(0xFF65432B), fontSize = 18.sp)
        }
    }
}

/** Also used by frame captures: the room is the existing service view with input disabled. */
@Composable
internal fun OpeningScene(d: Director, art: OpeningArt, time: Float, pulled: Float = 0f, releasedPull: Float = 0f) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(Color(0xFF861E1B))) {
        val placement = roomOpeningPlacement(maxWidth, maxHeight)
        val camera = openingCamera(time, maxWidth.value, maxHeight.value, placement)
        val density = LocalDensity.current.density
        Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = camera.scale; scaleY = camera.scale
            translationX = camera.x * density; translationY = camera.y * density
        }) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = OpeningMotion.progress(time, 4050f, 4900f)
            }.clearAndSetSemantics { }) { OttoRoom(d, opening = true) }
            Canvas(Modifier.fillMaxSize()) {
                val canvas = drawContext.canvas.nativeCanvas
                val checkpoint = canvas.save()
                canvas.scale(density, density)
                art.draw(canvas, time, pulled, releasedPull, placement)
                canvas.restoreToCount(checkpoint)
            }
        }
    }
}
