package com.example.finalproject_demo.demo

/**
 * 책 미션 — 아이와 나눈 대화에서 만든다 (v0.8).
 *
 * - 미션 1(4쪽 · 문지르기 · 쉬움): **누가 흔들었나(장면 4)** 에 따라 탈것에 남은 흔적이 달라진다.
 *   외계인이면 엔진에 불, 문어면 먹물, 원숭이면 바나나 껍질 … 도구도 그에 맞춘다.
 * - 미션 2(5쪽 · 끌어다 놓기 · 보통): **어떻게 친해질까(장면 10)** 에서 아이가 말한 것을 친구에게 건넨다.
 *   별 따기면 별, 선물이면 반짝이는 돌 · 딸기, 초대면 초대장, 춤이면 음표.
 *
 * 뼈대(문지르기 · 끌어다 놓기)는 그대로, 소품 그림과 문장만 바뀐다 (구현대본 §6 "미션은 장소에 묶지 않는다").
 */
data class Mission1(
    val blob: String, val blobEmoji: String, val blobName: String,
    val gone: String, val goneEmoji: String,
    val tool: String, val toolEmoji: String, val toolName: String,
    /** "불이 붙었어요" 처럼 흔적이 생긴 모양 (책 4쪽 자막) */
    val stuck: String,
    val done: String,
)

data class Mission2(
    val item: String, val itemEmoji: String, val itemName: String,
    /** "건네줄까" 뒤에 붙는 동작 — 책 5쪽 자막 */
    val give: String,
    val done: String,
)

/**
 * 일기 모드의 미션 1 — 뼈대(문지르기)는 그대로, **소품만 하루에서 나온 것으로** 바꾼다 (일기 설계 §7-1 ②).
 *
 * 9/21에 고친 것: 전에는 **장소만** 보고 골라서, 아이가 "블록이 무너졌어" 라고 말한 날에도
 * 장소를 못 알아들으면 "진흙이 가방에 묻었어요" 가 나왔다. 하지 않은 일이 책에 적힌 셈이다.
 *
 * 지금은 순서가 셋이다.
 *  1. **아이가 말한 일**에서 찾는다 — 물감 · 블록 · 모래 · 나뭇잎 · 물 … 이야기에 이미 나온 것.
 *  2. 없으면 **장소**에서 찾는다 (놀이터 → 모래, 어린이집 → 물감 …).
 *  3. 둘 다 없으면 **무엇이 묻었다고 지어내지 않는다.** 하루 동안 쌓인 먼지를 턴다 —
 *     어느 하루에나 맞는 말이라, 아이가 하지 않은 일을 적지 않는다.
 */
private fun diaryMission1(s: DemoState): Mission1 {
    // ⚠️ `after`(집에 와서 한 일)는 보지 않는다 — "저녁을 먹었어요" 때문에 간식 미션이 나왔다 (9/21)
    val said = listOf(s.problem, s.slots["detail"], s.solution, s.cause, s.slots["try"])
        .joinToString(" ") { it.orEmpty() }
    val p = s.placeLabel.orEmpty()

    val sand = Mission1("prop_sand", "🟡", "모래", "prop_sparkle", "✨", "ic_hand", "✋", "손", "모래가 잔뜩 묻었어요", "모래를 탈탈 다 털어 냈어!")
    val paint = Mission1("prop_paint", "🎨", "물감", "prop_splash", "💦", "prop_sponge", "🧽", "스펀지", "물감이 잔뜩 묻었어요", "물감을 깨끗이 다 닦았어!")
    val leaf = Mission1("prop_leaf", "🍂", "나뭇잎", "prop_sparkle", "✨", "prop_broom", "🧹", "빗자루", "나뭇잎이 잔뜩 붙었어요", "나뭇잎을 다 쓸어 냈어!")
    val water = Mission1("prop_splash", "💦", "물방울", "prop_sparkle", "✨", "prop_sponge", "🧽", "수건", "물이 잔뜩 튀었어요", "물기를 뽀송하게 다 닦았어!")
    val crumb = Mission1("prop_strawberry", "🍓", "부스러기", "prop_sparkle", "✨", "ic_hand", "✋", "손", "간식 부스러기가 묻었어요", "부스러기를 탈탈 다 털어 냈어!")
    val dust = Mission1("prop_cloud", "🌫", "먼지", "prop_sparkle", "✨", "ic_hand", "✋", "손", "하루 먼지가 뽀얗게 앉았어요", "먼지를 탈탈 다 털어 냈어!")

    return when {
        // ① 아이가 말한 일 — 장소보다 먼저다
        "물감" in said || "색칠" in said || "그리" in said -> paint
        "모래" in said || "미끄럼" in said || "그네" in said || "흙" in said -> sand
        "나뭇잎" in said || "낙엽" in said || "나무" in said || "풀" in said -> leaf
        "물" in said || "비" in said || "웅덩이" in said || "수영" in said -> water
        "밥" in said || "간식" in said || "과자" in said || "먹었" in said -> crumb
        // Blocks do not stain the bag; do not infer paint from the daycare setting.
        "블록" in said || "쌓" in said -> dust
        // ② 없으면 장소
        "놀이터" in p -> sand
        "어린이집" in p || "유치원" in p || "학교" in p -> paint
        "공원" in p || "산책" in p -> leaf
        // ③ 아무것도 못 찾으면 묻은 것을 지어내지 않는다
        else -> dust
    }
}

