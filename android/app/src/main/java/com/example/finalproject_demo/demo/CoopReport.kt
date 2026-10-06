package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.coopKind
import com.example.finalproject_demo.ui.reasonOrNull
import com.example.finalproject_demo.ui.templateQuestions

/*
 * 같이 만들기 — 부모 리포트의 말 (10-03 실기기).
 *
 * 「곧 체험해요」로 고른 소방관 이야기인데 리포트가 「오늘 있었던 일로 · 어른이 넣어 둔 질문으로 지은 책이에요」,
 * 「어른이 넣어 둔 질문에 한 답」, 놀이 카드 「오늘 ○○에서 제일 재밌었던 게 뭐였어?」라고 적었다 —
 * 아직 안 간 곳을 다녀온 것처럼, 오또가 물은 템플릿 질문을 부모가 넣은 것처럼 보였다.
 * 그래서 고른 이유(다녀왔어요 · 곧 해요 · 좋아해요)와 부모 질문을 썼는지로 말을 가른다.
 * 리포트를 열 때는 `coopPick` 이 이미 비어 있다(`clearParentQuestions`) — 이야기를 시작할 때 남겨 둔 것을 읽는다.
 */
data class CoopReportCopy(
    /** 책 제목 아래 한 줄 — 무엇으로 지은 책인가 */
    val madeFrom: String,
    /** 제목 줄의 「누구 질문으로 · 」 */
    val who: String,
    /** 아이 답 모음 칸 제목 · 부제 */
    val askedTitle: String,
    val askedSub: String,
    /** 다음에 넣어 볼 질문 하나와 그 까닭 */
    val nextQuestion: String,
    val nextWhy: String,
    /** 오늘 이야기로 해 볼 놀이 질문 3장 — null 이면 일기 모드 것을 그대로 쓴다 */
    val playCards: List<String>?,
    /** 「같은 질문에 한 답」 카드의 질문 — 고른 이야기의 첫 질문. null 이면 일기 것(「오늘 어디 갔었어?」) */
    val firstQuestion: String? = null,
    /** 고른 이야기로 지었는가 — 「함께하기」 축의 말이 갈린다 */
    private val storyName: String? = null,
) {
    /** 「함께하기」 축 — 부모 질문을 안 썼으면 「0개로 이야기했어요」 대신 고른 이야기로 지었다고 (10-03 실기기) */
    fun together(parentAsked: Int): String = when {
        parentAsked > 0 -> "어른이 넣어 둔 질문 ${parentAsked}개로 이야기했어요"
        storyName != null -> "부모님이 고른 ‘$storyName’ 이야기로 함께 지었어요"
        else -> "어른이 넣어 둔 질문으로 이야기했어요"
    }
}

