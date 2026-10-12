package com.example.finalproject_demo.demo.missions

import com.example.finalproject_demo.demo.DemoState

/**
 * 미션 자리 2(해결 쪽)의 소품이 책에 남기는 말 — A1 물대포 · A4 돌려 잠그기가 같이 쓴다(`Missions.kt` 가 이것만 본다).
 * 건네주기(E1) · 퍼즐(A3)은 원래 문장 그대로다
 */
interface Slot2Prop {
    /** 미션 전 책 문장 — 「수도꼭지에서 물이 졸졸 새고 있어요.」 */
    val before: String
    /** 아이가 한 일 — 해결 쪽 문장의 뒷절 「수도꼭지를 빙글빙글 돌려 꽉 잠갔어요.」 */
    val did: String
    /** 미션 뒤 책 문장 — 「물이 딱 멈췄어요.」 */
    val result: String
    /** 마스코트 안내 */
    val ask: String
    /** 다 했을 때 마스코트 말 */
    val cheer: String
}

/**
 * 자리 2 미션 — 아이가 말한 **해결**이 그 동작이면 그것을 손으로 해 본다(`docs/맞춤미션_설계.md` §4 · §6-1 · #101 셋째 순서).
 * 소품은 아이 말에서만(불 · 수도꼭지) — 실제 일에도 지어낸 소품이 들어가지 않는다(§3-8)
 */
enum class FixProp(
    val mission: MissionId,
    val art: String,
    val emoji: String,
    override val before: String,
    override val did: String,
    override val result: String,
    override val ask: String,
    override val cheer: String,
) : Slot2Prop {
    /** A1 물대포 — 불 셋을 손가락으로 꾹 겨누면 호스에서 물줄기가 날아가 꺼진다 */
    FIRE(MissionId.A1, "prop_fire", "🔥", "불이 활활 타오르고 있어요.", "물대포로 치익~ 불을 다 껐어요.", "불이 다 꺼졌어요.",
        "물대포로 불을 꺼 볼래? 불을 손가락으로 꾹 눌러 봐!", "치이익~ 불이 다 꺼졌어!"),
    /** A4 돌려 잠그기 — 수도꼭지 손잡이를 빙글빙글 돌리면 물이 멈춘다 */
    FAUCET(MissionId.A4, "prop_faucet", "🚰", "수도꼭지에서 물이 졸졸 새고 있어요.", "수도꼭지를 빙글빙글 돌려 꽉 잠갔어요.", "물이 딱 멈췄어요.",
        "수도꼭지를 빙글빙글 돌려서 잠가 볼래?", "꽉! 물이 딱 멈췄어!"),
    /** D4 기울여 굴리기 — 폰을 살살 기울여 공을 골대로. 센서가 없으면 끌어서 */
    BALL(MissionId.D4, "coop_el_soccer", "⚽", "공이 골대에서 멀리 떨어져 있어요.", "공을 데굴데굴 굴려 골대에 넣었어요.", "공이 골대에 쏙 들어갔어요.",
        "폰을 살살 기울여서 공을 골대로 굴려 볼래? 손으로 끌어도 돼!", "데굴데굴 쏙! 골인!"),
    /**
     * E2 고쳐 주기 — 떨어져 나간 조각을 끌어다 제자리에 맞춘다. 무엇이 부서졌는지는 아이 말에 맡기고
     * 화면 · 문장은 「조각」으로만 말한다 — 실제 일에 아이가 말하지 않은 물건을 지어내지 않는다(§3-8)
     */
    PIECES(MissionId.E2, "", "🧩", "조각이 떨어져 나가 있어요.", "떨어진 조각을 제자리에 꼭 맞췄어요.", "부서진 곳이 감쪽같이 고쳐졌어요.",
        "떨어진 조각을 끌어서 제자리에 맞춰 줄래?", "딱! 감쪽같이 고쳐졌어!"),
    /** A5 쌓기 — 흩어진 블록을 위로 끌어 올려 탑을 쌓는다 */
    BLOCKS(MissionId.A5, "", "🧱", "블록이 바닥에 흩어져 있어요.", "블록을 하나씩 다시 쌓았어요.", "블록 탑이 높이 섰어요.",
        "블록을 하나씩 끌어 올려서 탑을 쌓아 볼래?", "우와, 높은 탑이 됐어!"),
    ;

    companion object

    /** 부모 화면 「받은 선물」 — 건네주기의 「○○ 건넨 손」 자리. 물대포로 불을 껐는데 「별 건넨 손」이 남았다(10-06 실기기) */
    val badge: String get() = when (this) {
        FIRE -> "불 끈 물대포"
        FAUCET -> "수도꼭지 잠근 손"
        BALL -> "공 굴린 손"
        PIECES -> "조각 맞춘 손"
        BLOCKS -> "블록 쌓은 손"
    }
}

