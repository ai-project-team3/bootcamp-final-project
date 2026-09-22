package com.example.finalproject_demo.demo

/*
 * 일기 모드 · 부모 협업 모드의 질문 세트와 책 한 장.
 * 근거: `일기모드_설계.md`(2026-09-18 · 9/21 고침) · `부모협업모드_설계.md`(9/21) §5
 *
 * 한 줄로 (일기 §0)
 *   **일기 모드는 일기를 만들지 않는다.** 오늘 무슨 일이 있었는지 묻고,
 *   그 실제 사실을 재료로 **동화 모드와 똑같은 6~8쪽 동화책**을 만든다.
 *
 * 질문을 왜 늘렸나 (9/21 사용자 요청)
 *   처음 설계는 기승전결 네 질문뿐이었다. 네 문장으로는 책 여섯 쪽을 채울 재료가 안 되고,
 *   빈 자리를 마스코트가 메우는 만큼 **아이의 하루가 아니라 LLM의 문장이 책을 차지한다**.
 *   그래서 **네 자리는 그대로 두고 자리마다 꼬리질문을 붙였다** — 총 열한 걸음, 8~12턴.
 *   꼬리질문의 답은 슬롯 12종의 `extra`("위 어디에도 안 맞는 것 · 원문 그대로")에 쌓인다.
 *   **새 칸 이름을 만들지 않았다.** 판정 스키마도 평가셋 100개도 그대로다.
 *
 * 흐름을 어떻게 고쳤나
 *   전에는 *"왜 그랬을까?"* 가 앞뒤 없이 나왔다. 블록을 쌓았다고만 말한 아이에게 물을 데가 없는 질문이다.
 *   이제 **앞 답에서 다음 질문이 나온다**(소크라틱):
 *     - 장소를 들었으면 다음 질문이 *"{장소}에서 무슨 일이 있었어?"*
 *     - 사람을 들었으면 *"{누구}가 뭐라고 했어?"* 가 열리고, 없으면 그 걸음을 건너뛴다
 *     - 일이 어긋났을 때만 *"왜 그랬을까?"*, 그냥 즐거웠으면 *"그게 왜 제일 좋았어?"*
 */

/** 답 값은 "칸에 남길 값|책에 실을 문장" 으로 묶여 있다 (해결 칸이 이미 쓰는 방식과 같다) */
fun diarySlotOf(v: String): String = v.substringBefore('|')

/** 책 자막(-어요체). 없으면 null */
fun diaryLineOf(v: String): String? = v.substringAfter('|', "").ifEmpty { null }

/** 흔한 호칭 — 이 밖의 말은 실명으로 보고 이름 사전에 넣는다 (일기 §6 · 조사3 §3-2) */
private val COMMON_WHO = setOf(
    "친구", "친구들", "선생님", "할머니", "할아버지", "엄마", "아빠",
    "동생", "언니", "오빠", "누나", "형", "혼자", "이모", "삼촌",
)

fun isRealName(who: String): Boolean = who.isNotBlank() && who !in COMMON_WHO

/**
 * 일기 모드의 한 걸음 — 칸 하나와 그 칸을 얻기 위한 **사다리**.
 *
 * 사다리는 "말을 안 할 때 답을 고르게 하지 않고 질문을 바꾼다"는 규칙이다 (일기 §4).
 * 협업 모드에서는 같은 사다리를 **부모가 손으로 내린다** (협업 §5).
 */
class DiaryStep(
    /** 채우는 칸 — 슬롯 12종 안에서만 고른다 (guidelines/2 §1-1) */
    val slot: String,
    /** 기 · 승 · 전 · 결 · 맺음 */
    val part: String,
    /** 기승전결 네 자리인가 — 진행 막대와 `story_ready` 가 이것만 센다 */
    val required: Boolean,
    val kind: Kind,
    val probe: String,
    /** 이 걸음을 물을 조건 — 앞의 답에서 이어진다. false면 건너뛴다 */
    val ask: (DemoState) -> Boolean = { true },
    val rungs: (DemoState) -> List<String>,
    val answers: (DemoState) -> List<Answer>,
    /** 사다리가 다 떨어졌을 때 마스코트가 대신 채우는 값 (`by: mascot`). null이면 그냥 넘어간다 */
    val mascot: ((DemoState) -> Answer)? = null,
    /** 책에 쓰려고 따로 담아 두는 자리 (`extra` 로 기록되는 꼬리질문) */
    val bookKey: String = slot,
) {
    /**
     * 판정 · 수준 계산에 넘기는 변형.
     * ⚠️ [BANK]에 넣지 않는다 — 넣으면 동화 모드가 같은 칸을 물을 때 일기 질문이 섞여 나온다.
     */
    val variant: QVariant
        get() = QVariant(
            id = "diary_$bookKey", slot = slot, levels = Level.entries.toSet(), kind = kind, probe = probe,
            counts = required || slot != "extra",
            emotion = slot == "reaction",
            text = { st -> rungs(st).first() },
            easier = { st -> rungs(st).let { r -> r.getOrElse(1) { r.first() } } },
            answers = answers,
            fallback = mascot,
        )
}