fun DemoState.coopReportCopy(): CoopReportCopy {
    val pick = coopStoryPick
    val parent = coopParentUsed > 0
    val defaultWhy = "“재밌었어?” 처럼 예/아니오로 끝나는 질문 대신 이렇게 넣어 보세요. 의문사는 하나만, 선택지는 질문보다 먼저."
    if (pick == null) return CoopReportCopy(
        madeFrom = "오늘 있었던 일로 · 어른이 넣어 둔 질문으로 지은 책이에요",
        who = "어른 질문으로 · ",
        askedTitle = "어른이 넣어 둔 질문에 한 답",
        askedSub = "넣은 순서대로 · 아이가 말한 것만 따옴표",
        nextQuestion = "오늘 제일 재밌었던 거 하나만 말해 줄래?",
        nextWhy = defaultWhy,
        playCards = null,
    )
    val reason = pick.reasonOrNull() ?: CoopReason.DREAM
    val label = pick.reasonOrNull()?.let { coopKind(pick.kind)?.reasonLabels?.get(it) }
    val name = pick.name.trim()
    val story = "‘$name’${label?.let { " · $it" } ?: ""}"
    return CoopReportCopy(
        madeFrom = when (reason) {
            CoopReason.DONE -> "$story — 있었던 일로 지은 책이에요"
            CoopReason.SOON -> "$story — 앞으로 할 일을 미리 그려 본 책이에요"
            CoopReason.DREAM -> "$story — 상상으로 지은 책이에요"
        } + if (parent) " · 넣어 둔 질문도 함께 물었어요" else "",
        who = if (parent) "고른 이야기 · 어른 질문으로 · " else "고른 이야기로 · ",
        askedTitle = if (parent) "고른 이야기 질문 · 넣어 둔 질문에 한 답" else "고른 이야기 질문에 한 답",
        askedSub = "물은 순서대로 · 아이가 말한 것만 따옴표",
        nextQuestion = when (reason) {
            CoopReason.DONE -> "오늘 제일 재밌었던 거 하나만 말해 줄래?"
            CoopReason.SOON -> "$name 이야기에서 제일 궁금한 게 뭐야?"
            CoopReason.DREAM -> "이 이야기 다음엔 무슨 일이 생길까?"
        },
        nextWhy = when (reason) {
            CoopReason.DONE -> defaultWhy
            CoopReason.SOON -> "아직 안 해 본 일이라 “뭐 했어?” 대신 “~할까?” “~궁금해?” 처럼 앞으로의 말로 물어보세요. 다녀온 뒤 「${coopKind(pick.kind)?.reasonLabels?.get(CoopReason.DONE) ?: "다녀왔어요"}」로 한 번 더 만들면 생각한 것과 견줄 수 있어요."
            CoopReason.DREAM -> "상상 이야기라 정답이 없어요. “~했을까?” “~할까?” 처럼 열어 두면 아이가 더 길게 지어요."
        },
        firstQuestion = pick.templateQuestions().firstOrNull(),
        storyName = name,
        playCards = when (reason) {
            CoopReason.DONE -> null
            CoopReason.SOON -> listOf(
                "\"$name 이야기에서 제일 해 보고 싶은 게 뭐야?\"",
                "\"거기서 누구를 만날 것 같아?\"",
                "\"다녀오면 오늘 지은 이야기랑 뭐가 다를까?\"",
            )
            CoopReason.DREAM -> listOf(
                "\"$name 이야기에서 뭐가 제일 좋아?\"",
                "\"이 이야기 다음엔 무슨 일이 생길까?\"",
                "\"네가 이야기 속에 들어가면 뭐 할까?\"",
            )
        },
    )
}

// ── 아이 화면 · 책의 말 — 일기 모드 문구(「오늘 만난」)를 협업 곧 해요 · 좋아해요에 쓰지 않는다 (10-03 실기기) ──

/** 협업에서 고른 이유 — 일기 · 고른 이야기 없음 · 다녀왔어요면 null(일기 문구 그대로) */
private val DemoState.coopNotYet: CoopReason?
    get() = if (!isCoop) null else bookPick?.let { it.reasonOrNull() ?: CoopReason.DREAM }?.takeIf { it != CoopReason.DONE }

/** 그리기 안내 — 「오늘 만난 엄마와 아빠를 그려 줄래?」 대신 */
fun DemoState.coopDrawLine(who: String): String? = when (coopNotYet) {
    CoopReason.SOON -> "같이 갈 $who${eul(who)} 그려 줄래?"
    CoopReason.DREAM -> "이야기 속 $who${eul(who)} 그려 줄래?"
    else -> null
}

/** 책 등장 쪽의 이름표 — 「오늘 만난 사람」 대신 */
fun DemoState.coopMetLabel(): String? = when (coopNotYet) {
    CoopReason.SOON -> "같이 갈 사람"
    CoopReason.DREAM -> "이야기 속 사람"
    else -> null
}

/**
 * 책 끝 친구 고르기 — 「오늘 만난 친구들이야」 대신. 할머니 · 아빠도 나오므로 「친구」라고 하지 않는다 (#98).
 * 다녀왔어요 · 이야기 안 고름은 오늘 있었던 일이라 「오늘 함께한 사람들」, 곧 해요 · 좋아해요는 「이야기에 나온 사람들」
 */
