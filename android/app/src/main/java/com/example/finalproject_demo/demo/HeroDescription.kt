package com.example.finalproject_demo.demo

/** A live story description is kept only after the child confirms what was heard. */
internal suspend fun Director.confirmHeroDescription(heard: String): Boolean {
    var shown = false
    try {
        while (true) {
            val reply = awaitReplyShowing {
                if (!shown) {
                    s.stage = Stage.HeroAnswer(heard)
                    inputs(mic = true, next = false)
                    say("「$heard」, 맞아?")
                    buttons(
                        DemoBtn("🖐 맞아") { send(Reply.Tapped("ok", "맞아")) },
                        DemoBtn("🖐 다시 말할래") { send(Reply.Tapped("no", "다시 말할래")) },
                    )
                    shown = true
                }
            }
            when (reply) {
                is Reply.Tapped -> when (reply.value) {
                    "ok" -> return true
                    "no" -> return false
                }
                is Reply.Spoke -> spokenYesNo(reply.text)?.let { return it }
                else -> Unit
            }
        }
    } finally {
        inputs(mic = false, next = false)
        buttons()
    }
}
