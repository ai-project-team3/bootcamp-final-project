package com.example.finalproject_demo.demo

data class StoryServerInput(val slots: Map<String, String>, val sources: Map<String, String>)

/** Project template answers into extra only for a request; never replace the original slot or its source. */
fun DemoState.storyServerInput(): StoryServerInput {
    // template answers, then sentences kept from a guarded who-slot (StoryNameGuard.kt) — each with its own source
    val localKeys = template?.let { it.plot + it.ending }.orEmpty().filter { it !in com.example.finalproject_demo.net.Server.SLOTS } +
        slots.keys.filter { it.startsWith(WHO_SAID_PREFIX) }.sorted()
    val original = slots["extra"].orEmpty()
    val additions = localKeys.mapNotNull { key -> slots[key]?.takeIf(String::isNotBlank)?.let { key to it } }
    val extra = (listOf(original).filter(String::isNotBlank) + additions.map { it.second }.filterNot { it in original })
        .distinct().joinToString("\n")
    val values = slots.toMutableMap()
    val sources = slotBy.toMutableMap()
    // The sound slot holds the app's own note when the child recorded a sound (StorySound) — not story content.
    // 10-05 device: /story read it and wrote 「과학을 싫어하는 원주민이 친구의 소리를 직접 만들었어요」.
    // The recording itself plays from the book; the server gets no sound line for it.
    if (values["sound"] == RECORDED_SOUND_NOTE) { values.remove("sound"); sources.remove("sound") }
    if (extra.isNotBlank()) values["extra"] = extra
    val contributingSources = (if (original.isNotBlank()) listOf(slotBy["extra"]) else emptyList()) + additions.map { slotBy[it.first] }
    val source = contributingSources.distinct().singleOrNull()
    if (source != null) sources["extra"] = source else sources.remove("extra")
    return StoryServerInput(values, sources)
}

/** What StorySound writes into the sound slot after the child records — an app note, never sent to /story */
const val RECORDED_SOUND_NOTE = "친구의 소리를 직접 만들었어요"
