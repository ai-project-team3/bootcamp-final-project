package com.example.finalproject_demo.demo


/*
 * 이야기 엔진 (v0.9) — 질문 은행 · 더미 답 · 발달 판단 · 수준별 동화 템플릿.
 *
 * 근거
 *  - 역할1 요약 · 조사2 §3~4 : 수준 3단계 · 중심 신호 S1/S2 · 보조 A1/A2 · 마음 말하기는 신호 아님
 *                               · 처음 2턴으로 다시 판단 · 2턴 연속이면 한 칸 · 3턴째 템플릿 확정
 *  - 역할1 조사2 표(역할2 문서와 부딪히는 곳 #1) : 고르며=E 여정형 · 이어=C 위험-대응-도움형 / D 방문자-직업수행형
 *                                                · 까닭=A 도전-성취형 / G 우화-교훈형
 *  - 역할2 조사 : 뼈대 7개 · 소크라틱 질문 예시 · 속성 축(교훈 · 체험 · 상황 이해 · 직업 체험)
 *  - 사용자 요청(9/17) : 책은 6~8쪽 · 처음 고정 질문 뒤로는 매번 다른 질문과 답 · 책이 매번 다르게
 *
 * 데모에는 진짜 아이가 없으므로 답은 더미다. 답마다 "어느 수준 아이의 답처럼 보이는가(lv)"와
 * 신호 표시(reason · el · con)를 붙여 두었고, 시연 서랍의 [아이 수준 흉내]로 그 수준의 답이 더 자주 나오게 할 수 있다.
 */

// ── 이름 줄임 ──────────────────────────────────────────────────
private val DemoState.c get() = childName
private val DemoState.v get() = th.vehicle
private val DemoState.p get() = placeName
private val DemoState.nk get() = newcomerKind
private val DemoState.f get() = friendName
private val DemoState.d get() = dino.name
private fun DemoState.slot(k: String, def: String = "") = slots[k] ?: def

/** 지나가는 곳 · 도착하는 곳 — 새로 만든 눈 배경이면 눈 나라 것으로 (공룡 나라 뼈대를 빌려도 장소 말은 눈에 맞춘다) */
private val DemoState.stops: List<String> get() = if (generatedBg) listOf("하얀 눈꽃 다리", "폭신폭신 눈밭", "반짝반짝 얼음 호수") else th.stops
private val DemoState.goals: List<String> get() = if (generatedBg) listOf("눈사람 마을", "오로라 언덕") else th.goals

/** "외계인" → "외계인이었다면" · "문어" → "문어였다면" */
private fun wasIf(w: String) = if (bat(w)) "${w}이었다면" else "${w}였다면"

/** 함께 하는 사람 말투 — 높임 */
private fun DemoState.pSubj() = if (partner.honor) "${pn}께서" else "$pn${ga(pn)}"
private fun DemoState.pTop() = if (partner.honor) "${pn}께서는" else "$pn${eun(pn)}"

// ── 질문 은행 ──────────────────────────────────────────────────

/**
 * 질문 하나의 변형. 같은 slot에 여러 변형이 있고, 지금 수준(levels)에 맞는 것 중 무작위로 고른다.
 * 지난 이야기에서 쓴 변형은 되도록 피한다 → 매번 다른 질문이 나온다.
 */
class QVariant(
    val id: String,
    val slot: String,
    val levels: Set<Level>,
    val kind: Kind,
    /** 이 질문이 무엇을 보려는 것인가 (시연 서랍 · 판단 표) */
    val probe: String,
    /** 이야기 턴으로 세는가 (수준 이동 · 8턴 상한) */
    val counts: Boolean = true,
    /** 마음 질문 — 수준 신호로 쓰지 않는다 */
    val emotion: Boolean = false,
    val text: (DemoState) -> String,
    val easier: (DemoState) -> String,
    val hint: ((DemoState) -> String)? = null,
    val answers: (DemoState) -> List<Answer>,
    val cards: ((DemoState) -> List<Card>)? = null,
    val fallback: ((DemoState) -> Answer)? = null,
)

private val ALL = Level.entries.toSet()
private val MID_UP = setOf(Level.CHAIN, Level.REASON)
private val LOW_MID = setOf(Level.PICK, Level.CHAIN)

/** 수준마다 골고루 남기고 섞는다 (한 질문에 1~3 수준 답이 다 있게) */
private fun List<Answer>.shuffledKeepSpread(n: Int = 6): List<Answer> {
    val queues = groupBy { it.lv }.values.map { it.shuffled().toMutableList() }
    val out = mutableListOf<Answer>()
    while (out.size < n && queues.any { it.isNotEmpty() }) {
        queues.forEach { q -> if (q.isNotEmpty() && out.size < n) out += q.removeAt(0) }
    }
    return out.shuffled()
}

private val NAMES = listOf(
    Answer("뿌뿌!", "뿌뿌", lv = 1), Answer("삐삐!", "삐삐", lv = 1), Answer("콩이!", "콩이", lv = 1),
    Answer("초코! 초콜릿 색이니까.", "초코", reason = true, lv = 2), Answer("별이라고 할래.", "별이", lv = 2),
    Answer("보리! 우리 강아지 이름이야.", "보리", el = setOf("배경"), lv = 2),
    Answer("몽몽이! 몽글몽글하게 생겨서.", "몽몽이", reason = true, con = true, lv = 3),
    Answer("달콩이! 달처럼 동그랗고 콩처럼 작아서.", "달콩이", reason = true, con = true, lv = 3),
    Answer("뭉치! 털이 뭉쳐 있어서 그렇게 불러.", "뭉치", reason = true, con = true, lv = 3),
)

private fun causeAnswers(s: DemoState): List<Answer> = listOf(
    Answer("몰라.", "", kind = "lonely", lv = 1),
    Answer("인사!", "인사하고 싶었어", reason = true, kind = "hello", lv = 1),
    Answer("배고파서.", "배가 고팠어", reason = true, kind = "hungry", lv = 1),
    Answer("같이 놀고 싶어서!", "같이 놀고 싶었어", reason = true, kind = "play", lv = 2),
    Answer("장난치고 싶어서!", "장난치고 싶었어", reason = true, kind = "prank", lv = 2),
    Answer("발이 아파서 넘어진 거야.", "발이 아팠어", reason = true, kind = "hurt", lv = 2),
    Answer("심심해서! 친구가 없어서.", "친구가 없어서 심심했어", reason = true, el = setOf("계기"), kind = "lonely", lv = 3),
    Answer("길을 잃어서! 집을 못 찾아서 속상했어.", "길을 잃어서 속상했어", reason = true, el = setOf("계기"), emo = "속상했", kind = "lost", lv = 3),
    Answer("자기가 제일 힘세다고 뽐내려고. 그래서 세게 흔들었어.", "내가 제일 힘센 걸 보여 주고 싶었어", reason = true, el = setOf("계기", "결과"), con = true, kind = "strong", lv = 3),
    Answer("${s.v} 타 보고 싶었는데 문이 안 열려서 흔들었어.", "${s.v}${eul(s.v)} 타 보고 싶었어", reason = true, el = setOf("계기", "시도"), con = true, kind = "play", lv = 3),
)

private val CAUSE_CARDS: (DemoState) -> List<Card> = {
    listOf(
        Card("심심해", Art.Img("ic_bored", Art.Emoji("😐")), "lonely|친구가 없어서 심심했어"),
        Card("화났어", Art.Img("ic_angry", Art.Emoji("😠")), "prank|화가 나서 흔들었어"),
        Card("슬퍼", Art.Img("ic_sad", Art.Emoji("😢")), "lost|길을 잃어서 슬펐어"),
    )
}

/** 해결 방법 — 값 = 종류:미션2 물건:마지막 쪽 문장 */
private fun solutionPool(s: DemoState): List<Answer> = listOf(
    Answer("별 따기!", "play:star:같이 별을 땄어요", lv = 1),
    Answer("춤!", "play:note:손을 잡고 신나게 춤을 췄어요", lv = 1),
    Answer("딸기!", "gift:strawberry:딸기를 나눠 먹었어요", lv = 1),
    Answer("반짝이는 돌을 선물할래.", "gift:gem:반짝이는 돌을 나눠 가졌어요", lv = 2),
    Answer("풍선을 하나 줄래.", "play:balloon:풍선을 들고 같이 뛰어놀았어요", lv = 2),
    Answer("같이 노래 부를래.", "play:note:함께 노래를 불렀어요", lv = 2),
    Answer("우리 집에 초대할래! 같이 놀면 안 심심하니까.", "invite:invite:${s.c}네 집에서 함께 놀았어요", reason = true, el = setOf("시도"), con = true, lv = 3),
    Answer("딸기를 나눠 줄래. 배고프면 힘이 없으니까.", "gift:strawberry:딸기를 나눠 먹으며 웃었어요", reason = true, con = true, lv = 3),
    Answer("${s.d} 등에 태워 줄래! 그러면 높은 데 별도 딸 수 있어.", "play:star:${s.d} 등에 타고 높이 올라가 별을 땄어요", reason = true, el = setOf("시도", "결과"), con = true, lv = 3),
)