/**
 * 아이 말에서 자리 2 소품을 찾는다 — 없으면 null(지금처럼 건네주기 · 퍼즐).
 * **해결 동사가 먼저**(설계 §6-1 「껐어」가 「불」보다 세다) — 해결 · 먼저 한 일에서 동사를 찾고, 없을 때 문제 칸의 사물을 본다
 */
fun fixPropIn(solution: String, problem: String): FixProp? = when {
    // Words match only at the start of an eojeol (#259 · MissionWords.kt) — 「미끄럼틀」's 「끄」 no longer matches, but 「끄」
    // alone would still match 「끄덕였어」, so only with fire (「불을 끄 · 꺼 줬」) or 「껐」
    saysAny(solution, listOf("껐", "불을 끄", "불 끄", "불을 꺼", "물을 뿌", "물 뿌", "물대포", "소방")) -> FixProp.FIRE
    saysAny(solution, listOf("잠갔", "잠궜", "잠가", "잠그", "잠궈", "수도꼭지", "꼭지")) -> FixProp.FAUCET
    saysAny(solution, listOf("굴렸", "굴려", "데굴", "골인", "공을 넣", "공 넣", "골을 넣", "공을 찼", "공 찼", "공놀이", "축구")) -> FixProp.BALL
    saysAny(solution, listOf("고쳤", "고쳐", "붙였", "테이프", "맞췄", "꿰맸")) -> FixProp.PIECES
    saysAny(solution, listOf("쌓았", "쌓아", "다시 쌓", "탑을")) -> FixProp.BLOCKS
    // 「연기를 했어」 (acting) is not fire — only smoke that rose (#259 design §4-1 false positive)
    saysAny(problem, listOf("불이 났", "불났", "불이 붙", "연기가", "연기 나", "불이 나")) -> FixProp.FIRE
    // 「기쁨이 넘쳤어」 is not water — only water that overflowed (#259 design §4-1 false positive)
    saysAny(problem, listOf("샜", "새서", "새고", "물이 새", "물이 넘", "물이 졸졸", "물이 콸콸", "수도꼭지")) -> FixProp.FAUCET
    saysAny(problem, listOf("공이 굴러", "공이 데굴", "공을 놓쳤", "공이 멀리", "공이 날아")) -> FixProp.BALL
    saysAny(problem, listOf("무너", "와르르", "넘어뜨")) -> FixProp.BLOCKS
    saysAny(problem, listOf("부서", "망가", "깨졌", "깨져", "고장", "찢어", "부러")) -> FixProp.PIECES
    else -> null
}

/** That mission's prop — the screen and ask of a rotated slot 2 use it (#259). Giving · puzzle have no prop */
fun FixProp.Companion.forMission(m: MissionId): FixProp? = FixProp.entries.firstOrNull { it.mission == m }

/**
 * This book's slot 2 prop — **what the book's sentences use**. Picked from the child's words, or borrowed by rotation in
 * an imagined story (#259 · an imagined story may borrow · mission design §7-2). Null for a rotated mission on a real day —
 * no thing the child did not say goes into the book (§3-8). The screen and ask use [slot2PlayProp]
 */
fun DemoState.slot2Prop(): FixProp? {
    pinnedMissions()?.let { return it.fix.takeIf { _ -> it.fixInBook } }     // a re-read book (#321 review)
    val f = storyFacts()
    val m = missions()
    return fixPropIn(f.slot2Words, f.slot1Words)?.takeIf { it.mission == m.slot2 }
        ?: if (!f.realDay && !m.slot2FromChild) FixProp.forMission(m.slot2) else null
}

/** The prop for slot 2's screen · Otto's ask · the badge — a rotated mission on a real day too (the screen has the blocks · ball) */
fun DemoState.slot2PlayProp(): FixProp? = pinnedMissions()?.fix ?: slot2Prop() ?: FixProp.forMission(missions().slot2)
