package com.example.finalproject_demo.demo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.WeakHashMap

/*
 * 같이 만들기 책장 — **협업 책만의 저장소** (#83 · `guidelines/3` §3-5 「책장은 모드별로 따로, 한 책장에 12권 … 협업은 각자 저장소」).
 *
 * 전에는 동화만 저장해서(`Scenes.kt` sceneEnd) 같이 만들기 책은 책장에 표지만 올라가고, 다시 열 내용이 없었다.
 * 동화 보관함(`story_books`)에 섞지 않는다 — 섞으면 협업 책이 동화 책장의 12권 자리를 같이 차지한다.
 *
 * - 책 한 권의 모양은 동화와 같은 [SavedStoryBook](쪽마다 종류 · 문장)이라 책장의 읽기 화면(`SavedStoryView`)을 그대로 쓴다.
 *   그림 정보(`visuals`)는 담지 않는다 — 배경 그림 위에 쪽마다 문장을 보여 주는 읽기 전용 책이다.
 *   (주인공 · 그림까지 살려 다시 그리려면 동화의 다시 열기(`restoreStoryBook`)가 모드를 알아야 해서 공용 변경이 든다)
 * - 12권까지. 13권째는 **아무것도 지우지 않고** 꽂지 않는다 — 뺄 책을 고르는 화면은 책장 전체의 일이다(§9-0 조장)
 * - 폰 안에만 둔다(`shared_prefs/coop_books`). 서버에는 보내지 않는다
 */

const val COOP_SHELF_CAPACITY = 12

/** 책장 「다시 읽기」 표시 — 동화 · 그림일기 책 id 와 섞이지 않게 앞에 붙인다(`ShelfBook.savedStoryId`) */
const val COOP_SHELF_ID = "coop:"

interface CoopBookStore {
    fun load(): List<SavedStoryBook>
    /** 새 책을 맨 앞에. 12권이 차 있으면 실패한다 — 몰래 지우지 않는다 */
    fun save(book: SavedStoryBook)
}

/** 지금 같이 만든 이야기를 책 한 권으로 — 빈 쪽이 있으면 null(책이 덜 됐다) */
fun DemoState.completedCoopBook(): SavedStoryBook? {
    if (!isCoop || template == null || pageCount == 0) return null
    val pages = (1..pageCount).map { SavedStoryPage(pageKind(it), bookCaption(it)) }
    if (pages.any { it.caption.isBlank() }) return null
    return SavedStoryBook(UUID.randomUUID().toString(), title ?: autoTitleFor(), themeKey, bgName, pages)
}

/** 앱 내부 저장소에만 보관한다. 저장할 때 전체 배열을 한 번에 바꿔 중간 상태를 남기지 않는다 */
class LocalCoopBookStore(context: Context) : CoopBookStore {
    private val prefs = context.applicationContext.getSharedPreferences("coop_books", Context.MODE_PRIVATE)

    override fun load(): List<SavedStoryBook> {
        val raw = prefs.getString("books", null) ?: return emptyList()
        return coopBooksFromJson(raw)
    }

    override fun save(book: SavedStoryBook) {
        val raw = prefs.getString("books", null)
        val current = raw?.let(::coopBooksFromJson).orEmpty()
        // 읽지 못한 책이 있으면 덮어쓰지 않는다 — 다시 쓰면 그 책이 지워진다
        if (raw != null) check(current.size == JSONArray(raw).length()) { "읽지 못한 같이 만들기 책이 있어 저장하지 않는다" }
        val all = listOf(book) + current.filterNot { it.id == book.id }
        require(all.size <= COOP_SHELF_CAPACITY) { "같이 만들기 책장이 꽉 찼다 — 뺄 책을 고른 뒤에 꽂는다" }
        check(prefs.edit().putString("books", coopBooksToJson(all)).commit()) { "같이 만들기 책을 저장하지 못했습니다" }
    }
}

internal fun coopBooksToJson(books: List<SavedStoryBook>): String {
    val array = JSONArray()
    books.forEach { b ->
        val pages = JSONArray()
        b.pages.forEach { p -> pages.put(JSONObject().put("kind", p.kind.name).put("caption", p.caption)) }
        array.put(JSONObject().put("id", b.id).put("title", b.title).put("themeKey", b.themeKey)
            .put("bgName", b.bgName).put("pages", pages))
    }
    return array.toString()
}

