package com.example.finalproject_demo.demo

/*
 * Co-op mode's own question steps (09-30 · issue #36).
 *
 * Co-op used to walk `DIARY_STEPS`. The diary mode is being rebuilt as a picture diary with two required
 * slots, so the diary list is free to change — co-op keeps its own copy here and does not move with it.
 * Owner: co-op (안치영). The diary side must not need to touch this file, and co-op must not need `DIARY_STEPS`.
 *
 * What co-op still shares on purpose: the `DiaryStep` type and the trouble word lists (`TROUBLE_WORDS` ·
 * `TROUBLE_FEELINGS`) in `DiaryBank.kt`. The four required steps must stay place → problem → cause → solution:
 * `CoopScenes.kt` (`COOP_PART_SLOTS`) pairs the parent's four template lines with them by position, and
 * the variant ids (`diary_<bookKey>`) come from `DiaryStep`.
 *
 * Copied from `DIARY_STEPS` as of `546d8ba` (eleven steps, four required).
 */

private fun who(s: DemoState) = s.friendCallName

/** Did the child's day go wrong somewhere — "왜 그랬을까?" if so, "왜 제일 좋았어?" if not */
private fun DemoState.hadTrouble(): Boolean {
    val p = problem.orEmpty() + slots["detail"].orEmpty()
    return TROUBLE_WORDS.any { it in p } || feelings.any { it in TROUBLE_FEELINGS }
}

val COOP_STEPS: List<DiaryStep> = listOf(

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
        mascot = { Answer("오늘 있었던 곳", "오늘 있었던 곳|하루를 보냈어요") },
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
            val heardPlace = p != "오늘 있었던 곳"
            listOf(
                if (heardPlace) "${p}에서 무슨 일이 있었어?" else "오늘 무슨 일이 있었어?",
                if (heardPlace) "거기서 뭐 했어?" else "오늘은 뭐 하고 지냈어?",
                "오늘 기억나는 일 하나만 말해 줄래?",
                "오늘 마음에 남은 일이 있어?",
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
                // 협업은 「$w는 왜 …」가 같이 간 사람의 행동을 묻는 꼴이 된다 — 문제의 까닭을 묻는다 (#304 1-3 · 일기 사다리는 그대로)
                if (!it.isCoop && it.companionKind.isNotBlank() && "혼자" !in it.companionKind)
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
        mascot = { st ->
            if (st.hadTrouble()) Answer("그냥 그런 날", "잘 모르겠어|왜 그랬는지는 아직 아무도 몰라요")
            else Answer("아직 못 들은 이유", "아직 못 들은 이유|그때의 이유는 아직 듣지 못했어요")
        },
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
            if (it.hadTrouble()) listOf(
                "그래서 어떻게 됐어?",
                "그다음에 뭐 했어?",      // ⚠️ 순차 답을 유도하는 자리 — 판정이 가장 많이 틀린다 (일기 §4-4)
                "누가 도와줬어?",
                "다 끝나고 뭐 했어?",
            ) else listOf(
                "그다음엔 뭐 했어?",
                "제일 마지막에 뭐 했어?",
                "그날은 어떻게 끝났어?",
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

/** The four required steps — the progress bar and `story_ready` count only these */
val COOP_REQUIRED = COOP_STEPS.filter { it.required }

/**
 * 이 이야기에 어긋난 일이 있었나 — 이어 받기 「왜」에 사고 원인 선택지를 붙일지 정한다(CoopQuestions.kt).
 * 일기 낱말([TROUBLE_WORDS])에 협업 이야기에 자주 나오는 사고 낱말을 더한다. 못 찾으면 열린 「왜」 — 틀려도 질문은 자연스럽다
 */
internal fun DemoState.coopHadTrouble(): Boolean {
    val said = problem.orEmpty() + " " + slots["detail"].orEmpty() + " " + cause.orEmpty()
    return hadTrouble() || COOP_TROUBLE_WORDS.any { it in said }
}

private val COOP_TROUBLE_WORDS = listOf("잃어", "고장", "깨졌", "쏟", "다쳤", "부서", "망가", "놓쳤", "불이 났", "불났", "길을", "없어졌", "떨어뜨")
