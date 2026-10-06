package com.example.finalproject_demo.demo

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.WeakHashMap

/**
 * 책장에 꽂힌 그림일기 한 권 (#37 · `guidelines/3` §3-5).
 *
 * 겉(`id` · `title` · `madeAt` · `pages` · `mode`)은 세 모드가 같은 이름으로 갖는다 — 서버 책장 API 의
 * `{book_id, title, made_at, pages}` 와 같은 모양이라 나중에 책장 동기화를 한 번에 붙인다(치영 #62 와 맞춘 형식).
 * 속(쪽을 짜는 재료 · 조각)은 일기만의 것이다. **폰 안에만 둔다** — 아이 그림 · 아이 말은 서버로 보내지 않는다(차별점 1).
 */
data class SavedDiaryBook(
    val id: String,
    val title: String,
    /** 만든 날(ISO `2026-10-02`) — 그림일기 머리의 날짜 */
    val madeAt: String,
    val pages: Int,
    /** 쪽을 다시 짜는 재료 — 저장 당시 그대로(서버가 쓴 쪽 문장 · 아이 말 · 기분) */
    val input: DiaryBookInput,
    /** 조각 — 획 · 아이가 붙인 이름 · 고른 모습 · 오또 그림 */
    val pieces: List<DiaryPiece>,
    val weather: DiaryWeather? = null,
    val weatherBy: String? = null,
    val aspect: Float = 1f,
) {
    val mode: String get() = "diary"
}

/** 완성된 그림일기만 저장한다. 동화 [StoryBookStore] 와 같은 모양 — 모드마다 저장소는 따로다(§3-5) */
interface DiaryBookStore {
    fun load(): List<SavedDiaryBook>
    fun save(book: SavedDiaryBook)
    /** 한 권 빼기 — 부모 모드에서만(#80). 그런 책이 없으면 false */
    fun delete(id: String): Boolean { error("Diary deletion is not supported") }
    /** 그 책 쪽 문장의 오또 목소리(mp3) — 다시 읽을 때 `/tts` 를 다시 부르지 않게 (#179). 없으면 null */
    fun voice(bookId: String, line: String): ByteArray? = null
    fun keepVoice(bookId: String, line: String, mp3: ByteArray) {}
}

/** 책장 「다시 읽기」 표시 — 동화 책 id 와 섞이지 않게 앞에 붙인다(`ShelfBook.savedStoryId`) */
const val DIARY_SHELF_ID = "diary:"

/** 지금 상태의 그림일기를 책 한 권으로 — 그림도 말도 없으면 null(책이 없다) */
fun DemoState.completedDiaryBook(title: String, today: LocalDate = LocalDate.now()): SavedDiaryBook? {
    val input = diaryBookInput()
    val pages = buildDiaryBook(input).size
    if (pages == 0) return null
    val day = diaryDay
    val pieces = day.pieces.toList().ifEmpty { if (sceneDrawing.isEmpty()) emptyList() else listOf(DiaryPiece(0, sceneDrawing.toList())) }
    return SavedDiaryBook(
        UUID.randomUUID().toString(), title, today.toString(), pages, input,
        pieces.map { it.copy(strokes = it.strokes.map { s -> s.copy(pts = s.pts.toList()) }) },
        day.weather, day.weatherBy, drawingAspect.takeIf { it > 0f } ?: 1f,
    )
}

/**
 * 앱 내부 저장소에만 보관한다 — 백업 · 기기 이전은 매니페스트가 막는다(`allowBackup="false"` · 09-23).
 * 글은 `shared_prefs/diary_books` 에 전체 배열을 한 번에 교체하고, 오또 그림 PNG 는 `files/diary_images/` 에 둔다
 * (한 장 약 200KB — 글 저장소에 넣으면 무겁다). 동화의 `LocalStoryBookStore` 와 같은 길이다.
 * 쪽 문장의 오또 목소리는 `files/diary_voices/` 에 둔다(#179 · 한 권 약 100~150KB) — 오또 목소리라 아이 소리 규칙과 상관없다.
 */
class LocalDiaryBookStore(context: Context) : DiaryBookStore {
    private val prefs = context.applicationContext.getSharedPreferences("diary_books", Context.MODE_PRIVATE)
    private val images = File(context.applicationContext.filesDir, "diary_images")
    private val voices = File(context.applicationContext.filesDir, "diary_voices")

