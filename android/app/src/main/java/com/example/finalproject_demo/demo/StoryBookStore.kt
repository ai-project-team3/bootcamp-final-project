package com.example.finalproject_demo.demo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedStoryPage(val kind: PageKind, val caption: String)

data class SavedStoryBook(
    val id: String,
    val title: String,
    val themeKey: String,
    val bgName: String,
    val pages: List<SavedStoryPage>,
    val visuals: SavedStoryVisuals? = null,
)

/** 완성된 동화만 저장한다. 진행 중인 이야기와 다른 모드의 책은 이 저장소의 범위 밖이다. */
interface StoryBookStore {
    fun load(): List<SavedStoryBook>
    fun save(book: SavedStoryBook)
}

fun DemoState.completedStoryBook(): SavedStoryBook? {
    if (mode != StoryMode.STORY || template == null || pageCount == 0) return null
    val pages = (1..pageCount).map { SavedStoryPage(pageKind(it), bookCaption(it)) }
    if (pages.any { it.caption.isBlank() }) return null
    return SavedStoryBook(UUID.randomUUID().toString(), title ?: autoTitleFor(), themeKey, bgName, pages, captureStoryVisuals())
}

/** 앱 내부 저장소에만 보관한다. 저장 시 전체 배열을 한 번에 교체해 중간 상태를 남기지 않는다. */
class LocalStoryBookStore(context: Context) : StoryBookStore {
    private val prefs = context.applicationContext.getSharedPreferences("story_books", Context.MODE_PRIVATE)

    override fun load(): List<SavedStoryBook> {
        val raw = prefs.getString("books", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                try {
                    val obj = array.getJSONObject(i)
                    val pageArray = obj.getJSONArray("pages")
                    val pages = (0 until pageArray.length()).map { j ->
                        val page = pageArray.getJSONObject(j)
                        SavedStoryPage(PageKind.valueOf(page.getString("kind")), page.getString("caption"))
                    }
                    if (pages.isEmpty() || pages.any { it.caption.isBlank() }) null
                    else SavedStoryBook(
                        obj.getString("id"), obj.getString("title"), obj.getString("themeKey"),
                        obj.getString("bgName"), pages,
                        obj.optJSONObject("visuals")?.let { runCatching { storyVisualsFromJson(it) }.getOrNull() },
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    override fun save(book: SavedStoryBook) {
        val all = listOf(book) + load().filterNot { it.id == book.id }
        val array = JSONArray()
        all.forEach { entry ->
            val pages = JSONArray()
            entry.pages.forEach { page ->
                pages.put(JSONObject().put("kind", page.kind.name).put("caption", page.caption))
            }
            array.put(JSONObject()
                .put("id", entry.id).put("title", entry.title)
                .put("themeKey", entry.themeKey).put("bgName", entry.bgName)
                .put("pages", pages).put("visuals", entry.visuals?.toJson() ?: JSONObject.NULL))
        }
        check(prefs.edit().putString("books", array.toString()).commit()) { "동화책을 저장하지 못했습니다" }
    }
}