private val SOLUTION_CARDS: (DemoState) -> List<Card> = {
    listOf(
        Card("같이 놀기", Art.Img("ic_play", Art.Emoji("🎈")), "play:balloon:풍선을 들고 같이 뛰어놀았어요"),
        Card("선물 주기", Art.Img("ic_gift", Art.Emoji("🎁")), "gift:gem:반짝이는 돌을 나눠 가졌어요"),
        Card("초대하기", Art.Img("ic_invite", Art.Emoji("✉️")), "invite:invite:${it.c}네 집에서 함께 놀았어요"),
    )
}

private fun feelAnswers(s: DemoState) = listOf(
    Answer("좋아!", "기뻐", emo = "기뻤", lv = 1),
    Answer("웃어!", "기뻐", emo = "기뻤", lv = 1),
    Answer("신나서 폴짝폴짝 뛸 거야!", "신나", emo = "신났", lv = 2),
    Answer("고맙다고 할 거야.", "고마워", emo = "고마웠", lv = 2),
    Answer("이제 안 심심해서 행복할 거야. 친구가 생겼으니까.", "행복해", emo = "행복했", reason = true, con = true, lv = 3),
    Answer("처음엔 부끄러웠는데 이제 마음이 따뜻해졌을 거야.", "따뜻해", emo = "부끄러웠", el = setOf("결과"), lv = 3),
)

