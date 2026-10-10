package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.nameMask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ensureActive

/** A felt doll the server made for a character slot — used only while that slot still says [words]. */
data class GeneratedFriend(val words: String, val image: String, val rig: String?, val role: String = "friend")

/** 「친구」 alone names nobody in particular — the generic friend preset already fits it. */

/** Co-op: the child has chosen whether to draw the companion (or was not asked) — only then a doll may be asked for. */
internal const val COOP_DRAW_DECIDED = "coopdrawdecided"
private val GENERIC_FRIEND = setOf("친구", "친구들")

/**
 * The words to draw a character for, or null when a preset or the child's own drawing already covers it.
 * Story: the newcomer, when no felt preset has that name (it fell back to the alien — 「토끼」 showed as an alien).
 * Co-op: the companion, when no person preset matches (an animal showed as the generic boy).
 * Diary keeps its presets: what the child did not draw is not drawn for a real day (#168).
 */
internal fun DemoState.friendToDraw(): String? = when {
    drawing.isNotEmpty() -> null                               // the child's drawing is the default (differentiator 1)
    mode == StoryMode.STORY && "draw" !in done -> null          // wait for the child's drawing decision
    mode == StoryMode.STORY ->
        slots["newcomer"]?.trim()?.takeIf { it.isNotEmpty() && storyPresetMatch(it) == null }
    // Co-op waits until the child chose whether to draw the companion (the drawing step comes last) — a doll asked for
    // earlier was thrown away when the child drew, and still cost a request (#339 · design §5 · ⚖️5)
    isCoop -> companionKind.trim().takeIf {
        it.isNotEmpty() && COOP_DRAW_DECIDED in done && coopCompanionPreset(it) == null && it !in GENERIC_FRIEND && "혼자" !in it
    }
    else -> null
}

/**
 * 등장인물 칸에 맞는 그림이 없으면 오또가 펠트 인형을 만든다 (10-06 조장). 아이를 기다리게 하지 않는다(규칙 8):
 * 대화는 그대로 가고, 15초 안에 오면 무대와 책의 그 자리가 이 인형으로 바뀐다. 못 오면 프리셋 그대로.
 * 보내는 건 아이가 말한 낱말뿐이다(아이 그림은 보내지 않는다). 그사이 칸 값이 바뀌었으면 늦게 온 인형은 버린다.
 */
internal suspend fun Director.drawFriend(onReady: () -> Unit = {}) {
    if (!Server.liveFor(s.mode)) return
    val mode = if (s.isCoop) "coop" else "story"
    val mask = s.nameMask()
    for (request in s.charactersToDraw()) {
        if (s.generatedCharacters.any { it.role == request.role && it.words == request.words } ||
            request in s.characterAttempts || request in s.characterRequests ||
            (s.coopCharacterTries[request] ?: 0) >= COOP_CHARACTER_TRIES) continue
        val token = Any()
        s.characterRequests[request] = token
        val style = s.bookStyle
        CoroutineScope(currentCoroutineContext()).launch {
            try {
                val made = withTimeoutOrNull(15_000) { Server.character(mask.mask(request.words), mode, style) }
                currentCoroutineContext().ensureActive()
                if (s.characterRequests[request] !== token || request !in s.charactersToDraw()) return@launch
                val saved = made?.let { withContext(Dispatchers.IO) { saveStoryImage(it.png) } }
                currentCoroutineContext().ensureActive()
                if (s.characterRequests[request] === token && request in s.charactersToDraw()) {
                    // Cache completed attempts only. A cancelled/stale request may be retried after undo/redo.
                    // Co-op retains its existing next-turn retry behavior.
                    if (mode == "story" && saved == null) s.characterAttempts.add(request)
                    // co-op's second character is tried again on the next step, but twice at most (#339 ② §2-5)
                    if (mode == "coop" && request.role == COOP_SECOND_ROLE && saved == null)
                        s.coopCharacterTries[request] = (s.coopCharacterTries[request] ?: 0) + 1
                    if (saved != null) {
                        s.generatedCharacters.removeAll { it.role == request.role }
                        s.generatedCharacters.add(GeneratedFriend(request.words, saved, made.rig, request.role))
                        log("character ready role=${request.role} words=${request.words}")
                        onReady()
                    } else log("character fallback role=${request.role} words=${request.words}")
                }
            } finally {
                if (s.characterRequests[request] === token) s.characterRequests.remove(request)
            }
        }
    }
}
