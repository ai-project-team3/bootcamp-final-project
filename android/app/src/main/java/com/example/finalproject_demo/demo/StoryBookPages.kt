package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server

/** A finished story's frozen plan; other modes keep their own template rules. */
val DemoState.bookPages: List<PageSpec>
    get() = if (mode == StoryMode.STORY) storyBookPages ?: template?.pages.orEmpty()
        else template?.pages.orEmpty()

/** Add room for final content without moving either mission or replacing the early template. */
fun DemoState.prepareStoryBookPages() {
    if (mode != StoryMode.STORY) return
    val base = template?.pages ?: return
    val content = storyServerInput().slots.filter { (key, value) ->
        key in Server.SLOTS && key != "title" && value.isNotBlank()
    }
    val extraCount = (content.size - base.size).coerceIn(0, (8 - base.size).coerceAtLeast(0))
    // Every story template ends with TOGETHER, after its last mission.
    val ending = base.indexOfLast { it.kind == PageKind.TOGETHER }
    if (extraCount == 0 || ending != base.lastIndex ||
        base.drop(ending).any { it.kind == PageKind.RUB || it.kind == PageKind.DRAG }) {
        storyBookPages = base.toList()
        return
    }
    // Stable slot order, independent of the order answers arrived. A local fallback uses
    // recorded content verbatim, never inventing a quotation or changing its provenance.
    val order = listOf("place", "problem", "newcomer", "name", "cause", "solution",
        "reaction", "companion", "adult", "sound", "extra")
    val additions = order.mapNotNull(content::get).drop(base.size).take(extraCount)
        .mapIndexed { index, line ->
            PageSpec(if (index == 0) PageKind.JOURNEY else PageKind.TALK) { line }
        }
    storyBookPages = base.take(ending) + additions + base.drop(ending)
}