val BANK: List<QVariant> = listOf(
    // ── 장면 3 · 거기엔 뭐가 있을까 (배경 요소) ──
    QVariant("sight_a", "sight", ALL, Kind.EASY, "배경 요소를 스스로 채우나 (S2 배경)", counts = false,
        text = { "${it.p}에는 뭐가 있을 것 같아?" }, easier = { "눈을 감고 ${it.p}${eul(it.p)} 떠올려 봐. 뭐가 보여?" },
        answers = { it.sightAnswers }),
    QVariant("sight_b", "sight", ALL, Kind.EASY, "배경 요소를 스스로 채우나 (S2 배경)", counts = false,
        text = { "창밖을 봐! ${it.p}에서 뭐가 제일 먼저 보여?" }, easier = { "반짝이는 게 있나 찾아볼까?" },
        answers = { it.sightAnswers }),
    QVariant("sight_c", "sight", MID_UP, Kind.EASY, "배경을 이야기로 잇나 (S2 배경 · A1)", counts = false,
        text = { "${it.p}에 사는 친구들은 뭘 보면서 놀까?" }, easier = { "${it.p}에서 뭐가 보여?" },
        answers = { it.sightAnswers }),

    // ── 장면 4 · 그다음 (결과 · 대응) ──
    QVariant("follow_next", "follow", MID_UP, Kind.EASY, "결과를 스스로 잇나 (S2 결과 · A1)",
        text = { "${it.nk}${ga(it.nk)} 흔들었구나! 그다음엔 어떻게 됐을 것 같아?" },
        easier = { "${it.v} 안에 있던 ${it.c}${eun(it.c)} 어땠을까?" },
        answers = {
            listOf(
                Answer("멈췄어.", "멈췄어", lv = 1), Answer("놀랐어.", "놀랐어", emo = "놀랐", lv = 1),
                Answer("쿵 떨어졌어!", "쿵 떨어졌어", el = setOf("결과"), lv = 2),
                Answer("${it.v}${ga(it.v)} 삐뚤빼뚤 갔는데 재밌었어!", "삐뚤빼뚤 갔어", el = setOf("결과"), emo = "재밌었", lv = 2),
                Answer("${it.v}${ga(it.v)} 빙글빙글 돌았어. 그래서 무서웠어.", "빙글빙글 돌았어", el = setOf("결과"), con = true, emo = "무서웠", lv = 3),
                Answer("${it.c}${ga(it.c)} 손잡이를 꽉 잡았어. 그다음에 흔들림이 멈췄어.", "손잡이를 꽉 잡았어", el = setOf("시도", "결과"), con = true, lv = 3),
            )
        }),
    QVariant("follow_say", "follow", MID_UP, Kind.EASY, "인물의 대응을 떠올리나 (S2 시도)",
        text = { "그때 ${it.c}${eun(it.c)} 뭐라고 말했을까?" },
        easier = { "${it.c}${ga(it.c)} 소리쳤을까, 조용히 있었을까?" },
        answers = {
            listOf(
                Answer("으악!", "으악!", lv = 1), Answer("그만!", "그만!", lv = 1),
                Answer("누구야? 하고 물어봤어.", "누구야?", el = setOf("시도"), lv = 2),
                Answer("살살 해! 무서워!", "살살 해!", emo = "무서웠", lv = 2),
                Answer("흔들지 마! 떨어지면 아프니까!", "흔들지 마!", el = setOf("시도"), reason = true, con = true, lv = 3),
                Answer("안녕? 같이 놀래? 하고 말했어. 심심해 보여서.", "안녕? 같이 놀래?", el = setOf("시도"), reason = true, con = true, lv = 3),
            )
        }),
    QVariant("follow_who", "follow", LOW_MID, Kind.EASY, "누구 · 무엇 질문에 답하나",
        text = { "누가 제일 깜짝 놀랐을까?" }, easier = { "${it.c}${ga(it.c)} 놀랐을까?" },
        answers = {
            listOf(
                Answer("${it.c}!", "${it.c}", emo = "놀랐", lv = 1), Answer("나!", "${it.c}", emo = "놀랐", lv = 1),
                Answer("${it.c}${ga(it.c)} 깜짝 놀랐어.", "${it.c}", emo = "놀랐", lv = 2),
                Answer("${it.nk}도 놀랐어!", it.nk, el = setOf("결과"), lv = 2),
                Answer("${it.c}${ga(it.c)} 놀라서 의자에서 떨어질 뻔했어.", "${it.c}", el = setOf("결과"), emo = "놀랐", lv = 3),
                Answer("다 놀랐어! 쿵 소리가 너무 커서.", "모두", el = setOf("결과"), reason = true, con = true, lv = 3),
            )
        }),

    // ── 장면 5 · 까닭 (3턴 · 템플릿 확정) ──
    QVariant("cause_why", "cause", ALL, Kind.HARD, "'왜' 질문에 까닭을 담나 (S1)",
        text = { "${it.nk}${eun(it.nk)} 왜 그랬을까?" }, easier = { "${it.nk} 얼굴을 잘 봐. 어떤 기분인 것 같아?" },
        answers = { causeAnswers(it).shuffledKeepSpread() }, cards = CAUSE_CARDS),
    QVariant("cause_reason", "cause", MID_UP, Kind.HARD, "'왜' 질문에 까닭을 담나 (S1)",
        text = { "${it.nk}${ga(it.nk)} ${it.v}${eul(it.v)} 흔든 데는 까닭이 있을 거야. 뭘까?" },
        easier = { "${it.nk}${eun(it.nk)} 기분이 어땠을까?" },
        answers = { causeAnswers(it).shuffledKeepSpread() }, cards = CAUSE_CARDS),
    QVariant("cause_want", "cause", LOW_MID, Kind.HARD, "바람 · 까닭을 말하나 (S1)",
        text = { "${it.nk}${eun(it.nk)} 무엇을 하고 싶었을까?" },
        easier = { "${it.nk}${ga(it.nk)} 뭘 원했을까?" },
        answers = { causeAnswers(it).shuffledKeepSpread() }, cards = CAUSE_CARDS),

    // ── 장면 5 · 가정 질문 (까닭 짓기만 · 역할1 조사2 #5) ──
    QVariant("hypo_feel", "hypo", setOf(Level.REASON), Kind.HARD, "남의 입장이 되어 보나 (마음 · 신호 아님)", counts = false, emotion = true,
        text = { "${it.c}${ga(it.c)} ${wasIf(it.nk)} 기분이 어땠을까?" }, easier = { "${it.nk} 마음을 한번 생각해 볼까?" },
        answers = {
            listOf(
                Answer("슬펐을 거야.", "슬펐", emo = "슬펐", lv = 1),
                Answer("나도 심심했을 거야.", "심심했", emo = "심심했", lv = 2),
                Answer("외로웠을 거야.", "외로웠", emo = "외로웠", lv = 2),
                Answer("속상했을 거야. 아무도 안 놀아 줘서.", "속상했", emo = "속상했", reason = true, con = true, lv = 3),
                Answer("미안했을 거야. 세게 흔들었으니까.", "미안했", emo = "미안했", reason = true, con = true, lv = 3),
            )
        }),
    QVariant("hypo_other", "hypo", setOf(Level.REASON), Kind.HARD, "다른 방법을 떠올리나 (소크라틱 · S1)", counts = false,
        text = { "${it.nk}${eun(it.nk)} 흔드는 것 말고 다른 방법은 없었을까?" }, easier = { "말로 하면 어땠을까?" },
        answers = {
            listOf(
                Answer("말로!", "말로 하기", lv = 1),
                Answer("똑똑 두드려.", "똑똑 두드리기", lv = 2),
                Answer("손을 흔들어서 인사해.", "손 흔들기", lv = 2),
                Answer("같이 놀자고 말하면 돼. 그러면 안 무섭잖아.", "같이 놀자고 말하기", reason = true, con = true, lv = 3),
                Answer("창문에 그림을 그려서 보여 줘. 말이 안 통할 수도 있으니까.", "그림 보여 주기", reason = true, con = true, lv = 3),
            )
        }),

    // ── 장면 6 · 이름 ──
    QVariant("name_fit", "name", ALL, Kind.EASY, "이름 짓기 (만들기)", counts = false,
        text = { "${it.nk}한테 어울리는 이름은 뭘까?" }, easier = { "${it.nk}${eul(it.nk)} 보면 어떤 소리가 떠올라?" },
        answers = { NAMES.shuffledKeepSpread(5) }),
    QVariant("name_call", "name", ALL, Kind.EASY, "이름 짓기 (만들기)", counts = false,
        text = { "우리 새 친구를 뭐라고 부를까?" }, easier = { "${it.nk}${ga(it.nk)} 좋아할 이름이 뭘까?" },
        answers = { NAMES.shuffledKeepSpread(5) }),
    QVariant("name_tell", "name", MID_UP, Kind.EASY, "이름 짓기 · 까닭 (만들기)", counts = false,
        text = { "${it.nk}${ga(it.nk)} 자기 이름을 알려 준대! 뭐라고 했을까?" }, easier = { "어떤 이름이 좋을까?" },
        answers = { NAMES.shuffledKeepSpread(5) }),

    // ══ E 여정형 (고르며 짓기) ══
    QVariant("e_stop_where", "stop", ALL, Kind.EASY, "어디 질문에 답하나",
        text = { "${it.f}${rang(it.f)} 같이 어디를 지나가 볼까?" }, easier = { "반짝이는 곳? 신나는 곳?" },
        answers = {
            val st = it.stops
            listOf(
                Answer("${st[0]}!", st[0], lv = 1), Answer("${st[1]}!", st[1], lv = 1),
                Answer("${st[2]}에 가자.", st[2], lv = 2), Answer("${st[1]}에 갈래.", st[1], lv = 2),
                Answer("${st[0]}에 가서 미끄럼 탈래! 반짝거려서 예쁘니까.", st[0], el = setOf("시도"), reason = true, con = true, lv = 3),
                Answer("${st[2]}에 가면 친구가 더 있을 거야. 그래서 가 보고 싶어.", st[2], el = setOf("계기"), con = true, lv = 3),
            )
        },
        fallback = { Answer(it.stops[0], it.stops[0]) }),
    QVariant("e_stop_first", "stop", LOW_MID, Kind.EASY, "어디 질문에 답하나",
        text = { "${it.v}${ga(it.v)} 어디로 먼저 갈까?" }, easier = { "저 멀리 뭐가 보이지?" },
        answers = {
            val st = it.stops
            listOf(
                Answer("저기!", st[0], lv = 1), Answer("${st[1]}!", st[1], lv = 1),
                Answer("${st[2]}로 가자!", st[2], lv = 2), Answer("${st[0]}부터 갈래.", st[0], lv = 2),
                Answer("${st[1]}! 거기서 친구들이 기다릴 것 같아.", st[1], el = setOf("계기"), lv = 3),
            )
        },
        fallback = { Answer(it.stops[0], it.stops[0]) }),
    QVariant("e_saw", "saw", ALL, Kind.EASY, "무엇 질문 · 장면 채우기 (S2 배경)",
        text = { "${it.slot("stop", "거기")}에서 뭐가 보였어?" }, easier = { "반짝이는 거? 움직이는 거?" },
        answers = { sawAnswers(it) }, fallback = { sawAnswers(it).first() }),
    QVariant("e_met", "saw", ALL, Kind.EASY, "누구 질문 · 장면 채우기",
        text = { "${it.slot("stop", "거기")}에서 누구를 만났어?" }, easier = { "작은 친구? 큰 친구?" },
        answers = { sawAnswers(it) }, fallback = { sawAnswers(it).first() }),
    QVariant("e_play", "resolve", ALL, Kind.EASY, "무엇을 하고 놀까 (해결)",
        text = { "도착하면 ${it.f}${rang(it.f)} 뭐 하고 놀까?" }, easier = { "${it.f}${ga(it.f)} 좋아하는 놀이가 뭘까?" },
        answers = { solutionPool(it).shuffledKeepSpread() }, cards = SOLUTION_CARDS),
    QVariant("e_goal", "goal", ALL, Kind.EASY, "어디 질문 (도착)", counts = false,
        text = { "마지막엔 어디에 도착할까?" }, easier = { "제일 가 보고 싶은 곳은 어디야?" },
        answers = {
            val g = it.goals
            listOf(
                Answer("${g[0]}!", g[0], lv = 1), Answer("${g[1]}!", g[1], lv = 1),
                Answer("${g[1]}에 도착해.", g[1], lv = 2),
                Answer("${g[0]}! 거기서 소원을 빌 수 있거든.", g[0], reason = true, con = true, lv = 3),
            )
        },
        fallback = { Answer(it.goals[0], it.goals[0]) }),

    QVariant("e_goal_end", "goal", ALL, Kind.EASY, "어디 질문 (도착)", counts = false,
        text = { "여행 끝에는 ${it.f}${rang(it.f)} 어디에 닿으면 좋을까?" }, easier = { "${it.f}${ga(it.f)} 제일 좋아할 곳은 어디일까?" },
        answers = {
            val g = it.goals
            listOf(
                Answer("${g[1]}!", g[1], lv = 1), Answer("${g[0]}!", g[0], lv = 1),
                Answer("${g[0]}에 가고 싶어.", g[0], lv = 2),
                Answer("${g[1]}! 거기엔 친구들이 많이 사니까.", g[1], reason = true, con = true, lv = 3),
                Answer("${g[0]}에 닿으면 좋겠어. 그러면 소원을 빌 수 있어.", g[0], reason = true, con = true, lv = 3),
            )
        },
        fallback = { Answer(it.goals[1], it.goals[1]) }),

    // ══ C 위험-대응-도움형 (이어 짓기) ══
    QVariant("c_resp_say", "response", ALL, Kind.EASY, "위험할 때 대응을 말하나 (S2 시도)",
        text = { "${it.f}${ga(it.f)} 계속 흔들면 ${it.c}${eun(it.c)} 뭐라고 말할까?" },
        easier = { "${it.c}${ga(it.c)} 크게 말할까, 작게 말할까?" },
        answers = {
            listOf(
                Answer("그만!", "그만!", lv = 1), Answer("싫어!", "싫어!", lv = 1),
                Answer("안 돼요, 싫어요!", "안 돼요, 싫어요!", lv = 2),
                Answer("흔들지 마! 무서워!", "흔들지 마! 무서워!", emo = "무서웠", lv = 2),
                Answer("그만해 줘. 흔들면 떨어질 수 있어!", "그만해 줘. 흔들면 떨어질 수 있어!", el = setOf("시도"), reason = true, con = true, lv = 3),
                Answer("같이 놀자고 말할래. 그러면 안 흔들 거야.", "같이 놀자! 그러면 안 흔들어도 돼!", el = setOf("시도", "결과"), con = true, lv = 3),
            )
        },
        fallback = { Answer("그만!", "그만!") }),
    QVariant("c_resp_how", "response", MID_UP, Kind.HARD, "'어떻게' 질문에 방법을 담나 (S1)",
        text = { "어떻게 하면 ${it.f}${ga(it.f)} 흔드는 걸 멈출까?" },
        easier = { "${it.c}${ga(it.c)} 뭐라고 말하면 멈출까?" },
        answers = {
            listOf(
                Answer("말해!", "그만해!", lv = 1),
                Answer("그만하라고 말해.", "그만해 줘!", reason = true, lv = 2),
                Answer("크게 멈춰! 하고 외쳐.", "멈춰!", reason = true, lv = 2),
                Answer("손을 들고 멈춰! 하고 말해. 그러면 알아들어.", "멈춰! 손 들었지?", reason = true, el = setOf("결과"), con = true, lv = 3),
                Answer("창문을 똑똑 두드리고 인사해. 놀라서 멈출 거야.", "안녕! 똑똑!", reason = true, el = setOf("시도", "결과"), con = true, lv = 3),
            )
        },
        fallback = { Answer("그만!", "그만!") }),
    QVariant("c_help_who", "helper", ALL, Kind.EASY, "도움을 청할 사람을 떠올리나 (S2 시도)",
        text = { "누구한테 도와 달라고 할까?" }, easier = { "${it.c} 옆에 누가 있지?" },
        answers = {
            listOf(
                Answer("${it.pn}!", it.pn, lv = 1), Answer("경찰!", "경찰 아저씨", lv = 1),
                Answer("${it.pn}한테 말할래.", it.pn, el = setOf("시도"), lv = 2),
                Answer("마스코트한테 도와 달라고 해.", "마스코트", el = setOf("시도"), lv = 2),
                Answer("${it.pn}한테! 무서울 땐 어른한테 말해야 하니까.", it.pn, el = setOf("시도"), reason = true, con = true, lv = 3),
                Answer("${it.v} 선장님한테 알려서 고쳐 달라고 할래.", "${it.v} 선장님", el = setOf("시도", "결과"), lv = 3),
            )
        },
        fallback = { Answer(it.pn, it.pn) }),
    QVariant("c_help_how", "helper", MID_UP, Kind.HARD, "'어떻게' 알릴지 말하나 (S1)",
        text = { "어떻게 하면 도움을 받을 수 있을까?" }, easier = { "누구한테 말하면 될까?" },
        answers = {
            listOf(
                Answer("${it.pn}!", it.pn, lv = 1),
                Answer("${it.pn}한테 달려가서 말해.", it.pn, reason = true, lv = 2),
                Answer("큰 소리로 도와주세요! 해.", "마스코트", reason = true, lv = 2),
                Answer("${it.pn}한테 가서 무슨 일인지 차근차근 말해. 그러면 도와줄 거야.", it.pn, reason = true, el = setOf("결과"), con = true, lv = 3),
            )
        },
        fallback = { Answer(it.pn, it.pn) }),
    QVariant("c_resolve", "resolve", ALL, Kind.HARD, "해결 방법을 말하나 (S1 · S2)",
        text = { "어떻게 하면 ${it.f}${rang(it.f)} 사이좋게 지낼 수 있을까?" }, easier = { "${it.f}${ga(it.f)} 웃으려면 뭐가 있으면 좋을까?" },
        answers = { solutionPool(it).shuffledKeepSpread() }, cards = SOLUTION_CARDS),

    // ══ D 방문자-직업수행형 (이어 짓기) ══
    QVariant("d_need_what", "need", ALL, Kind.EASY, "상대에게 필요한 것을 떠올리나",
        text = { "${it.f}에게 뭐가 필요할까?" }, easier = { "${it.f}${ga(it.f)} 뭘 찾고 있을까?" },
        answers = { needAnswers(it) }, fallback = { needAnswers(it).first() }),
    // 의문사는 하나만 — 뒤에 붙었던 "왜?" 를 뗐다. 까닭은 판정기가 next_slot 으로 다음 턴에 묻는다
    QVariant("d_need_why", "need", MID_UP, Kind.HARD, "필요한 까닭까지 말하나 (S1)",
        text = { "${it.f}${eul(it.f)} 도와주려면 뭘 챙겨 가야 할까?" }, easier = { "뭘 가져가면 좋을까?" },
        answers = { needAnswers(it) }, fallback = { needAnswers(it).first() }),
    QVariant("d_role", "role", ALL, Kind.EASY, "하는 일(직업)을 떠올리나",
        text = { "${it.c}${eun(it.c)} 어떤 사람이 되어서 ${it.f}${eul(it.f)} 도와줄까?" },
        easier = { "의사 선생님? 길 안내원?" },
        answers = { roleAnswers() }, fallback = { Answer("구조대원", "구조대원") }),
    // 의문사는 하나만 — 위 d_need_why 와 같은 이유로 "왜?" 를 뗐다
    QVariant("d_role_do", "role", MID_UP, Kind.HARD, "하는 일과 까닭을 말하나 (S1)",
        text = { "${it.f}${eul(it.f)} 도우려면 누가 제일 잘할까?" }, easier = { "어떤 일을 하는 사람이 좋을까?" },
        answers = { roleAnswers() }, fallback = { Answer("구조대원", "구조대원") }),
    QVariant("d_resolve", "resolve", ALL, Kind.HARD, "도움을 마무리하나 (S1 · S2)",
        text = { "${it.f}${ga(it.f)} 기운 나게 뭘 해 주면 좋을까?" }, easier = { "${it.f}${ga(it.f)} 좋아할 게 뭘까?" },
        answers = { solutionPool(it).shuffledKeepSpread() }, cards = SOLUTION_CARDS),

    // ══ A 도전-성취형 (까닭 짓기) ══
    QVariant("a_try1", "try1", ALL, Kind.HARD, "첫 시도를 말하나 (S2 시도)",
        text = { "${it.c}${eun(it.c)} ${it.f}${ga(it.f)} 기운 나게 처음엔 어떻게 해 봤을까?" },
        easier = { "${it.c}${ga(it.c)} 뭘 줬을까, 뭐라고 했을까?" },
        answers = { try1Answers() }, fallback = { Answer("인사했어", "손을 흔들어 인사했어요") }),
    QVariant("a_try1_first", "try1", ALL, Kind.HARD, "첫 시도를 말하나 (S2 시도)",
        text = { "처음엔 ${it.f}한테 뭘 해 봤을 것 같아?" }, easier = { "${it.c}${ga(it.c)} 먼저 말을 걸었을까?" },
        answers = { try1Answers() }, fallback = { Answer("인사했어", "손을 흔들어 인사했어요") }),
    QVariant("a_fail", "fail", ALL, Kind.HARD, "실패의 까닭을 말하나 (S1)",
        text = { "그런데 잘 안 됐대. 왜 그랬을까?" }, easier = { "${it.f}${ga(it.f)} 부끄러웠을까?" },
        answers = { failAnswers(it) }, fallback = { Answer("부끄러워서", "${it.f}${ga(it.f)} 너무 부끄러워서") }),
    QVariant("a_fail_think", "fail", ALL, Kind.HARD, "실패의 까닭을 말하나 (S1)",
        text = { "${it.f}${ga(it.f)} 고개를 저었어. 무엇 때문이었을까?" }, easier = { "${it.f}${ga(it.f)} 무서웠을까?" },
        answers = { failAnswers(it) }, fallback = { Answer("부끄러워서", "${it.f}${ga(it.f)} 너무 부끄러워서") }),
    QVariant("a_try2", "resolve", ALL, Kind.HARD, "다른 방법을 스스로 찾나 (소크라틱 · S1 · S2)",
        text = { "다른 방법은 없었을까?" }, easier = { "이번엔 뭘 해 볼까?" },
        answers = { solutionPool(it).shuffledKeepSpread() }, cards = SOLUTION_CARDS),
    QVariant("a_try2_again", "resolve", ALL, Kind.HARD, "다시 시도할 방법을 찾나 (S1 · S2)",
        text = { "한 번 더 해 본다면 이번엔 어떻게 할까?" }, easier = { "${it.f}${ga(it.f)} 좋아할 걸 해 볼까?" },
        answers = { solutionPool(it).shuffledKeepSpread() }, cards = SOLUTION_CARDS),

    // ══ G 우화-교훈형 (까닭 짓기) ══
    QVariant("g_cons", "consequence", ALL, Kind.HARD, "행동의 결과를 떠올리나 (S2 결과)",
        text = { "${it.f}${ga(it.f)} 계속 세게 흔들다가 어떻게 됐을까?" }, easier = { "${it.f}도 흔들렸을까?" },
        answers = { consAnswers(it) }, fallback = { consAnswers(it).first() }),
    QVariant("g_cons_then", "consequence", ALL, Kind.HARD, "행동의 결과를 떠올리나 (S2 결과)",
        text = { "그렇게 뽐내다가 무슨 일이 생겼을 것 같아?" }, easier = { "${it.v}${ga(it.v)} 괜찮았을까?" },
        answers = { consAnswers(it) }, fallback = { consAnswers(it).first() }),
    QVariant("g_lesson", "lesson", ALL, Kind.HARD, "깨달음을 말하나 (S1)",
        text = { "${it.f}${eun(it.f)} 무엇을 알게 됐을까?" }, easier = { "세게 흔들면 어떻게 될까?" },
        answers = { lessonAnswers(it) }, fallback = { lessonAnswers(it).first() }),
    QVariant("g_lesson_next", "lesson", ALL, Kind.HARD, "다음 행동을 생각하나 (S1)",
        text = { "다음에 ${it.f}${eun(it.f)} 어떻게 하면 좋을까?" }, easier = { "살살 하면 어떨까?" },
        answers = { lessonAnswers(it) }, fallback = { lessonAnswers(it).first() }),
    QVariant("g_resolve", "resolve", ALL, Kind.HARD, "관계를 회복하는 방법 (S1 · S2)",
        text = { "${it.f}${rang(it.f)} 다시 친해지려면 어떻게 하면 좋을까?" }, easier = { "${it.f}${ga(it.f)} 뭘 하면 좋을까?" },
        answers = { solutionPool(it).shuffledKeepSpread() }, cards = SOLUTION_CARDS),

    // ── 공통 끝 질문 ──
    QVariant("feel_how", "feel", ALL, Kind.EASY, "마음 말하기 (신호 아님 · 기록 재료)", counts = false, emotion = true,
        text = { "그러면 ${it.f} 기분은 어떨까?" }, easier = { "${it.f} 얼굴을 떠올려 봐. 어떤 표정일까?" },
        answers = { feelAnswers(it) }),
    QVariant("feel_say", "feel", MID_UP, Kind.EASY, "마음 말하기 (신호 아님 · 기록 재료)", counts = false, emotion = true,
        text = { "${it.f}${ga(it.f)} ${it.c}한테 뭐라고 말했을까?" }, easier = { "${it.f}${ga(it.f)} 웃었을까?" },
        answers = { feelAnswers(it) }),
    QVariant("reflect_learn", "reflect", setOf(Level.REASON), Kind.HARD, "스스로 돌아보나 (S1)", counts = false,
        text = { "${it.c}${eun(it.c)} 오늘 무엇을 알게 됐을까?" }, easier = { "오늘 뭐가 제일 좋았어?" },
        answers = { reflectAnswers() }),
    QVariant("reflect_next", "reflect", setOf(Level.REASON), Kind.HARD, "다음 행동을 계획하나 (S1)", counts = false,
        text = { "다음에 또 이런 일이 생기면 어떻게 할까?" }, easier = { "또 만나면 뭐 할까?" },
        answers = { reflectAnswers() }),
)

