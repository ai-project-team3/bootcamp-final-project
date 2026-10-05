package com.example.finalproject_demo.demo

import android.content.Context
import androidx.compose.ui.graphics.toArgb
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
 * - 책 한 권의 겉은 동화와 같은 [SavedStoryBook](쪽마다 종류 · 문장 · 주인공 그림 `visuals`)이라 책장의 읽기 화면
 *   (`SavedStoryView`)이 **만들 때와 같은 책 화면**(그림 · 읽어 주기 · 미션 쪽)으로 연다.
 * - 같이 만들기 책의 쪽 구성 · 그림은 이야기 칸 · 만난 사람 · 화이트보드 그림이 정한다(`diaryTemplate`) —
 *   그래서 그 재료를 [CoopBookSnapshot] 으로 같이 저장하고, 다시 열 때 [restoreCoopBook] 이 그대로 되살린다
 * - 12권까지. 13권째는 **아무것도 지우지 않고** 꽂지 않는다 — 뺄 책을 고르는 화면은 책장 전체의 일이다(§9-0 조장)
 * - 폰 안에만 둔다(`shared_prefs/coop_books`). 서버에는 보내지 않는다
 */

const val COOP_SHELF_CAPACITY = 12

/** 책장 「다시 읽기」 표시 — 동화 · 그림일기 책 id 와 섞이지 않게 앞에 붙인다(`ShelfBook.savedStoryId`) */
const val COOP_SHELF_ID = "coop:"

/**
 * 같이 만들기 책을 다시 그리는 재료 — 쪽 구성(`diaryTemplate`)과 책 그림(`Book.kt`)이 읽는 것.
 * 이야기 칸 · 장소 · 같이 있던 사람 · 친구 이름 · 화이트보드 그림 · 함께한 사람 · 책 끝 말 · 배경
 */
data class CoopBookSnapshot(
    val childName: String,
    val slots: Map<String, String>,
    val placeLabel: String?,
    val companionKind: String,
    val friendName: String,
    val sceneDrawing: List<Stroke>,
    val sceneDrawingAspect: Float,
    val partnerKey: String,
    val partnerCall: String?,
    val partnerHelpLine: String?,
    val bookNote: String,
    val bgName: String,
    /** 뼈대 칸 값 — 쪽 구성이 읽는다(예: 기분 [reaction] 이 있으면 「그때 마음」 쪽이 선다) */
    val fields: Map<String, String> = emptyMap(),
    val slotBy: Map<String, String> = emptyMap(),
    val feelings: List<String> = emptyList(),
)

/** 쪽 구성 · 그림이 읽는 뼈대 칸 — 이름 → (읽기, 쓰기) */
private val COOP_BOOK_FIELDS: Map<String, Pair<(DemoState) -> String?, (DemoState, String?) -> Unit>> = mapOf(
    "place" to Pair({ s -> s.place }, { s, v -> s.place = v }),
    "problem" to Pair({ s -> s.problem }, { s, v -> s.problem = v }),
    "cause" to Pair({ s -> s.cause }, { s, v -> s.cause = v }),
    "solution" to Pair({ s -> s.solution }, { s, v -> s.solution = v }),
    "reaction" to Pair({ s -> s.reaction }, { s, v -> s.reaction = v }),
    "newcomer" to Pair({ s -> s.newcomer }, { s, v -> s.newcomer = v }),
    "friend" to Pair({ s -> s.friend }, { s, v -> s.friend = v }),
)

/** 저장된 같이 만들기 책 한 권 — 겉(책장 · 읽기 화면) + 다시 그리는 재료 */
data class SavedCoopBook(val book: SavedStoryBook, val snapshot: CoopBookSnapshot?)

interface CoopBookStore {
    fun load(): List<SavedCoopBook>
    /** 새 책을 맨 앞에. 12권이 차 있으면 실패한다 — 몰래 지우지 않는다 */
    fun save(book: SavedCoopBook)
}