fun DemoState.coopFriendsLine(): String? = when {
    !isCoop -> null
    coopNotYet == null -> "오늘 함께한 사람들이야. 누구를 또 만나고 싶어?"
    else -> "이야기에 나온 사람들이야. 누구를 또 만나고 싶어?"
}

/**
 * 책 제목 — 고른 이야기가 있으면 그 이름으로. 일기 제목(「{장소}에서 만난 {사람}」)은 아이 말을 그대로 끼워
 * 「거실, 소파 있는 데에서 만난 엄마랑 아빠랑」이 됐다(10-05 실기기) · 곧 해요에는 「만난」이 맞지 않았다.
 * 이야기를 안 고르고 질문만 적었으면 null — 일기 제목 그대로
 */
fun DemoState.coopTitle(): String? {
    if (!isCoop) return null
    val name = bookPick?.name?.trim() ?: return null
    return when (coopNotYet) {
        CoopReason.SOON -> "${childName}의 두근두근 $name 이야기"
        CoopReason.DREAM -> "${childName}의 상상 $name 이야기"
        else -> "${childName}의 $name 이야기"
    }
}

/**
 * 마음 낱말 + 「던」 — 앱 대본 꼴(「신났」 · 「기뻤」, 받침 ㅆ)만 「신났던」으로 잇는다. 아니면 null.
 * 서버 판정은 「신나다」 · 「떨려」 · 「기쁨」 · 「무섭다, 신나다」 꼴로도 준다 — 활용을 짐작하지 않는다 (10-03 실기기 「신나다던」)
 */
fun feelingThatWas(emo: String): String? {
    val e = emo.trim()
    val last = e.lastOrNull() ?: return null
    return if (last in '가'..'힣' && (last - '가') % 28 == 20) "${e}던" else null
}

/** 일기 reaction 문장의 마음 — 「신났던」 또는 「‘떨려’라는」 */
fun feelingPhrase(emo: String): String =
    feelingThatWas(emo) ?: emo.trim().let { "‘$it’${if (bat(it)) "이라는" else "라는"}" }

/**
 * 리포트 「마음 말하기」 — 다 대본 꼴이면 「신났던, 기뻤던 마음을 말했어요」,
 * 서버 낱말이 섞이면 들은 그대로 「마음을 말했어요 — ‘무섭다’ · ‘신나다’ · ‘떨려’」
 */
