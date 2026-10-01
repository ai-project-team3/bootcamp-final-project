package com.example.finalproject_demo.demo

import com.example.finalproject_demo.sound.ChildSound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

private fun soundSaveMarker(bookId: String): File? {
    require(bookId.matches(Regex("[A-Za-z0-9_-]+")))
    return ChildSound.root?.let { File(it, "story_pending/$bookId.pending") }
}

/** Recover only interrupted story transactions; other modes' recordings have no story marker. */
internal fun recoverStorySounds(books: List<SavedStoryBook>) {
    val root = ChildSound.root ?: return
    val savedIds = books.map { it.id }.toSet()
    File(root, "story_pending").listFiles()?.filter { it.isFile && it.extension == "pending" }?.forEach { marker ->
        val id = marker.nameWithoutExtension
        if (id.matches(Regex("[A-Za-z0-9_-]+"))) {
            if (id !in savedIds) ChildSound.deleteBook(id)
            marker.delete()
        }
    }
}

/** Keep the recording before saving its reference. Retries reuse the book that already owns it. */
internal fun DemoState.keepStorySound(book: SavedStoryBook): Boolean {
    val clip = storySoundClip ?: return book.soundClipId == null
    if (storySoundBookId == book.id) return ChildSound.find(book.id, clip.id) != null
    val marker = soundSaveMarker(book.id) ?: return false
    check(marker.parentFile!!.isDirectory || marker.parentFile!!.mkdirs())
    marker.writeText(clip.id)
    val kept = ChildSound.keep(clip, book.id) ?: return false
    storySoundClip = kept
    storySoundBookId = book.id
    return true
}

internal fun DemoState.commitStorySound() {
    storySoundSaved = true
    storySoundBookId?.let { soundSaveMarker(it)?.delete() }
}

/** Resetting an unfinished story must not remove a recording owned by a completed book. */
internal fun DemoState.clearStorySound() {
    val owner = storySoundBookId
    if (owner == null) storySoundClip?.let(ChildSound::cancel)
    else if (!storySoundSaved) {
        ChildSound.deleteBook(owner)
        soundSaveMarker(owner)?.delete()
    }
    storySoundClip = null
    storySoundBookId = null
    storySoundAttempted = false
    storySoundSaved = false
}

/** Creative sounds never pass through ask(), STT, or the language verdict. */
suspend fun Director.recordStorySound() {
    if (s.mode != StoryMode.STORY || s.storySoundAttempted) return
    inputs(false, false)
    setListening(null)
    var pending: ChildSound.SoundClip? = null
    var retries = 2
    var recordRequested = false

    fun choices(vararg cards: Card) {
        s.stage = Stage.CardsRow(cards.toList())
        buttons(*cards.map { card -> DemoBtn(card.label) { send(Reply.Tapped(card.value, card.label)) } }.toTypedArray())
    }

    fun finishWithoutSound() {
        s.storySoundAttempted = true
        if ("sound" !in s.storyUnneededSlots) s.storyUnneededSlots += "sound"
        say("좋아, 소리 없이 이야기를 이어 갈게!")
        log("창작 소리 건너뛰기 · 녹음이나 아이 발화를 만들지 않음")
        mark("sound")
    }

    try {
        while (true) {
            if (!recordRequested) {
                say("친구의 소리를 직접 만들어 볼까?")
                awaitVoice()
                choices(Card("🎤 소리 내기", Art.Emoji("🎤"), "sound:record"),
                    Card("소리 없이 계속", Art.Emoji("➡️"), "sound:skip"))
                if (awaitValue("sound:record", "sound:skip") == "sound:skip") { finishWithoutSound(); return }
            }
            recordRequested = false

            buttons()
            s.stage = Stage.Show(s.friendArt, "친구 소리 만들기")
            say("지금 소리 내 봐!")
            awaitVoice()
            s.line = "소리를 듣고 있어…"
            pending = try { ChildSound.record() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            if (pending == null) {
                say("소리가 잘 들리지 않았어. 다시 해도 되고, 소리 없이 넘어가도 돼.")
                awaitVoice()
                choices(Card("🎤 다시 소리 내기", Art.Emoji("🎤"), "sound:record"),
                    Card("소리 없이 계속", Art.Emoji("➡️"), "sound:skip"))
                if (awaitValue("sound:record", "sound:skip") == "sound:skip") { finishWithoutSound(); return }
                recordRequested = true
                continue
            }

            say("소리를 담았어! 다시 들어 볼까?")
            awaitVoice()
            while (pending != null) {
                val options = mutableListOf(
                    Card("🔊 다시 듣기", Art.Emoji("🔊"), "sound:play"),
                    Card("👍 이 소리로", Art.Emoji("👍"), "sound:ok"),
                )
                if (retries > 0) options += Card("🔁 다시 녹음", Art.Emoji("🔁"), "sound:retry")
                choices(*options.toTypedArray())
                when (awaitValue(*options.map { it.value }.toTypedArray())) {
                    "sound:play" -> {
                        buttons()
                        s.stage = Stage.Show(s.friendArt, "내가 만든 소리")
                        ChildSound.play(pending!!)
                    }
                    "sound:retry" -> {
                        ChildSound.cancel(pending!!)
                        pending = null
                        retries--
                        recordRequested = true
                    }
                    "sound:ok" -> {
                        if (s.storySoundBookId == null) s.storySoundClip?.let(ChildSound::cancel)
                        s.storySoundClip = pending
                        s.storySoundBookId = null
                        s.storySoundSaved = false
                        s.storySoundAttempted = true
                        // A description of the activity is safe to send; audio bytes and paths are not slots.
                        s.slots["sound"] = "친구의 소리를 직접 만들었어요"
                        s.slotBy["sound"] = "child"
                        s.sound = s.slots["sound"]
                        s.soundLine = "내가 만든 소리"
                        s.reactions++
                        event("make", "kind" to "sound", "source" to "child")
                        event("slot_filled", "slot" to "sound", "value" to s.sound, "source" to "child")
                        mark("sound")
                        log("창작 소리 원본을 기기에만 보관 · 받아쓰기 및 수준 판정 없음")
                        buttons()
                        return
                    }
                }
            }
        }
    } finally {
        if (pending != s.storySoundClip) pending?.let(ChildSound::cancel)
    }
}

suspend fun Director.playStorySound(book: SavedStoryBook? = null): Boolean {
    val clip = if (book == null) s.storySoundClip
        else book.soundClipId?.let { runCatching { ChildSound.find(book.id, it) }.getOrNull() }
    if (clip == null || !clip.file.exists()) return false
    awaitVoice()
    ChildSound.play(clip)
    return true
}
