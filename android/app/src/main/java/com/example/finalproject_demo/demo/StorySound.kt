package com.example.finalproject_demo.demo

import com.example.finalproject_demo.sound.ChildSound

/** Keep the recording before saving its reference. Retries reuse the book that already owns it. */
internal fun DemoState.keepStorySound(book: SavedStoryBook): Boolean {
    val clip = storySoundClip ?: return book.soundClipId == null
    if (storySoundBookId == book.id) return ChildSound.find(book.id, clip.id) != null
    val kept = ChildSound.keep(clip, book.id) ?: return false
    storySoundClip = kept
    storySoundBookId = book.id
    return true
}

/** Resetting an unfinished story must not remove a recording owned by a completed book. */
internal fun DemoState.clearStorySound() {
    if (storySoundBookId == null) storySoundClip?.let(ChildSound::cancel)
    storySoundClip = null
    storySoundBookId = null
    storySoundAttempted = false
}
