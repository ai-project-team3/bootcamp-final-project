package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import com.example.finalproject_demo.net.nameMask
import com.example.finalproject_demo.ui.ConsentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class Kind { EASY, HARD, CHOICE }

/**
 * 질문 하나. 무응답 흐름(⭐5 · ⭐22)이 붙어 있다.
 *
 * **소크라틱 질문 (v0.8)** — 답을 주지 않고 아이가 떠올리게 한다.
 * 질문에 선택지를 늘어놓지 않고, 아이가 방금 한 말에서 다음 질문을 잇는다.
 * 말이 없을 때도 선택지부터 주지 않고 **생각할 거리를 주는 질문(힌트)** 을 먼저 던진다.
 *
 * - EASY / HARD(열린 질문): 카드 없이 마이크로만 듣는다.
 *   무응답 → 쉬운 질문(easierText) → [noCards면] 힌트 질문(hint) → 마스코트가 "혹시 ○○일까?" 하고 채움
 *                                 → [카드가 있으면] 그림 3장(7초) → 최대 3번 교체 → 마스코트가 고름
 * - CHOICE(모호한 말 확인): "공룡!" 처럼 뜻이 여럿인 말을 되물을 때만 카드 3장을 바로 띄운다.
 * - 말로 답한 것만 수준 신호다. 탭 · 그림 · 마스코트가 채운 것은 세지 않는다.
 */
/** Set only by the test run that lists the app's own lines (`Director.dumpSpoken`) */
private val SPEECH_DUMP: String? = System.getenv("OTTO_SPEECH_DUMP")
private val SPEECH_DUMP_LOCK = Any()

data class Question(
    val text: String,
    val kind: Kind,
    val choices: List<Card> = emptyList(),
    val easierText: String? = null,
    val easierAsk: String? = null,
    val easierAnswer: String? = null,
    /** 쉬운 질문에도 말이 없을 때 던지는 두 번째 생각 거리 (카드 대신) */
    val hint: String? = null,
    /** 카드를 쓰지 않는 질문 (장면 4 "누가 흔들었을까?") */
    val noCards: Boolean = false,
    /** 끝까지 말이 없으면 마스코트가 "혹시 ○○일까?" 하고 채우는 값 */
    val fallback: Answer? = null,
    /** 아이가 말할 수 있는 답 후보 — 마이크를 끄면 이 중 하나가 무작위로 들어온다 */
    val spoken: List<Answer> = emptyList(),
    /** 함께 하는 사람만 말하는 갈래의 대사 (아이는 조용) */
    val partnerLine: String? = null,
    val partnerChildAnswer: List<Answer> = emptyList(),
    val drawerHint: Boolean = false,
    /** [직접 그리기 🖍️]로 답했을 때 이 칸에 들어갈 값. null이면 그리기 버튼을 숨긴다 */
    val drawAnswer: Answer? = null,
    /**
     * **사다리** — 말이 없을 때 답을 고르게 하지 않고 **질문을 바꿔 다시 묻는다** (일기 설계 §4).
     * 첫 칸이 [text]이고 여기에는 둘째 칸부터 담는다. 아래로 갈수록 기억을 덜 꺼내고 눈앞의 것에 답하게 된다.
     * 비어 있으면 동화 모드의 기존 흐름(쉬운 질문 → 힌트 → 카드)을 그대로 쓴다.
     */
    val ladder: List<String> = emptyList(),
    /** 이 질문에만 붙는 시연 버튼 (대본 버튼 뒤에 덧붙는다) */
    val extra: List<DemoBtn> = emptyList(),
    /** 질문 은행의 변형 id (시연 서랍 · 로그) */
    val id: String = "",
    /**
     * 마스코트가 **소리 내어 읽지 않는** 질문 — 부모 협업 모드 (협업 §2-1 ASK′).
     * 질문은 말풍선 대신 **부모 띠**에 뜨고, 어른이 읽고 자기 말로 묻는다.
     * 흐름(사다리 · 무응답 · 마스코트 채우기)은 동화 모드와 **똑같다** (9/21).
     */
    val silent: Boolean = false,
)