/** 망가진 책은 건너뛴다(읽기). 쓰기는 [LocalCoopBookStore.save] 가 수를 맞춰 보고 막는다 */
internal fun coopBooksFromJson(raw: String): List<SavedStoryBook> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { i ->
        runCatching {
            val o = array.getJSONObject(i)
            val pa = o.getJSONArray("pages")
            val pages = (0 until pa.length()).map { j ->
                val p = pa.getJSONObject(j)
                SavedStoryPage(PageKind.valueOf(p.getString("kind")), p.getString("caption"))
            }
            if (pages.isEmpty() || pages.any { it.caption.isBlank() }) null
            else SavedStoryBook(o.getString("id"), o.getString("title"), o.getString("themeKey"), o.getString("bgName"), pages)
        }.getOrNull()
    }
}.getOrDefault(emptyList())

/** 같이 만들기 책을 꽂은 결과 */
enum class CoopShelved { SAVED, FULL, FAILED, NO_STORE }

/**
 * 저장된 같이 만들기 책을 책장에 올리고 「다시 읽기」로 꺼낸다. 앱을 켤 때 한 번 [attach] — 그림일기 [DiaryShelf] 와 같은 길이다.
 * 저장소가 없으면(단위 테스트 · 붙이기 전) 같이 만들기 책은 전처럼 앱을 켜 둔 동안만 표지가 남는다
 */
object CoopShelf {
    private val stores = WeakHashMap<DemoState, CoopBookStore>()
    private val books = WeakHashMap<DemoState, MutableList<SavedStoryBook>>()

    fun attach(s: DemoState, store: CoopBookStore) {
        stores[s] = store
        val saved = runCatching { store.load() }.getOrDefault(emptyList())
        books[s] = saved.toMutableList()
        s.shelf.addAll(0, saved.map { it.onCoopShelf() })
    }

    fun attach(context: Context, s: DemoState) = attach(s, LocalCoopBookStore(context))

    /** 이 책장에 꽂힌 같이 만들기 책 수 */
    fun count(s: DemoState): Int = books[s]?.size ?: 0

    /** 지금 이야기를 꽂는다 — 저장에 성공했을 때만 책장에 「저장된 책」으로 올린다 */
    fun shelve(s: DemoState): CoopShelved {
        val store = stores[s] ?: return CoopShelved.NO_STORE
        val book = s.completedCoopBook() ?: return CoopShelved.FAILED
        if (count(s) >= COOP_SHELF_CAPACITY) return CoopShelved.FULL
        return try {
            store.save(book)
            books.getOrPut(s) { mutableListOf() }.add(0, book)
            s.shelf.add(0, book.onCoopShelf(fresh = true))
            CoopShelved.SAVED
        } catch (_: Exception) { CoopShelved.FAILED }
    }

    /** 책장에서 누른 책이 같이 만들기 책이면 그 책 */
    fun book(s: DemoState, shelfId: String): SavedStoryBook? =
        if (!shelfId.startsWith(COOP_SHELF_ID)) null
        else books[s]?.firstOrNull { COOP_SHELF_ID + it.id == shelfId }
}

private fun SavedStoryBook.onCoopShelf(fresh: Boolean = false) =
    ShelfBook(title, themeKey, bgName, pages.size, fresh, COOP_SHELF_ID + id)

/**
 * 책장에 꽂기 (sceneEnd) — 같이 만들기 몫. 다 했으면 true, 저장에 실패해 다시 눌러야 하면 false.
 * - 저장소가 없으면 전처럼 표지만(앱을 켜 둔 동안)
 * - 12권이 차 있으면 아무것도 지우지 않고 꽂지 않는다 — 아이에게는 탓하지 않는 말로 알린다
 */
fun Director.shelveCoopBook(): Boolean = when (CoopShelf.shelve(s)) {
    CoopShelved.SAVED -> { log("책장에 꽂기 → 같이 만들기 책을 폰 안(coop_books)에 저장 · 서버에는 보내지 않음 (#83)"); true }
    CoopShelved.NO_STORE -> {
        s.shelf.add(0, ShelfBook(s.title ?: s.autoTitleFor(), s.themeKey, s.bgName, pages = s.pageCount, fresh = true))
        log("책장에 꽂기 → 저장소가 없다 · 같이 만들기 책은 앱을 켜 둔 동안만 남는다")
        true
    }
    CoopShelved.FULL -> {
        say("같이 만든 책이 책장에 가득 찼어! 부모님이랑 책을 정리하면 또 꽂을 수 있어.")
        log("같이 만들기 책장 ${COOP_SHELF_CAPACITY}권이 차 있다 → 아무것도 지우지 않고 이번 책은 꽂지 않는다 (guidelines/3 §3-5)")
        true
    }
    CoopShelved.FAILED -> { log("책장에 꽂기 → 같이 만들기 책 저장 실패"); false }
}