private fun who(s: DemoState) = s.friendCallName

/**
 * **한 문장**이 일이 어긋난 문장인가 (9/22). 잇는 말을 고르는 데 쓴다.
 *
 * [hadTrouble] 은 하루 전체를 보지만, 두 문장을 이을 때는 **그 두 문장의 결**을 따로 봐야 한다.
 */
private fun isMishap(line: String): Boolean = listOf(
    "무너", "다툼", "싸", "넘어", "속상", "아팠", "울", "뺏", "못 ",
    "흔들리고", "쏟", "떨어뜨", "부딪", "잃", "안 됐", "말았", "깨졌", "찢",
).any { it in line }

/**
 * 두 문장을 잇는 말을 **둘의 결을 보고** 고른다 (9/22).
 *
 * 아이 답은 서로 이어지지 않을 수 있다. 앞이 이미 사고인데 반전("그런데")으로 이으면
 * *"손이 흔들리고 말았어요. **그런데** 그림책을 함께 읽었어요."* 처럼 읽다가 멈칫한다.
 * 답이 따로 놀아도 책은 한 줄기로 읽혀야 하므로, **잇는 말이 다리를 놓는다.**
 *
 * | 앞 → 뒤 | 잇는 말 |
 * |---|---|
 * | 사고 → 사고 | 게다가 |
 * | 평온 → 사고 | 그런데 (반전) |
 * | 사고 → 평온 | 그래도 (회복) |
 * | 평온 → 평온 | 그러고 나서 (시간) |
 */
private fun linkFor(prev: String, next: String): String = when {
    isMishap(prev) && isMishap(next) -> "게다가"
    !isMishap(prev) && isMishap(next) -> "그런데"
    isMishap(prev) && !isMishap(next) -> "그래도"
    else -> "그러고 나서"
}

/** 아이 말에 일이 어긋난 낌새가 있나 — 있으면 "왜 그랬을까?", 없으면 "왜 제일 좋았어?" */
private fun DemoState.hadTrouble(): Boolean {
    val p = problem.orEmpty() + slots["detail"].orEmpty()
    return listOf("무너", "다툼", "싸", "넘어", "속상", "아픔", "아팠", "울", "뺏", "못 ").any { it in p } ||
        feelings.any { it in setOf("속상했", "화났", "무서웠", "아팠", "슬펐") }
}

/**
 * 열한 걸음 — 기승전결 네 자리(필수)와 그 사이의 꼬리질문 일곱.
 *
 * | | 칸 | 묻는 것 | 필수 |
 * |---|---|---|---|
 * | 기 | `place` | 오늘 어디 있었어 | ✅ |
 * | 기 | `companion` | 거기 누구랑 있었어 | |
 * | 승 | `problem` | 무슨 일이 있었어 | ✅ |
 * | 승 | `extra`(detail) | 그때 뭐 하고 있었어 | |
 * | 승 | `reaction` | 기분이 어땠어 | |
 * | 전 | `cause` | 왜 그랬을까 (**S1**) | ✅ |
 * | 전 | `extra`(said) | 그 사람이 뭐라고 했어 | |
 * | 전 | `extra`(try) | 나는 어떻게 해 봤어 | |
 * | 결 | `solution` | 그래서 어떻게 됐어 (S2) | ✅ |
 * | 결 | `extra`(after) | 다 끝나고 뭐 했어 | |
 * | 맺음 | `extra`(keep) | 내일 또 하고 싶은 거 | |
 */
