package com.example.finalproject_demo.demo

data class StoryServerInput(val slots: Map<String, String>, val sources: Map<String, String>)

/** Project template answers into extra only for a request; never replace the original slot or its source. */
fun DemoState.storyServerInput(): StoryServerInput {
    val localKeys = template?.let { it.plot + it.ending }.orEmpty().filter { it !in com.example.finalproject_demo.net.Server.SLOTS }
    val original = slots["extra"].orEmpty()
    val additions = localKeys.mapNotNull { key -> slots[key]?.takeIf(String::isNotBlank)?.let { key to it } }
    val extra = (listOf(original).filter(String::isNotBlank) + additions.map { it.second }.filterNot { it in original })
        .distinct().joinToString("\n")
    val values = slots.toMutableMap()
    val sources = slotBy.toMutableMap()
    if (extra.isNotBlank()) values["extra"] = extra
    val contributingSources = (if (original.isNotBlank()) listOf(slotBy["extra"]) else emptyList()) + additions.map { slotBy[it.first] }
    val source = contributingSources.distinct().singleOrNull()
    if (source != null) sources["extra"] = source else sources.remove("extra")
    return StoryServerInput(values, sources)
}