/** 지금 같이 만든 이야기를 책 한 권으로 — 빈 쪽이 있으면 null(책이 덜 됐다) */
fun DemoState.completedCoopBook(): SavedCoopBook? {
    if (!isCoop || template == null || pageCount == 0) return null
    val pages = (1..pageCount).map { SavedStoryPage(pageKind(it), bookCaption(it)) }
    if (pages.any { it.caption.isBlank() }) return null
    // 주인공 · 아이 그림은 동화와 같은 그릇(SavedStoryVisuals)에 — 같이 만들기는 동화 틀이 없어 일기 틀 "N"
    val visuals = SavedStoryVisuals(
        template?.key ?: "N", persona, Hero(childName, heroAttr ?: com.example.finalproject_demo.ui.HeroAttr(), storyHeroImage, storyHeroRig),
        drawing.map { it.copy(pts = it.pts.toList()) }, drawnPreset, drawingAspect,
        dinoKey, dinoColor, solutionKey, solutionItem, friendName, solutionLine, placeLabel,
        newcomerKind, soundLine, causeLine,
    )
    val book = SavedStoryBook(UUID.randomUUID().toString(), title ?: autoTitleFor(), themeKey, bgName, pages, visuals)
    val snapshot = CoopBookSnapshot(
        childName, slots.toMap(), placeLabel, companionKind, friendName,
        sceneDrawing.map { it.copy(pts = it.pts.toList()) }, sceneDrawingAspect,
        partnerKey, partnerCall, partnerHelpLine, bookNote, bgName,
        COOP_BOOK_FIELDS.mapNotNull { (k, f) -> f.first(this)?.let { k to it } }.toMap(),
        slotBy.toMap(), feelings.toList(),
    )
    return SavedCoopBook(book, snapshot)
}

/** 읽기 화면용 상태에서 같이 만들기 배경 — 그 책을 만들 때의 배경 그대로(`coopBackdrop` 이 먼저 본다) */
private val readingBackdrop = WeakHashMap<DemoState, String>()
internal val DemoState.coopReadingBackdrop: String? get() = readingBackdrop[this]

/**
 * 같이 만들기 책을 **만들 때와 같은 책**으로 되살린다 — 읽기 화면이 따로 만든 상태에서만 부른다(지금 이야기를 덮지 않는다).
 * 같이 만들기 책이 아니면 false (동화 책은 [restoreStoryBook])
 */
fun DemoState.restoreCoopBook(book: SavedStoryBook): Boolean {
    val snap = CoopShelf.snapshotOf(book.id) ?: return false
    if (!restoreStoryBook(book)) return false              // 주인공 · 그림 · 쪽 문장
    mode = StoryMode.COOP
    templateKey = null                                     // 쪽 구성은 같이 만들기 틀(diaryTemplate)이 정한다
    slots.clear(); slots.putAll(snap.slots)
    placeLabel = snap.placeLabel
    companionKind = snap.companionKind
    friendName = snap.friendName
    sceneDrawing.clear(); sceneDrawing.addAll(snap.sceneDrawing.map { it.copy(pts = it.pts.toList()) })
    sceneDrawingAspect = snap.sceneDrawingAspect
    partnerKey = snap.partnerKey
    partnerCall = snap.partnerCall
    partnerHelpLine = snap.partnerHelpLine
    // 꽂을 때의 안내(마지막 쪽 「오른쪽 책 버튼을 눌러 봐!」)는 되살리지 않는다 — 읽기 화면은 쪽마다 같은 안내를 띄워
    // 다시 연 책 2쪽에 마지막 쪽 안내가 떴다 (#98)
    bookNote = ""
    COOP_BOOK_FIELDS.forEach { (k, f) -> f.second(this, snap.fields[k]) }
    slotBy.clear(); slotBy.putAll(snap.slotBy)
    feelings.clear(); feelings.addAll(snap.feelings)
    readingBackdrop[this] = snap.bgName
    return true
}