/**
 * 미션 2에서 건넬 것 — **아이가 말한 것에서 나온다** (⭐7 · 구현대본 §6).
 * 블록을 쌓은 날엔 블록을, 넘어진 날엔 반창고를, 책을 읽은 날엔 그림책을 건넨다.
 */
fun diaryGiveItem(solution: String, s: DemoState): String {
    val all = solution + " " + s.problem.orEmpty() + " " + s.slots["detail"].orEmpty()
    // 9/22 — 결말에 이미 나온 물건은 고르지 않는다. "반창고를 붙이고 다시 놀았어요.
    // 그리고 민서에게 반창고를 붙여 주었어요." 처럼 같은 물건이 한 쪽에 두 번 나왔다
    fun pickAvoidingSolution(first: String, fallback: String, word: String) =
        if (word in solution) fallback else first
    return when {
        "블록" in all || "쌓" in all -> "block"
        "넘어" in all || "아팠" in all || "다쳤" in all -> pickAvoidingSolution("bandaid", "star", "반창고")
        "책" in all || "그림" in all -> pickAvoidingSolution("picturebook", "star", "그림책")
        "밥" in all || "먹" in all || "간식" in all -> pickAvoidingSolution("strawberry", "star", "딸기")
        "노래" in all || "춤" in all -> "note"
        else -> "star"
    }
}

fun DemoState.mission1(): Mission1 = if (isDiary) diaryMission1(this) else when (newcomerKind) {
    "운석" -> Mission1("obj_rock", "🪨", "돌멩이", "prop_smoke", "💨", "prop_broom", "🧹", "빗자루", "돌멩이가 잔뜩 박혔어요", "돌멩이를 다 쓸어 냈어!")
    "바람" -> Mission1("prop_leaf", "🍂", "나뭇잎", "prop_sparkle", "✨", "prop_broom", "🧹", "빗자루", "나뭇잎이 잔뜩 붙었어요", "나뭇잎을 다 쓸어 냈어!")
    "문어" -> Mission1("prop_ink", "🟣", "먹물", "prop_splash", "💦", "prop_sponge", "🧽", "스펀지", "먹물이 잔뜩 묻었어요", "먹물을 깨끗이 닦았어!")
    "상어" -> Mission1("prop_seaweed", "🌿", "해초", "prop_sparkle", "✨", "ic_hand", "✋", "손", "해초가 칭칭 감겼어요", "해초를 다 떼어 냈어!")
    "인어" -> Mission1("obj_bubble", "🫧", "거품", "prop_sparkle", "✨", "ic_hand", "✋", "손", "거품이 잔뜩 붙었어요", "거품을 톡톡 다 터뜨렸어!")
    "아기 공룡" -> Mission1("prop_mud", "🟤", "진흙", "prop_splash", "💦", "prop_sponge", "🧽", "스펀지", "진흙 발자국이 잔뜩 찍혔어요", "진흙을 깨끗이 닦았어!")
    "원숭이" -> Mission1("prop_banana", "🍌", "바나나 껍질", "prop_sparkle", "✨", "ic_hand", "✋", "손", "바나나 껍질이 잔뜩 붙었어요", "바나나 껍질을 다 치웠어!")
    "화산" -> Mission1("prop_lava", "🔥", "용암", "prop_smoke", "💨", "prop_hose", "🚿", "물대포", "용암이 튀어 불이 붙었어요", "불이 다 꺼졌어!")
    else -> Mission1("prop_fire", "🔥", "불", "prop_smoke", "💨", "prop_hose", "🚿", "물대포", "불이 붙었어요", "불이 다 꺼졌어!")
}

fun DemoState.mission2(): Mission2 = when (solutionItem) {
    "note" -> Mission2("prop_note", "🎵", "음표", "노래를 불러 주었어요", "신나게 노래하며 춤을 춰!")
    "gem" -> Mission2("prop_gem", "💎", "반짝이는 돌", "반짝이는 돌을 건네주었어요", "반짝이는 돌을 받고 활짝 웃어!")
    "strawberry" -> Mission2("prop_strawberry", "🍓", "딸기", "딸기를 나눠 주었어요", "딸기를 냠냠, 활짝 웃어!")
    "invite" -> Mission2("ic_invite", "💌", "초대장", "초대장을 건네주었어요", "초대장을 받고 신이 났어!")
    "balloon" -> Mission2("ic_play", "🎈", "풍선", "풍선을 건네주었어요", "풍선을 받고 방긋 웃어!")
    // 일기 모드 — 아이가 말한 하루에서 나온 것들
    "block" -> Mission2("prop_block", "🧱", "블록", "블록 하나를 건네주었어요", "블록을 받고 같이 쌓기 시작했어!")
    "picturebook" -> Mission2("prop_picturebook", "📗", "그림책", "그림책을 건네주었어요", "그림책을 받고 눈이 반짝!")
    "bandaid" -> Mission2("prop_bandaid", "🩹", "반창고", "반창고를 붙여 주었어요", "반창고를 붙이고 씩 웃어!")
    else -> Mission2("obj_star", "⭐", "별", "반짝이는 별을 건네주었어요", "별을 받고 활짝 웃어!")
}

