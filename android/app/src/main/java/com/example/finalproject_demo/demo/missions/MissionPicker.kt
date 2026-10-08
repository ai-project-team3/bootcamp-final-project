package com.example.finalproject_demo.demo.missions

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.bookPick
import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.reasonOrNull

/**
 * What the picker may look at — only the facts that choose a mission, so the picker stays a pure
 * function (`docs/맞춤미션_설계.md` §5-1). [realDay] is a diary or a co-op 「다녀왔어요 · 곧 해요」 book:
 * a real day must not get a prop the child never said (§3-8).
 */
data class StoryFacts(
    val mode: StoryMode,
    val templateKey: String?,
    val problem: String?,
    val cause: String?,
    val solution: String?,
    val realDay: Boolean,
    /** 꼬리질문 「더 자세히(detail)」 — 자리 1 이 보는 칸 (design §6-1) */
    val detail: String? = null,
    /** 꼬리질문 「제일 먼저 한 일(try)」 — 자리 2 가 보는 칸 (design §6-1) */
    val tried: String? = null,
) {
    /** 자리 2 (해결) 가 보는 아이 말 — solution · try */
    val slot2Words: String get() = listOfNotNull(solution, tried).joinToString(" ")

    /** 자리 1 (사건 직후) 이 보는 아이 말 — problem · cause · detail */
    val slot1Words: String get() = listOfNotNull(problem, cause, detail).joinToString(" ")
}

fun DemoState.storyFacts() = StoryFacts(
    mode = mode,
    templateKey = templateKey,
    problem = problem,
    cause = cause,
    solution = solution,
    // A real day (§3-8): a diary, or a co-op 「다녀왔어요 · 곧 해요」 or questions-only story. A co-op
    // 「좋아해요」(or a pick with no reason, asked as imagination) is not — it may borrow a prop (1-b · §7-2)
    realDay = isDiary && !(isCoop && coopImagined()),
    detail = slots["detail"],
    tried = slots["try"],
)

/** 협업에서 상상으로 물은 이야기인가 — 좋아해요, 또는 이야기를 골랐는데 이유가 없을 때 */
private fun DemoState.coopImagined(): Boolean =
    bookPick?.let { (it.reasonOrNull() ?: CoopReason.DREAM) == CoopReason.DREAM } ?: false

/**
 * A book's two missions (#259 · docs/실기기수정_설계_1007.md §4-3 · lead 10-07: app only).
 *
 * 1. **The child's words first** — slot 2 the solution verb (껐어 · 잠갔어 · 쌓았어 …) before the things in the trouble,
 *    slot 1 something to blow (C1) or a sound to make (C3). Words match at the start of an eojeol (MissionWords.kt).
 *    A slot the child's words chose is never rotated away.
 * 2. **Otherwise rotate** — closest to 「do what the hero did」 first:
 *    slot 2: the frame's default (E1 handing over · A3 puzzle for frames A · G) → A5 stacking → E2 fixing → D4 rolling;
 *    slot 1: A6 rubbing → C1 blowing a borrowed leaf (an imagined story only — a real day gets no prop the child never said).
 * 3. **Not the combination of the last two books** of this mode ([recent], newest first). When every candidate is
 *    one of them, the one used longest ago.
 *
 * Pure on its inputs: the same facts and the same history give the same missions.
 */
fun pickMissions(facts: StoryFacts, recent: List<String> = emptyList()): BookMissions {
    // Story and co-op share the word signals (10-06 lead) — the co-op missions were not reachable from a story.
    // The picture diary has no mission pages (PictureDiary.kt); its old step flow still reads no words
    val words = facts.mode == StoryMode.STORY || facts.mode == StoryMode.COOP
    val fix = if (words) fixPropIn(facts.slot2Words, facts.slot1Words) else null
    val frame2 = if (facts.templateKey in PUZZLE_FRAMES) MissionId.A3 else MissionId.E1
    val slot2s = fix?.mission?.let(::listOf) ?: (listOf(frame2) + ROTATE_SLOT2.filter { it != frame2 })
    // design §7-1 · §7-3: slot 1 blows (C1) when the child's own words have something to blow, or makes the sound (C3)
    val said1 = when {
        words && blowPropIn(facts.slot1Words, facts.realDay) != null -> MissionId.C1
        words && SERVER_KNOWS_C3 && soundPropIn(facts.slot1Words) != null -> MissionId.C3
        else -> null
    }
    val slot1s = said1?.let(::listOf) ?: (listOf(MissionId.A6) + if (words && !facts.realDay) listOf(MissionId.C1) else emptyList())
    val combos = slot2s.flatMap { b -> slot1s.map { a -> BookMissions(a, b, said1 != null, fix != null) } }
    val avoid = recent.take(2).toSet()
    val pick = combos.firstOrNull { it.combo !in avoid }
        // every candidate is one of the last two — the one used longest ago (never used counts as longest)
        ?: combos.maxBy { c -> recent.indexOf(c.combo).let { if (it < 0) Int.MAX_VALUE else it } }
    check(pick.slot1.built && pick.slot2.built) { "picked a mission without a screen: ${pick.combo}" }
    return pick
}

private val PUZZLE_FRAMES = setOf("A", "G")

