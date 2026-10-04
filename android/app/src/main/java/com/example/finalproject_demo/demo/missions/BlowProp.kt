package com.example.finalproject_demo.demo.missions

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.ga
import com.example.finalproject_demo.demo.eul

/**
 * C1 불어서 날리기의 소품 (`docs/맞춤미션_설계.md` §4 · §7-1 1-b 첫 순서).
 *
 * 소품은 **아이가 한 말**에서만 나온다 — 신호 단어가 곧 소품이다(촛불이라고 했으면 촛불). 그래서 실제 일
 * (다녀왔어요 · 곧 해요)에도 지어낸 소품이 들어가지 않는다(§3-8). 「바람」만은 날려 보낼 것이 말에 없으니
 * 상상 이야기는 나뭇잎, 실제 일은 어느 날에나 맞는 먼지로 둔다.
 *
 * @property art 남아 있을 때 그림 · [emoji] 그림이 없을 때
 * @property word 조사 계산용 이름
 * @property flame 불이 붙은 것인가 — 날아가지 않고 제자리에서 꺼진다. 다 불면 [gone] 그림 + 연기
 */
enum class BlowProp(
    val art: String,
    val emoji: String,
    val word: String,
    val flame: Boolean,
    override val before: String,
    override val result: String,
    override val ask: String,
    override val cheer: String,
    /** 다 불고 난 그림 — 촛불만(불꽃을 지운 그림). 나머지는 날아가 사라진다 */
    val gone: String? = null,
) : Slot1Prop {

    CANDLE("prop_candle", "🕯", "촛불", true, "촛불이 활활 타고 있어요.", "촛불이 다 꺼졌어요.",
        "촛불을 후~ 불어서 꺼 볼래?", "후~ 촛불이 다 꺼졌어!", gone = "prop_candle_out"),
    DANDELION("prop_dandelion", "🌼", "민들레 씨앗", false, "민들레 씨앗이 동그랗게 피어 있어요.", "민들레 씨앗이 훨훨 날아갔어요.",
        "민들레 씨앗을 후~ 불어서 날려 볼래?", "훨훨~ 씨앗이 멀리멀리 날아갔어!"),
    DUST("prop_cloud", "🌫", "먼지", false, "먼지가 뽀얗게 앉아 있어요.", "먼지가 후~ 다 날아갔어요.",
        "먼지를 후~ 불어서 날려 볼래?", "후~ 먼지가 다 날아갔어!"),
    LEAF("prop_leaf", "🍂", "나뭇잎", false, "나뭇잎이 잔뜩 쌓여 있어요.", "나뭇잎이 바람에 훨훨 날아갔어요.",
        "나뭇잎을 후~ 불어서 날려 볼래?", "휘이잉~ 나뭇잎이 다 날아갔어!"),
    ;

    /** 「촛불을 후~ 불었어요.」 */
    override val did: String get() = "$word${eul(word)} 후~ 불었어요."
}

/**
 * 아이 말에서 C1 소품을 찾는다 — 없으면 null(C1 을 고르지 않는다).
 * 순서는 구체적인 것부터: 촛불 · 생일 > 민들레 > 먼지 > 바람 · 불었다
 */
fun blowPropIn(said: String, realDay: Boolean): BlowProp? = when {
    listOf("촛불", "초를", "초가", "생일", "케이크").any { it in said } -> BlowProp.CANDLE
    "민들레" in said -> BlowProp.DANDELION
    "먼지" in said -> BlowProp.DUST
    listOf("바람", "불었", "후~", "후우").any { it in said } -> if (realDay) BlowProp.DUST else BlowProp.LEAF
    else -> null
}

/** 이 책의 C1 소품 — 자리 1 이 C1 일 때만 */
fun DemoState.blowProp(): BlowProp? {
    val f = storyFacts()
    return if (missions().slot1 == MissionId.C1) blowPropIn(f.slot1Words, f.realDay) else null
}

/** 「촛불을」 · 「먼지를」 */
fun BlowProp.withEul(): String = "$word${eul(word)}"

/** 「촛불이」 · 「먼지가」 */
fun BlowProp.withGa(): String = "$word${ga(word)}"
