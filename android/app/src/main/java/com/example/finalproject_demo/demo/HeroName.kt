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
    var tries = 0
    while (true) {
        s.stage = Stage.NameEntry(attr, image)
        val r = ask(Question(
            text = if (tries == 0) "이 친구 이름은 뭐라고 할까?" else "다시 한 번 말해 줄래? 글로 적어도 돼!",
            kind = Kind.EASY,
            easierText = "부르고 싶은 이름을 말해 줘. 「콩이」처럼!",
            noCards = true,
            spoken = listOf(Answer("콩이", lv = 1), Answer("뽀삐라고 할래", lv = 2), Answer("이름은 반짝이야", lv = 2)),
        ))
        // 글로 적은 이름은 본 그대로라 되묻지 않는다
        if (r is Reply.Tapped && r.value == NAME_TYPED)
            return heroNameFrom(r.label)?.also { log("주인공 이름: 「$it」 (글로)") }
        val heard = (r as? Reply.Spoke)?.text?.let(::heroNameFrom) ?: return null

        // 말로 들은 이름은 맞는지 묻는다 — 10-02 실기기: 「삐죽이」가 「비주기」로 적혔다.
        // 화면에 들은 이름이 글 칸에 미리 들어가 있어서, 틀렸으면 고쳐 적어도 된다
        when (confirmHeroName(attr, image, heard)) {
            NAME_OK -> { log("주인공 이름: 「$heard」 (말로 · 확인)"); return heard }
            NAME_AGAIN -> {
                tries++
                log("주인공 이름 「$heard」 아님 → 다시 듣기 ($tries)")
                if (tries >= 2) say("글로 적어 줘도 돼! 엄마 아빠가 적어 줘도 좋아.")
            }
            else -> return heroNameFrom(s.typedName.orEmpty())?.also { log("주인공 이름: 「$it」 (들은 것을 글로 고침)") }
        }
    }
}

/** 들은 이름 확인 — [맞아] · [아니야] · 글 칸에서 고쳐 [이 이름으로]. 고쳐 적은 것은 [DemoState.typedName] 으로 */
private suspend fun Director.confirmHeroName(attr: HeroAttr, image: String?, heard: String): String {
    // The mic stays on: 10-05 device, the child answered 「응」 out loud and nothing moved — only the buttons worked
    inputs(mic = true, next = false)
    say("「$heard」 맞아?")
    var shown = false
    while (true) {
        when (val r = awaitReplyShowing { if (!shown) { s.stage = Stage.NameEntry(attr, image, heard = heard); shown = true } }) {
            is Reply.Tapped -> when (r.value) {
                NAME_OK, NAME_AGAIN -> return r.value
                NAME_TYPED -> { s.typedName = r.label; return NAME_TYPED }
            }
            is Reply.Spoke -> {
                when (spokenYesNo(r.text)) {
                    true -> return NAME_OK
                    false -> return NAME_AGAIN
                    // said a name again — take that one, as if it had been typed
                    null -> heroNameFrom(r.text)?.let { s.typedName = it; return NAME_TYPED }
                }
            }
            else -> {}
        }
    }
}

/** 「응 · 맞아 · 네」 → true, 「아니 · 다시」 → false, anything else → null (10-05) */
internal fun spokenYesNo(text: String): Boolean? {
    val t = text.trim().trimEnd('.', '!', '?', '~', ' ')
    if (Regex("^(아니|아냐|아닌데|틀려|다시)").containsMatchIn(t)) return false
    if (t == "어" || Regex("^(응|웅|네|넹|예|맞아|맞|그래|좋아|ㅇㅇ)").containsMatchIn(t) && t.length <= 6) return true
    return null
}

const val NAME_OK = "name:ok"
const val NAME_AGAIN = "name:again"
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
