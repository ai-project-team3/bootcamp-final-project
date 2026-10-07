package com.example.finalproject_demo.demo

import android.content.Context
import java.io.File
import java.security.MessageDigest
import android.util.AtomicFile

/** Only Otto's generated narration. Child recordings use their separate private store. */
class BookVoiceStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "book_voices")
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun directory(mode: StoryMode, id: String) = File(File(root, mode.name), hash(id))
    private fun file(mode: StoryMode, id: String, line: String) = File(directory(mode, id), hash(line) + ".mp3")

    fun read(mode: StoryMode, id: String, line: String): ByteArray? = runCatching {
        AtomicFile(file(mode, id, line)).readFully().takeIf { it.isNotEmpty() }
    }.getOrNull()

    fun keep(mode: StoryMode, id: String, line: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val target = file(mode, id, line)
        target.parentFile!!.mkdirs()
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }

    fun delete(mode: StoryMode, id: String) {
        directory(mode, id).let { if (it.exists()) check(it.deleteRecursively()) }
    }
}