class Director(
    private val scope: CoroutineScope,
    private val storyBookStore: StoryBookStore? = null,
    private val storyImageStore: StoryImageStore? = null,
) {

    val s = DemoState()
    private val savedStories = mutableListOf<SavedStoryBook>()

    init { reloadSavedStories() }

    private fun reloadSavedStories() {
        savedStories.clear()
        savedStories += storyBookStore?.load().orEmpty()
        s.shelf.addAll(0, savedStories.map { it.onShelf() })
    }

    private fun SavedStoryBook.onShelf(fresh: Boolean = false) =
        ShelfBook(title, themeKey, bgName, pages.size, fresh, id)

    /** 실패한 저장은 책장에 성공한 것처럼 표시하지 않는다. */
    fun saveFinishedStory(): Boolean {
        val book = s.completedStoryBook() ?: return false
        return try {
            if (book.soundClipId != null && storyBookStore == null) return false
            if (!s.keepStorySound(book)) return false
            storyBookStore?.save(book)
            s.commitStorySound()
            savedStories.add(0, book)
            s.shelf.add(0, book.onShelf(fresh = true))
            true
        } catch (_: Exception) { false }
    }

    fun savedStory(id: String): SavedStoryBook? = savedStories.firstOrNull { it.id == id }

    fun keepStoryBackground(png: ByteArray): Boolean {
        val path = saveStoryImage(png) ?: return false
        s.storyBackground = path
        return true
    }
    fun saveStoryImage(png: ByteArray): String? = storyImageStore?.save(png)
    private var job: Job? = null
    private val input = Channel<Reply>(Channel.BUFFERED)

    /** 지금 마이크가 듣고 있는 질문. 마이크를 끄면 이 질문의 대본 답이 들어간다. */
    private var currentQ: Question? = null

    /** 마이크가 들을 질문을 직접 정한다 (ask를 쓰지 않는 장면용) */
    fun setListening(q: Question?) {
        currentQ = q
    }

    fun send(r: Reply) {
        input.trySend(r)
    }

    /** 화면을 탭할 때까지 기다린다 (대기 타이머 없는 장면용). */
    suspend fun awaitReply(): Reply {
        drain()
        return input.receive()
    }

    /**
     * 정해진 시간만 기다린다.
     * 아이 무응답 타이머(⭐5)와는 별개라 시연 서랍의 [무응답 타이머]와 무관하게 늘 동작한다.
     */
    suspend fun withTimeoutOrNullReply(sec: Double): Reply? {
        drain()
        return withTimeoutOrNull((sec * 1000 * s.speed).toLong()) { input.receive() }
    }

    /** 특정 값이 올 때까지 기다린다. */
    suspend fun awaitValue(vararg values: String): String {
        while (true) {
            val r = awaitReply()
            if (r is Reply.Tapped && (values.isEmpty() || r.value in values)) return r.value
        }
    }

    private fun drain() {
        while (input.tryReceive().isSuccess) { /* 이전 장면의 입력 버리기 */ }
    }

    /**
     * 장면 사이 쉬는 시간. 대본은 「이 정도면 말이 끝났겠지」로 ms 를 정해 두었다.
     * 서버 모드에서는 **진짜 목소리가 끝날 때까지** 먼저 기다리고, 남은 시간만 쉰다 (09-29 S25+) —
     * 전에는 화면이 목소리보다 앞서 달려가서 대사 세 개가 「후루룩」 넘어가고, 🎤 를 누를 때마다
     * 밀린 대사가 버려져 뒤로 갈수록 목소리가 안 들렸다.
     */
    suspend fun pause(ms: Long) {
        val total = (ms * s.speed).toLong()
        if (!Server.liveFor(s.mode)) { delay(total); return }
        val t0 = System.currentTimeMillis()
        awaitVoice()
        // 최소 쉬는 틈도 속도를 따른다 — 고정 250ms 는 테스트의 빨리 감기(speed 0.01)를 무시해서, 질문이
        // 준비되기 전에 온 답이 버려지고(ask 는 그 전 입력을 버린다) 서버 모드 테스트가 멈췄다(09-29 StoryLiveAskTest)
        delay(maxOf(total - (System.currentTimeMillis() - t0), (250 * s.speed).toLong()))
    }

    /**
     * 말풍선에 한 줄 띄운다.
     *
     * 협업 모드에는 자막이 둘이다 — 어른에게 주는 **질문 카드**(아래 띠)와 **마스코트 말풍선**.
     * 둘이 같이 떠 있으면 어른이 어느 쪽을 읽어야 할지 헷갈린다. 그래서 **번갈아 뜬다** (9/22):
     * 여기서 말풍선을 띄울 때 띠를 비우고, [askSay] 가 띠에 질문을 올릴 때 말풍선을 비운다.
     *
     * 역할 나눔은 그대로다 — **질문은 띠**(어른이 읽고 묻는다), **받아주는 반응은 말풍선**
     * (부모협업모드_설계.md §2-2).
     */
    fun say(text: String, who: String = "마스코트") {
        s.parentCard = null
        s.speaker = who
        s.line = text
        s.lineId++
        if (who == "마스코트" && surprise.containsMatchIn(text)) feel(Mood.SURPRISED)
        if (who == "마스코트") { dumpSpoken(text); speakLive(text) }
    }

    /**
     * Writes each mascot line to the file named by `OTTO_SPEECH_DUMP`, only when it is set — the
     * test suite runs with it to list every line the app itself can say, to bake into audio once
     * (eval/fixed_lines.py · 10-01: fixed lines going to TypeCast every time used up the month).
     * The app never has the variable, so this does nothing there.
     */
    private fun dumpSpoken(text: String) {
        val path = SPEECH_DUMP ?: return
        val line = s.nameMask().speakable(text, named = false)   // what TypeCast would get
        synchronized(SPEECH_DUMP_LOCK) { java.io.File(path).appendText(line.replace('\n', ' ') + "\n") }
    }

    /** 마스코트 기분을 켠다 — 얼굴이 그에 맞게 움직인다 ([Mood]) */
    fun feel(m: Mood) {
        s.mood = m
        s.moodId++
    }

    /**
     * 서버 모드면 마스코트 말을 **목소리로도** 낸다(`/tts` · 09-29).
     * 이름은 보호자가 「이름 읽기」에 동의했을 때만 소리로 나간다 — 아니면 아이는 「너」 · 친구는 「그 친구」(규칙 6 개정 · 10-01 #50).
     * 목소리가 실패해도 말풍선은 이미 떴다 — 조용한 마스코트일 뿐 멈추지 않는다.
     *
     * **대사는 줄을 서서 끝까지 읽는다** (09-29 S25+). 전에는 새 대사가 앞 대사를 끊어서
     * 「받아주기 → 질문」이 연달아 오면 앞말이 반쯤 잘렸다. 지금은 목소리를 **먼저 받아 두고**
     * (기다리는 동안 다음 것을 받는다) 앞 대사가 끝나면 튼다. 아이 차례는 [awaitVoice] 뒤에 온다.
     */
    private var voiceJob: Job? = null

    private fun speakLive(text: String) {
        // 소리를 낼 수 없으면(단위 테스트 — Voice 가 붙지 않았다) 목소리를 청하지도 않는다.
        // 들리지 않을 목소리 때문에 가짜 서버 주소로 대사마다 연결을 시도할 까닭이 없다
        if (!Server.liveFor(s.mode) || text.isBlank() || !Voice.canSpeak) return
        val line = s.nameMask().speakable(text, ConsentStore.nameVoiceAgreed)
        val before = voiceJob
        val audio = scope.async { Server.tts(line) }          // 앞 대사를 읽는 동안 미리 받는다
        voiceJob = scope.launch {
            before?.join()
            audio.await()?.let { Voice.playAndWait(it) }
        }
    }

    /** 마스코트가 하던 말을 끝낼 때까지 기다린다 — 서버 모드가 아니면 바로 돌아온다 */
    suspend fun awaitVoice() {
        voiceJob?.join()
    }

    /** 목소리를 지금 멈추고 줄 선 대사도 버린다 — 🎤 가 눌렸다(마스코트 소리가 녹음에 섞이면 안 된다) */
    private fun hushVoice() {
        voiceJob?.cancel()
        voiceJob = null
        Voice.stopPlaying()
    }

    /**
     * 질문을 띄운다. 협업 모드([Question.silent])면 말풍선이 아니라 **부모 띠**에 올린다 —
     * 마스코트가 읽어 주면 부모가 물을 이유가 없어진다 (협업 §2-1).
     */
    private fun askSay(q: Question, text: String) {
        if (!q.silent) { say(text); return }
        // 같은 걸음 안에서 글이 바뀌었다 = 질문을 바꿔 다시 물은 것(사다리 한 칸). 첫 질문은 세지 않는다 —
        // 부모 리포트가 "오늘 n번 다르게 물어보셨어요" 로 쓰기 때문이다
        // ⚠️ 세는 기준은 `parentAsk` 다. `parentCard` 는 자막을 번갈아 띄우느라 수시로 비워져서
        //    그것으로 세면 사다리를 내려가도 칸이 안 세어진다 (9/22에 실제로 깨졌다)
        if (s.parentAsk != null && s.parentAsk != text) s.parentRung++
        s.parentAsk = text
        s.parentCard = text
        // 띠에 질문이 올라오면 말풍선은 비운다 — 자막 둘이 같이 뜨지 않는다 (9/22)
        s.line = ""
    }

    fun childSays(text: String) = say(text, s.childName)
    fun partnerSays(text: String) = say(text, s.pn)

    fun log(t: String) {
        s.log.add(0, t)
        if (s.log.size > 80) s.log.removeAt(s.log.size - 1)
    }

    fun mark(id: String) {
        if (id !in s.done) s.done += id
    }

    fun buttons(vararg b: DemoBtn) {
        s.buttons.clear()
        s.buttons += b
    }

    fun inputs(mic: Boolean, next: Boolean, draw: Boolean = false) {
        s.micEnabled = mic
        s.nextEnabled = next
        s.drawEnabled = draw
        if (!mic) s.micOn = false
    }

    /**
     * 리포트 6축의 재료 (구현대본 §0-5). 실제 앱에서는 폰 로컬 DB에 쌓이고,
     * 부모 모드가 이것만 읽어서 문장을 만든다 — 화면 어디에도 점수는 없다.
     */
    fun event(name: String, vararg fields: Pair<String, Any?>) {
        val body = fields.filter { it.second != null }.joinToString(", ") { "${it.first}=${it.second}" }
        s.events.add(0, if (body.isEmpty()) name else "$name  $body")
        if (s.events.size > 60) s.events.removeAt(s.events.size - 1)
    }

    /**
     * 🎤 온/오프. 시간 제한 없음 — 켜면 듣고, 다시 누르면 끝.
     * 데모에는 진짜 음성 인식이 없으므로, 끄는 순간 이 질문의 대본 답이 들어온다.
     */
    fun toggleMic() {
        if (!s.micEnabled) return
        if (Server.liveFor(s.mode)) { liveMic(); return }
        if (!s.micOn) {
            s.micOn = true
            s.countdown = null
            log("🎤 켬 — 듣는 중 (실제 앱: 우리 서버 Whisper로 스트리밍, 침묵으로 끊지 않음)")
            return
        }
        s.micOn = false
        val q = currentQ ?: return
        val a = s.pickAnswer(q.spoken) ?: Answer("응", lv = 1)
        log("🎤 끔 — 녹음 끝 → 글자로: \"${a.text}\" (더미 답 ${q.spoken.size}개 중 · 아이 흉내: ${s.profile.label})")
        send(Reply.Spoke(a.text, a.value, a))
    }

    // ── 진짜 마이크 (서버 모드일 때만 · 09-29 오케스트레이터 ①) ────────────
    //
    // 🎤 누름 → 녹음 → 말이 끝나면 VAD 가 0.3초 뒤 스스로 끊는다(⏹ 로 먼저 끊어도 된다)
    // → 우리 서버 `/stt` → 들은 글자를 대본 답과 **같은 모양**(`Reply.Spoke`)으로 흐름에 넣는다.
    // 그래서 장면 코드는 대본인지 진짜인지 모른다. 글자는 **실명 그대로**다 — 서버로 다시
    // 보낼 때는 모드 담당자가 `s.nameMask().mask(...)` 를 거친다(규칙 6).
    // 아무것도 못 들었거나(빈 글자) 서버가 실패하면 **무응답**으로 보낸다 — 무응답 흐름(⭐5)이 이어받는다.

    @Volatile private var stopMic = false
    private var micJob: Job? = null

    private fun liveMic() {
        if (s.micOn) { stopMic = true; return }            // ⏹ — 녹음을 여기서 끊는다
        stopMic = false
        hushVoice()                                       // 마스코트 소리가 녹음에 들어가지 않게
        s.micOn = true
        s.countdown = null
        log("🎤 켬 — 진짜 녹음 · 말이 끝나면 저절로 끊는다 (VAD 0.3초)")
        micJob = scope.launch {
            val audio = Voice.listen { stopMic }
            s.micOn = false
            if (audio == null) { log("🎤 아무것도 못 들음 → 무응답"); send(Reply.Silent); return@launch }
            log("🎤 끝 → 우리 서버로 받아쓰기 (${audio.size / 1024}KB)")
            val text = Voice.transcribe(audio)
            when {
                text == null -> { log("받아쓰기 실패 → 무응답으로 넘김"); send(Reply.Silent) }
                text.isBlank() -> { log("받아쓰기: 들을 말이 없음 → 무응답"); send(Reply.Silent) }
                else -> { log("받아쓰기: \"$text\""); send(Reply.Spoke(text)) }
            }
        }
    }

    /** ➡️ 말 없이 넘김 = 무응답 */
    fun skip() {
        if (!s.nextEnabled) return
        s.micOn = false
        send(Reply.Silent)
    }

    // ── 장면 이동 ────────────────────────────────────────────────

    private val progressScenes = setOf(
        Scene.DIARY,
        Scene.PLACE, Scene.EVENT, Scene.CAUSE, Scene.DRAW, Scene.PLOT, Scene.DINO,
        Scene.SOUND, Scene.CHECK, Scene.SOLUTION, Scene.MAKING,
    )

    fun go(scene: Scene) {
        // 책이 펼쳐지면 이야기는 끝났다 — 멈춰 둔 이야기(이어 가기)도 비운다
        if (scene == Scene.BOOK) s.paused = null
        scope.launch {
            job?.cancelAndJoin()
            drain()
            currentQ = null
            s.scene = scene
            s.buttons.clear()
            s.countdown = null
            s.stage = Stage.Empty
            s.line = ""
            inputs(mic = false, next = false)
            s.progressVisible = scene in progressScenes
            s.behind = behindText(scene)
            seedFor(scene)
            job = scope.launch { runScene(scene) }
        }
    }

    /**
     * Stops the running scene and runs [next] in its place, in the same scene — e.g. a parent ending co-op
     * early (#36). A plain [go] would restart the scene from its first line.
     */
    fun replaceScene(next: suspend () -> Unit) {
        scope.launch {
            job?.cancelAndJoin()
            drain()
            currentQ = null
            s.buttons.clear()
            s.countdown = null
            inputs(mic = false, next = false)
            job = scope.launch { next() }
        }
    }

    /**
     * 처음 화면으로 — 책장 · 부모 설정 · 하루 별은 그대로 둔다.
     * 방금 만든 이야기는 새 이야기를 시작할 때 지운다 (그 전까지 부모 모드에서 오늘의 기록으로 볼 수 있게).
     */
    fun goHome() {
        scope.launch {
            job?.cancelAndJoin()
            s.shelf.replaceAll { it.copy(fresh = false) }
            // ⚠️ 부모가 넣어 둔 질문은 **여기서 비우지 않는다.** 부모 모드를 나올 때도 이 길을 타서,
            // 방금 입력한 질문이 [같이 만들기] 전에 사라졌다 (90de2d6 의 실수 · 박진웅 4549b88 지적).
            // 비우는 곳은 협업 이야기가 끝날 때(`coopFinishLog`)와 `reset()` 둘이다
            go(Scene.ADULT)
        }
    }

    /**
     * 이야기 **도중** 🔒 부모 문 (09-29 앱 틀 · 디자인 시스템 「왼쪽 위 = 시스템」).
     * 전에는 부모 신호를 첫 화면 · 책장에서만 받아, 이야기 중에 누르면 아무 일도 없었다.
     * 지금 장면을 멈추고 어른 확인(태어난 해) → 부모 영역. 취소하면 오또의 방으로 — 만들던 이야기는 이어 가지 않는다
     */
    fun openParent() {
        pauseStory()
        scope.launch {
            job?.cancelAndJoin()
            drain()
            currentQ = null
            inputs(mic = false, next = false)
            s.progressVisible = false
            s.line = ""
            job = scope.launch { if (pinGate("parent")) go(Scene.PARENT) else go(Scene.ADULT) }
        }
    }

    /**
     * 이야기 **도중** 🏠 방으로 (09-29) — 멈춘 장면을 기억해 두고 첫 화면(오또의 방)으로.
     * 다시 같은 모드로 들어오면 「이어서 할까?」 → `resume` 신호로 이 장면부터 이어 간다 (`Scenes.sceneAdult`)
     */
    fun leaveToRoom() {
        pauseStory()
        goHome()
    }

    /** 지금이 이야기 **안**이면 그 장면을 기억한다 — 방 · 부모 · 책장은 이야기 밖이다 */
    private fun pauseStory() {
        // 책(BOOK)은 **다 만든 이야기**다 — 이어 갈 것이 없다. 전에는 책을 보다 🏠 로 나가면 BOOK 을 기억해,
        // 방에서 동화 만들기를 누를 때마다 「이어서 할까?」가 뜨고 이어 가면 그 책 마지막 쪽이 다시 열렸다 (09-29 사용자 지적)
        if (s.scene !in setOf(Scene.ADULT, Scene.PARENT, Scene.SHELF, Scene.BOOK)) {
            s.paused = s.scene
            log("이야기 도중 나감 — 「${s.scene.label}」을 기억해 둔다. 다시 들어오면 이어서 할지 묻는다")
        }
    }

    /** 시연 서랍 "처음부터" — 앱을 새로 켠 것처럼 */
    fun restart() {
        scope.launch {
            job?.cancelAndJoin()
            val persona = s.persona
            val speed = s.speed
            val timer = s.timerOn
            s.reset()
            reloadSavedStories()
            s.persona = persona
            s.speed = speed
            s.timerOn = timer
            go(Scene.ADULT)
        }
    }

    // ── 질문 엔진 ────────────────────────────────────────────────

    /**
     * 아이 반응을 기다린다. 타이머가 켜져 있으면 초를 세고 시간이 다 되면 null,
     * 꺼져 있으면(기본) 마이크를 끄거나 카드를 탭하거나 ➡️를 누를 때까지 기다린다.
     */
    private suspend fun waitReply(sec: Double): Reply? {
        drain()
        if (!s.timerOn) return input.receive()
        var left = sec
        s.countdown = left
        while (left > 0.0) {
            if (s.micOn) { // 마이크가 켜져 있는 동안은 세지 않는다
                s.countdown = null
                val r = input.receive()
                s.countdown = null
                return r
            }
            val r = withTimeoutOrNull((100 * s.speed).toLong()) { input.receive() }
            if (r != null) {
                s.countdown = null
                return r
            }
            left -= 0.1
            s.countdown = if (left < 0) 0.0 else left
        }
        s.countdown = null
        return null
    }

    private fun shuffled(list: List<Card>) = list.shuffled()

    private fun scriptButtons(q: Question): MutableList<DemoBtn> {
        val b = mutableListOf<DemoBtn>()
        if (q.spoken.isNotEmpty()) {
            b += DemoBtn("🎲 ${s.childName}${ga(s.childName)} 말함 — 더미 답 ${q.spoken.size}개 중 (${s.profile.label})") {
                val a = s.pickAnswer(q.spoken)!!; send(Reply.Spoke(a.text, a.value, a))
            }
            q.spoken.forEach { a -> b += DemoBtn("🗣 ${tag(a)} \"${a.text}\"") { send(Reply.Spoke(a.text, a.value, a)) } }
        }
        if (q.drawAnswer != null) {
            b += DemoBtn("🖍 직접 그려서 답함 — \"${q.drawAnswer.text}\"") { send(Reply.Tapped("draw", "직접 그리기")) }
        }
        return b
    }

    /** 더미 답 옆에 붙이는 표시 — 어느 수준 아이의 답처럼 보이나 · 신호 */
    private fun tag(a: Answer): String {
        val lv = when (a.lv) { 1 -> "①"; 3 -> "③"; else -> "②" }
        val sig = buildList {
            if (a.reason) add("S1")
            if (a.el.isNotEmpty()) add("S2")
            if (a.con) add("A1")
        }
        return if (sig.isEmpty()) lv else "$lv${sig.joinToString("·")}"
    }

    /**
     * 질문 은행에서 이 slot의 질문을 골라 묻고, 답을 발달 판단에 기록한다.
     * 같은 slot이라도 수준 · 지난 이야기에 따라 다른 변형이 나온다.
     */
    suspend fun askSlot(slot: String, tweak: (Question) -> Question = { it }): Pair<QVariant, Reply> {
        val v = s.pick(slot)
        log("질문 은행 [$slot] ${v.id} — ${v.probe} · 지금 수준 ${s.level.label}")
        val q = tweak(v.toQuestion(s)).let { local ->
            val serverText = s.storyServerQuestion?.takeIf {
                Server.liveFor(s.mode) && s.mode == StoryMode.STORY && s.storyNextSlot == slot && it.isNotBlank()
            }
            if (serverText == null) local else local.copy(text = serverText)
        }
        val r = if (s.mode == StoryMode.STORY) askStory(q, slot) else ask(q)
        judge(v, r, q.text)
        return v to r
    }

    /**
     * 질문을 하고 답을 받는다. 무응답이면 ⭐5 흐름을 끝까지 밟고 결과를 돌려준다.
     * 말로 답한 것만 Reply.Spoke — 탭 · 마스코트가 골라준 것은 수준 신호가 아니다.
     */
    suspend fun ask(q: Question): Reply {
        currentQ = q
        askSay(q, q.text)
        inputs(mic = true, next = true, draw = q.drawAnswer != null)

        val scripted = scriptButtons(q)
        q.partnerLine?.let {
            scripted += DemoBtn("${s.partner.emoji} ${s.pn}만 말함 — \"$it\"") { send(Reply.PartnerOnly) }
        }
        scripted += DemoBtn("🤐 대답 없음 (➡️와 같음)") { send(Reply.Silent) }

        if (q.kind == Kind.CHOICE) {
            showCards(shuffled(q.choices), q.drawerHint)
            scripted += DemoBtn("🖐 화면의 첫 카드를 탭 (고르기형)") {
                val c = (s.stage as? Stage.CardsRow)?.cards?.firstOrNull() ?: return@DemoBtn
                send(Reply.Tapped(c.value, c.label))
            }
            log("모호한 말 확인: 뜻이 여럿인 말이라 그림 카드 3장을 바로 띄움 · 말로 답해도 됨 (구현대본 §0-1)")
        } else {
            log("소크라틱 열린 질문 — 선택지를 말하지 않고 아이가 떠올리게 한다 (v0.8 · 결정 30)")
        }
        scripted += q.extra
        buttons(*scripted.toTypedArray())

        // 마스코트 말이 끝나면(TTS 종료) 아이 차례 — 서버 모드는 **진짜 목소리가 끝날 때까지** 기다린다 (09-29 S25+)
        if (Server.liveFor(s.mode)) { awaitVoice(); pause(300) } else pause(1200)
        val sec = when (q.kind) { Kind.EASY -> 5.0; Kind.HARD -> 8.0; Kind.CHOICE -> 7.0 }
        val first = waitReply(sec)

        val result = when {
            first is Reply.Spoke -> {
                acceptSpoken(first.text)
                first
            }
            first is Reply.Tapped && first.value == "draw" -> drawBranch(q)
            first is Reply.Tapped -> {
                acceptTap(first)
                first
            }
            first is Reply.PartnerOnly -> partnerBranch(q)
            else -> noAnswer(q)
        }
        currentQ = null
        inputs(mic = false, next = false)
        if (s.mood == Mood.WAITING) s.mood = Mood.NONE
        return result
    }

    /**
     * 함께 하는 사람에게 묻는 질문 — 이야기 흐름 안에서 엄마 · 아빠 · 할머니 · 친구 … 가 참여하는 자리 (구현대본 §2 어른 칸).
     * 마이크는 그 사람이 쓴다. 답이 없으면(➡️) **아무것도 대신 고르지 않고** 넘어간다.
     */
    suspend fun askPartner(text: String, spoken: List<Answer>): Answer? {
        val q = Question(text = text, kind = Kind.EASY, spoken = spoken)
        currentQ = q
        say(text)
        inputs(mic = true, next = true)
        val who = s.pn
        val e = s.partner.emoji
        val b = mutableListOf<DemoBtn>()
        b += DemoBtn("🎲 ${who}${ga(who)} 답함 — 후보 ${spoken.size}개 중 무작위") { val a = spoken.random(); send(Reply.Spoke(a.text, a.value, a)) }
        spoken.forEach { a -> b += DemoBtn("$e \"${a.text}\"") { send(Reply.Spoke(a.text, a.value, a)) } }
        b += DemoBtn("🤐 ${who}${ga(who)} 답하지 않음 (➡️) → 책에 넣지 않고 넘어감") { send(Reply.Silent) }
        buttons(*b.toTypedArray())
        pause(1000)
        val r = awaitReply()
        currentQ = null
        inputs(mic = false, next = false)
        buttons()
        if (r !is Reply.Spoke) return null
        partnerSays(r.text)
        s.partnerTurns++
        event("utterance", "speaker" to (if (s.partner.adult) "adult" else "peer"), "who" to who, "mode" to "voice", "text" to r.text)
        pause(1300)
        return r.answer ?: spoken.firstOrNull { it.text == r.text } ?: Answer(r.text, r.value)
    }

    /** [직접 그리기 🖍️] — 말 대신 그림으로 답한다. 수준 신호로는 세지 않는다 (mode: draw) */
    suspend fun drawBranch(q: Question): Reply {
        val a = q.drawAnswer ?: return Reply.Silent
        inputs(mic = false, next = false)
        s.stage = Stage.DrawPad(forAnswer = true)
        say("좋아, 그려서 알려줄래?")
        log("직접 그리기로 답함 → 그림판 (말이 어려운 아이도 같은 칸을 채울 수 있다)")
        buttons(DemoBtn("✅ 다 그렸어") { send(Reply.Tapped("done", "완료")) })
        awaitValue("done")
        s.reactions++
        childSays(a.text)
        s.modeDraw++
        event("utterance", "speaker" to "child", "mode" to "draw", "text" to a.text)
        log("그림으로 답함 → 칸 값 \"${a.value}\" · mode: draw 로 기록 (수준 신호 아님)")
        pause(900)
        return Reply.Tapped(a.value, a.text)
    }

    suspend fun acceptSpoken(text: String) {
        s.micOn = false
        childSays(text)
        s.reactions++
        s.modeVoice++
        feel(Mood.CHEER)
        event("utterance", "speaker" to "child", "confidence" to "0.9", "mode" to "voice", "text" to text)
        log("[${s.childName}] $text  →  우리 서버 Whisper → 글자 (음성 사본 즉시 삭제)")
        pause(900)
    }

    suspend fun acceptTap(r: Reply.Tapped) {
        s.micOn = false
        s.stage = (s.stage as? Stage.CardsRow)?.copy(picked = r.value) ?: s.stage
        s.reactions++
        s.modeCard++
        feel(Mood.CHEER)
        event("utterance", "speaker" to "child", "mode" to "card", "text" to r.label)
        log("탭으로 고름: ${r.label} → 수준 신호로 세지 않음 (mode: card)")
        pause(800)
    }

    /** 기록에 남길 "어떻게 답했나" (구현대본 §0-5 source) */
    fun sourceOf(r: Reply): String = when {
        r is Reply.Spoke -> "voice"
        r is Reply.Tapped && r.byMascot -> "mascot"
        r is Reply.Tapped -> "card"
        else -> "mascot"
    }

    /** 함께 하는 사람만 말한 갈래 — 2~3초 기다렸다가 "○○는 어떻게 생각해?" 한 번 (⭐5 · 구현대본 §0-2) */
    private suspend fun partnerBranch(q: Question): Reply {
        val line = q.partnerLine ?: return Reply.Silent
        partnerSays(line)
        s.partnerTurns++
        log("[${s.pn}] $line → 칸을 채우지 않음. 함께 놀기 기록 재료로만")
        event("utterance", "speaker" to (if (s.partner.adult) "adult" else "peer"), "who" to s.pn, "mode" to "voice", "text" to line)
        buttons()
        pause(2500)
        mark("partnerfirst")
        val follow = q.copy(
            text = "${s.childName}${eun(s.childName)} 어떻게 생각해?",
            kind = Kind.HARD,
            spoken = q.partnerChildAnswer.ifEmpty { q.spoken },
            partnerLine = null,
        )
        return ask(follow)
    }

    /** 기다림 → 쉬운 질문 → (힌트 질문 → 마스코트) 또는 (그림 카드 → 교체 3 → 마스코트) (⭐5 · ⭐22 · v0.8) */
    private suspend fun noAnswer(q: Question): Reply {
        s.modeSilent++
        feel(Mood.WAITING)
        mark("noanswer")
        event("utterance", "speaker" to "unsure", "mode" to "silent", "text" to "(무응답)")

        // 1) 쉬운 질문 · 2) 힌트 질문 — 둘 다 선택지를 늘어놓지 않는다
        // 일기 모드는 이 자리에 **사다리**가 들어온다: 답을 고르게 하지 않고 질문만 바꿔 다시 묻는다 (일기 설계 §4)
        val rungs = q.ladder.isNotEmpty()
        val steps = if (rungs) q.ladder
        else listOfNotNull(q.easierText, if (q.noCards || q.choices.isEmpty()) q.hint else null)
        for ((i, stepText) in steps.withIndex()) {
            currentQ = q.copy(text = stepText)
            askSay(q, stepText)
            inputs(mic = true, next = true, draw = q.drawAnswer != null)
            log(
                when {
                    rungs -> "무응답 → 사다리 ${i + 2}번째 칸으로 질문을 바꿔 다시 묻는다: \"$stepText\" (답을 고르게 하지 않는다 · 일기 설계 §4)"
                    i == 0 -> "무응답 → 쉬운 질문: \"$stepText\""
                    else -> "또 무응답 → 생각할 거리를 주는 질문: \"$stepText\" (선택지 대신 · 소크라틱)"
                }
            )
            val b = scriptButtons(q)
            b += DemoBtn("🤐 여전히 대답 없음") { send(Reply.Silent) }
            buttons(*b.toTypedArray())
            val r = waitReply(5.0)
            if (r is Reply.Tapped && r.value == "draw") return drawBranch(q)
            if (r is Reply.Spoke) {
                acceptSpoken(r.text)
                return r
            }
        }

        if (q.noCards || q.choices.isEmpty()) {
            val fb = q.fallback
            if (fb == null) {
                say("괜찮아, 다음에 같이 생각해 보자!")
                log("선택지가 없는 질문 → 넘어간다 (벌점 · 아쉬움 표현 없음)")
                pause(1200)
                return Reply.Silent
            }
            // `isDiary` — 협업도 일기 질문을 쓴다. `mode == DIARY` 로 보면 협업에서만 「혹시 ○○일까?」가 남아
            // 부모 앞에서 아이가 안 간 곳을 간 것처럼 말한다 (09-22 민우 f6ec7ff 검토)
            if (s.isDiary && q.id.startsWith("diary_")) {
                say("괜찮아. 지금은 생각나지 않아도 돼. 다음 이야기를 들어 볼게.")
                log("끝까지 말이 없음 → 모르는 부분만 책에 표시하고 다음 질문으로 (카드 없음 · source=mascot)")
            } else {
                say("혹시 ${fb.text}일까? 그렇게 해 볼게!")
                log("끝까지 말이 없음 → 마스코트가 \"혹시 ${fb.text}일까?\" 하고 채움 (카드 없음 · source=mascot)")
            }
            pause(1600)
            return Reply.Tapped(fb.value, fb.text, byMascot = true)
        }

        log(
            if (q.kind == Kind.CHOICE) "무응답 (확인 카드) → 다시 읽기 1회 → 최대 3번 교체 → 마스코트 (⭐5 · ⭐22)"
            else "쉬운 질문에도 무응답 → 그림 3장 (순서 섞음) · 최대 3번 교체 (구현대본 §5)"
        )

        var set = shuffled(q.choices)
        var round = 0
        var reread = q.kind == Kind.CHOICE // 확인 카드는 이미 떠 있었으므로 '다시 읽기'부터
        while (true) {
            currentQ = q
            inputs(mic = true, next = true, draw = q.drawAnswer != null)
            showCards(set, q.drawerHint)
            val names = set.joinToString(", ") { it.label }
            val tail = q.easierAsk ?: q.text
            askSay(q, if (reread) "$names${ga(set.last().label)} 있어. 다시 봐, $tail" else "$names${ga(set.last().label)} 있어. $tail")
            log(
                when {
                    reread -> "그림 선택지 · 다시 읽어줌 (1회)"
                    round == 0 -> "그림 선택지 3장 (순서 섞음)"
                    else -> "선택지 교체 ${round}회째"
                }
            )
            buttons(
                DemoBtn("🖐 첫 번째 그림을 탭") {
                    val c = (s.stage as? Stage.CardsRow)?.cards?.firstOrNull() ?: return@DemoBtn
                    send(Reply.Tapped(c.value, c.label))
                },
                DemoBtn("🤐 안 고름 (➡️와 같음)") { send(Reply.Silent) },
            )
            val r = waitReply(7.0)
            if (r is Reply.Tapped && r.value == "draw") return drawBranch(q)
            if (r is Reply.Tapped) {
                acceptTap(r)
                return r
            }
            if (r is Reply.Spoke) {
                acceptSpoken(r.text)
                return r
            }
            if (reread) {
                reread = false
                set = shuffled(q.choices)
            } else if (round < 3) {   // 최대 3번 교체 (⭐22 · 구현대본 §5)
                set = shuffled(q.choices)
                round++
            } else {
                val c = set.first()
                say("그럼 마스코트가 고를게! ${c.label}!")
                log("마스코트가 골라줌: ${c.label} (벌점 · 아쉬움 표현 없음)")
                s.stage = (s.stage as? Stage.CardsRow)?.copy(picked = c.value) ?: s.stage
                pause(1300)
                return Reply.Tapped(c.value, c.label, byMascot = true)
            }
        }
    }

    private fun showCards(set: List<Card>, drawerHint: Boolean) {
        s.stage = Stage.CardsRow(
            cards = set,
            drawerHint = drawerHint || s.persona == Persona.DRAWER,
        )
    }

    // ── 수준 신호 (역할 1) ───────────────────────────────────────

    fun signal(kind: String, evidence: String, note: String = "") {
        s.signals += "$kind — \"$evidence\"${if (note.isNotEmpty()) " · $note" else ""}"
        if (kind == "S1") s.s1count++
        event("signal", "S1" to (kind == "S1"), "S2" to (if (kind == "S2") note else null))
        log("신호 $kind — \"$evidence\"${if (note.isNotEmpty()) " · $note" else ""}")
    }

    fun quote(text: String) {
        if (text !in s.quotes) s.quotes += text
    }
}

/** 「쉿, 창문에서 뭔가 움직였어!」 처럼 놀라며 시작하는 말 — 대본마다 따로 표시하지 않고 말머리로 알아본다 */
private val surprise = Regex("""^(쉿|앗|헉|어라|어\?|깜짝)""")