fun feelingsSaid(feelings: List<String>): String {
    val words = feelings.flatMap { it.split(',', '·', '/') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val joined = words.map { feelingThatWas(it) }
    return if (joined.all { it != null }) "${joined.joinToString(", ")} 마음을 말했어요"
    else "마음을 말했어요 — ${words.joinToString(" · ") { "‘$it’" }}"
}

// ── 「가기 전 · 다녀온 뒤」 짝책의 말 (협업모드_확장_설계 §2) ──

/** 「동물원에 가기 전에」 · 「소방관 체험하기 전에」 · 「수영을 해 보기 전에」 */
fun coopBeforeWhen(kind: String, name: String): String {
    val n = name.trim()
    return when (kind) {
        "job" -> "$n 체험하기 전에"
        "sport" -> "$n${eul(n)} 해 보기 전에"
        else -> "${n}에 가기 전에"
    }
}

/** 「다녀온 뒤」 이야기를 시작할 때 오또가 하는 한 줄 — 가기 전 책을 말하는 유일한 자리 */
fun coopAfterIntroLine(pick: CoopPick): String =
    "${coopBeforeWhen(pick.kind, pick.name)} 지은 이야기 기억나? 이번엔 진짜 있었던 일을 들려줘!"

/** 부모 카드 제목 — 「동물원, 다녀왔나요?」 · 「소방관, 체험했나요?」 · 「수영, 해 봤나요?」 */
fun coopAfterAsk(a: CoopAfter): String {
    val done = coopKind(a.kind)?.reasonLabels?.get(CoopReason.DONE) ?: "다녀왔어요"
    return "${a.name}, ${done.removeSuffix("어요")}나요?"
}

/** 리포트 「가기 전 · 다녀온 뒤」 칸의 한 줄 — 자리 이름 · 가기 전 답 · 다녀온 뒤 답 (출처 표시까지 붙인 글) */
data class BeforeAfterRow(val label: String, val before: String, val after: String, val beforeByChild: Boolean, val afterByChild: Boolean)

/** 리포트 「가기 전 · 다녀온 뒤」 칸 (협업모드_확장_설계 §2-5) */
data class BeforeAfter(val name: String, val beforeDate: String, val afterDate: String, val rows: List<BeforeAfterRow>, val summary: String?)

/** 뼈대 네 자리 — 두 책의 템플릿 질문이 달라서(곧 해요 · 다녀왔어요) 질문 대신 자리 이름으로 맞춘다 */
private val BEFORE_AFTER_PARTS = listOf("place" to "어디", "problem" to "무슨 일", "cause" to "왜", "solution" to "그래서")

/** 한 칸의 글 — 아이가 말한 것만 따옴표. 카드 · 마스코트는 그렇다고 적는다(guidelines/2 §1-4 · 지금 「한 답」 칸과 같다) */
private fun CoopBookSnapshot.answerOf(key: String): Pair<String, Boolean> {
    val v = (fields[key] ?: slots[key])?.trim()?.takeIf { it.isNotEmpty() } ?: return "답하지 않았어요" to false
    return when (slotBy[key]) {
        "child" -> "“$v”" to true
        "card" -> "$v (카드로 골랐어요)" to false
        "mascot" -> "(마스코트가 대신 정했어요)" to false
        else -> v to false
    }
}

/**
 * 오늘 꽂은 책이 「다녀온 뒤」 책이고 짝(「가기 전」 책)이 책장에 있으면 두 책을 나란히 — 아니면 null(칸이 없다).
 *
 * 맞고 틀림 · 늘고 줄음을 말하지 않는다. 글자 수 · 신호 수를 두 책 사이에서 견주지 않는다(두 책은 질문이 다르다).
 * 요약 한 줄은 「무슨 일」 자리가 두 책 모두 아이 말일 때만, 고정 틀 + 인용 — LLM 을 부르지 않는다
 */
fun DemoState.coopBeforeAfter(): BeforeAfter? {
    val todayId = CoopShelf.lastShelved(this) ?: return null
    val today = CoopShelf.snapshotOf(todayId) ?: return null
    if (today.pick?.reasonOrNull() != CoopReason.DONE) return null
    val beforeBook = CoopShelf.pairOf(this, todayId) ?: return null
    val before = CoopShelf.snapshotOf(beforeBook.id) ?: return null
    val todayBook = CoopShelf.books(this).firstOrNull { it.id == todayId } ?: return null
    val rows = BEFORE_AFTER_PARTS.map { (key, label) ->
        val (b, bc) = before.answerOf(key); val (a, ac) = today.answerOf(key)
        BeforeAfterRow(label, b, a, bc, ac)
    }
    val trouble = rows.first { it.label == "무슨 일" }
    val summary = if (trouble.beforeByChild && trouble.afterByChild)
        "가기 전엔 ${trouble.before}라고 상상했고, 다녀와서는 ${trouble.after}라고 말했어요." else null
    return BeforeAfter(today.pick?.name?.trim() ?: "", beforeBook.madeAt, todayBook.madeAt, rows, summary)
}

/** 「다녀온 뒤」 책 리포트의 첫 놀이 카드 — 두 책을 같이 읽는 것은 부모가 여는 놀이다(앱이 아이에게 권하지 않는다) */
const val COOP_PAIR_PLAY_CARD = "\"가기 전에 지은 이야기도 다시 읽어 볼까? 뭐가 달랐어?\""

/** 상자에서 꺼낸 「다녀온 뒤」 계획의 첫 추천 질문 — 비교는 아이에게 시키지 않고 부모가 물을 때만 */
const val COOP_AFTER_SUGGESTION = "가기 전에 지은 이야기랑 뭐가 달랐어?"