/** "심심했어" → "심심했대" (남의 말을 전할 때) */
fun reported(line: String): String = if (line.endsWith("어")) line.dropLast(1) + "대" else line + "대"

/** 미션 안내 · 완료 · 자막 문장 — 이름 · 탈것 · 대화에서 나온 말로 채운다 */
fun DemoState.m1Line(): String {
    val m = mission1(); val v = rideName
    if (isDiary) {
        // 9/22 — 전에는 **가방**에 묻은 것을 털게 했다. 가방은 아이가 말한 적 없는 물건이라
        // "블록이 무너졌어" 라고 말한 날에도 뜬금없이 가방이 나왔다. 이제 **그 일이 일어난 자리**를 치운다
        val where = placeLabel?.let { "${it}에는" } ?: "놀던 자리에는"
        return "$where ${m.blobName}${ga(m.blobName)} 아직 잔뜩 남아 있어. ${m.toolName}${ro(m.toolName)} 슥슥 치워 줄래?"
    }
    return "큰일이야! $newcomerKind${ga(newcomerKind)} 흔들어서 $v${eul(v)} 보니 ${m.blobName}${ga(m.blobName)} 잔뜩! ${m.toolName}${ro(m.toolName)} 슥슥 치워 줄래?"
}

/**
 * 미션 1이 책에 남는 문장.
 *
 * [withSubject] 가 false면 **주어를 빼고 절로만** 돌려준다. 앞 문장이 이미 아이 이야기일 때
 * "지호는 다시 쌓아 봤어요. 지호는 모래를 치웠어요." 처럼 이름이 두 번 나오지 않게 하려는 것이다 (9/22).
 */
fun DemoState.m1Caption(withSubject: Boolean = true): String {
    val m = mission1(); val v = rideName; val f = friendCallName
    if (isDiary) {
        // 자막은 **아이가 한 일**로 쓴다. 미션이 이야기 옆에 붙은 딴 이야기가 아니라
        // "그래서 나는 이렇게 했어" 자리에 들어가야 흐름이 끊기지 않는다 (9/22)
        val where = placeLabel?.let { "${it}에 " } ?: ""
        // ⚠️ `$where남은` 으로 쓰면 안 된다 — 한글도 식별자 문자라서 Kotlin이 `where남은` 을
        //    변수 하나로 읽는다. 한글이 바로 뒤에 붙는 자리는 **반드시 중괄호**로 끊는다
        val clause = "${where}남은 ${m.blobName}${eul(m.blobName)} ${m.toolName}${ro(m.toolName)} 슥슥 치웠어요."
        return if (withSubject) "$childName${eun(childName)} $clause" else clause
    }
    return "$f${ga(f)} 너무 세게 흔드는 바람에 ${v}에 ${m.stuck}!"
}

/**
 * 미션 2가 책에 남는 절 — 주어 없이. 앞의 "마침내 …" 문장에 이어 붙는다 (9/22).
 * 결(結) 한 쪽이 두 문장으로 갈라지지 않게 하려는 것이다.
 */
fun DemoState.m2Clause(): String =
    // 아무도 없었던 날엔 마스코트가 받는다. 그런데 마스코트는 앞쪽에 한 번도 안 나온 인물이라
    // 그냥 "마스코트에게 건네주었어요" 라고 하면 뜬금없다. 한 마디로 자리를 만들어 준다 (9/22)
    if (hasCompanion) "${giveTargetName}에게 ${mission2().give}."
    else "오늘 이야기를 들어준 마스코트에게 ${mission2().give}."

fun DemoState.m1Done(): String =
    if (isDiary) "${mission1().done} 자리가 다시 깨끗해졌어!"
    else "${mission1().done} $childName 덕분에 ${rideName}${ga(rideName)} 다시 반짝반짝!"

fun DemoState.m2Line(easy: Boolean): String {
    val m = mission2()
    // 9/22 — 아무도 없었던 날에는 "그 친구" 를 지어내지 않는다. 마스코트가 받는다 (그림도 이미 마스코트다)
    val f = giveTargetName
    if (easy) return "${m.itemName}${eul(m.itemName)} 톡톡 누르면 ${f}에게 날아가!"
    // 일기 모드는 "미안해"를 앞세우지 않는다 — 아이가 그렇게 말하지 않았을 수 있다 (일기 설계 §3-2)
    if (isDiary) return "${m.itemName}${eul(m.itemName)} 끌어서 ${f}한테 건네줄래?"
    return "${f}${ga(f)} ${reported(causeLine)}. ${m.itemName}${eul(m.itemName)} 끌어서 ${f}한테 건네줄래?"
}

fun DemoState.m2Done(): String = "${giveTargetName}${ga(giveTargetName)} ${mission2().done}"
