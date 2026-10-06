package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import com.example.finalproject_demo.net.nameMask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
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
    /** 첫 답을 기다리는 초 — null 이면 [kind] 의 기본(쉬운 5 · 어려운 8 · 고르기 7). 그림일기 D1 은 그리면서 답해서 길게 둔다 */
    val waitSec: Double? = null,
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
            recoverStoryImages()
            true
        } catch (_: Exception) { false }
    }

    fun savedStory(id: String): SavedStoryBook? = savedStories.firstOrNull { it.id == id }

    /** 꽂힌 동화 — 새 책이 앞 (#80 부모 책장 정리) */
    fun storyBooks(): List<SavedStoryBook> = savedStories.toList()

    /** 동화 권수. 저장 정보를 읽지 못하면 null — 0권(덮어써도 되는 빈 책장)으로 보지 않는다 (민우 #78) */
    fun storyBookCount(): Int? = runCatching { storyBookStore?.count() ?: savedStories.size }.getOrNull()

    /**
     * 동화 한 권 빼기 — 부모 모드에서 PIN · 「정말 뺄까요?」를 거친 뒤에만 (#80 · 민우 #78).
     * 저장소에서 먼저 지우고, 성공했을 때만 책장 · 그 책의 소리 · 아무 책도 안 쓰는 그림을 정리한다
     */
    fun deleteStoryBook(id: String): Boolean {
        if (s.scene != Scene.PARENT || savedStories.none { it.id == id }) return false
        return try {
            if (storyBookStore != null && !storyBookStore.delete(id)) return false
            savedStories.removeAll { it.id == id }
            s.shelf.removeAll { it.savedStoryId == id }
            runCatching { com.example.finalproject_demo.sound.ChildSound.deleteBook(id) }
            recoverStoryImages()
            true
        } catch (_: Exception) { false }
    }

    /**
     * 서버가 그려 준 그림(`story_images/`) 중 **아무 책도 · 지금 화면도 안 쓰는 것**만 지운다 (#61 · 민우 #78).
     * 같이 만들기 책도 같은 그림 폴더를 쓴다. 저장 정보를 하나라도 읽지 못하면 아무것도 지우지 않는다.
     * 앱을 켤 때는 세 책장을 다 붙인 **뒤에** 부른다(`MainActivity`) — 붙이기 전에 부르면 같이 만들기 책 그림이 지워진다
     */
    fun recoverStoryImages() {
        val store = storyImageStore ?: return
        val saved = storyBookStore?.let { runCatching { it.imageReferences() }.getOrNull() ?: return }
            ?: savedStories.flatMap { listOfNotNull(it.bgName, it.visuals?.hero?.image) }.toSet()
        val coop = CoopShelf.imageReferences(s) ?: return
        val active = listOfNotNull(s.storyBackground, s.storyHeroImage, s.coopGeneratedBackground) +
            s.heroes.mapNotNull { it.image } + s.shelf.map { it.bgName }
        store.recover(saved + coop + active)
    }

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
        // 마스코트가 말하는 중에 아이가 화면을 눌렀다 — 말을 끊고 그 입력으로 바로 넘어간다(10-02 조장).
        // 전에는 목소리가 끝날 때까지 기다린 뒤 [drain] 이 그 탭을 버려서 「눌러도 안 넘어간다」였다.
        // 「붓 멈춤」(DiaryViews)은 누른 게 아니라 그리기가 보낸 신호라 끊지 않는다
        if (r is Reply.Tapped && !r.byMascot && r.value != "pause") cutVoiceFor(r)
        input.trySend(r)
    }

    // 말 끊고 들어온 입력 — 곧바로 오는 [drain] 한 번은 이것을 버리지 않는다.
    // 짧게만 살린다: 한참 뒤의 drain 은 다른 화면이라 그때 살리면 앞 화면 탭이 다음 화면에 들어간다(#50 ④)
    private var cutIn: Reply? = null
    private var cutInAt = 0L
    private val CUT_IN_KEEP_MS = 1_500L

    private fun cutVoiceFor(r: Reply) {
        if (synchronized(voiceLines) { voiceLines.isEmpty() }) return
        hushVoice()
        cutIn = r
        cutInAt = System.currentTimeMillis()
    }

    /** 화면을 탭할 때까지 기다린다 (대기 타이머 없는 장면용). */
    suspend fun awaitReply(): Reply {
        drain()
        return input.receive()
    }

    /**
     * 앞 입력을 비운 **다음에** 화면을 띄우고 기다린다. 화면을 먼저 띄우고 [awaitReply] 를 부르면,
     * 뜨자마자 누른 탭이 그 사이 비워져 사라진다(10-02 · 이름 확인 「맞아」).
     */
    suspend fun awaitReplyShowing(show: () -> Unit): Reply {
        drain()
        show()
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

    /** [keepSpoken] — 들어온 말 하나는 남긴다. 이 질문에 마이크를 연 뒤 한 말이라 답이다(탭은 연달아 누른 것일 수 있어 버린다) */
    private fun drain(keepSpoken: Boolean = false) {
        val keep = cutIn?.takeIf { System.currentTimeMillis() - cutInAt < CUT_IN_KEEP_MS }
        cutIn = null
        var kept: Reply? = null
        while (true) {
            val r = input.tryReceive().getOrNull() ?: break      // 이전 장면의 입력 버리기
            if (kept == null && (r === keep || keepSpoken && r is Reply.Spoke)) kept = r
        }
        kept?.let { input.trySend(it) }                          // 말을 끊고 누른 것 · 마이크를 연 뒤 한 말만 이 화면의 답으로
    }

    /**
     * 장면 사이 쉬는 시간. 대본은 「이 정도면 말이 끝났겠지」로 ms 를 정해 두었다.
     * 서버 모드에서는 **진짜 목소리가 끝날 때까지** 먼저 기다리고, 남은 시간만 쉰다 (09-29 S25+) —
     * 전에는 화면이 목소리보다 앞서 달려가서 대사 세 개가 「후루룩」 넘어가고, 🎤 를 누를 때마다
     * 밀린 대사가 버려져 뒤로 갈수록 목소리가 안 들렸다.
     */
    suspend fun pause(ms: Long) {
        while (s.holding) delay(100)                  // ⏸ 동안 흐름은 그 자리에 선다 (#125)
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
        com.example.finalproject_demo.net.Trace.line(if (who == "마스코트") "otto" else "said:$who", text)
        if (who == "마스코트" && surprise.containsMatchIn(text)) feel(Mood.SURPRISED)
        if (who == "마스코트") { dumpSpoken(text); speakLive(text) }
    }

    /**
     * Writes each mascot line to the file named by `OTTO_SPEECH_DUMP`, only when it is set — the
     * test suite runs with it to list every line the app itself can say, to bake into audio once
     * (eval/bake_lines.py · 10-01: the app's own lines went to the voice vendor every time).
     * The app never has the variable, so this does nothing there.
     */
    private fun dumpSpoken(text: String) {
        val path = SPEECH_DUMP ?: return
        val line = s.nameMask().speakable(text)                  // what /tts gets
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

    // 줄 선 대사 전부의 부모 — 받아 오는 중인 소리까지 한 번에 끊는다 ([hushVoice]).
    // 10-01 #50: 전에는 맨 끝 대사만 취소해서, 앞에 줄 서 있던 대사가 다음 화면에서 늦게 나왔다
    private val voiceLines = mutableSetOf<Job>()

    private fun queueVoice(j: Job): Job {
        synchronized(voiceLines) { voiceLines += j }
        j.invokeOnCompletion { synchronized(voiceLines) { voiceLines -= j } }
        return j
    }

    /** Voices asked for ahead of time, by the exact spoken text ([prefetchSpeech]) */
    private val prefetched = java.util.concurrent.ConcurrentHashMap<String, Deferred<ByteArray?>>()

    /**
     * Start making a line's voice now, before it is said (10-05 trace). The question used to be voiced only
     * when said — after the ack had finished playing — so the child waited one more /tts (~2.5 s) every turn.
     * The voice is kept for the same text; a line that is never said costs one unused /tts.
     */
    fun prefetchSpeech(text: String) {
        if (!Server.liveFor(s.mode) || text.isBlank() || !Voice.canSpeak) return
        val line = s.nameMask().speakable(text)
        if (prefetched.size > 4) prefetched.clear()
        prefetched.getOrPut(line) { scope.async { Voice.baked(line) ?: Server.tts(line) } }
    }

    private fun speakLive(text: String) {
        // 소리를 낼 수 없으면(단위 테스트 — Voice 가 붙지 않았다) 목소리를 청하지도 않는다.
        // 들리지 않을 목소리 때문에 가짜 서버 주소로 대사마다 연결을 시도할 까닭이 없다
        if (!Server.liveFor(s.mode) || text.isBlank() || !Voice.canSpeak) return
        val line = s.nameMask().speakable(text)          // names read as they are (10-02 · ChildCall)
        // 앱에 구워 둔 대사면 그 소리를, 아니면 서버에 청한다 — 앞 대사를 읽는 동안 미리 받는다
        val audio = (prefetched.remove(line) ?: scope.async { Voice.baked(line) ?: Server.tts(line) }).also { queueVoice(it) }
        enqueue { audio.await() }
    }

    /** 방금 한 말을 다시 들려준다 — 아이가 오또 얼굴을 눌렀을 때(#56). 서버 모드가 아니면 아무것도 안 한다 */
    fun replayLine() = speakLive(s.line)

    /** 앞 대사가 끝난 뒤 [sound] 를 튼다 — 대사 줄의 맨 끝에 선다. null 이면 조용히 지나간다 */
    private fun enqueue(sound: suspend () -> ByteArray?) {
        val before = voiceJob
        voiceJob = queueVoice(scope.launch {
            before?.join()
            while (s.holding) delay(100)              // ⏸ 동안 받아 둔 대사는 [이어 하기] 뒤에 (#125)
            sound()?.let { play(it) }
        })
    }

    // ── 말 사이 숨 (10-02 조장 실기기) ─────────────────────────────────
    //
    // 구운 소리는 기다림이 0 이라 대사가 「다다다」 붙어 나왔고, 말 끝 리액션은 너무 일찍 끼어들었다.
    // 사람 대화의 차례 넘김은 보통 0.2초 안팎이지만, 어린아이에게 말하는 어른은 더 천천히 말하고 더 쉰다.
    // 아래 두 값은 그 사이에서 조장 귀(10-02)로 맞춘 출발점이다 — 「느리다/빠르다」 말이 나오면 이 두 줄만 고친다.

    /** 마스코트 대사와 대사 사이 최소 쉼. 앞 대사가 끝난 시각부터 잰다 — 서버 대사는 이미 늦게 오므로 보통 안 기다린다 */
    private val LINE_GAP_MS = 400L

    /**
     * 아이 말이 끝나고 리액션까지. VAD 가 이미 0.5초 침묵을 듣고 끊으므로(`Voice.vad`) 여기 0.8초를 더하면
     * 아이가 말을 멈춘 뒤 **약 1.3초**에 리액션이 나온다(10-02 조장: 1초도 「조금 빠르다」).
     * 받아쓰기와 겹쳐 흐른다. 리액션이 3~5초로 길어서(10-02) 질문이 빨리 오면 리액션이 끝날 때까지 기다린다.
     */
    private val NEUTRAL_DELAY_MS = 800L

    @Volatile private var lastVoiceEnd = 0L

    private suspend fun play(audio: ByteArray) {
        val wait = LINE_GAP_MS - (System.currentTimeMillis() - lastVoiceEnd)
        if (wait > 0) delay(wait)
        try { Voice.playAndWait(audio) } finally { lastVoiceEnd = System.currentTimeMillis() }
    }

    /**
     * 아이 말이 끝나고 약 1.3초 뒤([NEUTRAL_DELAY_MS]) 폰에 든 중립 소리(「음~」 「응응.」 「응, 그랬구나.」)를 낸다 — 리액션 1단계(10-01).
     * 받아쓰기 + 판정 + 목소리(공개 주소로 5~6초)를 기다리는 동안 마스코트가 듣고 있다는 걸 알린다.
     * 아직 아이 말을 모르므로 감정 · 칭찬 · 질문이 없다. 말풍선은 바꾸지 않고, 세지도 않는다(마스코트 말).
     * 뒤에 오는 대사는 이 소리 뒤에 줄을 선다.
     */
    private fun speakNeutral() {
        if (!Server.liveFor(s.mode) || !Voice.canSpeak) return
        val clip = Voice.neutral() ?: return
        val heardAt = System.currentTimeMillis()
        enqueue {
            val wait = NEUTRAL_DELAY_MS - (System.currentTimeMillis() - heardAt)
            if (wait > 0) delay(wait)
            clip
        }
    }

    /** 마스코트가 하던 말을 끝낼 때까지 기다린다 — 서버 모드가 아니면 바로 돌아온다 */
    suspend fun awaitVoice() {
        voiceJob?.join()
    }

    /**
     * 목소리를 지금 멈추고 줄 선 대사 · 받아 오던 소리도 버린다.
     * 🎤 가 눌렸다(마스코트 소리가 녹음에 섞이면 안 된다) · 아이가 선택 버튼을 눌렀다([awaitChoice]).
     */
    private fun hushVoice() {
        synchronized(voiceLines) { voiceLines.toList() }.forEach { it.cancel() }
        voiceJob = null
        Voice.stopPlaying()
    }

    // ── 선택 구간 (10-01 #50 · 민우 S25) ─────────────────────────────────
    //
    // 「안녕」 · 「또 만날래」처럼 아이가 고르는 구간은 마스코트가 말하는 중에도 바로 받는다.
    // 일반 질문은 그대로다 — 목소리가 끝나야 아이 차례가 온다([pause] · [ask]).
    // 쓰는 법: 장면에 들어올 때 한 번 [awaitChoice], 고른 뒤 쉬는 자리는 [pauseOrChoice].
    //   var next: Reply? = null
    //   while (true) {
    //       val r = next ?: awaitChoice(); next = null
    //       … 결과를 한 번 적용 · 화면을 먼저 바꾸고 say(…) …
    //       next = pauseOrChoice(1100)
    //   }

    /**
     * 선택을 기다린다. 들어오기 전 화면의 입력은 버리고, **목소리가 나오는 중에도** 받는다.
     * 받으면 지금 목소리와 줄 선 대사를 끊는다 — 늦게 받아 둔 소리가 다음 화면에서 나오지 않는다.
     */
    suspend fun awaitChoice(): Reply {
        drain()
        return input.receive().also { hushVoice() }
    }

    /**
     * [pause] 와 같지만 그 사이 아이가 고르면 **바로** 그 입력을 돌려준다(버리지 않는다) — 목소리는 끊는다.
     * 아무것도 안 고르면 null. 쉬는 동안 쌓인 입력은 [awaitChoice] 처럼 버리지 않고 첫 것을 쓴다.
     */
    suspend fun pauseOrChoice(ms: Long): Reply? = coroutineScope {
        val resting = async { pause(ms) }
        select<Reply?> {
            input.onReceive { resting.cancel(); hushVoice(); it }
            resting.onAwait { null }
        }
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
        com.example.finalproject_demo.net.Trace.line("log", t)
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
            log("🎤 켬 — 듣는 중 (대본 모드: 한 번 더 누르면 끝 · 서버 모드는 말이 끝나면 저절로 끊는다)")
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
    // 🎤 누름 → 녹음 → 말이 끝나면 VAD 가 0.5초 뒤 스스로 끊는다(⏹ 로 먼저 끊어도 된다)
    // → 우리 서버 `/stt` → 들은 글자를 대본 답과 **같은 모양**(`Reply.Spoke`)으로 흐름에 넣는다.
    // 그래서 장면 코드는 대본인지 진짜인지 모른다. 글자는 **실명 그대로**다 — 서버로 다시
    // 보낼 때는 모드 담당자가 `s.nameMask().mask(...)` 를 거친다(규칙 6).
    // 아무것도 못 들었거나(빈 글자) 서버가 실패하면 **무응답**으로 보낸다 — 무응답 흐름(⭐5)이 이어받는다.

    @Volatile private var stopMic = false
    private var micJob: Job? = null

    private fun liveMic() {
        if (s.micOn) { stopMic = true; return }            // ⏹ — 녹음을 여기서 끊는다
        stopMic = false
        unheardWait?.cancel()                             // 되물은 뒤 다시 말하러 왔다
        hushVoice()                                       // 마스코트 소리가 녹음에 들어가지 않게
        s.micOn = true
        s.countdown = null
        log("🎤 켬 — 진짜 녹음 · 말이 끝나면 저절로 끊는다 (VAD 0.5초)")
        micJob = scope.launch {
            val audio = Voice.listen { stopMic }
            s.micOn = false
            if (audio == null) { log("🎤 아무것도 못 들음 → 무응답"); send(Reply.Silent); return@launch }
            speakNeutral()
            log("🎤 끝 → 우리 서버로 받아쓰기 (${audio.size / 1024}KB)")
            val text = Voice.transcribe(audio)
            when {
                text == null || text.isBlank() -> unheard(if (text == null) "받아쓰기 실패" else "들을 말이 없음")
                else -> {
                    unheardStreak = 0
                    val fixed = fixKnownNames(text, s.knownNames())
                    log(if (fixed == text) "받아쓰기: \"$text\"" else "받아쓰기: \"$text\" → 정한 이름으로 \"$fixed\"")
                    send(Reply.Spoke(fixed))
                }
            }
        }
    }

    // ── 말했는데 못 알아들음 (10-02 조장) ───────────────────────────────
    //
    // 녹음에서 말소리는 들렸는데 받아쓰기가 비었거나 실패했다 — 아이가 대답을 안 한 게 아니다.
    // 전에는 이것도 무응답으로 보내서, 받아쓰기가 두 번 틀리면 대답한 아이 앞에 카드가 떴다(#79).
    // 이제는 같은 질문을 그대로 두고 「한 번 더 말해 줄래?」로 되묻는다 — 사다리를 내려가지 않는다.
    // 두 번 연달아 못 알아들으면 그때는 무응답으로 넘긴다(끝없이 되묻지 않는다 · 아이가 지칠 수 있다).
    // 되물은 뒤 아이가 아무것도 안 하면 [UNHEARD_WAIT_MS] 뒤 무응답으로 넘긴다 — 질문이 멈춰 서지 않게.

    private val UNHEARD_RETRIES = 2
    private val UNHEARD_WAIT_MS = 8_000L
    private var unheardStreak = 0
    private var unheardFor: Question? = null
    private var unheardWait: Job? = null

    private fun unheard(why: String) {
        if (unheardFor !== currentQ) { unheardFor = currentQ; unheardStreak = 0 }
        unheardStreak++
        if (unheardStreak > UNHEARD_RETRIES) {
            log("$why — ${UNHEARD_RETRIES}번 되물어도 못 알아들음 → 무응답으로 넘김")
            unheardStreak = 0
            send(Reply.Silent)
            return
        }
        log("$why — 말소리는 들렸다 → 같은 질문으로 되묻기 ($unheardStreak/$UNHEARD_RETRIES)")
        hushVoice()                                       // 「잘 들었어」 리액션이 아직 나오고 있으면 끊는다
        say(if (unheardStreak == 1) "어? 소리가 작았나 봐. 한 번 더 말해 줄래?" else "미안, 또 못 들었어. 천천히 한 번만 더 말해 줄래?")
        val asked = currentQ
        unheardWait?.cancel()
        unheardWait = scope.launch {
            delay((UNHEARD_WAIT_MS * s.speed).toLong())
            // 그 사이 다시 말했거나(마이크) 다른 질문으로 넘어갔으면 아무것도 하지 않는다
            if (!s.micOn && currentQ === asked && unheardFor === asked) send(Reply.Silent)
        }
    }

    /** ➡️ 말 없이 넘김 = 무응답 */
    fun skip() {
        if (!s.nextEnabled) return
        s.micOn = false
        cutVoiceFor(Reply.Silent)                     // ➡️ 도 말하는 중이면 끊고 넘어간다(10-02)
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
        s.holding = false
        pauseStory()
        goHome()
    }

    /**
     * ⏸ 일시정지 (#125 · 10-05 조장) — 이야기 중의 ⏸ 를 누르거나 화면이 꺼지면(앱이 뒤로 가면).
     * 오또 목소리를 끊고, 녹음 중이면 그 녹음은 버린다(보내지 않는다). 흐름은 다음 쉼 · 아이 차례에서 선다.
     * 앱이 살아 있는 동안만 이어진다 — 안드로이드가 앱을 내리면 「이어하기」(체크포인트)가 따로 필요하다
     */
    fun holdSession() {
        if (s.holding) return
        s.holding = true
        hushVoice()
        if (s.micOn) { stopMic = true; micJob?.cancel(); s.micOn = false }
        log("⏸ 일시정지 — 목소리 · 녹음을 멈추고 흐름을 세운다")
    }

    /** ▶ 이어 하기 — 멈출 때 말하던 대사를 다시 들려준다(끊겼으니) */
    fun resumeSession() {
        if (!s.holding) return
        s.holding = false
        log("▶ 이어 하기")
        replayLine()
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
    private suspend fun waitReply(sec: Double, keepSpoken: Boolean = false): Reply? {
        drain(keepSpoken)
        if (!s.timerOn) return input.receive()
        var left = sec
        s.countdown = left
        while (left > 0.0) {
            if (s.holding) { delay(100); continue }   // ⏸ 동안은 세지 않는다 (#125)
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
    suspend fun ask(q: Question, silentFollowUp: Boolean = false): Reply {
        currentQ = q
        askSay(q, q.text)
        // 앞 장면의 입력은 마이크를 열기 **전에** 버린다 — 연 뒤에 한 말은 이 질문의 답이라 목소리가 끝난 뒤에도 남긴다.
        // 목소리가 끝난 뒤에 비우면 질문을 보고 먼저 한 답이 사라졌다(10-05 실기기 · /tts 11.6초)
        drain()
        inputs(mic = true, next = true, draw = q.drawAnswer != null)

        val scripted = scriptButtons(q)
        q.partnerLine?.let {
            scripted += DemoBtn("${s.partner.emoji} ${s.pn}만 말함 — \"$it\"") { send(Reply.PartnerOnly) }
        }
        scripted += DemoBtn("🤐 대답 없음 (➡️와 같음)") { send(Reply.Silent) }

        if (q.kind == Kind.CHOICE) {
            showCards(q.choices.shuffled(), q.drawerHint)
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
        val sec = q.waitSec ?: when (q.kind) { Kind.EASY -> 5.0; Kind.HARD -> 8.0; Kind.CHOICE -> 7.0 }
        val first = waitReply(sec, keepSpoken = true)
        // 되돌리기 · 앞으로 가기는 답이 아니다 — 흐름(TurnHistory)이 받도록 그대로 돌려준다 (10-02)
        if (first != null && TurnHistory.isNav(first)) {
            currentQ = null
            inputs(mic = false, next = false)
            return first
        }

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
            else -> noAnswer(q, silentFollowUp)
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
        // Live: not pause() — it waits for the mascot's voice first, and the voice playing now is the neutral
        // reaction (3~5 s sentences). The /turn request sat behind it: the 10-05 trace's 3.5~5 s gap between the
        // transcript and the request. The reaction keeps playing while the server works.
        if (com.example.finalproject_demo.net.Server.liveFor(s.mode)) delay((150 * s.speed).toLong()) else pause(900)
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
    private suspend fun noAnswer(q: Question, silentFollowUp: Boolean): Reply {
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
                if (silentFollowUp) return Reply.Silent
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

        var set = q.choices.shuffled()
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
                set = q.choices.shuffled()
            } else if (round < 3) {   // 최대 3번 교체 (⭐22 · 구현대본 §5)
                set = q.choices.shuffled()
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
