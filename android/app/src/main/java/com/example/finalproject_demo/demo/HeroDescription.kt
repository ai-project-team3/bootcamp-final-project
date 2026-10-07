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
                    val yesNo = spokenYesNo(reply.text)
                    if (s.mode != StoryMode.STORY) {
                        yesNo?.let { return if (it) candidate else null }
                        continue
                    }
                    // A rejection may include the replacement in the same breath.
                    val replacement = reply.text.trim()
                        .replace(Regex("^(아니야|아니요|아니|아냐|아닌데)[,，.!?\\s]*"), "")
                        .trim().trimEnd('.', '!', '?', '~', ' ')
                    when {
                        yesNo == true -> return candidate
                        yesNo == false && replacement.isBlank() -> return null
                        yesNo == false && replacement == reply.text.trim().trimEnd('.', '!', '?', '~', ' ') -> return null
                        yesNo == false && replacement in listOf("다시", "다시 말할래", "다시 할래") -> return null
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