/** Slot 2 when the child's words chose nothing — after the frame's default, in this order (design §4-3 3) */
private val ROTATE_SLOT2 = listOf(MissionId.A5, MissionId.E2, MissionId.D4)

private val picked = java.util.WeakHashMap<DemoState, Pair<Pair<StoryFacts, List<String>>, BookMissions>>()
private val pinned = java.util.WeakHashMap<DemoState, PinnedMissions>()

/**
 * What a saved book was made with — the missions and the props the screens drew (#321 review). A re-read book
 * does not have all its facts (a co-op book loses its pick, so a borrowed leaf turned into rubbing), so the props
 * are kept too. [fixInBook] whether the book's text named slot 2's prop ([slot2Prop] was not null)
 */
data class PinnedMissions(
    val missions: BookMissions,
    val blow: BlowProp? = null,
    val sound: SoundProp? = null,
    val fix: FixProp? = null,
    val fixInBook: Boolean = false,
) {
    /** 「A6-:E1-|blow=LEAF|sound=|fix=BLOCKS|book=0」 — the first part is the pre-review format, still read alone */
    fun encode(): String = listOf(missions.encode(), "blow=${blow?.name.orEmpty()}", "sound=${sound?.name.orEmpty()}",
        "fix=${fix?.name.orEmpty()}", "book=${if (fixInBook) 1 else 0}").joinToString("|")

    companion object {
        fun decode(s: String?): PinnedMissions? {
            val parts = s?.split("|") ?: return null
            val m = BookMissions.decode(parts.first()) ?: return null
            val kv = parts.drop(1).mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return PinnedMissions(
                m,
                kv["blow"]?.takeIf(String::isNotEmpty)?.let { n -> BlowProp.entries.firstOrNull { it.name == n } },
                kv["sound"]?.takeIf(String::isNotEmpty)?.let { n -> SoundProp.entries.firstOrNull { it.name == n } },
                kv["fix"]?.takeIf(String::isNotEmpty)?.let { n -> FixProp.entries.firstOrNull { it.name == n } },
                kv["book"] == "1",
            )
        }
    }
}

/** The pinned record of a re-read book, or null while a book is being made */
internal fun DemoState.pinnedMissions(): PinnedMissions? = synchronized(picked) { pinned[this] }

/** This book's missions and props as they are now — saved with the book */
fun DemoState.missionRecord(): String =
    PinnedMissions(missions(), blowProp(), soundProp(), slot2PlayProp(), slot2Prop() != null).encode()

/**
 * This book's two missions. While a book is being made they follow its facts and this mode's history; a saved
 * book being re-read keeps the ones it was made with ([pinMissions] · `SavedStoryVisuals.missions`) — its
 * facts are not all stored, and the history has moved on since
 */
fun DemoState.missions(): BookMissions {
    synchronized(picked) {
        pinned[this]?.let { return it.missions }
        val key = storyFacts() to MissionHistory.recent(mode)
        picked[this]?.takeIf { it.first == key }?.let { return it.second }
        return pickMissions(key.first, key.second).also { picked[this] = key to it }
    }
}

/** A re-read book: use the missions and props it was made with. null = pick again (a book saved before #259) */
fun DemoState.pinMissions(m: PinnedMissions?) {
    synchronized(picked) { if (m == null) pinned.remove(this) else pinned[this] = m }
}

/**
 * The last books' mission combinations, per mode, on the phone only (`mission_history` · newest first · 4 kept).
 * A book is recorded when it goes on the shelf — an unfinished story does not count. Without [attach] nothing is
 * recorded — the flow tests shelve books, and one test's history must not change another's missions.
 * Cleared with the other child data (`LocalWipe`)
 */
object MissionHistory {
    const val PREFS = "mission_history"
    private const val KEEP = 4
    private var prefs: android.content.SharedPreferences? = null
    /** mode → (book id, combo), newest first */
    private val books = mutableMapOf<StoryMode, MutableList<Pair<String, String>>>()

    @Synchronized fun attach(context: android.content.Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        reload()
    }

    @Synchronized fun reload() {
        books.clear()
        val p = prefs ?: return
        StoryMode.entries.forEach { mode ->
            val list = p.getString(mode.name, null)?.split(";")?.mapNotNull { e ->
                e.split("|").takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.orEmpty()
            if (list.isNotEmpty()) books[mode] = list.toMutableList()
        }
    }

    @Synchronized fun recent(mode: StoryMode): List<String> = books[mode]?.map { it.second }.orEmpty()

    /** The book [bookId] went on the shelf with [m]. Saving the same book again replaces its line */
    @Synchronized fun record(mode: StoryMode, bookId: String, m: BookMissions) {
        val p = prefs ?: return
        val list = books.getOrPut(mode) { mutableListOf() }
        list.removeAll { it.first == bookId }
        list.add(0, bookId to m.combo)
        while (list.size > KEEP) list.removeAt(list.size - 1)
        p.edit().putString(mode.name, list.joinToString(";") { "${it.first}|${it.second}" }).apply()
    }

    /** tests — forget everything and stop recording */
    @Synchronized fun detach() { books.clear(); prefs?.edit()?.clear()?.commit(); prefs = null }
}