val DIARY_STEPS: List<DiaryStep> = listOf(

    // ── 기 ─────────────────────────────────────────────────────
    DiaryStep(
        slot = "place", part = "기", required = true, kind = Kind.EASY,
        probe = "기준 질문 ① · 오늘 있었던 곳 (기억 인출)",
        rungs = {
            listOf(
                "오늘 어디 갔었어?",
                // 선택지 먼저, 질문 마지막 · 순서를 매번 섞는다 (일기 §4-5)
                listOf("어린이집", "놀이터", "할머니 집").shuffled().joinToString(", ") + ". 오늘은 어디 있었어?",
                "아침에 밥 먹고 어디 갔어?",
            )
        },
        answers = {
            listOf(
                Answer("어린이집!", "어린이집|어린이집에 갔어요", lv = 1),
                Answer("놀이터!", "놀이터|놀이터에 갔어요", lv = 1),
                Answer("할머니 집 갔어.", "할머니 집|할머니 집에 갔어요", lv = 2),
                Answer("공원에서 놀았어.", "공원|공원에 갔어요", lv = 2),
                Answer("어린이집 갔다가 놀이터 갔어.", "놀이터|어린이집에 갔다가 놀이터에도 갔어요", con = true, lv = 2),
                Answer("놀이터! 미끄럼틀 타고 싶어서 갔어.", "놀이터|미끄럼틀이 타고 싶어서 놀이터에 갔어요", reason = true, el = setOf("계기"), con = true, lv = 3),
                Answer("할머니 집. 엄마가 늦게 온다고 해서 거기 있었어.", "할머니 집|엄마가 늦게 오는 날이라 할머니 집에서 기다렸어요", reason = true, el = setOf("배경"), con = true, lv = 3),
            )
        },
        // ⚠️ 구체적인 곳을 지어내지 않는다 — 아이가 가지 않은 곳이 그 아이의 하루로 적히면 안 된다 (일기 §3-2)
        mascot = { Answer("오늘 있었던 곳", "오늘 있었던 곳|오늘 하루를 보냈어요") },
    ),

    DiaryStep(
        slot = "companion", part = "기", required = false, kind = Kind.EASY,
        probe = "누구와 있었나 — 뒤의 질문이 물을 데를 얻는다",
        rungs = {
            val p = it.placeName
            listOf(
                "거기 누구랑 있었어?",
                "혼자 있었어, 아니면 같이 있었어?",
                "${p}에서 만난 사람 있어?",
            )
        },
        answers = {
            listOf(
                Answer("친구랑!", "친구|친구와 함께 있었어요", lv = 1, kind = "친구"),
                Answer("혼자 있었어.", "혼자|혼자서 놀았어요", lv = 1, kind = "혼자"),
                Answer("선생님이랑 있었어.", "선생님|선생님과 함께 있었어요", lv = 2, kind = "선생님"),
                Answer("할머니랑 있었어.", "할머니|할머니와 함께 있었어요", lv = 2, kind = "할머니"),
                Answer("민준이랑 놀았어.", "민준이|민준이와 함께 놀았어요", lv = 2, kind = "민준이"),
                Answer("친구들 많았어. 다 같이 놀았어.", "친구들|친구들과 다 같이 놀았어요", el = setOf("배경"), con = true, lv = 3, kind = "친구들"),
                Answer("동생이랑 있었어. 동생이 자꾸 따라와서.", "동생|동생과 함께 있었어요", reason = true, el = setOf("배경"), con = true, lv = 3, kind = "동생"),
            )
        },
        mascot = null,   // 사람은 지어내지 않는다. 안 들으면 그냥 넘어간다
    ),

    // ── 승 ─────────────────────────────────────────────────────
    DiaryStep(
        slot = "problem", part = "승", required = true, kind = Kind.HARD,
        probe = "기준 질문 ② · 오늘 있었던 일",
        rungs = {
            val p = it.placeName
            listOf(
                "${p}에서 무슨 일이 있었어?",          // 앞 답(장소)을 되받아 잇는다
                "거기서 뭐 하고 놀았어?",
                "오늘 제일 재밌었던 거 하나만 말해 줄래?",
                listOf("속상한 일도 있었어?", "다 재밌었어?").shuffled().joinToString(" 아니면 "),
            )
        },
        answers = {
            listOf(
                Answer("블록 쌓았어.", "블록 놀이|블록을 하나하나 높이 쌓았어요", lv = 1),
                Answer("미끄럼틀 탔어!", "미끄럼틀|미끄럼틀을 쌩쌩 탔어요", lv = 1),
                Answer("그림 그렸어.", "그림 그리기|크레용으로 그림을 그렸어요", lv = 2),
                Answer("책 읽어 줬어.", "책 읽기|그림책을 함께 읽었어요", lv = 2),
                Answer("블록 쌓았는데 무너졌어.", "블록이 무너짐|높이 쌓은 블록이 와르르 무너졌어요", el = setOf("결과"), con = true, lv = 3),
                Answer("장난감 때문에 다퉜어. 속상했어.", "장난감 때문에 다툼|장난감을 같이 쓰지 못해 마음이 속상했어요", reason = true, el = setOf("계기"), emo = "속상했", lv = 3),
                Answer("달리다가 넘어져서 무릎 아팠어.", "넘어짐|달리다가 넘어져 무릎이 아팠어요", el = setOf("결과"), emo = "아팠", con = true, lv = 3),
            )
        },
        mascot = { Answer("아직 못 들은 오늘 이야기", "아직 못 들은 일|오늘 있었던 일은 아직 다 듣지 못했어요") },
    ),

    DiaryStep(
        slot = "extra", bookKey = "detail", part = "승", required = false, kind = Kind.EASY,
        probe = "장면을 더 채우나 (S2 배경 · 시도)",
        rungs = {
            val c = it.childName
            listOf(
                "그때 $c${eun(c)} 뭐 하고 있었어?",
                "어떻게 했는지 얘기해 줄래?",
                "그거 하는 거 보여 줄 수 있어?",
            )
        },
        answers = {
            val c = it.childName
            listOf(
                Answer("그냥 했어.", "", lv = 1),
                Answer("높이높이 쌓았어.", "높이높이 쌓았어|$c${eun(c)} 블록을 높이높이 쌓아 올렸어요", el = setOf("시도"), lv = 1),
                Answer("빨리 내려왔어!", "빨리 내려왔어|$c${eun(c)} 미끄럼틀을 쌩 하고 빨리 내려왔어요", el = setOf("시도"), lv = 2),
                Answer("친구랑 같이 했어.", "같이 했어|친구와 나란히 앉아 함께했어요", el = setOf("배경"), lv = 2),
                Answer("제일 큰 거 밑에 놓고 작은 거 위에 올렸어.", "큰 것부터 쌓았어|큰 것을 아래에, 작은 것을 위에 차곡차곡 올렸어요", el = setOf("시도"), con = true, lv = 3),
                Answer("조심조심했는데 손이 흔들렸어. 그래서 무너졌어.", "손이 흔들렸어|조심조심했지만 손이 흔들리고 말았어요", reason = true, el = setOf("시도", "결과"), con = true, lv = 3),
            )
        },
    ),

    DiaryStep(
        slot = "reaction", part = "승", required = false, kind = Kind.EASY,
        probe = "마음 말하기 (신호 아님 · 부모 기록 재료)",
        // 이미 마음을 말했으면 또 묻지 않는다 — 같은 걸 두 번 묻는 게 흐름을 깬다
        ask = { it.reaction == null },
        rungs = {
            val c = it.childName
            listOf(
                "그때 기분이 어땠어?",
                "마음이 어땠어? 좋았어, 속상했어?",
                "$c 얼굴이 어땠을 것 같아?",
            )
        },
        answers = {
            listOf(
                Answer("좋았어!", "기뻤어|참 기분이 좋았어요", emo = "기뻤", lv = 1),
                Answer("속상했어.", "속상했어|마음이 속상했어요", emo = "속상했", lv = 1),
                Answer("신났어! 또 하고 싶었어.", "신났어|신이 나서 또 하고 싶었어요", emo = "신났", con = true, lv = 2),
                Answer("좀 무서웠어.", "무서웠어|조금 무서운 마음이 들었어요", emo = "무서웠", lv = 2),
                Answer("처음엔 속상했는데 나중엔 괜찮아졌어.", "속상했다 괜찮아졌어|처음엔 속상했지만 나중엔 마음이 풀렸어요", emo = "속상했", el = setOf("결과"), con = true, lv = 3),
                Answer("뿌듯했어. 내가 제일 높이 쌓았으니까.", "뿌듯했어|스스로가 자랑스러워 마음이 뿌듯했어요", emo = "뿌듯했", reason = true, con = true, lv = 3),
            )
        },
    ),

    // ── 전 ─────────────────────────────────────────────────────
    DiaryStep(
        slot = "cause", part = "전", required = true, kind = Kind.HARD,
        probe = "'왜' 질문에 까닭을 담나 (S1)",
        rungs = {
            val w = who(it)
            val c = it.childName
            // 일이 어긋났을 때만 "왜 그랬을까?" — 그냥 즐거웠던 날에 물으면 물을 데가 없다
            if (it.hadTrouble()) listOf(
                "왜 그렇게 됐을까?",
                if (it.companionKind.isNotBlank() && "혼자" !in it.companionKind)
                    "$w${eun(w)} 왜 그랬을 것 같아?"
                else "무엇 때문에 그런 일이 생겼을까?",
                "그 일이 생기기 전에 무슨 일이 있었어?",
            ) else listOf(
                "그게 왜 제일 좋았어?",
                "$c${eun(c)} 그거 할 때 왜 신났을까?",
                "또 하고 싶으면 왜 그런 걸까?",
            )
        },
        answers = {
            val c = it.childName
            listOf(
                Answer("몰라.", "", lv = 1),
                Answer("그냥.", "", lv = 1),
                Answer("실수로 그런 거야.", "실수였어|실수였다고 했어요", reason = true, lv = 2),
                Answer("블록을 너무 높이 쌓아서 무너졌어.", "너무 높이 쌓아서|블록을 너무 높이 쌓아 무너졌어요", reason = true, el = setOf("계기"), con = true, lv = 3),
                Answer("나 울었어. 그래서 선생님이 왔어.", "울어서 선생님이 왔어|$c${ga(c)} 울자 선생님이 다가왔어요", el = setOf("시도", "결과"), con = true, lv = 2),
                Answer("재밌으니까! 빠르니까 좋아.", "빨라서 재밌었어|빠르게 내려가는 게 신나서 좋았어요", reason = true, con = true, lv = 2),
                Answer("나랑 놀고 싶어서 그랬나 봐.", "같이 놀고 싶었어|같이 놀고 싶어서 그랬대요", reason = true, con = true, lv = 3),
                Answer("화났나 봐. 내가 장난감 안 빌려줘서.", "화가 났어|장난감을 빌려주지 않아서 화가 났대요", reason = true, el = setOf("계기"), con = true, lv = 3),
            )
        },
        mascot = { Answer("그냥 그런 날", "잘 모르겠어|왜 그랬는지는 아직 아무도 몰라요") },
    ),

    DiaryStep(
        slot = "extra", bookKey = "said", part = "전", required = false, kind = Kind.HARD,
        probe = "남의 말을 옮기나 (S2 배경 · 상황 이해)",
        // 사람이 나온 날에만 묻는다. 혼자 논 날에 "그 친구가 뭐라고 했어?"는 물을 데가 없다
        ask = { it.companionKind.isNotBlank() && "혼자" !in it.companionKind },
        rungs = {
            val w = who(it)
            listOf(
                "$w${ga(w)} 뭐라고 했어?",
                "$w${eun(w)} 그때 어떻게 했어?",
                "$w 얼굴이 어땠어?",
            )
        },
        answers = {
            val w = who(it)
            listOf(
                Answer("몰라.", "", lv = 1),
                Answer("미안하다고 했어.", "미안하다고 했어|$w${ga(w)} \"미안해\" 하고 말했어요", el = setOf("결과"), lv = 2),
                Answer("같이 하자고 했어!", "같이 하자고 했어|$w${ga(w)} \"같이 하자\" 하고 손을 내밀었어요", el = setOf("시도"), lv = 2),
                Answer("괜찮냐고 물어봤어.", "괜찮냐고 물었어|$w${ga(w)} 괜찮은지 물어봐 주었어요", el = setOf("시도"), lv = 2),
                Answer("웃었어. 나도 같이 웃었어.", "같이 웃었어|둘은 마주 보고 함께 웃었어요", el = setOf("결과"), con = true, lv = 3),
                Answer("아무 말도 안 하고 그냥 갔어. 속상했어.", "아무 말 없이 갔어|$w${eun(w)} 아무 말 없이 가 버렸어요", el = setOf("결과"), emo = "속상했", con = true, lv = 3),
            )
        },
    ),

    /**
     * 9/21에 더한 걸음 — 전(轉)이 "왜"와 "뭐라고 했어" 둘뿐이라 **아이가 한 일**이 비어 있었다.
     * 이야기가 기승전결로 서려면 어긋난 일과 끝 사이에 **아이의 시도**가 한 문장 있어야 한다.
     */
    DiaryStep(
        slot = "extra", bookKey = "try", part = "전", required = false, kind = Kind.HARD,
        probe = "스스로 한 시도를 말하나 (S2 시도)",
        rungs = {
            val c = it.childName
            if (it.hadTrouble()) listOf(
                "그래서 $c${eun(c)} 어떻게 했어?",
                "그때 $c${ga(c)} 제일 먼저 한 게 뭐야?",
                "$c${ga(c)} 누구한테 말했어?",
            ) else listOf(
                "$c${ga(c)} 제일 열심히 한 게 뭐야?",
                "그거 할 때 $c${ga(c)} 뭘 제일 조심했어?",
                "$c${ga(c)} 혼자 했어, 같이 했어?",
            )
        },
        answers = {
            val c = it.childName
            val w = who(it)
            listOf(
                Answer("몰라.", "", lv = 1),
                Answer("그냥 있었어.", "가만히 있었어|$c${eun(c)} 잠깐 가만히 서 있었어요", lv = 1),
                Answer("선생님한테 갔어.", "선생님에게 갔어|$c${eun(c)} 선생님에게 달려가 이야기했어요", el = setOf("시도"), lv = 2),
                Answer("다시 해 봤어.", "다시 해 봤어|$c${eun(c)} 포기하지 않고 다시 해 보았어요", el = setOf("시도"), lv = 2),
                Answer("${w}한테 같이 하자고 했어.", "같이 하자고 했어|$c${ga(c)} $w${ege(w)} \"같이 하자\" 하고 말했어요", el = setOf("시도"), con = true, lv = 3),
                Answer("천천히 다시 했어. 빨리 하면 또 그럴까 봐.", "천천히 다시 했어|이번엔 천천히 해 보기로 했어요", reason = true, el = setOf("시도"), con = true, lv = 3),
            )
        },
    ),

    // ── 결 ─────────────────────────────────────────────────────
    DiaryStep(
        slot = "solution", part = "결", required = true, kind = Kind.HARD,
        probe = "결과 · 시도를 스스로 잇나 (S2)",
        rungs = {
            listOf(
                "그래서 어떻게 됐어?",
                "그다음에 뭐 했어?",      // ⚠️ 순차 답을 유도하는 자리 — 판정이 가장 많이 틀린다 (일기 §4-4)
                "누가 도와줬어?",
                "다 끝나고 뭐 했어?",
            )
        },
        answers = {
            val w = who(it)
            listOf(
                Answer("몰라.", "", lv = 1),
                Answer("선생님!", "선생님이 도와줌|선생님이 도와주었어요", lv = 1),
                // ⚠️ 아래 둘은 까닭이 아니라 **순차 답**이다. 차례를 말했을 뿐이라 S1이 아니다 (일기 §4-4 · 평가셋 §2-8)
                Answer("가서 밥 먹었어.", "그다음에 밥을 먹음|그러고 나서 맛있는 밥을 먹었어요", con = true, lv = 2),
                Answer("일어나서 또 탔어.", "일어나서 또 탔음|자리에서 일어나 한 번 더 탔어요", con = true, lv = 2),
                Answer("다시 쌓았어! 이번엔 안 무너졌어.", "다시 쌓았어|다시 쌓은 블록은 이번엔 무너지지 않았어요", el = setOf("시도", "결과"), con = true, lv = 3),
                Answer("${w}랑 같이 하기로 했어. 그래서 또 놀았어.", "같이 하기로 했어|$w${wa(w)} 같이 하기로 하고 다시 즐겁게 놀았어요", el = setOf("시도", "결과"), con = true, lv = 3),
            )
        },
        mascot = { Answer("아직 못 들은 뒷이야기", "아직 못 들은 뒷이야기|그 뒤에 어떻게 되었는지는 아직 듣지 못했어요") },
    ),

    DiaryStep(
        slot = "extra", bookKey = "after", part = "결", required = false, kind = Kind.EASY,
        probe = "하루를 끝까지 잇나 (A1)",
        rungs = {
            listOf(
                "집에 와서는 뭐 했어?",
                "저녁에 뭐 했어?",
                "자기 전에 뭐 했어?",
            )
        },
        answers = {
            val c = it.childName
            listOf(
                Answer("몰라.", "", lv = 1),
                Answer("밥 먹었어.", "밥 먹었어|집에 돌아와 저녁을 맛있게 먹었어요", lv = 1),
                Answer("티비 봤어.", "티비 봤어|집에 돌아와 텔레비전을 보았어요", lv = 1),
                Answer("목욕하고 책 읽었어.", "목욕하고 책 읽었어|목욕을 하고 그림책을 읽었어요", con = true, lv = 2),
                Answer("엄마한테 오늘 있었던 거 다 얘기했어.", "엄마에게 이야기했어|$c${eun(c)} 오늘 있었던 일을 하나하나 이야기해 주었어요", el = setOf("결과"), con = true, lv = 3),
            )
        },
    ),

    // ── 맺음 ───────────────────────────────────────────────────
    DiaryStep(
        slot = "extra", bookKey = "keep", part = "맺음", required = false, kind = Kind.HARD,
        probe = "돌아보고 앞을 내다보나 (S1)",
        rungs = {
            listOf(
                "오늘 중에 내일 또 하고 싶은 거 있어?",
                "오늘 제일 기억에 남는 게 뭐야?",
                "내일은 뭐 하고 싶어?",
            )
        },
        answers = {
            val c = it.childName
            listOf(
                Answer("몰라.", "", lv = 1),
                Answer("또 미끄럼틀!", "또 미끄럼틀|내일도 미끄럼틀을 타고 싶어요", lv = 1),
                Answer("블록 또 하고 싶어.", "블록 또 하고 싶어|내일도 블록을 쌓고 싶어요", lv = 2),
                Answer("친구랑 또 놀고 싶어.", "친구랑 또 놀고 싶어|내일도 친구와 함께 놀고 싶어요", lv = 2),
                Answer("내일은 안 무너지게 쌓을 거야. 밑을 튼튼하게 하면 돼.", "튼튼하게 쌓을 거야|내일은 아래를 튼튼하게 해서 쌓기로 마음먹었어요", reason = true, el = setOf("시도"), con = true, lv = 3),
                Answer("$c${ga(c)} 먼저 같이 놀자고 할 거야.", "먼저 말 걸 거야|내일은 먼저 \"같이 놀자\" 하고 말해 보기로 했어요", el = setOf("시도"), reason = true, con = true, lv = 3),
            )
        },
    ),
)