    override fun load(): List<SavedDiaryBook> {
        val raw = prefs.getString("books", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> runCatching { diaryBookFromJson(array.getJSONObject(i), ::readPng) }.getOrNull() }
        } catch (_: Exception) { emptyList() }
    }

    override fun save(book: SavedDiaryBook) {
        images.mkdirs()
        book.pieces.forEach { p -> p.ottoPng?.let { File(images, pngName(book.id, p.id)).writeBytes(it) } }
        val all = listOf(book) + load().filterNot { it.id == book.id }
        val array = JSONArray()
        all.forEach { array.put(it.toJson()) }
        check(prefs.edit().putString("books", array.toString()).commit()) { "그림일기를 저장하지 못했습니다" }
    }

    override fun delete(id: String): Boolean {
        val raw = prefs.getString("books", null) ?: return false
        val current = load()
        // 읽지 못한 책이 있으면 다시 쓰지 않는다 — 다시 쓰면 그 책까지 지워진다
        check(current.size == JSONArray(raw).length()) { "읽지 못한 그림일기가 있어 빼지 않는다" }
        if (current.none { it.id == id }) return false
        val array = JSONArray()
        current.filterNot { it.id == id }.forEach { array.put(it.toJson()) }
        check(prefs.edit().putString("books", array.toString()).commit()) { "그림일기를 빼지 못했습니다" }
        images.listFiles()?.filter { it.name.startsWith("${id}_") }?.forEach { it.delete() }
        voices.listFiles()?.filter { it.name.startsWith("${id}_") }?.forEach { it.delete() }
        return true
    }

    override fun voice(bookId: String, line: String): ByteArray? =
        File(voices, voiceName(bookId, line)).takeIf { it.exists() }?.readBytes()

    override fun keepVoice(bookId: String, line: String, mp3: ByteArray) {
        voices.mkdirs()
        File(voices, voiceName(bookId, line)).writeBytes(mp3)
    }

    /** 문장은 파일 이름이 될 수 없다(띄어쓰기 · 부호 · 길이) — 해시로 */
    private fun voiceName(bookId: String, line: String): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(line.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        return "${bookId}_$hash.mp3"
    }

    private fun readPng(bookId: String, pieceId: Int): ByteArray? =
        File(images, pngName(bookId, pieceId)).takeIf { it.exists() }?.readBytes()

    private fun pngName(bookId: String, pieceId: Int) = "${bookId}_$pieceId.png"
}

/**
 * 저장된 그림일기를 책장에 올리고 「다시 읽기」로 꺼낸다. 앱을 켤 때 한 번 [attach] — 저장소는 폰 안에만 있다.
 * 저장소가 없으면(단위 테스트 · 붙이기 전) 그림일기는 전처럼 앱을 켜 둔 동안만 책장에 남는다
 */
object DiaryShelf {
    private val stores = WeakHashMap<DemoState, DiaryBookStore>()
    private val books = WeakHashMap<DemoState, MutableList<SavedDiaryBook>>()

    /** 저장소를 붙이고 저장된 그림일기를 책장 · 표지에 올린다 */
    fun attach(s: DemoState, store: DiaryBookStore) {
        stores[s] = store
        val saved = runCatching { store.load() }.getOrDefault(emptyList())
        books[s] = saved.toMutableList()
        saved.forEach { b -> if (b.pieces.isNotEmpty()) s.diaryCovers[DIARY_SHELF_ID + b.id] = DiaryCover(b.pieces, b.aspect) }
        s.shelf.addAll(0, saved.map { it.onShelf() })
    }

    fun attach(context: Context, s: DemoState) = attach(s, LocalDiaryBookStore(context))

    /** 저장소가 붙어 있나 — 없으면 그림일기는 앱을 켜 둔 동안만 남는다 */
    fun attached(s: DemoState): Boolean = stores[s] != null

    /** 저장한다. 실패하면 false — 책장에 저장된 것처럼 꽂지 않는다(동화와 같은 원칙) */
    fun save(s: DemoState, book: SavedDiaryBook): Boolean {
        val store = stores[s] ?: return false
        return try {
            store.save(book)
            books.getOrPut(s) { mutableListOf() }.add(0, book)
            true
        } catch (_: Exception) { false }
    }

    /** 그 책 쪽 문장의 오또 목소리 — 저장소가 없거나 못 읽으면 null(그때는 `/tts` 로 간다) */
    fun voice(s: DemoState, bookId: String, line: String): ByteArray? =
        stores[s]?.let { runCatching { it.voice(bookId, line) }.getOrNull() }

    /** 남긴다 — 못 남겨도 책은 그대로다(다음 다시 읽기에 `/tts` 를 부를 뿐) */
    fun keepVoice(s: DemoState, bookId: String, line: String, mp3: ByteArray) {
        stores[s]?.let { runCatching { it.keepVoice(bookId, line, mp3) } }
    }

    fun book(s: DemoState, shelfId: String): SavedDiaryBook? =
        books[s]?.firstOrNull { DIARY_SHELF_ID + it.id == shelfId }

    /** 꽂힌 그림일기 — 새 책이 앞. 저장소가 없으면 빈 목록 */
    fun books(s: DemoState): List<SavedDiaryBook> = books[s]?.toList().orEmpty()

