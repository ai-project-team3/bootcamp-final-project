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
