package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.nameMask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A felt doll the server made for a character slot — used only while that slot still says [words]. */
data class GeneratedFriend(val words: String, val image: String, val rig: String?)

/** 「친구」 alone names nobody in particular — the generic friend preset already fits it. */
private val GENERIC_FRIEND = setOf("친구", "친구들")

/**
 * The words to draw a character for, or null when a preset or the child's own drawing already covers it.
 * Story: the newcomer, when no felt preset has that name (it fell back to the alien — 「토끼」 showed as an alien).
 * Co-op: the companion, when no person preset matches (an animal showed as the generic boy).
 * Diary keeps its presets: what the child did not draw is not drawn for a real day (#168).
 */
internal fun DemoState.friendToDraw(): String? = when {
    drawing.isNotEmpty() -> null                               // the child's drawing is the default (differentiator 1)
    mode == StoryMode.STORY ->
        slots["newcomer"]?.trim()?.takeIf { it.isNotEmpty() && storyPresetMatch(it) == null }
    isCoop -> companionKind.trim().takeIf { it.isNotEmpty() && companionPreset(it) == null && it !in GENERIC_FRIEND }
    else -> null
}

/**
 * 등장인물 칸에 맞는 그림이 없으면 오또가 펠트 인형을 만든다 (10-06 조장). 아이를 기다리게 하지 않는다(규칙 8):
 * 대화는 그대로 가고, 15초 안에 오면 무대와 책의 그 자리가 이 인형으로 바뀐다. 못 오면 프리셋 그대로.
 * 보내는 건 아이가 말한 낱말뿐이다(아이 그림은 보내지 않는다). 그사이 칸 값이 바뀌었으면 늦게 온 인형은 버린다.
 */
internal suspend fun Director.drawFriend() {
    if (!Server.liveFor(s.mode)) return
    val words = s.friendToDraw() ?: return
    if (s.generatedFriend?.words == words || s.friendRequested == words) return
    s.friendRequested = words
    val mode = if (s.isCoop) "coop" else "story"
    val mask = s.nameMask()
    CoroutineScope(currentCoroutineContext()).launch {
        val made = withTimeoutOrNull(15_000) { Server.character(mask.mask(words), mode, s.bookStyle) }
        val saved = made?.let { withContext(Dispatchers.IO) { saveStoryImage(it.png) } }
        when {
            saved == null -> log("[등장인물] 「$words」 인형 생성 실패 또는 15초 경과 → 프리셋 그대로")
            s.friendToDraw() == words -> {
                s.generatedFriend = GeneratedFriend(words, saved, made.rig)
                log("[등장인물] 「$words」 인형을 무대와 책에 연결")
            }
            else -> log("[등장인물] 그사이 칸이 바뀌어 「$words」 인형은 버린다")
        }
        if (s.friendRequested == words) s.friendRequested = null
    }
}
