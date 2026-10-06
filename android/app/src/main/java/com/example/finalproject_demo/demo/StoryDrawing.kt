package com.example.finalproject_demo.demo

/** Story presets reuse felt artwork; the saved kind/index reproduce the same selection. */
internal fun storyPresetMatch(kind: String): Art? = THEMES.asSequence()
    .flatMap { it.newcomers.asSequence() }
    .firstOrNull { it.value == kind || it.label == kind }
    ?.art

internal fun storyPresetResource(kind: String): String = (storyPresetMatch(kind) as? Art.Img)?.name ?: "nc_alien"

internal fun storyPresetArt(kind: String, preset: Int): Art =
    Art.ChildDrawing(emptyList(), preset, presetAsset = storyPresetResource(kind))

/** The child chooses the newcomer's appearance. Original strokes never enter an AI request. */
suspend fun Director.prepareStoryFriendDrawing() {
    val newcomer = s.slots["newcomer"]?.takeIf(String::isNotBlank) ?: return
    if (s.drawing.isNotEmpty() || "draw" in s.done) return
    inputs(false, false)
    s.stage = Stage.DrawPad()
    say("${newcomer}${eun(newcomer)} 어떻게 생겼을까? 크레용으로 그려 줄래?")
    buttons(
        DemoBtn("✅ 다 그렸어") { send(Reply.Tapped("done", "완료")) },
        DemoBtn("그리기 싫어") { send(Reply.Tapped("preset", "프리셋")) },
    )
    val answer = awaitValue("done", "preset")
    if (answer == "preset" || s.drawing.isEmpty()) {
        s.drawing.clear()
        // 오또가 만든 인형이 벌써 왔으면 그 모습으로 — 아이가 말한 친구다. 프리셋 카드는 이름이 맞지 않으면 다 외계인이었다
        if (s.storyFriendDoll != null) {
            event("make", "kind" to "doll", "source" to "server")
            s.stage = Stage.Show(s.friendArt, s.slots["name"]?.takeIf(String::isNotBlank) ?: newcomer)
            say("그럼 오또가 만든 ${newcomer}${if (bat(newcomer)) "이야" else "야"}. 이 모습 그대로 책에 넣을게.")
            pause(900)
            mark("draw")
            return
        }
        s.stage = Stage.CardsRow((0..2).map { Card(listOf("뿌뿌", "반짝", "동글")[it], storyPresetArt(newcomer, it), "$it") })
        say("그럼 이 중에 어떤 모습이 좋을까?")
        buttons(DemoBtn("첫 번째 그림") { send(Reply.Tapped("0", "뿌뿌")) })
        s.drawnPreset = awaitValue("0", "1", "2").toInt()
        event("make", "kind" to "preset", "source" to "card")
    } else {
        event("make", "kind" to "draw", "source" to "child")
        log("아이 그림 ${s.drawing.size}획 · 원본을 책에 사용 · 서버 전송 없음")
    }
    s.reactions++
    val label = s.slots["name"]?.takeIf(String::isNotBlank) ?: newcomer
    s.stage = Stage.Show(s.friendArt, label)
    say("좋아! 이 모습 그대로 책에 넣을게.")
    pause(900)
    mark("draw")
}