private fun sawAnswers(s: DemoState): List<Answer> = if (s.generatedBg) listOf(
    Answer("눈사람!", "눈사람이 반갑게 손을 흔들었어요", lv = 1),
    Answer("토끼!", "하얀 토끼가 깡충 뛰어갔어요", lv = 1),
    Answer("펭귄이 미끄럼을 탔어.", "펭귄들이 쭈욱 미끄럼을 탔어요", lv = 2),
    Answer("고드름이 반짝반짝 빛났어.", "고드름이 반짝반짝 빛났어요", lv = 2),
    Answer("눈이 펑펑 와서 온 세상이 하얘졌어. 그래서 발자국이 생겼어.", "눈이 펑펑 내려 발자국이 폭폭 찍혔어요", el = setOf("배경", "결과"), con = true, lv = 3),
) else when (s.themeKey) {
    "sea" -> listOf(
        Answer("물고기!", "알록달록 물고기들이 인사했어요", lv = 1),
        Answer("해파리!", "말랑말랑 해파리가 둥실 떠 있었어요", lv = 1),
        Answer("진주가 반짝반짝 빛났어.", "반짝반짝 진주가 빛났어요", lv = 2),
        Answer("꽃게가 옆으로 걸어갔어.", "꽃게가 옆으로 총총 걸어갔어요", lv = 2),
        Answer("고래가 노래를 불렀어. 그래서 물이 출렁했어.", "커다란 고래가 노래를 부르자 물결이 출렁였어요", el = setOf("배경", "결과"), con = true, lv = 3),
    )
    "space" -> listOf(
        Answer("별똥별!", "별똥별이 슝 지나갔어요", lv = 1),
        Answer("토끼!", "달토끼가 떡을 찧고 있었어요", lv = 1),
        Answer("로봇이 손을 흔들었어.", "작은 로봇이 손을 흔들었어요", lv = 2),
        Answer("반짝이는 무지개가 있었어.", "우주 무지개가 반짝였어요", lv = 2),
        Answer("별들이 줄을 서서 길을 만들어 줬어. 그래서 안 헤맸어.", "별들이 줄을 서서 길을 밝혀 주었어요", el = setOf("배경", "결과"), con = true, lv = 3),
    )
    else -> listOf(
        Answer("알!", "커다란 공룡 알이 데굴데굴 굴러갔어요", lv = 1),
        Answer("나비!", "나비가 팔랑팔랑 날아다녔어요", lv = 1),
        Answer("익룡이 하늘로 날아갔어.", "익룡이 하늘 높이 날아갔어요", lv = 2),
        Answer("아기 공룡들이 놀고 있었어.", "아기 공룡들이 술래잡기를 하고 있었어요", lv = 2),
        Answer("공룡 발자국이 있었어. 그래서 따라가 봤어.", "커다란 발자국을 따라가 보았어요", el = setOf("배경", "시도"), con = true, lv = 3),
    )
}

