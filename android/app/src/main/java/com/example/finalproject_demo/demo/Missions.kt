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

fun DemoState.mission1(): Mission1 = when (newcomerKind) {
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
    else -> Mission2("obj_star", "⭐", "별", "반짝이는 별을 건네주었어요", "별을 받고 활짝 웃어!")
}

/** "심심했어" → "심심했대" (남의 말을 전할 때) */
fun reported(line: String): String = if (line.endsWith("어")) line.dropLast(1) + "대" else line + "대"

/** 미션 안내 · 완료 · 자막 문장 — 이름 · 탈것 · 대화에서 나온 말로 채운다 */
fun DemoState.m1Line(): String {
    val m = mission1(); val v = th.vehicle
    return "큰일이야! $newcomerKind${ga(newcomerKind)} 흔들어서 $v${eul(v)} 보니 ${m.blobName}${ga(m.blobName)} 잔뜩! ${m.toolName}${ro(m.toolName)} 슥슥 치워 줄래?"
}

fun DemoState.m1Caption(): String {
    val m = mission1(); val v = th.vehicle; val f = friendName
    return "$f${ga(f)} 너무 세게 흔드는 바람에 ${v}에 ${m.stuck}!"
}

fun DemoState.m1Done(): String = "${mission1().done} $childName 덕분에 ${th.vehicle}${ga(th.vehicle)} 다시 반짝반짝!"

fun DemoState.m2Line(easy: Boolean): String {
    val m = mission2(); val f = friendName
    return if (easy) "${m.itemName}${eul(m.itemName)} 톡톡 누르면 ${f}에게 날아가!"
    else "${f}${ga(f)} ${reported(causeLine)}. ${m.itemName}${eul(m.itemName)} 끌어서 ${f}한테 건네줄래?"
}

fun DemoState.m2Caption(): String {
    val f = friendName
    return "$f${eun(f)} \"$causeLine. 미안해\" 하고 말했어요. $childName${eun(childName)} ${f}에게 ${mission2().give}."
}

fun DemoState.m2Done(): String = "$friendName${ga(friendName)} ${mission2().done}"
