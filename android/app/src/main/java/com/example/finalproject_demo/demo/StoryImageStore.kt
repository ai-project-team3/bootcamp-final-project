package com.example.finalproject_demo.demo

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID

/** 서버 PNG를 앱 전용 파일에 남겨 책을 다시 열 때 같은 배경을 쓴다. */
class StoryImageStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "story_images")

    fun save(png: ByteArray): String? {
        if (png.isEmpty() || BitmapFactory.decodeByteArray(png, 0, png.size) == null) return null
        if (!directory.exists() && !directory.mkdirs()) return null
        val file = File(directory, "${UUID.randomUUID()}.png")
        return try {
            file.writeBytes(png)
            "local:${file.absolutePath}"
        } catch (_: Exception) {
            file.delete()
            null
        }
    }
}