private fun needAnswers(s: DemoState): List<Answer> = when (s.causeKind) {
    "hungry" -> listOf(
        Answer("사과!", "맛있는 사과", lv = 1), Answer("밥!", "따뜻한 밥", lv = 1),
        Answer("맛있는 도시락이 필요해.", "맛있는 도시락", lv = 2),
        Answer("따뜻한 수프. 배고프면 힘이 없으니까.", "따뜻한 수프", reason = true, con = true, lv = 3),
        Answer("같이 먹을 간식. 혼자 먹으면 심심하잖아.", "함께 먹을 간식", reason = true, con = true, lv = 3),
    )
    "hurt" -> listOf(
        Answer("반창고!", "반창고", lv = 1), Answer("약!", "약", lv = 1),
        Answer("약을 발라 줘야 해.", "바르는 약", lv = 2),
        Answer("푹신한 베개. 아프면 쉬어야 하니까.", "푹신한 베개", reason = true, con = true, lv = 3),
        Answer("얼음주머니. 부으면 차갑게 해야 낫거든.", "얼음주머니", reason = true, con = true, lv = 3),
    )
    else -> listOf(
        Answer("지도!", "지도", lv = 1), Answer("손전등!", "손전등", lv = 1),
        Answer("집에 가는 길을 알려 줘야 해.", "집으로 가는 지도", lv = 2),
        Answer("반짝이는 손전등. 어두우면 길이 안 보이니까.", "반짝이는 손전등", reason = true, con = true, lv = 3),
        Answer("나침판! 그러면 방향을 알 수 있어.", "나침판", reason = true, con = true, lv = 3),
    )
}

private fun roleAnswers() = listOf(
    Answer("의사!", "의사 선생님", lv = 1), Answer("선생님!", "선생님", lv = 1),
    Answer("길 안내원이 될래.", "길 안내원", lv = 2), Answer("요리사가 될래.", "요리사", lv = 2),
    Answer("구조대원이 될래. 위험할 때 구해 주니까.", "구조대원", reason = true, con = true, lv = 3),
    Answer("소방관이 좋아. 불도 끄고 사람도 도와주거든.", "소방관", reason = true, el = setOf("시도"), con = true, lv = 3),
)

private fun try1Answers() = listOf(
    Answer("인사했어.", "손을 흔들어 인사했어요", el = setOf("시도"), lv = 1),
    Answer("안아 줬어.", "꼭 안아 주었어요", el = setOf("시도"), lv = 1),
    Answer("노래를 불러 줬어.", "노래를 불러 주었어요", el = setOf("시도"), lv = 2),
    Answer("과자를 줬어.", "과자를 건넸어요", el = setOf("시도"), lv = 2),
    Answer("먼저 이름을 불러 봤어. 그다음에 손을 내밀었어.", "이름을 부르고 손을 내밀었어요", el = setOf("시도"), con = true, lv = 3),
    Answer("재밌는 표정을 지어 줬어. 웃으면 기운이 나니까.", "재밌는 표정을 지어 보였어요", el = setOf("시도"), reason = true, con = true, lv = 3),
)

private fun failAnswers(s: DemoState) = listOf(
    Answer("몰라.", "${s.f}${ga(s.f)} 깜짝 놀라서", lv = 1),
    Answer("부끄러워서!", "${s.f}${ga(s.f)} 너무 부끄러워서", reason = true, lv = 1),
    Answer("${s.f}${ga(s.f)} 무서웠어.", "${s.f}${ga(s.f)} 조금 무서워서", reason = true, lv = 2),
    Answer("아직 친하지 않아서.", "아직 서로 친하지 않아서", reason = true, lv = 2),
    Answer("너무 갑자기 다가가서 ${s.f}${ga(s.f)} 깜짝 놀라 숨었어.", "너무 갑자기 다가가서 ${s.f}${ga(s.f)} 깜짝 놀라는 바람에", reason = true, el = setOf("결과"), lv = 3),
    Answer("${s.f}${eun(s.f)} 아직 마음의 준비가 안 됐으니까. 그래서 고개를 저었어.", "${s.f}${ga(s.f)} 아직 마음의 준비가 안 돼서", reason = true, el = setOf("결과"), con = true, lv = 3),
)

private fun consAnswers(s: DemoState) = listOf(
    Answer("넘어졌어!", "${s.f}${ga(s.f)} 휘청하고 넘어졌어요.", el = setOf("결과"), lv = 1),
    Answer("쿵!", "쿵! ${s.v}${ga(s.v)} 기우뚱했어요.", el = setOf("결과"), lv = 1),
    Answer("${s.f}${ga(s.f)} 어지러워졌어.", "${s.f}${ga(s.f)} 빙글빙글 어지러워졌어요.", el = setOf("결과"), lv = 2),
    Answer("${s.v}${ga(s.v)} 고장 났어.", "${s.v}${ga(s.v)} 삐걱 고장이 나 버렸어요.", el = setOf("결과"), lv = 2),
    Answer("너무 세게 흔들어서 자기도 넘어졌어. 그래서 울었어.", "너무 세게 흔들다가 ${s.f}${ga(s.f)} 쿵 넘어져 울고 말았어요.", el = setOf("결과"), reason = true, con = true, lv = 3),
    Answer("친구들이 다 도망갔어. 무서워서.", "모두 무서워서 도망가 버렸어요.", el = setOf("결과"), reason = true, lv = 3),
)

private fun lessonAnswers(s: DemoState) = listOf(
    Answer("흔들면 안 돼.", "세게 흔들면 안 된다는 걸 알았어요", lv = 1),
    Answer("미안해!", "친구에게 미안하다고 말해야 한다는 걸 알았어요", lv = 1),
    Answer("살살 해야 돼.", "살살 해야 한다는 걸 알았어요", reason = true, lv = 2),
    Answer("친구한테 물어봐야 돼.", "먼저 물어봐야 한다는 걸 알았어요", reason = true, lv = 2),
    Answer("힘이 세도 친구를 다치게 하면 안 돼. 친구가 아프니까.", "힘이 세도 친구를 아프게 하면 안 된다는 걸 알았어요", reason = true, con = true, lv = 3),
    Answer("같이 놀고 싶으면 말로 하면 돼. 흔들면 무서우니까.", "같이 놀고 싶으면 말로 하면 된다는 걸 알았어요", reason = true, con = true, lv = 3),
)

