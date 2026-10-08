package com.example.finalproject_demo.demo

import android.content.Context
import android.util.AtomicFile
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.finalproject_demo.ui.HeroAttr
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * ── Unfinished-book checkpoint across a killed process (#336 · lead decision 10-08) ──────────────────
 *
 * In-app 🏠 → 「이어서 할까?」 keeps the story in memory only (`DemoState.paused` · `Director.pauseStory`).
 * When Android kills the process (swipe away, low memory, crash) that memory is gone and the child starts over.
 *
 * This file writes the same thing to the phone: the scene the story stopped in plus the book's state, at every
 * scene boundary and after each answer. On the next launch the draft is put back into `DemoState` and
 * `paused` is set, so the room offers the existing 「만들던 이야기 이어서 할까?」 and the existing `resume` path
 * (`Scenes.sceneAdult`) runs it — no new start, so no star, no `CallLimits.bookStarted`, no new book count.
 *
 * Contract:
 *  - **Local only.** App-private `files/session_draft/draft.json`, never sent to the server. `LocalWipe` deletes it.
 *  - **Atomic.** `AtomicFile` (write to a new file, then rename) — a kill mid-write leaves the previous draft.
 *  - **Provenance exact.** `slotBy` (child · card · mascot) and every `talk` line's `who` are copied as they are (rule 5).
 *  - **Scene boundary.** Resume reruns the stopped scene from its start with the saved state, as in-app continue does.
 *    State a scene keeps in its own locals (a live question in progress) is not saved.
 *  - **Cleared** when the book is finished (`Scene.BOOK`, the picture diary shelved), a new story starts,
 *    the 시연 「처음부터」 resets, or the parent wipes the phone.
 *  - Unknown [VERSION] or an unreadable file is discarded — never half-restored.
 */

/** One unfinished book: the scene to resume in and the explicit state below. */
data class SessionDraft(
    val scene: Scene,
    val mode: StoryMode,
    val savedAtMs: Long,
    /** The book's state as written by [captureDraftState] — the explicit field list is the contract */
    val state: JSONObject,
) {
    /** Server pictures (`local:` paths) this draft still needs — image cleanup must keep them */
    fun imageReferences(): Set<String> = buildSet {
        listOf("storyBackground", "storyHeroImage").forEach { k -> state.optStr(k)?.let(::add) }
        state.optJSONObject("generatedFriend")?.optStr("image")?.let(::add)
        state.optJSONArray("heroes")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).optStr("image")?.let(::add) }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("version", VERSION).put("scene", scene.name).put("mode", mode.name)
        .put("savedAtMs", savedAtMs).put("state", state)

    companion object {
        /** Bump when a field changes meaning; an older draft is then discarded, not misread */
        const val VERSION = 1

        fun fromJson(o: JSONObject): SessionDraft? = runCatching {
            if (o.getInt("version") != VERSION) return null
            SessionDraft(Scene.valueOf(o.getString("scene")), StoryMode.valueOf(o.getString("mode")),
                o.getLong("savedAtMs"), o.getJSONObject("state"))
        }.getOrNull()
    }
}

/** Where a draft is kept. Tests use [Memory]; the app [LocalSessionDraftStore]. */
interface SessionDraftStore {
    fun load(): SessionDraft?
    fun save(draft: SessionDraft)
    fun clear()

    class Memory : SessionDraftStore {
        var raw: String? = null
        var writes = 0
        override fun load() = raw?.let { runCatching { SessionDraft.fromJson(JSONObject(it)) }.getOrNull() }
        override fun save(draft: SessionDraft) { raw = draft.toJson().toString(); writes++ }
        override fun clear() { raw = null }
    }
}

/** App-private file, replaced atomically. A damaged or older-version file is deleted on read. */
class LocalSessionDraftStore(context: Context) : SessionDraftStore {
    private val file = AtomicFile(File(File(context.applicationContext.filesDir, "session_draft").apply { mkdirs() }, "draft.json"))

    @Synchronized override fun load(): SessionDraft? {
        val bytes = runCatching { file.readFully() }.getOrNull() ?: return null
        val draft = runCatching { SessionDraft.fromJson(JSONObject(String(bytes, Charsets.UTF_8))) }.getOrNull()
        if (draft == null) file.delete()
        return draft
    }

