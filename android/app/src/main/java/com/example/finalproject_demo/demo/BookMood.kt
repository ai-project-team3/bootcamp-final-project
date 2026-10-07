package com.example.finalproject_demo.demo

/*
 * 동화책 배경음악의 분위기 (#221 · docs/superpowers/specs/2026-10-07-bgm-design.md).
 * 쪽 종류가 이야기 흐름을 담고 있어 그것으로 고른다 — 서버 · 프롬프트는 바꾸지 않는다.
 */

/** 분위기 여섯 · 분위기마다 곡 둘 (`assets/bgm/`, eval/bake_bgm.py) */
enum class BgmMood(val tracks: List<String>) {
    DISCOVERY(listOf("discovery_1.webm", "discovery_2.webm")),
    PLAYFUL(listOf("playful_1.webm", "playful_2.webm")),
    ADVENTURE(listOf("adventure_1.webm", "adventure_2.webm")),
    TENSE(listOf("tense_1.webm", "tense_2.webm")),
    NIGHT(listOf("night_1.webm", "night_2.webm")),
    ENDING(listOf("ending_1.webm", "ending_2.webm")),
}

/** 쪽 종류 → 분위기 (진웅 10-07). 다 읽은 뒤 끝 화면은 [BgmMood.NIGHT] — [sceneMusic] */
fun moodOf(kind: PageKind): BgmMood = when (kind) {
    PageKind.COVER, PageKind.MEET -> BgmMood.DISCOVERY
    PageKind.DEPART, PageKind.JOURNEY -> BgmMood.ADVENTURE
    PageKind.TALK, PageKind.RUB, PageKind.DRAG -> BgmMood.PLAYFUL
    PageKind.SHAKE, PageKind.FAIL -> BgmMood.TENSE
    PageKind.TOGETHER -> BgmMood.ENDING
}

/**
 * 책마다 같은 곡 — 책 id 는 책장에 꽂을 때 생겨 첫 읽기에는 없다. 제목 · 첫 본문 쪽 문장은
 * 첫 읽기와 다시 읽기에 같으므로 그것으로 정한다
 */
fun bgmBookKey(title: String, firstCaption: String): Int = java.util.zip.CRC32().apply { update("$title\n$firstCaption".toByteArray(Charsets.UTF_8)) }.value.toInt()

fun trackOf(mood: BgmMood, bookKey: Int): String = mood.tracks[Math.floorMod(bookKey, mood.tracks.size)]

/**
 * 지금 읽는 동화책의 키 — 책장에 꽂힌 책([completedStoryBook]: 제목 `title ?: autoTitleFor()` · 첫 쪽 `bookCaption(1)`)
 * 으로 만든 다시 읽기 키와 같아야 같은 곡이 나온다
 */
fun DemoState.storyBookKey(): Int = bgmBookKey(title ?: autoTitleFor(), bookCaption(1))

/**
 * 그림일기 쪽 → 분위기 (#221 확장 · 진웅 10-07). 하루 이야기라 동화보다 순하게 — 일 · 마음 쪽은
 * **그 쪽 문장에** 어긋난 일 · 속상한 마음 낱말([TROUBLE_WORDS] · [TROUBLE_FEELINGS])이 있을 때만 긴장, 아니면 신나는 곡.
 * 하루 단위([troubled])로 정했더니 모래성이 무너진 날의 「재밌었어요」 쪽까지 긴장 곡이었다(10-07 실기기)
 */
fun diaryMoodOf(kind: DiaryPageKind, text: String): BgmMood = when (kind) {
    DiaryPageKind.DRAWING -> BgmMood.DISCOVERY
    DiaryPageKind.PLACE -> BgmMood.ADVENTURE
    DiaryPageKind.PROBLEM, DiaryPageKind.REACTION -> if (troubledLine(text)) BgmMood.TENSE else BgmMood.PLAYFUL
    DiaryPageKind.SOLUTION, DiaryPageKind.KEEP -> BgmMood.ENDING
    DiaryPageKind.PUZZLE -> BgmMood.PLAYFUL
}

private fun troubledLine(text: String): Boolean = TROUBLE_WORDS.any { it in text } || TROUBLE_FEELINGS.any { it in text }

/**
 * 그림일기는 제목을 다 읽은 뒤에 붙이기도 해서(`askTitle`) 제목으로 키를 만들면 첫 읽기와 다시 읽기의 곡이 갈린다 —
 * 첫 쪽 본문(기분 맺음 줄은 빼고)으로 정한다
 */
fun diaryBookKey(pages: List<DiaryPage>): Int = bgmBookKey("diary", pages.firstOrNull()?.text.orEmpty())

/** 동화책 장면(`sceneBook`)에서 음악을 트는 책 — 동화와 같이 만들기. 그림일기는 자기 책 화면(`readPictureDiary`)에서 튼다 */
val DemoState.bookMusic: Boolean get() = mode != StoryMode.DIARY

/**
 * 장면이 바뀔 때 — 책(쪽마다는 `sceneBook`)과 동화의 끝 화면 밖으로 나가면 음악을 끈다.
 * 끝 화면(친구 · 끝)은 [BgmMood.NIGHT]. 그림일기는 `PictureDiary.kt` 에서 쪽마다 튼다
 */
fun Director.sceneMusic(scene: Scene) {
    when {
        scene == Scene.BOOK && s.bookMusic -> {}
        (scene == Scene.FRIENDS || scene == Scene.END) && s.bookMusic ->
            com.example.finalproject_demo.net.Bgm.play(trackOf(BgmMood.NIGHT, s.storyBookKey()))
        else -> com.example.finalproject_demo.net.Bgm.stop()
    }
}