private fun reflectAnswers() = listOf(
    Answer("몰라!", "오늘이 참 즐거웠어요", lv = 1),
    Answer("친구!", "새 친구가 생겨서 마음이 따뜻해졌어요", lv = 1),
    Answer("다음엔 먼저 물어볼래.", "다음엔 먼저 물어보기로 했어요", el = setOf("시도"), lv = 2),
    Answer("안 되면 다시 해 보면 돼! 포기하면 못 하니까.", "안 되면 다른 방법으로 다시 해 보면 된다는 걸 알았어요", reason = true, con = true, lv = 3),
    Answer("기다려 주는 거. 친구가 부끄러울 수 있으니까.", "친구가 준비될 때까지 기다려 주면 된다는 걸 알았어요", reason = true, con = true, lv = 3),
)

/** 함께 하는 사람에게 묻는 질문 — 누구냐에 따라 말투 · 답이 다르다 */
fun partnerQuestion(s: DemoState): Pair<String, List<Answer>> {
    val pn = s.pn
    val pt = s.partner
    val qs = if (pt.honor) listOf(
        "$pn, ${s.c}${wa(s.c)} ${s.f}${ga(s.f)} 친해지게 뭘 도와주실래요?",
        "${pn}께서도 ${s.p}에 같이 가신다면 뭘 챙겨 가실래요?",
    ) else if (pt.adult) listOf(
        "$pn, ${s.c}${wa(s.c)} ${s.f}${ga(s.f)} 친해지게 $pn${eun(pn)} 뭘 도와줄까?",
        "$pn${eun(pn)} 이 이야기에서 뭘 해 주고 싶어?",
        "$pn${ga(pn)} ${s.p}에 같이 간다면 뭘 챙겨 갈까?",
    ) else listOf(
        "친구야, ${s.f}${rang(s.f)} 친해지게 너는 뭘 해 줄래?",
        "친구도 같이 간다면 뭘 하고 싶어?",
    )
    val ans = when {
        pt.honor -> listOf(
            Answer("맛있는 떡을 싸 갈게.", "${s.pTop()} 맛있는 떡을 싸 오셨어요"),
            Answer("옛날이야기를 들려줄게.", "${s.pTop()} 재미있는 옛날이야기를 들려주셨어요"),
            Answer("따뜻한 목도리를 떠 줄게.", "${s.pTop()} 따뜻한 목도리를 떠 주셨어요"),
            Answer("박수를 쳐 줄게.", "${s.pTop()} 짝짝짝 박수를 쳐 주셨어요"),
            Answer("손잡고 같이 걸어갈게.", "${s.pTop()} 손을 꼭 잡고 함께 걸어가셨어요"),
        )
        pt.adult -> listOf(
            Answer("내가 맛있는 간식을 싸 갈게!", "$pn${eun(pn)} 맛있는 간식을 싸 왔어요"),
            Answer("내가 신나는 노래를 불러 줄게!", "$pn${eun(pn)} 신나는 노래를 불러 주었어요"),
            Answer("셋이 같이 사진을 찍어 줄게!", "$pn${eun(pn)} 셋의 사진을 찍어 주었어요"),
            Answer("내가 박수 쳐 줄게!", "$pn${eun(pn)} 짝짝짝 박수를 쳐 주었어요"),
            Answer("나도 손잡고 같이 갈게!", "$pn${eun(pn)} 손을 잡고 함께 갔어요"),
        )
        else -> listOf(
            Answer("내 과자 나눠 줄래!", "친구는 과자를 나눠 주었어요"),
            Answer("같이 술래잡기 할래!", "친구는 술래잡기를 하자고 했어요"),
            Answer("재밌는 춤 알려 줄래!", "친구는 재미있는 춤을 알려 주었어요"),
            Answer("내 스티커 줄래.", "친구는 반짝이 스티커를 붙여 주었어요"),
            Answer("같이 그림 그릴래!", "친구는 함께 그림을 그렸어요"),
        )
    }
    return qs.random() to ans
}

/** 함께 하는 사람이 장면 안에서 하는 말 */
fun partnerLine(s: DemoState, what: String): String {
    val friend = !s.partner.adult
    return when (what) {
        "place" -> if (friend) "우와, ${s.p}? 나도 갈래!" else "우와, ${s.p}? ${s.v} 타고 가는 거야?"
        "cause" -> if (friend) "같이 놀고 싶었나 봐!" else "혹시 같이 놀고 싶었던 거 아닐까?"
        "shake" -> if (friend) "뭐지? 누가 흔드는 거야?" else "${s.v}${ga(s.v)} 왜 흔들리지? 누가 왔나?"
        "drawn" -> if (friend) "우와, 잘 그렸다!" else "다 그렸네! 멋지다!"
        "picked" -> if (friend) "그 친구 귀엽다!" else "오, 그 친구구나!"
        else -> ""
    }
}

// ── 질문 고르기 ────────────────────────────────────────────────

/** slot에 맞는 변형을 고른다 — 지금 수준에 맞는 것 · 이번 이야기와 지난 이야기에서 안 쓴 것 우선 */
fun DemoState.pick(slot: String): QVariant {
    // 여러 템플릿이 함께 쓰는 칸(해결 등)은 지금 템플릿의 변형만 (id 앞머리 e_ · c_ · d_ · a_ · g_)
    val shared = BANK.filter { it.slot == slot }
    val all = templateKey?.lowercase()?.let { tk -> shared.filter { it.id.startsWith("${tk}_") } }?.ifEmpty { null } ?: shared
    val fit = all.filter { level in it.levels }.ifEmpty { all }
    // 안 쓴 것 → 가장 오래전에 쓴 것 순으로 (지난 이야기와 같은 질문이 바로 다시 나오지 않게)
    val pool = fit.filter { it.id !in askedThisStory }.ifEmpty { fit }
    val best = pool.minOf { usedVariants.indexOf(it.id) }
    val chosen = pool.filter { usedVariants.indexOf(it.id) == best }.random()
    askedThisStory += chosen.id
    usedVariants.remove(chosen.id); usedVariants += chosen.id
    if (usedVariants.size > 30) usedVariants.removeAt(0)
    return chosen
}

fun QVariant.toQuestion(s: DemoState): Question = Question(
    text = text(s),
    kind = kind,
    easierText = easier(s),
    spoken = answers(s),
    choices = cards?.invoke(s).orEmpty(),
    noCards = cards == null,
    hint = hint?.invoke(s) ?: if (cards == null) "천천히 생각해도 돼. 떠오르는 대로 말해 줘!" else null,
    fallback = fallback?.invoke(s),
    id = id,
)

/** 더미 아이의 답 고르기 — [아이 수준 흉내]가 있으면 그 수준의 답을 70%로 */
fun DemoState.pickAnswer(options: List<Answer>): Answer? {
    if (options.isEmpty()) return null
    val lv = profile.level ?: return options.random()
    val same = options.filter { it.lv == lv.rank }
    return if (same.isNotEmpty() && Math.random() < 0.7) same.random() else options.random()
}

// ── 템플릿 ────────────────────────────────────────────────────

enum class PageKind { COVER, DEPART, SHAKE, MEET, TALK, JOURNEY, FAIL, RUB, DRAG, TOGETHER }

class PageSpec(val kind: PageKind, val text: (DemoState) -> String)

class StoryTemplate(
    val key: String,
    val code: String,
    val name: String,
    val level: Level,
    /** 이 템플릿이 쓰는 이야기 질문 slot (장면 6↳) */
    val plot: List<String>,
    /** 매듭 짓는 질문 slot (장면 10) */
    val ending: List<String>,
    val pages: List<PageSpec>,
    val shape: String,
)

private fun DemoState.partnerTail() = partnerHelpLine?.let { " $it." } ?: ""
private fun DemoState.give() = mission2().give
private fun DemoState.stuck() = mission1().stuck
private fun DemoState.eg(w: String) = "${w}에게"

private fun helperLine(s: DemoState): String {
    val h = s.slot("helper", s.pn)
    return when {
        h == s.pn && s.partner.honor -> "${s.pSubj()} \"말해 줘서 고맙구나\" 하고 꼭 안아 주셨어요."
        h == s.pn && !s.partner.adult -> "친구가 \"내가 같이 있을게!\" 하고 손을 잡아 주었어요."
        h == s.pn -> "$h${ga(h)} \"말해 줘서 고마워\" 하고 꼭 안아 주었어요."
        h == "마스코트" -> "마스코트가 \"걱정 마!\" 하고 날개를 활짝 폈어요."
        else -> "$h${ga(h)} \"잘했어!\" 하고 달려와 주었어요."
    }
}

private fun ieoss(w: String) = if (bat(w)) "${w}이었어요" else "${w}였어요"

