package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.HeroAttr

/**
 * 주인공 이름 짓기 (10-02 조장 · #87) — 아이가 만든 인형에 이름을 붙인다. 말로도 · 글로도.
 *
 * 그 이름이 이 이야기의 주인공(`{주인공}`)이 된다(`DemoState.nameMask`). 마스코트가 아이를 부르는 말은
 * 그대로 보호자가 정한 호칭이다(`net/ChildCall`) — 아이는 지은이, 인형은 주인공.
 * 아무 말도 안 하면 이름 없이 간다(인형은 모습으로 불린다) — 이름을 지어 주지 않는다.
 */
suspend fun Director.askHeroName(attr: HeroAttr, image: String?): String? {
    s.stage = Stage.NameEntry(attr, image)
    val r = ask(Question(
        text = "이 친구 이름은 뭐라고 할까?",
        kind = Kind.EASY,
        easierText = "부르고 싶은 이름을 말해 줘. 「콩이」처럼!",
        noCards = true,
        spoken = listOf(Answer("콩이", lv = 1), Answer("뽀삐라고 할래", lv = 2), Answer("이름은 반짝이야", lv = 2)),
    ))
    val raw = when (r) {
        is Reply.Spoke -> r.text
        is Reply.Tapped -> if (r.value == NAME_TYPED) r.label else null
        else -> null
    } ?: return null
    return heroNameFrom(raw)?.also { log("주인공 이름: 「$it」 (${if (r is Reply.Spoke) "말로" else "글로"})") }
}

const val NAME_TYPED = "name:typed"

/**
 * 「콩이라고 할래」 · 「이름은 뽀삐야」 → 「콩이」 · 「뽀삐」. 앞의 「이름은」, 끝의 「라고 할래 · 야 · 예요」를 떼고
 * 글자 · 숫자 · 띄어쓰기만, 10자까지. 남는 게 없으면 null.
 */
fun heroNameFrom(raw: String): String? {
    var t = raw.trim().trimEnd('.', '!', '?', '~', ' ')
    t = t.replace(Regex("^(음+|어+|그|이|저)?\\s*(이름은|이름|얘는|이건|걔는)\\s*"), "")
    // 「콩이라고」는 「콩이」 — 어린이 이름은 「~이」로 끝나는 일이 많아서 「이」는 이름 쪽에 둔다
    t = t.replace(Regex("\\s*(라고|라구)\\s*(할래|해|하자|부를래|불러|지을래|할게|했어)?$"), "")
    t = t.replace(Regex("(예요|에요|야|다)$"), "")
    t = t.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(10)
    return t.ifBlank { null }
}
