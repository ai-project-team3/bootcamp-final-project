package com.example.finalproject_demo.demo

/** Read stored story text through the scene's voice queue; never regenerate or save the book. */
internal suspend fun Director.readSavedStory(book: SavedStoryBook, mode: StoryMode = StoryMode.STORY) {
    val previousMode = s.mode
    var page = 0
    var shown = -1
    s.mode = mode
    buttons()
    try {
        while (true) {
            if (shown != page) {
                s.stage = Stage.SavedStory(book, page)
                say(if (page == 0) "『${book.title}』" else book.pages[page - 1].caption)
                shown = page
            }
            when ((awaitReply() as? Reply.Tapped)?.value) {
                "next" -> if (page < book.pages.size) page++
                "prev" -> if (page > 0) page--
                "speak" -> say(if (page == 0) "『${book.title}』" else book.pages[page - 1].caption)
                "close" -> return
            }
        }
    } finally {
        stopSpeech()
        s.mode = previousMode
    }
}
