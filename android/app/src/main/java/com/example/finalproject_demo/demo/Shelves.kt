package com.example.finalproject_demo.demo

/*
 * 책장이 꽉 차면 — 세 모드 책장을 한 모양으로 본다 (10-02 조장 결정 #80 · `guidelines/3` §3-5).
 *
 * 1. 방에서 모드를 고르는 순간 그 모드 책장이 12권이면 들어가지 않고 알린다(`sceneAdult` → 방의 알림)
 * 2. [부모 모드로] → PIN → 부모 모드 「책장 정리」의 그 모드 · [닫기] → 방
 * 3. 책 빼기는 부모 모드 안에서만 — 아이 말과 그림이 같이 사라지는 일이다
 *
 * 저장소는 모드마다 따로다(동화 `StoryBookStore` · 그림일기 `DiaryShelf` · 같이 만들기 `CoopShelf`).
 * 여기는 그 셋을 「몇 권 · 목록 · 한 권 빼기」로 같은 모양으로 내준다 — 알림과 부모 화면은 이것만 본다.
 */

/** 한 책장에 꽂히는 책 수 — 세 모드 같다(#37) */
const val SHELF_CAPACITY = 12

/** 부모 책장 정리 목록의 한 줄 — `id · title · madeAt · pages · mode`(#70 겉 모양) */
data class ShelfEntry(val id: String, val title: String, val madeAt: String, val pages: Int, val mode: StoryMode)

/** 책장 정리에 보이는 모드 순서 · 이름 */
val SHELF_MODES = listOf(StoryMode.STORY to "동화", StoryMode.DIARY to "그림일기", StoryMode.COOP to "같이 만들기")

/** 그 모드 책장의 책 — 새 책이 앞 */
fun Director.shelfEntries(mode: StoryMode): List<ShelfEntry> = when (mode) {
    StoryMode.STORY -> storyBooks().map { ShelfEntry(it.id, it.title, it.madeAt, it.pages.size, mode) }
    StoryMode.DIARY -> DiaryShelf.books(s).map { ShelfEntry(it.id, it.title, it.madeAt, it.pages, mode) }
    StoryMode.COOP -> CoopShelf.books(s).map { ShelfEntry(it.id, it.title, it.madeAt, it.pages.size, mode) }
}

/** 그 모드 책장의 권수. 동화 저장 정보를 읽지 못하면 null — 0권으로 보지 않는다 */
fun Director.shelfCount(mode: StoryMode): Int? = when (mode) {
    StoryMode.STORY -> storyBookCount()
    StoryMode.DIARY -> DiaryShelf.books(s).size
    StoryMode.COOP -> CoopShelf.count(s)
}

/**
 * 그 모드 책장이 꽉 찼나 — 새로 시작하기 전에 본다.
 * 권수를 모르면(null) 막지 않는다: 아이를 세워 두지 않고, 꽂을 때 저장소가 몰래 지우지 않고 막는다
 */
fun Director.shelfIsFull(mode: StoryMode): Boolean = (shelfCount(mode) ?: 0) >= SHELF_CAPACITY

/** 한 권 빼기 — 부모 모드에서만. 저장소에서 지워졌을 때만 참 */
fun Director.removeShelfBook(mode: StoryMode, id: String): Boolean {
    if (s.scene != Scene.PARENT) return false
    return when (mode) {
        StoryMode.STORY -> deleteStoryBook(id)
        StoryMode.DIARY -> DiaryShelf.delete(s, id)
        StoryMode.COOP -> CoopShelf.delete(s, id).also { if (it) recoverStoryImages() }
    }
}

/** 부모 화면이 보내는 빼기 신호 — `shelf:del:<mode>:<id>` */
fun shelfDeleteSignal(mode: StoryMode, id: String) = "shelf:del:${mode.name}:$id"

internal fun parseShelfDelete(value: String): Pair<StoryMode, String>? {
    if (!value.startsWith("shelf:del:")) return null
    val rest = value.removePrefix("shelf:del:")
    val mode = StoryMode.entries.firstOrNull { rest.startsWith(it.name + ":") } ?: return null
    val id = rest.removePrefix(mode.name + ":").takeIf(String::isNotEmpty) ?: return null
    return mode to id
}