/** 기승전결 네 자리 — 진행 막대와 `story_ready` 가 이것만 센다 */
val DIARY_REQUIRED = DIARY_STEPS.filter { it.required }

/** One coherent demo day. Each reply also exists in the normal scripted answers. */
private val DIARY_DEMO_REPLY = mapOf(
    "place" to "어린이집!",
    "companion" to "민준이랑 놀았어.",
    "problem" to "블록 쌓았는데 무너졌어.",
    "detail" to "높이높이 쌓았어.",
    "reaction" to "속상했어.",
    "cause" to "블록을 너무 높이 쌓아서 무너졌어.",
    "said" to "괜찮냐고 물어봤어.",
    "try" to "다시 해 봤어.",
    "solution" to "다시 쌓았어! 이번엔 안 무너졌어.",
    "after" to "목욕하고 책 읽었어.",
    "keep" to "내일은 안 무너지게 쌓을 거야. 밑을 튼튼하게 하면 돼.",
)

fun DiaryStep.demoAnswer(s: DemoState): Answer? =
    DIARY_DEMO_REPLY[bookKey]?.let { text -> answers(s).firstOrNull { it.text == text } }

/** 순차 답인가 — 잇는 말은 있는데 까닭이 없다. 여기서 S1로 세면 안 된다 (일기 §4-4) */
fun Answer.isSequential(): Boolean = con && !reason && el.isEmpty()

