package com.example.finalproject_demo.demo

/**
 * 되돌리기 · 앞으로 가기 (10-02 조장 · #87) — 잘못 알아들은 답을 아이가 무를 수 있게.
 *
 * 칸 하나를 `null` 로 돌리지 않는다. 한 답이 칸 둘을 채우거나(다중 채움) 다른 칸을 필요 없게 하거나
 * (`no_longer_needed`) 다음 질문 · 수준 · 배경 요청을 바꾸기 때문에 **직전 차례를 통째로** 되돌린다.
 * 되돌린 답은 주고받기 횟수 · 판단 기록 · 말한 방식 수(부모 리포트 재료)에서도 빠진다(규칙 5).
 *
 * 쓰는 곳은 답 하나를 받는 모드 흐름이다: 차례를 시작할 때 [TurnHistory.before] 로 찍고,
 * 답이 반영되면 [TurnHistory.done], 버튼이 오면 [TurnHistory.undo] · [TurnHistory.redo].
 * 버튼은 `Reply.Tapped(UNDO)` · `Reply.Tapped(REDO)` 로 온다 — `Director.ask` 가 답으로 세지 않고 그대로 돌려준다.
 */
class TurnState internal constructor(
    private val slots: Map<String, String>,
    private val slotBy: Map<String, String>,
    private val unneeded: List<String>,
    private val nextSlot: String?, private val serverQuestion: String?, private val clarification: String?,
    private val endReason: String?, private val turn: Int, private val noAnswerStreak: Int,
    private val level: Level, private val notes: Int, private val feelings: Int, private val templateKey: String?,
    private val mascotPicks: Int, private val reactions: Int,
    private val modeCard: Int, private val modeVoice: Int, private val modeDraw: Int, private val modeSilent: Int,
    private val themeKey: String, private val placeLabel: String?, private val generatedBg: Boolean,
    private val place: String?, private val problem: String?, private val cause: String?, private val reaction: String?,
    private val causeLine: String, private val causeKind: String,
    private val newcomer: String?, private val newcomerKind: String, private val friendName: String,
    private val solution: String?, private val solutionLine: String, private val solutionKey: String, private val solutionItem: String,
    private val sound: String?, private val soundLine: String, private val storyBackground: String?,
    private val sceneKit: String?, private val sceneSeed: Long,
    private val answerOptions: StoryOptions?,
) {
    internal fun restoreInto(s: DemoState) {
        // 배경은 그림이 늦게 온다 — 차례를 찍은 뒤에 도착한 생성 배경을, 같은 장소로 되돌릴 때 지우지 않는다.
        // 10-02 실기기: 되돌리기 하면 생성 배경 대신 프리셋이 떴다. 장소가 바뀔 때만 그때 배경으로 돌린다
        val keepBackground = s.place == place
        s.slots.clear(); s.slots.putAll(slots)
        s.slotBy.clear(); s.slotBy.putAll(slotBy)
        s.storyUnneededSlots.clear(); s.storyUnneededSlots.addAll(unneeded)
        s.storyNextSlot = nextSlot; s.storyServerQuestion = serverQuestion; s.storyClarificationSlot = clarification
        s.storyAnswerOptions = answerOptions
        s.endReason = endReason; s.turn = turn; s.noAnswerStreak = noAnswerStreak; s.level = level
        while (s.notes.size > notes) s.notes.removeAt(s.notes.size - 1)
        while (s.feelings.size > feelings) s.feelings.removeAt(s.feelings.size - 1)
        s.templateKey = templateKey; s.mascotPicks = mascotPicks; s.reactions = reactions
        s.modeCard = modeCard; s.modeVoice = modeVoice; s.modeDraw = modeDraw; s.modeSilent = modeSilent
        s.themeKey = themeKey; s.placeLabel = placeLabel; s.generatedBg = generatedBg
        s.place = place; s.problem = problem; s.cause = cause; s.reaction = reaction
        s.causeLine = causeLine; s.causeKind = causeKind
        s.newcomer = newcomer; s.newcomerKind = newcomerKind; s.friendName = friendName
        s.solution = solution; s.solutionLine = solutionLine; s.solutionKey = solutionKey; s.solutionItem = solutionItem
        s.sound = sound; s.soundLine = soundLine
        if (!keepBackground) s.storyBackground = storyBackground
        // the kit scene follows the place it was laid out for — same seed, same scene after undo/redo
        s.sceneKit = sceneKit; s.sceneSeed = sceneSeed
    }
}

fun DemoState.captureTurn(): TurnState = TurnState(
    slots.toMap(), slotBy.toMap(), storyUnneededSlots.toList(),
    storyNextSlot, storyServerQuestion, storyClarificationSlot,
    endReason, turn, noAnswerStreak, level, notes.size, feelings.size, templateKey,
    mascotPicks, reactions, modeCard, modeVoice, modeDraw, modeSilent,
    themeKey, placeLabel, generatedBg, place, problem, cause, reaction, causeLine, causeKind,
    newcomer, newcomerKind, friendName, solution, solutionLine, solutionKey, solutionItem,
    sound, soundLine, storyBackground, sceneKit, sceneSeed,
    storyAnswerOptions,
)

/** 한 이야기의 차례 기록 — 되돌리기 줄과 앞으로 가기 줄 */
class TurnHistory(private val s: DemoState) {
    private val back = ArrayDeque<TurnState>()
    private val forward = ArrayDeque<TurnState>()
    private var pending: TurnState? = null

    val canUndo: Boolean get() = back.isNotEmpty()
    val canRedo: Boolean get() = forward.isNotEmpty()

    /** 이 차례를 시작하기 직전 — 답이 반영되면 이 상태로 돌아갈 수 있다 */
    fun before() { pending = s.captureTurn() }

    /** 답이 반영됐다 — 되돌리기 줄에 올리고, 새 길을 갔으니 앞으로 가기 줄은 비운다 */
    fun done() {
        pending?.let { back.addLast(it) }
        pending = null
        forward.clear()
        publish()
    }

    /** 직전 차례 전으로. 지금 상태는 앞으로 가기 줄로 */
    fun undo(): Boolean {
        val to = back.removeLastOrNull() ?: return false
        forward.addLast(s.captureTurn())
        to.restoreInto(s)
        publish()
        return true
    }

    /** 되돌린 차례를 다시 */
    fun redo(): Boolean {
        val to = forward.removeLastOrNull() ?: return false
        back.addLast(s.captureTurn())
        to.restoreInto(s)
        publish()
        return true
    }

    private fun publish() { s.canUndo = canUndo; s.canRedo = canRedo }

    companion object {
        const val UNDO = "nav:undo"
        const val REDO = "nav:redo"
        fun isNav(r: Reply) = r is Reply.Tapped && (r.value == UNDO || r.value == REDO)
    }
}
