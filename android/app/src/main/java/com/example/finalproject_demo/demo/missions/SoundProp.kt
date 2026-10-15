package com.example.finalproject_demo.demo.missions

import com.example.finalproject_demo.demo.DemoState

/**
 * 미션 자리 1 의 소품이 책에 남기는 말 — C1 불기 · C3 소리 흉내가 같이 쓴다(`Missions.kt` 가 이것만 본다).
 * 문장은 미션 화면이 아니라 책이 쓴다(서버 스키마 주석 `the result line stays the app's`)
 */
interface Slot1Prop {
    /** 미션 전 책 문장의 뒷부분 — 「(우리집에) 촛불이 활활 타고 있어요.」 */
    val before: String
    /** 미션 뒤 책 문장 — 「촛불이 다 꺼졌어요.」 */
    val result: String
    /** 아이가 한 일 — 미션 뒤 쪽 문장의 앞부분 「촛불을 후~ 불었어요.」 */
    val did: String
    /** 마스코트 안내 */
    val ask: String
    /** 다 했을 때 마스코트 말 */
    val cheer: String
    /** 부모 화면 「받은 선물」 — A6 의 「○○ 치운 손」 자리 */
    val badge: String
    /** `mission` 이벤트의 motion — A6 은 rub */
    val motion: String
}

/**
 * C3 소리 흉내 (`docs/맞춤미션_설계.md` §4 ★C3 · #101 둘째 순서) — 「말로 짓는」 앱에 가장 맞는 미션.
 *
 * 아이가 말한 소리 · 그 소리를 내는 것에서만 고른다(실제 일에 지어낸 소품 없음 · §3-8). 마이크는 무슨 말인지는 보지 않고
 * **끊어 말한 소리 덩어리 수**만 센다(`Blow.kt` `VoiceOnsets`) — 길게 이어지는 「아아아아」로는 차지 않는다(10-05).
 *
 * @property sound 화면에 크게 띄우는 소리 — 아이가 따라 하는 말
 */
enum class SoundProp(
    val art: String,
    val emoji: String,
    val sound: String,
    override val before: String,
    override val result: String,
    override val did: String,
    override val ask: String,
    override val cheer: String,
) : Slot1Prop {
    SIREN("prop_firetruck", "🚒", "삐뽀삐뽀", "소방차가 출동 준비를 하고 있어요.", "소방차가 힘차게 출동했어요.",
        "삐뽀삐뽀 큰 소리를 냈어요.", "소방차처럼 「삐뽀삐뽀!」 크게 소리 내 볼래?", "삐뽀삐뽀! 우와, 진짜 소방차 같아!"),
    CAR("prop_car", "🚗", "부릉부릉", "자동차가 출발하려고 해요.", "자동차가 부릉부릉 출발했어요.",
        "부릉부릉 소리를 냈어요.", "자동차처럼 「부릉부릉!」 소리 내 볼래?", "부릉부릉! 자동차가 신나게 달려!"),
    TRAIN("train", "🚂", "칙칙폭폭", "기차가 떠나려고 해요.", "기차가 칙칙폭폭 떠났어요.",
        "칙칙폭폭 소리를 냈어요.", "기차처럼 「칙칙폭폭!」 소리 내 볼래?", "칙칙폭폭! 기차가 출발했어!"),
    LION("prop_lion", "🦁", "어흥", "사자가 졸고 있어요.", "사자가 「어흥!」 하고 깨어났어요.",
        "어흥 소리를 냈어요.", "사자처럼 「어흥!」 크게 소리 내 볼래?", "어흥! 사자가 번쩍 깼어!"),
    DOG("prop_puppy", "🐶", "멍멍", "강아지가 꼬리를 흔들고 있어요.", "강아지가 「멍멍!」 하고 뛰어왔어요.",
        "멍멍 소리를 냈어요.", "강아지처럼 「멍멍!」 소리 내 볼래?", "멍멍! 강아지가 신나서 달려왔어!"),
    CHEER("coop_el_soccer", "⚽", "슛", "공이 골대 앞에 있어요.", "공이 쏙 골인했어요.",
        "슛 하고 외쳤어요.", "「슛!」 하고 크게 외쳐 볼래?", "슛! 골인이야!"),
    ;

    override val badge: String get() = "$sound 소리 낸 목소리"
    override val motion: String get() = "sound"
}

/** 아이 말에서 C3 소품을 찾는다 — 없으면 null. 소리 낱말이 먼저, 그다음 그 소리를 내는 것 */
fun soundPropIn(said: String): SoundProp? = when {
    // Words match only at the start of an eojeol (#259 · MissionWords.kt)
    saysAny(said, listOf("삐뽀", "사이렌", "소방차", "구급차", "경찰차")) -> SoundProp.SIREN
    saysAny(said, listOf("부릉", "빵빵", "자동차", "버스", "트럭", "택시", "오토바이")) -> SoundProp.CAR
    saysAny(said, listOf("칙칙", "기차", "지하철")) -> SoundProp.TRAIN
    saysAny(said, listOf("어흥", "사자", "호랑이")) -> SoundProp.LION
    saysAny(said, listOf("멍멍", "강아지", "왈왈")) -> SoundProp.DOG
    saysAny(said, listOf("슛", "골인", "응원")) -> SoundProp.CHEER
    else -> null
}