/** 앱 내부 저장소에만 보관한다. 저장할 때 전체 배열을 한 번에 바꿔 중간 상태를 남기지 않는다 */
class LocalCoopBookStore(context: Context) : CoopBookStore {
    private val prefs = context.applicationContext.getSharedPreferences("coop_books", Context.MODE_PRIVATE)

    override fun load(): List<SavedCoopBook> {
        val raw = prefs.getString("books", null) ?: return emptyList()
        return coopBooksFromJson(raw)
    }

    override fun save(book: SavedCoopBook) {
        val raw = prefs.getString("books", null)
        val current = raw?.let(::coopBooksFromJson).orEmpty()
        // 읽지 못한 책이 있으면 덮어쓰지 않는다 — 다시 쓰면 그 책이 지워진다
        if (raw != null) check(current.size == JSONArray(raw).length()) { "읽지 못한 같이 만들기 책이 있어 저장하지 않는다" }
        val all = listOf(book) + current.filterNot { it.book.id == book.book.id }
        require(all.size <= COOP_SHELF_CAPACITY) { "같이 만들기 책장이 꽉 찼다 — 뺄 책을 고른 뒤에 꽂는다" }
        check(prefs.edit().putString("books", coopBooksToJson(all)).commit()) { "같이 만들기 책을 저장하지 못했습니다" }
    }
}

private fun JSONObject?.toStringMap(): Map<String, String> =
    this?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()

