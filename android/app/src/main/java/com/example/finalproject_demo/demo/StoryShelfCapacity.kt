package com.example.finalproject_demo.demo

/** One cover at a time keeps the existing card layout usable on phones as well as tablets. */
suspend fun Director.saveStoryWithChoice(): Boolean {
    if (!storyNeedsReplacement()) return saveFinishedStory()
    val books = storyReplacementChoices()
    val originalStage = s.stage
    val originalLine = s.line
    var index = 0
    inputs(false, false)
    try {
        say("동화책장이 가득 찼어. 새 책을 넣으려면 뺄 책을 골라 줘. 취소하면 지금 책들은 그대로 있어.")
        awaitVoice()
        while (true) {
            val book = books[index]
            s.line = "뺄 책을 골라 줘 (${index + 1}/${books.size})"
            val cards = listOf(
                Card(book.title, Art.Img(book.bgName, Art.Emoji("📚")), "replace:choose"),
                Card("다음 책", Art.Emoji("▶️"), "replace:next"),
                Card("취소", Art.Emoji("↩️"), "replace:cancel"),
            )
            s.stage = Stage.CardsRow(cards)
            buttons(*cards.map { c -> DemoBtn(c.label) { send(Reply.Tapped(c.value, c.label)) } }.toTypedArray())
            when (awaitValue(*cards.map { it.value }.toTypedArray())) {
                "replace:cancel" -> return false
                "replace:next" -> index = (index + 1) % books.size
                "replace:choose" -> {
                    say("『${book.title}』와 그 책의 소리를 빼고 새 동화책을 넣을까?")
                    s.stage = Stage.Confirm(Art.Img(book.bgName, Art.Emoji("📚")), "새 책으로 바꾸기", "다시 고르기")
                    buttons(DemoBtn("새 책으로 바꾸기") { send(Reply.Tapped("ok", "바꾸기")) },
                        DemoBtn("다시 고르기") { send(Reply.Tapped("no", "다시 고르기")) })
                    if (awaitValue("ok", "no") == "ok") return saveFinishedStory(book.id)
                }
            }
        }
    } finally {
        // Cancelling the picker must not leave an interactive card from the old scene.
        s.stage = originalStage
        s.line = originalLine
        buttons()
    }
}