// ── 책 한 장 (일기 §5 · §7-1 ①) ───────────────────────────────────

/**
 * 일기 책의 쪽 구성 — **6~8쪽** (기획안: 기 1 / 승 1~2 / 전 2 / 결 1 / 에필로그 1).
 *
 * 꼬리질문으로 모은 것이 쪽마다 **한 문장씩 더** 들어간다. 그게 질문을 늘린 이유다 —
 * 같은 여섯 쪽이라도 마스코트가 메운 문장이 줄고 아이가 한 말이 늘어난다.
 *
 * 빈 자리는 이야기로 메우고 `by: mascot` 으로 남긴다. 책 자막에는 보이지만
 * 주고받기 횟수 · 수준 신호 · 리포트 원문 인용에는 들어가지 않는다 (일기 §5-1).
 */
fun diaryTemplate(s: DemoState): StoryTemplate {
    // 등장 쪽이 따로 서면 "누구랑 있었어" 문장은 그쪽으로 보낸다 — 같은 말이 두 쪽에 겹치지 않게
    val hasMeetPage = s.friendOrPartnerArt != null
    val pages = buildList {
        // ── 기 · 어디에 누구랑 ──────────────────────────────────
        add(
            PageSpec(PageKind.DEPART) { st ->
                line(st, "place", "${st.childName}${eun(st.childName)} 오늘 밖에 나갔어요.", "${st.childName}${eun(st.childName)} 오늘 ") +
                    if (hasMeetPage) "" else tail(st, "companion")
            }
        )
        if (hasMeetPage) {
            add(
                PageSpec(PageKind.MEET) { st ->
                    val drawn = if (st.drawing.isNotEmpty()) " ${st.childName}${ga(st.childName)} 직접 그려 준 모습이에요." else ""
                    val f = st.friendCallName
                    (st.slots["companion"]?.let { if (it.last() in ".!?") it else "$it." } ?: "거기서 $f${eul(f)} 만났어요.") + drawn
                }
            )
        }
        // ── 승 · 무엇을 하다가 무슨 일이 ────────────────────────
        //
        // ⚠️ 차례가 중요하다. 전에는 일(결과)을 먼저 쓰고 하던 일(시도)을 뒤에 붙여서
        //    "블록이 와르르 무너졌어요. 지호는 블록을 높이 쌓아 올렸어요." 처럼 **거꾸로** 읽혔다 (9/21 "중구난방").
        //    하던 일 → 그런데 → 일어난 일 순서로 놓고, 사이에 잇는 말을 넣는다.
        add(
            PageSpec(PageKind.SHAKE) { st ->
                val did = st.slots["detail"]?.takeIf { it.isNotBlank() }
                val what = line(st, "problem", "이런저런 일이 있었어요.")
                if (did == null) what
                // 9/22 — 잇는 말을 **두 문장의 결**로 고른다. 전에는 하루 전체가 사고면 무조건 "그런데" 라,
                //         앞이 이미 사고인데 또 반전으로 이어 붙어 문장이 따로 놀았다 (linkFor)
                else sentence(did) + " " + joinWith(linkFor(did, what), what)
            }
        )
        if (s.reaction != null) {
            add(PageSpec(PageKind.FAIL) { st -> joinWith("그때", line(st, "reaction", "마음이 오래 남았어요.")) })
        }
        // ── 전 · 왜 그랬을까 · 뭐라고 했을까 · 그래서 나는 ───────
        add(
            PageSpec(PageKind.TALK) { st ->
                // 까닭 쪽 — 일이 어긋난 날에만 "알고 보니"로 연다. 그냥 즐거웠던 날엔 붙이지 않는다
                val lead = if (st.hadTrouble()) "알고 보니" else ""
                // 9/22 — 빈 칸을 메우는 문장도 그날에 맞춰야 한다. 아무 일도 없던 날에
                // "왜 그랬는지는 아직 아무도 몰라요" 가 붙으면 없던 사고가 있었던 것처럼 읽힌다
                val noCause = if (st.hadTrouble()) "왜 그랬는지는 아직 아무도 몰라요." else "오늘은 그게 제일 좋았어요."
                joinWith(lead, line(st, "cause", noCause)) + tail(st, "said")
            }
        )
        // 전(2) · 그래서 나는 이렇게 해 봤다 — **미션 1이 곧 이 자리의 행동이다** (9/22).
        //
        // 전에는 아이 말 뒤에 미션 자막을 통째로 붙여서 한 쪽에 아이 이름이 두 번 나오고
        // 문장 둘이 따로 놀았다 ("그래서 지호는 다시 쌓았어요. 지호는 모래를 치웠어요.").
        // 이제 아이가 한 말이 있으면 **주어를 빼고 이어 붙인다** — 한 사람이 이어서 한 일로 읽힌다.
        add(
            PageSpec(PageKind.RUB) { st ->
                val tried = st.slots["try"]?.takeIf { it.isNotBlank() }
                if (tried == null) st.m1Caption()
                else joinWith("그래서", sentence(tried)) + " 그러고는 " + st.m1Caption(withSubject = false)
            }
        )
        // ── 결 · 그래서 어떻게 됐나 ─────────────────────────────
        // 미션 2도 같은 방식이다. "마침내 …" 한 문장에 절로 이어 붙어 결(結) 한 쪽이 갈라지지 않는다
        add(
            PageSpec(PageKind.DRAG) { st ->
                // 9/22 — 두 갈래를 합쳤다.
                //
                //  ① 결말 칸이 비었으면 "하루가 저물었어요" 를 쓰지 않는다 — **맺음 쪽과 겹쳐**
                //     하루가 두 번 저물었다. 비면 이 쪽은 건네주는 장면 하나로만 둔다
                //  ② 마스코트가 메운 결말에는 **"마침내" 를 붙이지 않는다** (main · 최민우).
                //     아이가 지어낸 결말이 아닌데 "마침내" 를 붙이면 책이 아이 말인 척한다
                val sol = st.slots["solution"]?.takeIf { it.isNotBlank() }
                val give = st.m2Clause()
                if (sol == null) "${st.childName}${eun(st.childName)} $give"
                else {
                    val head = sentence(sol)
                    val lead = if (st.slotBy["solution"] == "mascot") head else joinWith("마침내", head)
                    "$lead 그리고 $give"
                }
            }
        )
        // ── 맺음 · 하루의 끝과 내일 ─────────────────────────────
        add(
            PageSpec(PageKind.TOGETHER) { st ->
                // 9/22 — **차례를 바로잡았다.** 전에는 "그렇게 하루가 저물었어요. 집에 와서 손을 씻었어요."
                // 처럼 하루가 끝난 **뒤에** 집에 온 일이 적혔다. 집에 와서 한 일(`after`)이 먼저고,
                // 맺는 문장이 그 뒤다. 내일 이야기(`keep`)만 맺음 뒤에 온다.
                val after = st.slots["after"]?.takeIf { it.isNotBlank() }?.let { "${sentence(it)} " }.orEmpty()
                val keep = st.slots["keep"]?.takeIf { it.isNotBlank() }?.let { " ${sentence(it)}" }.orEmpty()
                // ⚠️ `$after그렇게` 로 쓰면 Kotlin이 한 변수 이름으로 읽는다 (한글도 식별자 문자다)
                "${after}그렇게 ${st.childName}의 하루가 저물었어요.$keep".trim()
            }
        )
    }
    return StoryTemplate(
        key = "N", code = "N", name = coopBookName(s), level = s.level,
        plot = emptyList(), ending = emptyList(),
        pages = pages,
        shape = "기(어디·누구랑) → 승(뭐 하다가 무슨 일) → 전(왜·뭐라고·그래서 나는) → 결(어떻게 됐어) → 맺음 · ${pages.size}쪽",
    )
}