val TEMPLATES: List<StoryTemplate> = listOf(
    StoryTemplate(
        "E", "E", "여정형", Level.PICK,
        plot = listOf("stop", "saw"), ending = listOf("resolve", "goal"),
        shape = "길 나섬 → 여러 곳을 지나감 → 도착 · 같은 말 되풀이 · 6쪽",
        pages = listOf(
            PageSpec(PageKind.DEPART) { "${it.c}${eun(it.c)} ${it.v}${eul(it.v)} 타고 ${it.p}${ro(it.p)} 떠났어요. 출발, 출발!" },
            PageSpec(PageKind.SHAKE) { "그런데 ${it.th.eventLine}. 창밖을 보니 ${it.nk} ${it.f}${ga(it.f)} 손을 흔들고 있었어요." },
            PageSpec(PageKind.JOURNEY) { "${it.c}${wa(it.c)} ${it.f}${eun(it.f)} 함께 ${it.slot("stop", it.stops[0])}${eul(it.slot("stop", it.stops[0]))} 지나갔어요. ${it.slot("saw", "모두 반짝반짝 빛났어요")}." },
            PageSpec(PageKind.RUB) { "또 덜컹, 또 덜컹! 흔들린 ${it.v}에 ${it.stuck()}! 슥슥 치워 볼까요?" },
            PageSpec(PageKind.DRAG) { "드디어 ${it.slot("goal", it.goals[0])}에 도착했어요. ${it.c}${eun(it.c)} ${it.eg(it.f)} ${it.give()}." },
            PageSpec(PageKind.TOGETHER) { "${it.c}${wa(it.c)} ${it.f}${eun(it.f)} ${it.solutionLine}.${it.partnerTail()} \"또 오자, 또 오자, 또 오자!\" 모두 손가락을 걸었어요." },
        ),
    ),
    StoryTemplate(
        "C", "C", "위험-대응-도움형", Level.CHAIN,
        plot = listOf("response", "helper"), ending = listOf("resolve"),
        shape = "위협 → 배운 대로 대응 → 믿는 사람에게 알림 → 해결 · 7쪽",
        pages = listOf(
            PageSpec(PageKind.DEPART) { "${it.c}${eun(it.c)} ${it.v}${eul(it.v)} 타고 반짝이는 ${it.p}${ro(it.p)} 떠났어요." },
            PageSpec(PageKind.SHAKE) { "그런데 갑자기 ${it.th.eventLine}. ${it.nk} ${it.f}${ga(it.f)} ${it.v}${eul(it.v)} 붙잡고 마구 흔들고 있었어요!" },
            PageSpec(PageKind.TALK) { "${it.c}${eun(it.c)} 용기를 내서 \"${it.slot("response", "그만!")}\" 하고 또박또박 말했어요." },
            PageSpec(PageKind.RUB) { "하지만 흔들린 ${it.v}에 ${it.stuck()}! ${it.c}${eun(it.c)} ${it.mission1().toolName}${ro(it.mission1().toolName)} 슥슥 치웠어요." },
            PageSpec(PageKind.TALK) { "그다음 ${it.c}${eun(it.c)} ${it.slot("helper", it.pn).let { h -> if (h == it.pn && it.partner.honor) "${h}께" else "${h}에게" }} 달려가 무슨 일이 있었는지 말했어요. ${helperLine(it)}" },
            PageSpec(PageKind.DRAG) { "그러자 ${it.f}${eun(it.f)} 고개를 숙이고 \"${it.causeLine}. 미안해\" 하고 말했어요. ${it.c}${eun(it.c)} ${it.eg(it.f)} ${it.give()}." },
            PageSpec(PageKind.TOGETHER) { "그 뒤로 ${it.c}${wa(it.c)} ${it.f}${eun(it.f)} ${it.solutionLine}.${it.partnerTail()} 둘은 다음에도 사이좋게 놀기로 했어요." },
        ),
    ),
    StoryTemplate(
        "D", "D", "방문자-직업수행형", Level.CHAIN,
        plot = listOf("need", "role"), ending = listOf("resolve"),
        shape = "낯선 친구의 어려움 → 맡은 일로 도움 → 일을 해냄 · 7쪽",
        pages = listOf(
            PageSpec(PageKind.DEPART) { "${it.c}${eun(it.c)} ${it.v}${eul(it.v)} 타고 ${it.p}${ro(it.p)} 여행을 떠났어요." },
            PageSpec(PageKind.SHAKE) { "그런데 ${it.th.eventLine}. 창밖에서 ${it.nk} ${it.f}${ga(it.f)} 울상을 짓고 있었어요." },
            PageSpec(PageKind.TALK) { "${it.f}${eun(it.f)} \"${it.causeLine}\" 하고 말했어요. 그래서 ${it.v}${eul(it.v)} 흔들어 도움을 청한 거예요." },
            PageSpec(PageKind.TALK) { "${it.c}${eun(it.c)} 씩씩한 ${it.slot("role", "구조대원")}${ga(it.slot("role", "구조대원"))} 되기로 했어요. \"제가 도와줄게요!\"" },
            PageSpec(PageKind.RUB) { "${it.f}에게 필요한 건 ${ieoss(it.slot("need", "지도"))}. 그런데 흔들린 ${it.v}에 ${it.stuck()}! 먼저 슥슥 치워요." },
            PageSpec(PageKind.DRAG) { "${it.slot("role", "구조대원")} ${it.c}${eun(it.c)} ${it.eg(it.f)} ${it.slot("need", "지도")}${eul(it.slot("need", "지도"))} 챙겨 주고, ${it.give()}. \"다 됐어요!\"" },
            PageSpec(PageKind.TOGETHER) { "${it.f}${eun(it.f)} 활짝 웃었어요. ${it.c}${wa(it.c)} ${it.f}${eun(it.f)} ${it.solutionLine}.${it.partnerTail()} 오늘의 일을 멋지게 해냈어요." },
        ),
    ),
    StoryTemplate(
        "A", "A", "도전-성취형", Level.REASON,
        plot = listOf("try1", "fail"), ending = listOf("resolve", "reflect"),
        shape = "시도 → 실패 → 다시 시도 → 성공 · 8쪽",
        pages = listOf(
            PageSpec(PageKind.DEPART) { "${it.c}${eun(it.c)} ${it.v}${eul(it.v)} 타고 반짝이는 ${it.p}${ro(it.p)} 떠났어요." },
            PageSpec(PageKind.SHAKE) { "그런데 갑자기 ${it.th.eventLine}. 창밖에서 ${it.nk} ${it.f}${ga(it.f)} 쳐다보고 있었어요." },
            PageSpec(PageKind.TALK) { "${it.f}${eun(it.f)} \"${it.causeLine}\" 하고 말했어요. ${it.c}${eun(it.c)} ${it.f}${eul(it.f)} 꼭 도와주고 싶었어요." },
            PageSpec(PageKind.FAIL) { "${it.c}${eun(it.c)} 먼저 ${it.slot("try1", "손을 흔들어 인사했어요")}. 하지만 ${it.slot("fail", "${it.f}${ga(it.f)} 부끄러워서")} 잘 되지 않았어요." },
            PageSpec(PageKind.RUB) { "게다가 흔들린 ${it.v}에 ${it.stuck()}! ${it.c}${eun(it.c)} 포기하지 않고 슥슥 치웠어요." },
            PageSpec(PageKind.TALK) { "${it.c}${eun(it.c)} 곰곰이 생각했어요. \"다른 방법이 있을 거야!\" 그리고 다시 한번 해 보기로 했어요." },
            PageSpec(PageKind.DRAG) { "이번엔 달랐어요! ${it.c}${eun(it.c)} ${it.eg(it.f)} ${it.give()}. ${it.f}${ga(it.f)} 드디어 웃었어요." },
            PageSpec(PageKind.TOGETHER) { "\"해냈다!\" ${it.c}${wa(it.c)} ${it.f}${eun(it.f)} ${it.solutionLine}.${it.partnerTail()} ${it.c}${eun(it.c)} ${it.slot("reflect", "안 되면 다시 해 보면 된다는 걸 알았어요")}." },
        ),
    ),
    StoryTemplate(
        "G", "G", "우화-교훈형", Level.REASON,
        plot = listOf("consequence", "lesson"), ending = listOf("resolve", "reflect"),
        shape = "뽐냄 → 곤란 → 깨달음 → 화해 · 8쪽",
        pages = listOf(
            PageSpec(PageKind.DEPART) { "${it.c}${eun(it.c)} ${it.v}${eul(it.v)} 타고 ${it.p}${ro(it.p)} 떠났어요." },
            PageSpec(PageKind.SHAKE) { "그때 ${it.nk} ${it.f}${ga(it.f)} ${it.v}${eul(it.v)} 붙잡고 쿵쿵 마구 흔들었어요." },
            PageSpec(PageKind.TALK) { "${it.f}${eun(it.f)} \"${it.causeLine}!\" 하고 더 세게 흔들었어요. ${it.c}${eun(it.c)} 조마조마했어요." },
            PageSpec(PageKind.FAIL) { "그러다 ${it.slot("consequence", "${it.f}${ga(it.f)} 휘청하고 넘어졌어요.")}" },
            PageSpec(PageKind.RUB) { "흔들린 ${it.v}에는 ${it.stuck()}! ${it.c}${eun(it.c)} ${it.mission1().toolName}${ro(it.mission1().toolName)} 슥슥 치웠어요." },
            PageSpec(PageKind.TALK) { "${it.f}${eun(it.f)} \"어? 그게 아니었네\" 하고 생각했어요. 그리고 ${it.slot("lesson", "세게 흔들면 안 된다는 걸 알았어요")}." },
            PageSpec(PageKind.DRAG) { "${it.f}${eun(it.f)} \"미안해\" 하고 사과했어요. ${it.c}${eun(it.c)} 괜찮다며 ${it.eg(it.f)} ${it.give()}." },
            PageSpec(PageKind.TOGETHER) { "${it.c}${wa(it.c)} ${it.f}${eun(it.f)} ${it.solutionLine}.${it.partnerTail()} ${it.c}${eun(it.c)} ${it.slot("reflect", "친구를 아끼는 마음이 제일 힘세다는 걸 알았어요")}." },
        ),
    ),
)