    @Synchronized override fun save(draft: SessionDraft) {
        val out = runCatching { file.baseFile.parentFile?.mkdirs(); file.startWrite() }.getOrNull() ?: return
        try {
            out.write(draft.toJson().toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (_: Exception) { file.failWrite(out) }
    }

    @Synchronized override fun clear() { file.delete() }
}

// ── The explicit field list ─────────────────────────────────────────────────────
//
// Book-level state only: what `DemoState.resetStory()` clears, plus the made dolls and the co-op plan.
// Parent settings, the shelf, consent and the child's call (`ChildCall`) have their own stores.

private fun JSONObject.optStr(k: String): String? = if (!has(k) || isNull(k)) null else optString(k)
private fun JSONObject.putN(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)
private fun strings(c: Collection<String>) = JSONArray().apply { c.forEach { put(it) } }
private fun JSONArray?.strings(): List<String> = this?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
private fun map(m: Map<String, String>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }
private fun JSONObject?.stringMap(): Map<String, String> = this?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()

private fun HeroAttr.json() = JSONObject().put("hair", hair).put("shirt", shirt.toArgb()).put("eyes", eyes)
    .put("glasses", glasses).put("likes", likes).put("bottom", bottom)
private fun JSONObject.heroAttr() = HeroAttr(getString("hair"), Color(getInt("shirt")), getString("eyes"),
    getString("glasses"), getString("likes"), getString("bottom"))

private fun strokesJson(list: List<Stroke>) = JSONArray().apply {
    list.forEach { st ->
        val pts = JSONArray().apply { st.pts.forEach { put(it.x.toDouble()); put(it.y.toDouble()) } }
        put(JSONObject().put("color", st.color.toArgb()).put("w", st.w.toDouble()).put("pts", pts))
    }
}
private fun JSONArray?.strokes(): List<Stroke> = this?.let { a ->
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        val p = o.getJSONArray("pts")
        Stroke(Color(o.getInt("color")), (0 until p.length() / 2).map { Offset(p.getDouble(it * 2).toFloat(), p.getDouble(it * 2 + 1).toFloat()) },
            o.getDouble("w").toFloat())
    }
}.orEmpty()

/** The draft of the book being made now, to resume in [scene] */
fun DemoState.captureDraft(scene: Scene, now: Long = System.currentTimeMillis()) =
    SessionDraft(scene, mode, now, captureDraftState())

internal fun DemoState.captureDraftState(): JSONObject = JSONObject().apply {
    // the story's facts
    putN("place", place); putN("problem", problem); putN("cause", cause); putN("newcomer", newcomer)
    putN("friend", friend); putN("sound", sound); putN("solution", solution); putN("title", title); putN("reaction", reaction)
    put("slots", map(slots)); put("slotBy", map(slotBy))          // provenance child · card · mascot, as is (rule 5)
    putN("storyNextSlot", storyNextSlot); putN("storyClarificationSlot", storyClarificationSlot)
    putN("storyServerQuestion", storyServerQuestion)
    storyAnswerOptions?.let { o -> put("storyAnswerOptions", JSONObject().putN("slot", o.slot).put("question", o.question).put("values", strings(o.values))) }
    put("storyUnneededSlots", strings(storyUnneededSlots)); put("storyStartedAtMs", storyStartedAtMs)
    putN("partnerHelp", partnerHelp); putN("partnerHelpLine", partnerHelpLine)
    put("partnerKey", partnerKey); putN("partnerCall", partnerCall)
    put("diaryStart", diaryStart); put("diaryTimeUp", diaryTimeUp); put("mascotPicks", mascotPicks)
    put("storyAnswers", storyAnswers); put("storyGaugeHigh", storyGaugeHigh); putN("endReason", endReason)
    put("companionKind", companionKind)
    putN("parentAsk", parentAsk); put("parentRung", parentRung); putN("adultLine", adultLine); put("stepsDone", stepsDone)
    // level and template — computed by rules from signals (rule 4); restored, not recomputed
    put("level", level.name); put("levelAtStart", levelAtStart.name); putN("nextLevel", nextLevel?.name)
    putN("templateKey", templateKey); putN("attribute", attribute); put("causeKind", causeKind); put("levelWhy", levelWhy)
    put("notes", JSONArray().apply {
        notes.forEach { n -> put(JSONObject().put("q", n.q).put("a", n.a).put("mode", n.mode).put("s1", n.s1)
            .put("el", strings(n.el)).put("a1", n.a1).put("words", n.words)) }
    })
    put("askedThisStory", strings(askedThisStory)); put("usedVariants", strings(usedVariants))
    // the world and pictures
    put("themeKey", themeKey); putN("placeLabel", placeLabel); put("generatedBg", generatedBg)
    putN("storyBackground", storyBackground); putN("sceneKit", sceneKit); put("sceneSeed", sceneSeed)
    put("enteredOnStage", strings(enteredOnStage)); put("mentioned", strings(mentioned)); put("hotspotIntroShown", hotspotIntroShown)
    put("newcomerKind", newcomerKind); put("newcomerEmoji", newcomerEmoji); put("dinoKey", dinoKey)
    put("solutionKey", solutionKey); put("solutionItem", solutionItem)
    put("drawing", strokesJson(drawing)); put("drawnPreset", drawnPreset); put("drawingAspect", drawingAspect.toDouble())
    put("sceneDrawing", strokesJson(sceneDrawing)); put("sceneDrawingAspect", sceneDrawingAspect.toDouble())
    put("friendName", friendName); put("causeLine", causeLine); put("solutionLine", solutionLine); put("soundLine", soundLine)
    putN("m1Result", m1Result); putN("m2Result", m2Result)          // mission picks
    storyCaptions?.let { put("storyCaptions", strings(it)) }
    put("images", images); put("redraws", redraws); put("dinoColor", dinoColor.toArgb())
    heroAttr?.let { put("heroAttr", it.json()) }
    putN("storyHeroImage", storyHeroImage); putN("storyHeroRig", storyHeroRig); putN("storyHeroCall", storyHeroCall)
    generatedFriend?.let { g -> put("generatedFriend", JSONObject().put("words", g.words).put("image", g.image).putN("rig", g.rig)) }
    put("heroTries", JSONArray().apply { heroTries.forEach { put(it.json()) } })
    put("heroes", JSONArray().apply {
        heroes.forEach { h -> put(JSONObject().put("name", h.name).put("attr", h.attr.json()).putN("image", h.image).putN("rig", h.rig).putN("called", h.called)) }
    })
    put("bookStyle", bookStyle)
    // level signals and counters — 주고받기 횟수 is `turn` · `reactions` · the mode counts
    put("turn", turn); put("s1streak", s1streak); put("s1count", s1count); put("noAnswerStreak", noAnswerStreak)
    put("signals", strings(signals)); put("quotes", strings(quotes)); put("feelings", strings(feelings)); put("partnerTurns", partnerTurns)
    put("achievements", strings(achievements)); put("reactions", reactions)
    put("modeVoice", modeVoice); put("modeCard", modeCard); put("modeDraw", modeDraw); put("modeSilent", modeSilent)
    put("events", strings(events)); put("done", strings(done))
    // the report transcript — `who` is the source (child · card · draw · mascot · otto · adult), kept exactly
    put("talk", JSONArray().apply { talk.forEach { t -> put(JSONObject().put("who", t.who).put("text", t.text).put("tags", strings(t.tags))) } })
    put("talkStartedAtMs", talkStartedAtMs)
    // co-op: the parent's template and questions (CoopPlan keeps them too, until the book ends)
    coopPick?.let { p -> put("coopPick", JSONObject().put("kind", p.kind).put("name", p.name).putN("reason", p.reason)) }
    put("parentQuestions", strings(parentQuestions)); put("parentQIndex", parentQIndex)
    // picture diary: the pieces the child drew (strokes · the name the child gave · look · role). Otto's PNG is not kept
    if (mode == StoryMode.DIARY) diaryDay.let { day ->
        put("diaryPieces", JSONArray().apply {
            day.pieces.forEach { p -> put(JSONObject().put("id", p.id).put("strokes", strokesJson(p.strokes)).putN("name", p.name)
                .put("look", p.look.name).put("role", p.role.name)) }
        })
        put("diaryAlsoDrawn", strings(day.alsoDrawn))
        put("diaryPieceStories", JSONObject().apply { day.pieceStories.forEach { (k, v) -> put(k.toString(), v) } })
        putN("diaryWeather", day.weather?.name); putN("diaryWeatherBy", day.weatherBy); putN("diaryFeel", day.feel?.name)
    }
}

/**
 * Puts a draft back. Starts from a clean book (`resetStory`) so nothing of another book leaks in, then copies
 * the fields as they were. Does not touch stars, `CallLimits` or the shelf — a resumed book is not a new book.
 */
fun DemoState.applyDraft(d: SessionDraft) {
    val o = d.state
    resetStory()
    mode = d.mode
    place = o.optStr("place"); problem = o.optStr("problem"); cause = o.optStr("cause"); newcomer = o.optStr("newcomer")
    friend = o.optStr("friend"); sound = o.optStr("sound"); solution = o.optStr("solution"); title = o.optStr("title")
    reaction = o.optStr("reaction")
    slots.putAll(o.optJSONObject("slots").stringMap()); slotBy.putAll(o.optJSONObject("slotBy").stringMap())
    storyNextSlot = o.optStr("storyNextSlot"); storyClarificationSlot = o.optStr("storyClarificationSlot")
    storyServerQuestion = o.optStr("storyServerQuestion")
    storyAnswerOptions = o.optJSONObject("storyAnswerOptions")?.let { StoryOptions(it.optStr("slot"), it.getString("question"), it.optJSONArray("values").strings()) }
    storyUnneededSlots.addAll(o.optJSONArray("storyUnneededSlots").strings()); storyStartedAtMs = o.optLong("storyStartedAtMs")
    partnerHelp = o.optStr("partnerHelp"); partnerHelpLine = o.optStr("partnerHelpLine")
    partnerKey = o.optString("partnerKey", partnerKey); partnerCall = o.optStr("partnerCall")
    diaryStart = o.optLong("diaryStart"); diaryTimeUp = o.optBoolean("diaryTimeUp"); mascotPicks = o.optInt("mascotPicks")
    storyAnswers = o.optInt("storyAnswers"); storyGaugeHigh = o.optInt("storyGaugeHigh"); endReason = o.optStr("endReason")
    companionKind = o.optString("companionKind")
    parentAsk = o.optStr("parentAsk"); parentRung = o.optInt("parentRung"); adultLine = o.optStr("adultLine"); stepsDone = o.optInt("stepsDone")
    level = Level.valueOf(o.getString("level")); levelAtStart = Level.valueOf(o.getString("levelAtStart"))
    nextLevel = o.optStr("nextLevel")?.let(Level::valueOf)
    templateKey = o.optStr("templateKey"); attribute = o.optStr("attribute"); causeKind = o.optString("causeKind", causeKind)
    levelWhy = o.optString("levelWhy")
    o.optJSONArray("notes")?.let { a ->
        for (i in 0 until a.length()) a.getJSONObject(i).let { n ->
            notes += TurnNote(n.getString("q"), n.getString("a"), n.getString("mode"), n.getBoolean("s1"),
                n.optJSONArray("el").strings().toSet(), n.getBoolean("a1"), n.getInt("words"))
        }
    }
    askedThisStory.addAll(o.optJSONArray("askedThisStory").strings())
    o.optJSONArray("usedVariants").strings().forEach { if (it !in usedVariants) usedVariants += it }
    themeKey = o.optString("themeKey", themeKey); placeLabel = o.optStr("placeLabel"); generatedBg = o.optBoolean("generatedBg")
    storyBackground = o.optStr("storyBackground"); sceneKit = o.optStr("sceneKit"); sceneSeed = o.optLong("sceneSeed")
    enteredOnStage.addAll(o.optJSONArray("enteredOnStage").strings()); mentioned.addAll(o.optJSONArray("mentioned").strings())
    hotspotIntroShown = o.optBoolean("hotspotIntroShown")
    newcomerKind = o.optString("newcomerKind", newcomerKind); newcomerEmoji = o.optString("newcomerEmoji", newcomerEmoji)
    dinoKey = o.optString("dinoKey", dinoKey); solutionKey = o.optString("solutionKey", solutionKey)
    solutionItem = o.optString("solutionItem", solutionItem)
    drawing.addAll(o.optJSONArray("drawing").strokes()); drawnPreset = o.optInt("drawnPreset")
    drawingAspect = o.optDouble("drawingAspect", 1.0).toFloat()
    sceneDrawing.addAll(o.optJSONArray("sceneDrawing").strokes()); sceneDrawingAspect = o.optDouble("sceneDrawingAspect", 1.0).toFloat()
    friendName = o.optString("friendName", friendName); causeLine = o.optString("causeLine", causeLine)
    solutionLine = o.optString("solutionLine", solutionLine); soundLine = o.optString("soundLine", soundLine)
    m1Result = o.optStr("m1Result"); m2Result = o.optStr("m2Result")
    storyCaptions = o.optJSONArray("storyCaptions")?.strings()
    images = o.optInt("images"); redraws = o.optInt("redraws"); dinoColor = Color(o.optInt("dinoColor", dinoColor.toArgb()))
    heroAttr = o.optJSONObject("heroAttr")?.heroAttr()
    storyHeroImage = o.optStr("storyHeroImage"); storyHeroRig = o.optStr("storyHeroRig"); storyHeroCall = o.optStr("storyHeroCall")
    generatedFriend = o.optJSONObject("generatedFriend")?.let { GeneratedFriend(it.getString("words"), it.getString("image"), it.optStr("rig")) }
    o.optJSONArray("heroTries")?.let { a -> for (i in 0 until a.length()) heroTries += a.getJSONObject(i).heroAttr() }
    o.optJSONArray("heroes")?.let { a ->
        heroes.clear()
        for (i in 0 until a.length()) a.getJSONObject(i).let { h ->
            heroes += Hero(h.getString("name"), h.getJSONObject("attr").heroAttr(), h.optStr("image"), h.optStr("rig"), h.optStr("called"))
        }
    }
    bookStyle = o.optString("bookStyle", bookStyle); WorldStyle.current = bookStyle
    turn = o.optInt("turn"); s1streak = o.optInt("s1streak"); s1count = o.optInt("s1count"); noAnswerStreak = o.optInt("noAnswerStreak")
    signals.addAll(o.optJSONArray("signals").strings()); quotes.addAll(o.optJSONArray("quotes").strings())
    feelings.addAll(o.optJSONArray("feelings").strings()); partnerTurns = o.optInt("partnerTurns")
    achievements.addAll(o.optJSONArray("achievements").strings()); reactions = o.optInt("reactions")
    modeVoice = o.optInt("modeVoice"); modeCard = o.optInt("modeCard"); modeDraw = o.optInt("modeDraw"); modeSilent = o.optInt("modeSilent")
    events.addAll(o.optJSONArray("events").strings()); done.addAll(o.optJSONArray("done").strings())
    o.optJSONArray("talk")?.let { a ->
        for (i in 0 until a.length()) a.getJSONObject(i).let { t -> talk += TalkLine(t.getString("who"), t.getString("text"), t.optJSONArray("tags").strings()) }
    }
    talkStartedAtMs = o.optLong("talkStartedAtMs")
    // the plan the parent saved is loaded by CoopPlan at launch; the draft fills in only what is missing
    if (coopPick == null) coopPick = o.optJSONObject("coopPick")?.let { CoopPick(it.getString("kind"), it.getString("name"), it.optStr("reason")) }
    if (parentQuestions.isEmpty()) parentQuestions.addAll(o.optJSONArray("parentQuestions").strings())
    parentQIndex = o.optInt("parentQIndex")
    o.optJSONArray("diaryPieces")?.let { a ->
        val day = newDiaryDay()
        for (i in 0 until a.length()) a.getJSONObject(i).let { p ->
            day.pieces += DiaryPiece(p.getInt("id"), p.optJSONArray("strokes").strokes(), p.optStr("name"),
                PieceLook.valueOf(p.getString("look")), null, PieceRole.valueOf(p.getString("role")))
        }
        day.alsoDrawn.addAll(o.optJSONArray("diaryAlsoDrawn").strings())
        o.optJSONObject("diaryPieceStories").stringMap().forEach { (k, v) -> day.pieceStories[k.toInt()] = v }
        day.weather = o.optStr("diaryWeather")?.let(DiaryWeather::valueOf); day.weatherBy = o.optStr("diaryWeatherBy")
        day.feel = o.optStr("diaryFeel")?.let(DiaryFeel::valueOf)
    }
}