    /** 한 권 빼기 — 저장소에서 먼저 지우고, 성공했을 때만 책장 · 표지에서 내린다(#80) */
    fun delete(s: DemoState, id: String): Boolean {
        val store = stores[s] ?: return false
        if (books[s]?.none { it.id == id } != false) return false
        if (!runCatching { store.delete(id) }.getOrDefault(false)) return false
        books[s]?.removeAll { it.id == id }
        s.diaryCovers.remove(DIARY_SHELF_ID + id)
        s.shelf.removeAll { it.savedStoryId == DIARY_SHELF_ID + id }
        return true
    }
}

/** 표지 — 그림이 있으면 아이 그림(`diaryCovers`), 없으면 아이가 말한 곳의 펠트 그림. 전에는 빈 노란 표지였다 (#98) */
fun SavedDiaryBook.onShelf(fresh: Boolean = false) = ShelfBook(title, "diary", diaryPlaceBg(input.lines["place"]), pages, fresh, DIARY_SHELF_ID + id)

// ── JSON ──────────────────────────────────────────────────────────

internal fun SavedDiaryBook.toJson(): JSONObject {
    val i = input
    val diary = JSONObject().put("version", 1)
        .put("lines", JSONObject(i.lines)).put("by", JSONObject(i.by))
        .put("pieceNames", JSONArray(i.pieceNames)).put("hasDrawing", i.hasDrawing)
        .put("feel", i.feel?.name ?: JSONObject.NULL)
        .put("written", i.written?.let { JSONArray(it) } ?: JSONObject.NULL)
        .put("missions", i.missions).put("puzzle", i.puzzle)
        .put("weather", weather?.name ?: JSONObject.NULL).put("weatherBy", weatherBy ?: JSONObject.NULL)
        .put("aspect", aspect.toDouble())
        .put("pieces", JSONArray().apply {
            pieces.forEach { p ->
                put(JSONObject().put("id", p.id).put("name", p.name ?: JSONObject.NULL).put("look", p.look.name)
                    .put("otto", p.ottoPng != null).put("role", p.role.name).put("strokes", strokesToJson(p.strokes)))
            }
        })
    return JSONObject().put("id", id).put("title", title).put("madeAt", madeAt).put("pages", pages)
        .put("mode", mode).put("diary", diary)
}

internal fun diaryBookFromJson(obj: JSONObject, png: (String, Int) -> ByteArray?): SavedDiaryBook {
    check(obj.getString("mode") == "diary")
    val id = obj.getString("id")
    val d = obj.getJSONObject("diary")
    check(d.getInt("version") == 1)
    fun map(o: JSONObject) = o.keys().asSequence().associateWith { o.getString(it) }
    val input = DiaryBookInput(
        lines = map(d.getJSONObject("lines")),
        by = map(d.getJSONObject("by")),
        pieceNames = d.getJSONArray("pieceNames").let { a -> (0 until a.length()).map(a::getString) },
        hasDrawing = d.getBoolean("hasDrawing"),
        feel = d.optString("feel").takeIf { !d.isNull("feel") }?.let(DiaryFeel::valueOf),
        written = if (d.isNull("written")) null else d.getJSONArray("written").let { a -> (0 until a.length()).map(a::getString) },
        missions = d.getBoolean("missions"),
        puzzle = d.getBoolean("puzzle"),
    )
    val pieces = d.getJSONArray("pieces").let { a ->
        (0 until a.length()).map { k ->
            val p = a.getJSONObject(k)
            val pid = p.getInt("id")
            DiaryPiece(
                pid, strokesFromJson(p.getJSONArray("strokes")),
                if (p.isNull("name")) null else p.getString("name"),
                PieceLook.valueOf(p.getString("look")),
                if (p.getBoolean("otto")) png(id, pid) else null,
                PieceRole.valueOf(p.optString("role", PieceRole.OBJECT.name)),
            )
        }
    }
    return SavedDiaryBook(
        id, obj.getString("title"), obj.getString("madeAt"), obj.getInt("pages"), input, pieces,
        if (d.isNull("weather")) null else DiaryWeather.valueOf(d.getString("weather")),
        if (d.isNull("weatherBy")) null else d.getString("weatherBy"),
        d.getDouble("aspect").toFloat(),
    )
}

private fun strokesToJson(strokes: List<Stroke>) = JSONArray().apply {
    strokes.forEach { s ->
        val pts = JSONArray()
        s.pts.forEach { pts.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
        put(JSONObject().put("color", s.color.toArgb()).put("width", s.w.toDouble()).put("points", pts))
    }
}

private fun strokesFromJson(a: JSONArray): List<Stroke> = (0 until a.length()).map { i ->
    val s = a.getJSONObject(i)
    val pts = s.getJSONArray("points")
    Stroke(Color(s.getInt("color")), (0 until pts.length()).map { j ->
        val xy = pts.getJSONArray(j)
        Offset(xy.getDouble(0).toFloat(), xy.getDouble(1).toFloat())
    }, s.getDouble("width").toFloat())
}