fun templateOf(key: String) = TEMPLATES.first { it.key == key }

/** 3턴째 — 수준과 까닭 종류로 템플릿 · 속성을 고른다 */
fun chooseTemplate(level: Level, causeKind: String): Pair<String, String> {
    val tpl = when (level) {
        Level.PICK -> "E"
        Level.CHAIN -> if (causeKind in setOf("lost", "hungry", "hurt")) "D" else "C"
        Level.REASON -> if (causeKind in setOf("prank", "strong")) "G" else "A"
    }
    val attr = when (causeKind) {
        "lonely", "play", "hello" -> "상황 이해"
        "prank", "strong" -> "교훈"
        "lost", "hungry", "hurt" -> if (tpl == "D") "직업 체험" else "상황 이해"
        else -> "체험"
    }
    return tpl to (if (tpl == "E") "체험" else attr)
}

// ── 발달 판단 ─────────────────────────────────────────────────

/**
 * 한 턴의 답을 기록하고 신호를 센다.
 *  - 말로 답했을 때만 S1 · S2 · A1을 센다 (탭 · 그림 · 마스코트가 채운 것은 세지 않음)
 *  - 마음 질문은 신호로 쓰지 않는다
 *  - 이야기 턴(counts)만 수준 이동에 쓴다: 올림 신호 2턴 연속 → 한 칸 위 (고르며→이어: S1 또는 S2 · 이어→까닭: S1 + 구조) /
 *    내림 신호(무응답 · 카드 · 두 어절 이하에 신호 없음) 2턴 연속 → 한 칸 아래
 *  - 3턴째 템플릿이 확정되면 이후 이동은 "질문 난이도"만 바꾸고 다음 세션 시작점으로 남긴다
 */
fun Director.judge(q: QVariant?, r: Reply, label: String = q?.text?.invoke(s) ?: "") {
    val a = (r as? Reply.Spoke)?.answer
    val mode = when {
        r is Reply.Spoke -> "voice"
        r is Reply.Tapped && r.byMascot -> "mascot"
        r is Reply.Tapped -> "card"
        else -> "silent"
    }
    val emotion = q?.emotion == true
    val s1 = !emotion && a?.reason == true
    val el = if (emotion) emptySet() else a?.el.orEmpty()
    val a1 = !emotion && a?.con == true
    val words = a?.words ?: 0
    s.notes += TurnNote(label, a?.text ?: "(${mode})", mode, s1, el, a1, words)
    if (s1) signal("S1", a!!.text, q?.probe ?: "")
    if (el.isNotEmpty()) signal("S2", a!!.text, el.joinToString("·"))
    a?.emo?.takeIf { it.isNotEmpty() }?.let { s.feelings += it; event("emotion_mention", "text" to it) }
    if (a != null) quote(a.text)

    if (q?.counts != true) return
    s.turn++
    // 올림 신호: 고르며 → 이어는 S1 또는 S2 하나면 되고,
    // 이어 → 까닭은 까닭(S1)에 이야기 구조(계기 · 시도 · 결과 중 하나 또는 잇는 말)가 함께 있어야 한다 (역할1 조사2 §4 표)
    val structure = el.any { it in setOf("계기", "시도", "결과") } || a1
    val up = if (s.level == Level.PICK) (s1 || el.isNotEmpty()) else (s1 && structure)
    val down = mode != "voice" || (words <= 2 && !up)
    s.s1streak = if (up) s.s1streak + 1 else 0
    s.noAnswerStreak = if (down) s.noAnswerStreak + 1 else 0
    if (s.s1streak >= 2) {
        s.s1streak = 0
        val to = s.level.up()
        if (to != s.level) moveLevel(to, "올림 신호 2턴 연속")
    }
    if (s.noAnswerStreak >= 2) {
        s.noAnswerStreak = 0
        val to = s.level.down()
        if (to != s.level) moveLevel(to, "내림 신호(짧은 답 · 카드 · 무응답) 2턴 연속")
    }
    // 일기 모드는 수준별 템플릿을 고르지 않는다 — 기승전결이 곧 쪽 차례다 (일기 설계 §5)
    if (s.turn == 3 && s.templateKey == null && !s.isDiary) decideTemplate("3턴째")
}

/** 수준 · 까닭 종류로 템플릿과 속성을 확정한다 (한 이야기에 한 번) */
fun Director.decideTemplate(why: String) {
    val (tpl, attr) = chooseTemplate(s.level, s.causeKind)
    s.templateKey = tpl
    s.attribute = attr
    val t = templateOf(tpl)
    s.levelWhy = "${s.level.label} → ${t.code} ${t.name} · 속성 $attr · ${t.pages.size}쪽"
    log("템플릿 확정($why): ${s.levelWhy} (까닭 종류 ${s.causeKind}) — 역할1 조사2 표 #1")
    event("template", "level" to s.level.label, "template" to "${t.code} ${t.name}", "attribute" to attr, "pages" to t.pages.size)
}

private fun Director.moveLevel(to: Level, why: String) {
    if (s.templateKey == null) {
        log("수준 ${s.level.label} → ${to.label} ($why · 처음 판단 구간)")
        s.level = to
    } else {
        log("수준 ${s.level.label} → ${to.label} ($why) · 템플릿은 그대로 두고 질문 난이도만 바꿈 · 다음 세션 시작점")
        s.level = to
        s.nextLevel = to
    }
}

/** 세션 누적 추정 (역할1 조사2 §4 표) — 규칙이 계산한다. LLM은 요소 표시만 한다 */
fun DemoState.ruleEstimate(): Pair<Level, String> {
    val n = notes.filter { it.mode != "mascot" }
    if (n.isEmpty()) return level to "아직 답이 없음"
    val s1 = n.count { it.s1 }
    val core = n.flatMap { it.el }.toSet().intersect(setOf("계기", "시도", "결과"))
    val allEl = n.flatMap { it.el }.toSet()
    val shortOrPick = n.count { it.mode != "voice" || it.words <= 2 }
    val lvl = when {
        s1 >= 2 && core.size >= 2 -> Level.REASON
        shortOrPick * 10 >= n.size * 6 && s1 == 0 && allEl.isEmpty() -> Level.PICK
        else -> Level.CHAIN
    }
    val avg = n.filter { it.mode == "voice" }.map { it.words }.average().let { if (it.isNaN()) 0.0 else it }
    return lvl to "S1 ${s1}번 · S2 요소 ${allEl.joinToString("·").ifEmpty { "없음" }} · A1 ${n.count { it.a1 }}번 · 평균 ${"%.1f".format(avg)}어절 · 짧은 답/카드 ${shortOrPick}/${n.size}"
}

/** 제목 — 템플릿과 대화로 지어 준다 (묻지 않는다 · 책장에서 바꿀 수 있게 할 자리) */
fun DemoState.autoTitleFor(): String {
    if (isDiary) return diaryTitle()
    val f = friendName.takeUnless { it.startsWith("{") } ?: newcomerKind
    val c = childName
    return when (templateKey) {
        "E" -> "${f}${wa(f)} 떠난 ${slots["goal"] ?: placeName} 여행"
        "C" -> "\"${slots["response"]?.split('!', '.', '?')?.firstOrNull()?.trim()?.ifEmpty { null } ?: "그만"}!\" 용감한 $c"
        "D" -> "${slots["role"] ?: "구조대원"} ${c}${wa(c)} $f"
        "A" -> "다시 해 본 ${c}${wa(c)} $f"
        "G" -> if (causeKind == "strong") "힘자랑하던 $f" else "장난꾸러기 ${f}${wa(f)} $c"
        else -> "${placeName}에서 만난 친구 $f"
    }
}

/** 표지 · 쪽 수 · 자막 */
val DemoState.pageCount: Int get() = (template?.pages?.size ?: 6)

fun DemoState.pageKind(i: Int): PageKind = if (i == 0) PageKind.COVER else template?.pages?.getOrNull(i - 1)?.kind ?: PageKind.TOGETHER

fun DemoState.bookCaption(i: Int): String =
    if (i == 0) title ?: "우리 책" else template?.pages?.getOrNull(i - 1)?.text?.invoke(this) ?: ""
