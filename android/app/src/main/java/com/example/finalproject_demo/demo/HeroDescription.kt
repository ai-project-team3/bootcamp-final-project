package com.example.finalproject_demo.demo

/** A live story description is kept only after the child confirms what was heard. */
internal suspend fun Director.confirmHeroDescription(heard: String): String? {
    var candidate = heard
    var shown = false
    try {
        while (true) {
            val reply = awaitReplyShowing {
                if (!shown) {
                    s.stage = Stage.HeroAnswer(candidate)
                    inputs(mic = true, next = false)
                    say("「$candidate」, 맞아?")
                    buttons(
                        DemoBtn("🖐 맞아") { send(Reply.Tapped("ok", "맞아")) },
                        DemoBtn("🖐 다시 말할래") { send(Reply.Tapped("no", "다시 말할래")) },
                    )
                    shown = true
                }
            }
            when (reply) {
                is Reply.Tapped -> when (reply.value) {
                    "ok" -> return candidate
                    "no" -> return null
                }
                is Reply.Spoke -> {
                    val raw = reply.text.trim().trimEnd('.', '!', '?', '~', ' ')
                    val yesNo = if (s.mode == StoryMode.STORY && spokenYesNo(raw) == true &&
                        !Regex("^(응|웅|어|네|넹|예|맞아|맞아요|맞|그래|좋아|ㅇㅇ)([,\\s]+(맞아|맞아요|그래|좋아))?$").matches(raw)) null
                        else spokenYesNo(raw)
                    if (s.mode != StoryMode.STORY) {
                        yesNo?.let { return if (it) candidate else null }
                        continue
                    }
                    // A rejection may include the replacement in the same breath.
                    val replacement = raw
                        .replace(Regex("^(아니야|아니요|아니|아냐|아닌데)[,，.!?\\s]*"), "")
                        .trim().trimEnd('.', '!', '?', '~', ' ')
                    when {
                        yesNo == true -> return candidate
                        yesNo == false && replacement.isBlank() -> return null
                        yesNo == false && replacement == raw -> return null
                        yesNo == false && Regex("^다시(\\s*(말할래|말할게|할래|할게|말하고 싶어|말할 거야))?$").matches(replacement) -> return null
                        replacement.isNotBlank() -> { candidate = replacement; shown = false }
                    }
                }
                else -> Unit
            }
        }
    } finally {
        inputs(mic = false, next = false)
        buttons()
    }
}