private fun strokesToJson(strokes: List<Stroke>): JSONArray = JSONArray().also { arr ->
    strokes.forEach { s ->
        val pts = JSONArray()
        s.pts.forEach { pts.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
        arr.put(JSONObject().put("color", s.color.toArgb()).put("width", s.w.toDouble()).put("points", pts))
    }
}

private fun strokesFromJson(arr: JSONArray): List<Stroke> = (0 until arr.length()).map { i ->
    val o = arr.getJSONObject(i)
    val pa = o.getJSONArray("points")
    Stroke(
        androidx.compose.ui.graphics.Color(o.getInt("color")),
        (0 until pa.length()).map { j -> pa.getJSONArray(j).let { androidx.compose.ui.geometry.Offset(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } },
        o.getDouble("width").toFloat(),
    )
}

internal fun coopBooksToJson(books: List<SavedCoopBook>): String {
    val array = JSONArray()
    books.forEach { (b, s) ->
        val pages = JSONArray()
        b.pages.forEach { p -> pages.put(JSONObject().put("kind", p.kind.name).put("caption", p.caption)) }
        val snap = s?.let { snapshotToJson(it) } ?: JSONObject.NULL
        array.put(JSONObject().put("id", b.id).put("title", b.title).put("themeKey", b.themeKey)
            .put("bgName", b.bgName).put("pages", pages)
            .put("visuals", b.visuals?.toJson() ?: JSONObject.NULL).put("coop", snap))
    }
    return array.toString()
}

private fun snapshotToJson(s: CoopBookSnapshot): JSONObject {
        val slots = JSONObject().also { o -> s.slots.forEach { (k, v) -> o.put(k, v) } }
        return JSONObject().put("childName", s.childName).put("slots", slots)
            .put("placeLabel", s.placeLabel ?: JSONObject.NULL).put("companionKind", s.companionKind)
            .put("friendName", s.friendName).put("sceneDrawing", strokesToJson(s.sceneDrawing))
            .put("sceneDrawingAspect", s.sceneDrawingAspect.toDouble()).put("partnerKey", s.partnerKey)
            .put("partnerCall", s.partnerCall ?: JSONObject.NULL).put("partnerHelpLine", s.partnerHelpLine ?: JSONObject.NULL)
            .put("bookNote", s.bookNote).put("bgName", s.bgName)
            .put("fields", JSONObject().also { o -> s.fields.forEach { (k, v) -> o.put(k, v) } })
            .put("slotBy", JSONObject().also { o -> s.slotBy.forEach { (k, v) -> o.put(k, v) } })
            .put("feelings", JSONArray().also { a -> s.feelings.forEach { a.put(it) } })
}

/** 망가진 책은 건너뛴다(읽기). 쓰기는 [LocalCoopBookStore.save] 가 수를 맞춰 보고 막는다 */
internal fun coopBooksFromJson(raw: String): List<SavedCoopBook> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { i ->
        runCatching {
            val o = array.getJSONObject(i)
            val pa = o.getJSONArray("pages")
            val pages = (0 until pa.length()).map { j ->
                val p = pa.getJSONObject(j)
                SavedStoryPage(PageKind.valueOf(p.getString("kind")), p.getString("caption"))
            }
            if (pages.isEmpty() || pages.any { it.caption.isBlank() }) return@runCatching null
            val visuals = o.optJSONObject("visuals")?.let(::storyVisualsFromJson)
            val book = SavedStoryBook(o.getString("id"), o.getString("title"), o.getString("themeKey"), o.getString("bgName"), pages, visuals)
            // 앞 빌드(10-02 c68b857 전)가 남긴 책은 다시 그리는 재료가 없다 — 글자 책으로 남긴다(지우지 않는다)
            val c = o.optJSONObject("coop") ?: return@runCatching SavedCoopBook(book, null)
            val sl = c.getJSONObject("slots")
            val snap = CoopBookSnapshot(
                c.getString("childName"), sl.keys().asSequence().associateWith { sl.getString(it) },
                if (c.isNull("placeLabel")) null else c.getString("placeLabel"), c.getString("companionKind"),
                c.getString("friendName"), strokesFromJson(c.getJSONArray("sceneDrawing")),
                c.getDouble("sceneDrawingAspect").toFloat(), c.getString("partnerKey"),
                if (c.isNull("partnerCall")) null else c.getString("partnerCall"),
                if (c.isNull("partnerHelpLine")) null else c.getString("partnerHelpLine"),
                c.getString("bookNote"), c.getString("bgName"),
                c.optJSONObject("fields").toStringMap(), c.optJSONObject("slotBy").toStringMap(),
                c.optJSONArray("feelings")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            )
            SavedCoopBook(book, snap)
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
    private val books = WeakHashMap<DemoState, MutableList<SavedCoopBook>>()
    /** 책 id → 다시 그리는 재료. 읽기 화면은 상태를 따로 만들어 열기 때문에 상태가 아니라 책 id 로 찾는다 */
    private val snapshots = java.util.concurrent.ConcurrentHashMap<String, CoopBookSnapshot>()

    fun attach(s: DemoState, store: CoopBookStore) {
        stores[s] = store
        val saved = runCatching { store.load() }.getOrDefault(emptyList())
        books[s] = saved.toMutableList()
        saved.forEach { b -> b.snapshot?.let { snapshots[b.book.id] = it } }
        s.shelf.addAll(0, saved.map { it.book.onCoopShelf() })
    }

    /** 같이 만들기 책이면 다시 그리는 재료, 아니면 null */
    fun snapshotOf(bookId: String): CoopBookSnapshot? = snapshots[bookId]

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
            book.snapshot?.let { snapshots[book.book.id] = it }
            s.shelf.add(0, book.book.onCoopShelf(fresh = true))
            CoopShelved.SAVED
        } catch (_: Exception) { CoopShelved.FAILED }
    }

    /** 책장에서 누른 책이 같이 만들기 책이면 그 책 */
    fun book(s: DemoState, shelfId: String): SavedStoryBook? =
        if (!shelfId.startsWith(COOP_SHELF_ID)) null
        else books[s]?.firstOrNull { COOP_SHELF_ID + it.book.id == shelfId }?.book
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