/**
 * 서버 `MissionId` 에 C3 가 들어갔나(`backend/app/schemas/story.py`). 들어가기 전에 보내면 `/story` 가 422 로 떨어져
 * 책 문장이 템플릿으로 간다(설계 §8) — 그래서 그전에는 C3 를 고르지 않는다. 화면은 미리 만들어 둔다(#101)
 */
const val SERVER_KNOWS_C3 = true   // main 0dfa664 — 서버 MissionId 에 C3 (10-05)

/** 이 책의 C3 소품 — 자리 1 이 C3 일 때만 */
fun DemoState.soundProp(): SoundProp? = pinnedMissions()?.sound            // a re-read book (#321 review)
    ?: if (pinnedMissions() == null && missions().slot1 == MissionId.C3) soundPropIn(storyFacts().slot1Words) else null

/** 이 책 자리 1 의 소품 말 — C1 · C3 면 그 소품, 아니면 null(A6 문지르기 · `Missions.kt` 원래 문장) */
fun DemoState.slot1Prop(): Slot1Prop? = blowProp() ?: soundProp()?.let { p ->
    // a re-read book has no words left — the word it was made with is pinned (#491 review)
    pinnedMissions()?.let { pin -> pin.soundWord?.let { namedSound(p, it) } } ?: saidSound(p, storyFacts().slot1Words)
        ?: themeSound(p, storyFacts().slot1Words, storyTheme()) ?: p
}

/**
 * The words of the whole story the theme is read from — the co-op card's name and what the child filled. A police story's
 * 「삐뽀삐뽀」 is a police car though the slot-1 words never said so (10-14 device: 「소방차처럼…」 in a police story)
 */
private fun DemoState.storyTheme(): String = (listOfNotNull(coopPick?.name) +
    slots.filterKeys { slotBy[it] in setOf("child", "card") }.values).joinToString(" ")

/** What a sound's story is about → the thing that makes it there. Only the siren has more than one maker */
private val SOUND_THEMES = mapOf(
    SoundProp.SIREN to listOf(listOf("경찰", "도둑") to "경찰차", listOf("구급", "병원", "응급") to "구급차"),
)

/**
 * The sound prop under the story's theme — only when the slot-1 words name no maker of the sound themselves
 * (「소방차가 삐뽀삐뽀」 in a police story stays 소방차)
 */
internal fun themeSound(p: SoundProp, slot1Words: String, theme: String): SaidSound? {
    val (own, others) = SOUND_NAMES[p] ?: return null
    if (saysAny(slot1Words, listOf(own) + others)) return null
    if (saysAny(theme, listOf(own, own.removeSuffix("차")))) return null
    return SOUND_THEMES[p]?.firstOrNull { (words, _) -> saysAny(theme, words) }?.let { (_, word) -> SaidSound(p, own, word) }
}

/** The thing each sound prop names in its lines, and the other things the child may have said for the same sound */
private val SOUND_NAMES = mapOf(
    SoundProp.SIREN to ("소방차" to listOf("구급차", "경찰차")),
    SoundProp.CAR to ("자동차" to listOf("버스", "트럭", "택시", "오토바이")),
    SoundProp.TRAIN to ("기차" to listOf("지하철")),
    SoundProp.LION to ("사자" to listOf("호랑이")),
)

/**
 * The sound prop under the child's own word — 「호랑이」 roars like the lion prop, but the book and Otto say 호랑이, not 사자
 * (coop book check k18 · 10-14: a taekwondo story with a tiger master got 「매트 옆 사자는 눈을 감고 있었어요」). Null when the
 * child said the prop's own word, or none of the others
 */
internal fun saidSound(p: SoundProp, said: String): SaidSound? {
    val (own, others) = SOUND_NAMES[p] ?: return null
    if (saysAny(said, listOf(own))) return null
    return others.firstOrNull { saysAny(said, listOf(it)) }?.let { SaidSound(p, own, it) }
}

/** [p] under [word] — a pinned word read back */
internal fun namedSound(p: SoundProp, word: String): SaidSound? = SOUND_NAMES[p]?.let { (own, _) -> SaidSound(p, own, word) }

class SaidSound(val prop: SoundProp, private val own: String, val word: String) : Slot1Prop {
    /** The maker's own picture when one is drawn — the C3 screen falls back to the prop's (no police car art baked yet) */
    val art: String? get() = WORD_ART[word]
    private fun named(line: String) = line.replace(own, word).let { fixParticles(it, word) }
    override val before get() = named(prop.before)
    override val result get() = named(prop.result)
    override val did get() = named(prop.did)
    override val ask get() = named(prop.ask)
    override val cheer get() = named(prop.cheer)
    override val badge get() = prop.badge
    override val motion get() = prop.motion
}

private val WORD_ART = mapOf("경찰차" to "prop_police_car", "구급차" to "prop_ambulance")

/** 「사자가」 → 「호랑이가」 is fine, but 「버스가」 after 「자동차가」 needs its particle checked — 이/가 · 은/는 · 을/를 follow the new word */
private fun fixParticles(line: String, word: String): String {
    val last = word.last()
    val bat = last in '가'..'힣' && (last - '가') % 28 != 0
    return line.replace(Regex("${Regex.escape(word)}(이|가|은|는|을|를)(?![가-힣])")) { m ->
        val p = m.groupValues[1]
        word + when (p) { "이", "가" -> if (bat) "이" else "가"; "은", "는" -> if (bat) "은" else "는"; else -> if (bat) "을" else "를" }
    }
}