/** 문장 끝에 마침표를 붙인다 (이미 있으면 그대로) */
private fun sentence(v: String): String = if (v.isNotEmpty() && v.last() in ".!?") v else "$v."

/**
 * 잇는 말을 앞에 붙인다 — "그런데", "그래서", "그때" …
 *
 * 이미 그 말로 시작하는 문장에는 또 붙이지 않는다. 아이가 "그래서 다시 쌓았어" 라고 말한 날
 * "그래서 그래서 다시 쌓았어요" 가 되면 안 된다.
 */
private val LINKERS = listOf("그런데", "그래서", "그리고", "그때", "그러고", "그러자", "알고 보니", "하지만", "마침내")

private fun joinWith(linker: String, line: String): String {
    val t = line.trimStart()
    if (linker.isEmpty() || t.isEmpty()) return t
    if (LINKERS.any { t.startsWith(it) }) return t
    return "$linker $t"
}

/** 칸에 딸린 책 문장. 아직 없으면 미리 만들어 둔 문장 (책은 언제나 나온다 · 구현대본 §5) */
private fun line(s: DemoState, key: String, fallback: String, prefix: String = ""): String {
    val v = s.slots[key]?.takeIf { it.isNotBlank() } ?: return fallback
    val t = prefix + v
    return if (t.last() in ".!?") t else "$t."
}

/** 꼬리질문으로 얻은 한 문장을 뒤에 붙인다 — 없으면 아무것도 안 붙는다 */
private fun tail(s: DemoState, key: String): String {
    val v = s.slots[key]?.takeIf { it.isNotBlank() } ?: return ""
    return " " + if (v.last() in ".!?") v else "$v."
}

// pageAuthor() 는 지웠다 — 새 협업에서 모든 쪽이 🧒 로 같아져 표시가 뜻을 잃었다 (09-22)

/** 제목 — 아이에게 묻지 않고 지어 준다 (동화 모드와 같다) */
fun DemoState.diaryTitle(): String {
    val p = placeName
    val f = friendName.takeUnless { it.startsWith("{") }
        ?: companionKind.takeUnless { it.isBlank() || "혼자" in it || !isRealName(it) }
    return when {
        f != null -> "${p}에서 만난 $f"
        p != "오늘 있었던 곳" -> "${childName}의 $p 하루"
        else -> "${childName}의 오늘 하루"
    }
}
