package com.example.finalproject_demo.demo

import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.ui.HeroAttr

// 장면별 대본. 기준: 구현대본.md(9/16) + v0.8 요청 + v0.9 요청(9/17).
// v0.9
//  - 함께 하는 사람을 처음에 묻는다 (엄마 · 아빠 · 이모 · 할머니 · 할아버지 · 친구) → 질문 · 자막 · 기록 호칭이 그걸 따른다
//  - 처음 두 질문(어디로 · 누가 흔들었나)만 고정, 그 뒤 질문은 질문 은행(StoryBank)에서 매번 다른 변형이 나온다
//  - 답마다 신호(S1 · S2 · A1)가 붙어 있고, 3턴째 수준에 맞는 템플릿(E · C · D · A · G)이 정해져 책이 6~8쪽으로 만들어진다
//  - 배경에 새 물건을 얹지 않는다 — 배경 그림에 이미 그려진 것(구름 · 행성 · 산호 …)이 반짝이고 눌리면 통 튄다
//
// 소크라틱 질문 원칙 (역할2 조사 · 역할1 조사2 #5)
//  1. 선택지를 말하지 않는다 — "우주, 바닷속이 있어" 대신 "어디로 가 볼까?"
//  2. 아이가 방금 한 말에서 다음 질문을 잇는다 — "우주에는 뭐가 있을까?"
//  3. 답 대신 생각할 거리를 준다 — "그다음엔 어떻게 됐을 것 같아?" · "네가 ○○였다면?" · "다른 방법은 없었을까?"
//  4. 말이 없으면 선택지 대신 관찰 질문부터 — "창문 밖을 잘 봐. 뭐가 보여?"

suspend fun Director.runScene(scene: Scene) {
    when (scene) {
        Scene.ADULT -> sceneAdult()
        Scene.PARTNER -> scenePartner()
        Scene.BESTIARY -> sceneBestiary()
        Scene.MAKEHERO -> sceneMakeHero()
        Scene.DIARY -> sceneDiary()
        Scene.PLACE -> scenePlace()
        Scene.EVENT -> sceneEvent()
        Scene.CAUSE -> sceneCause()
        Scene.DRAW -> sceneDraw()
        Scene.PLOT -> scenePlot()
        Scene.DINO -> sceneDino()
        Scene.SOUND -> sceneSound()
        Scene.CHECK -> sceneCheck()
        Scene.SOLUTION -> sceneSolution()
        Scene.MAKING -> sceneMaking()
        Scene.BOOK -> sceneBook()
        Scene.FRIENDS -> sceneFriends()
        Scene.END -> sceneEnd()
        Scene.SHELF -> sceneShelf()
        Scene.PARENT -> sceneParent()
    }
}

/** 장면을 건너뛰어 들어왔을 때 앞 장면의 결과를 채워 둔다 (아이가 고른 값은 건드리지 않는다). */
fun Director.seedFor(scene: Scene) {
    val order = Scene.entries.indexOf(scene)
    fun after(target: Scene, fill: () -> Unit) {
        if (order > Scene.entries.indexOf(target) && order <= Scene.entries.indexOf(Scene.END)) fill()
    }
    if (s.heroAttr == null && order > Scene.entries.indexOf(Scene.BESTIARY)) s.heroAttr = s.heroes.first().attr

    // 시연 서랍에서 장면을 바로 열었을 때 모드가 화면과 어긋나지 않게 맞춘다.
    // ⚠️ 이미 일기 질문을 쓰는 모드(일기 · 협업)면 건드리지 않는다 — 협업 모드가 일기 모드로 덮여 버린다
    if (scene == Scene.DIARY && !s.isDiary) s.mode = StoryMode.DIARY
    if (scene in STORY_ONLY) s.mode = StoryMode.STORY
    // 부모 띠는 **질문을 하고 있는 동안에만** 떠 있어야 한다. 시연 서랍으로 장면을 건너뛰면
    // 질문이 끝나지 않은 채 화면만 바뀌어 띠가 그대로 남았다 — 책에서는 자막까지 가렸다 (9/21 에뮬레이터 확인).
    if (scene != Scene.DIARY) { s.parentCard = null; s.parentRung = 0; s.parentHasMore = false }
    if (s.isDiary) { seedDiary(order); return }

    after(Scene.PLACE) { if (s.place == null) s.place = s.placeName }
    after(Scene.EVENT) {
        // ⚠️ 새 친구부터 맞추고 나서 문장을 만든다 — 순서가 바뀌면 "외계인이 거북이를 흔듦"이 남는다 (9/21)
        if (s.newcomerKind !in s.th.newcomers.map { it.value }) s.newcomerKind = s.th.defaultNewcomer
        if (s.problem == null) s.problem = "${s.newcomerKind}${ga(s.newcomerKind)} ${s.th.vehicle}${eul(s.th.vehicle)} 흔듦"
    }
    after(Scene.CAUSE) {
        if (s.cause == null) s.cause = "심심해서 · 친구가 없어서"
        if (s.templateKey == null) {
            val (t, a) = chooseTemplate(s.level, s.causeKind)
            s.templateKey = t; s.attribute = a
            s.levelWhy = "${s.level.label} → ${templateOf(t).code} ${templateOf(t).name} · 속성 $a · ${templateOf(t).pages.size}쪽 (장면 건너뜀)"
        }
    }
    after(Scene.DRAW) {
        if (s.newcomer == null) {
            if (s.friendName.startsWith("{")) s.friendName = "뿌뿌"
            s.newcomer = "${s.friendName} (아이 그림)"
        }
    }
    after(Scene.DINO) {
        // 장면 7을 건너뛰었으면 **그 장소의** 친구로 채운다 — 우주에 공룡이 따라가지 않게 (9/21)
        if (s.dinoKey !in s.buddies.map { it.key }) s.dinoKey = s.buddies.first().key
        if (s.friend == null) s.friend = s.dino.name
    }
    after(Scene.SOUND) { if (s.sound == null) s.sound = "${s.soundLine} (원본 녹음)" }
    after(Scene.SOLUTION) { if (s.solution == null) s.solution = solutionText() }
    after(Scene.MAKING) { if (s.title == null) s.title = s.autoTitleFor() }
    if (order >= Scene.entries.indexOf(Scene.PLACE) && s.images == 0) s.images = 2
}

/** 동화 모드에서만 나오는 장면 — 여기로 바로 들어오면 모드를 동화로 되돌린다 */
private val STORY_ONLY = setOf(
    Scene.PLACE, Scene.EVENT, Scene.CAUSE, Scene.DRAW, Scene.PLOT, Scene.DINO, Scene.SOUND, Scene.CHECK, Scene.SOLUTION,
)

/**
 * 일기 모드로 뒤쪽 장면(책 · 선물 · 부모 모드)을 바로 열었을 때 기승전결 네 자리를 채워 둔다.
 * 동화 모드의 씨앗(외계인 · 공룡 · 소리)을 쓰지 않는다 — 묻지 않는 칸이다 (일기 설계 §2-2).
 */
private fun Director.seedDiary(order: Int) {
    if (order <= Scene.entries.indexOf(Scene.DIARY)) return
    if (s.place == null) {
        s.placeLabel = "놀이터"; s.place = "놀이터"; s.slots["place"] = "놀이터에 갔어요"
        s.slotBy["place"] = "child"
    }
    if (s.problem == null) {
        s.problem = "블록이 무너짐"; s.slots["problem"] = "높이 쌓은 블록이 와르르 무너졌어요"
        s.slotBy["problem"] = "child"
    }
    if (s.cause == null) {
        s.cause = "같이 놀고 싶었어"; s.causeLine = "같이 놀고 싶었어"
        s.slots["cause"] = "같이 놀고 싶어서 그랬대요"; s.slotBy["cause"] = "child"
    }
    if (s.solution == null) {
        s.solution = "선생님이랑 다시 쌓음"; s.solutionLine = "선생님과 함께 블록을 다시 쌓았어요"
        s.slots["solution"] = "선생님과 함께 블록을 다시 쌓았어요"; s.slotBy["solution"] = "child"
        s.solutionItem = "star"
    }
    if (order > Scene.entries.indexOf(Scene.MAKING) && s.title == null) s.title = s.autoTitleFor()
    if (s.images == 0) s.images = 1
}

/** 배경 속 것 key → 말로 부르는 이름 */
fun hotspotWord(key: String) = when (key) {
    "moon" -> "달님"; "planet" -> "행성"; "star" -> "별"
    "bubble" -> "거품"; "seaweed" -> "미역"; "coral" -> "산호"; "rock" -> "바위"
    "cloud" -> "구름"; "volcano" -> "화산"; "tree" -> "나무"; "dino" -> "아기 공룡"
    "snowflake" -> "눈송이"; "hill" -> "눈 언덕"
    else -> key
}

private fun joinWords(words: List<String>): String = when (words.size) {
    0 -> ""
    1 -> words[0]
    2 -> "${words[0]}${wa(words[0])} ${words[1]}"
    else -> words.joinToString(", ")
}

/** 세계 무대 — 배경 속 것들(아이가 말한 것)은 반짝이게 둔다. 새 물건은 얹지 않는다 (9/17) */
private fun Director.world(
    items: List<WorldItem>, quake: Boolean = false, brush: Boolean = false, retry: Int = -1,
    glow: Set<String> = s.mentioned.toSet(), bump: Boolean = false,
): Stage.World {
    if (bump) s.pulse++
    return Stage.World(items, brush = brush, retry = retry, quake = quake, glow = glow, pulse = s.pulse)
}

private val Director.hero get() = Art.HeroArt(s.heroAttr ?: HeroAttr())

private fun Director.solutionText() = when (s.solutionKey) {
    "gift" -> "선물 주기 · ${s.solutionLine}"
    "invite" -> "집에 초대하기 · ${s.solutionLine}"
    else -> "같이 놀기 · ${s.solutionLine}"
}

/** 답에서 slot 값 꺼내기 — 말 · 탭 · 마스코트 모두 */
private fun valueOf(r: Reply): String? = when (r) {
    is Reply.Spoke -> r.value.ifEmpty { r.text.trimEnd('!', '.') }
    is Reply.Tapped -> r.value
    else -> null
}

/**
 * 8턴이 지나면 남은 칸은 마스코트가 채우고 책으로 넘어간다 (구현대본 §2).
 * 데모의 이야기 턴은 6개(장소 · 누가 · 까닭 · 이야기 잇기 2 · 해결)라 평소에는 걸리지 않는다.
 * 시연 서랍의 [8턴 지난 것으로]로 볼 수 있다.
 */
private suspend fun Director.budgetOver(): Boolean {
    if (s.turn < 8) return false
    val left = mutableListOf<String>()
    if (s.place == null) { s.place = s.placeName; left += "장소" }
    if (s.problem == null) { s.problem = "${s.newcomerKind}${ga(s.newcomerKind)} ${s.th.vehicle}${eul(s.th.vehicle)} 흔듦"; left += "문제" }
    if (s.cause == null) { s.cause = "심심해서"; s.causeLine = "친구가 없어서 심심했어"; left += "까닭" }
    if (s.newcomer == null) {
        if (s.friendName.startsWith("{")) s.friendName = "${s.newcomerKind} 친구"
        s.newcomer = "${s.friendName} (프리셋)"; left += "등장인물"
    }
    if (s.sound == null) { s.sound = "기본 효과음"; left += "소리" }
    if (s.solution == null) { s.solution = solutionText(); left += "해결" }
    if (s.templateKey == null) {
        val (t, a) = chooseTemplate(s.level, s.causeKind)
        s.templateKey = t; s.attribute = a
    }
    buttons()
    inputs(false, false)
    say("이야기가 벌써 이만큼 됐네! 이제 책으로 만들어 볼까?")
    log("8턴 상한 — 남은 칸(${left.joinToString(" · ").ifEmpty { "없음" }})을 마스코트가 채우고 책으로 넘어간다 (구현대본 §2)")
    pause(1600)
    go(Scene.MAKING)
    return true
}

/**
 * 비밀번호 4자리. 맞으면 true, [취소]면 false.
 * 데모에서는 아무 숫자 4개면 통과한다.
 */
suspend fun Director.pinGate(purpose: String): Boolean {
    s.stage = Stage.Pin(purpose, 0)
    buttons(
        DemoBtn("🔢 (시연) 비밀번호 4자리 입력") { send(Reply.Tapped("pin:ok", "통과")) },
        DemoBtn("✖ 취소") { send(Reply.Tapped("pin:cancel", "취소")) },
    )
    var typed = 0
    while (true) {
        val r = awaitReply() as? Reply.Tapped ?: continue
        when {
            r.value == "pin:ok" -> { typed = 4 }
            r.value == "pin:cancel" -> { log("비밀번호 취소"); return false }
            r.value == "pin:back" -> typed = (typed - 1).coerceAtLeast(0)
            r.value.startsWith("pin:") -> typed++
            else -> continue
        }
        s.stage = Stage.Pin(purpose, typed.coerceAtMost(4))
        if (typed >= 4) {
            pause(350)
            log(if (purpose == "start") "비밀번호 통과 → 이야기 시작 (부모 설정: 시작할 때 비밀번호)" else "비밀번호 통과 → 부모 모드")
            return true
        }
    }
}

// ── 장면 1 · 시작 화면 ──────────────────────────────────────────

private suspend fun Director.sceneAdult() {
    s.stage = Stage.Adult
    s.notice = null
    buttons(
        DemoBtn("🖐 이야기 만들기 탭 (동화 모드)") { send(Reply.Tapped("start", "이야기 만들기")) },
        DemoBtn("🌙 오늘 있었던 일로 탭 (일기 모드)") { send(Reply.Tapped("diary", "오늘 있었던 일로")) },
        DemoBtn("👪 같이 만들기 탭 (부모 협업 모드)") { send(Reply.Tapped("coop", "같이 만들기")) },
        DemoBtn("📚 책장 탭") { send(Reply.Tapped("shelf", "책장")) },
        DemoBtn("👪 부모 모드 탭") { send(Reply.Tapped("parent", "부모 모드")) },
    )
    // 갈래가 갈라지는 유일한 자리 (일기 §1 · 협업 §3). 뒤의 흐름은 질문 세트와 **묻는 사람**만 다르고 나머지는 같다
    var picked = StoryMode.STORY
    when (awaitValue("start", "diary", "coop", "shelf", "parent", "notice:ok", "notice:shelf")) {
        "shelf", "notice:shelf" -> { go(Scene.SHELF); return }
        "parent" -> {
            if (pinGate("parent")) go(Scene.PARENT) else go(Scene.ADULT)
            return
        }
        "notice:ok" -> { go(Scene.ADULT); return }
        "diary" -> picked = StoryMode.DIARY
        "coop" -> picked = StoryMode.COOP
        else -> {}
    }
    // ⭐ 0이면 오늘은 여기까지 — 누를 때 한 번만 말해 준다 (결정 2)
    if (s.limitOn && s.dayStars <= 0) {
        s.notice = "오늘 만들 이야기는 다 썼어요! 책장에서 지난 이야기를 볼까요?"
        log("하루 별 0 → 시작을 막고 책장을 권한다. 누를 때 한 번만 말하고 재촉하지 않는다 (결정 2 · ⭐2)")
        buttons(
            DemoBtn("📚 책장 보기") { send(Reply.Tapped("notice:shelf", "책장")) },
            DemoBtn("🙂 괜찮아") { send(Reply.Tapped("notice:ok", "괜찮아")) },
        )
        if (awaitValue("notice:shelf", "notice:ok") == "notice:shelf") go(Scene.SHELF) else go(Scene.ADULT)
        return
    }
    // 부모 설정 — 시작할 때 비밀번호 (아이 혼자 별을 써 버리거나 계속 만드는 것을 막는다)
    if (s.pinToStart) {
        log("부모 설정: 이야기를 시작하려면 비밀번호 → 어른이 함께 있을 때만 시작")
        if (!pinGate("start")) { go(Scene.ADULT); return }
    }
    s.resetStory()
    s.mode = picked
    if (s.isDiary) {
        s.diaryStart = System.currentTimeMillis()
        mark("diaryentry")
        log(
            "일기 모드로 시작 — S3의 질문 세트와 칸 목록만 갈아끼운다. 새 화면 · 새 이벤트는 없다 (일기 설계 §1 · §8)\n" +
                "⚠️ 화면 어디에도 \"일기\"라고 쓰지 않는다 — 아이에게 숙제처럼 들린다. 팀 안에서만 쓰는 이름이다 (§0)"
        )
    }
    if (s.isCoop) {
        mark("coopentry")
        log("부모 협업 모드로 시작 — **부모에게 소재를 받는 모드가 아니라 질문하는 사람을 바꾸는 모드다** (협업 §0)")
        log("질문 데이터는 새로 만들지 않았다. 일기 모드 사다리를 그대로 띄우고 내리는 주체만 AI → 부모로 바뀐다 (협업 §5)")
    }
    // 이벤트에 더하는 것은 story_start 의 mode 필드 **하나뿐**이다 (일기 §8 · 협업 §8)
    val modeField = "mode" to when (picked) {
        StoryMode.DIARY -> "diary"
        StoryMode.COOP -> "coop"
        StoryMode.STORY -> "story"
    }
    if (s.limitOn) {
        val before = s.dayStars
        s.usedToday++
        event("story_start", modeField, "star_before" to before, "star_after" to s.dayStars)
        log("⭐ 하나를 쓰고 이야기 시작 — 하루 별 $before → ${s.dayStars} (진행 막대와 다른 것)")
        if (s.isDiary) log("⚠️ 일기 모드도 6~8쪽 동화책이라 원가 · 월 권수 소모가 동화 모드와 같다. 매일 쓰라고 파는 기능인데 월 2권과 부딪힌다 — 조장 · 멘토 #11과 정할 일 (§7-2)")
    } else {
        event("story_start", modeField, "star_before" to "무제한", "star_after" to "무제한")
        log("하루 한도 꺼짐(부모 설정) → 별을 쓰지 않고 시작")
    }
    mark("adult")
    pause(500)
    // 일기 · 협업 모드는 "누구랑 같이 만들래?"를 묻지 않는다 (9/21 사용자 요청).
    // 재료가 아이의 실제 하루라 **오늘 누구와 있었는지는 이야기 안에서 묻는 것**이 자연스럽고(질문 2번),
    // 협업 모드는 옆에 있는 사람이 곧 질문하는 사람이라 따로 고를 이유가 없다.
    if (s.isDiary) go(if (s.firstDay) Scene.MAKEHERO else Scene.BESTIARY) else go(Scene.PARTNER)
}

// ── 장면 2 · 도감 (⭐8 주인공 4칸 + 점선 ＋ 버튼) ──────────────────

private suspend fun Director.sceneBestiary() {
    while (true) {
        s.stage = Stage.Bestiary(s.heroes.toList(), s.heroes.size < 4)
        val names = s.heroes.map { it.name }
        val full = s.heroes.size >= 4
        say(if (full) "주인공이 다 찼어! 안 쓰는 친구를 지우면 새로 만들 수 있어." else "오늘 이야기의 주인공은 누구로 할까?")
        val b = mutableListOf(
            DemoBtn("🖐 ${names.first()} 카드를 탭") { send(Reply.Tapped("hero:0", names.first())) },
        )
        if (!full) b += DemoBtn("➕ ＋ 버튼 — 새 주인공 만들기 (⭐20)") { send(Reply.Tapped("plus", "＋")) }
        // 네 칸이 다 차면 더 못 만든다 → 지울 수 있게 한다 (9/21 요청)
        if (s.heroes.size > 1) b += DemoBtn("🗑 ${names.last()} 지우기") { send(Reply.Tapped("del:${s.heroes.lastIndex}", names.last())) }
        buttons(*b.toTypedArray())

        val v = awaitValue()
        if (v == "plus") {
            log("＋ 버튼 → 주인공은 이때 한 번만 만든다. 이야기 중에는 고정 (⭐20)")
            go(Scene.MAKEHERO)
            return
        }
        if (v.startsWith("del:")) {
            val di = v.removePrefix("del:").toIntOrNull() ?: continue
            if (di !in s.heroes.indices) continue
            // 마지막 한 명은 남긴다 — 도감이 비면 이야기를 시작할 수 없다
            if (s.heroes.size <= 1) {
                say("마지막 한 명은 지울 수 없어. 새로 만들고 나서 지워 줘!")
                pause(1600)
                continue
            }
            val gone = s.heroes.removeAt(di)
            if (s.heroAttr == gone.attr) s.heroAttr = null
            s.reactions++
            log("도감에서 \"${gone.name}\" 지움 → 빈 칸이 생겨 새로 만들 수 있다 (9/21 요청 · 네 칸이 다 차면 못 만들던 문제)")
            say("${gone.name}${eul(gone.name)} 지웠어. 이제 새로 만들 수 있어!")
            mark("herodelete")
            pause(1500)
            continue
        }
        onHeroPicked(v)
        return
    }
}

private suspend fun Director.onHeroPicked(v: String) {
    val idx = v.removePrefix("hero:").toIntOrNull() ?: 0
    s.heroAttr = s.heroes[idx].attr
    mark("bestiary")
    log("주인공 고름: ${s.heroes[idx].name} → 고정 스프라이트 그대로 씀 (⭐20 · ⭐26)")
    say("${s.heroes[idx].name}${ya(s.heroes[idx].name)}, 준비됐지?")
    pause(900)
    // 도감은 두 모드가 함께 쓴다 — 일기 모드에서도 오늘 이야기의 주인공은 아이가 고른 인형이다
    go(if (s.isDiary) Scene.DIARY else Scene.PLACE)
}

// ── 장면 2↳ · 주인공 만들기 (말로 / 골라서) — 둘 다 같은 펠트 그림 ──

private suspend fun Director.sceneMakeHero() {
    var attr = HeroAttr(hair = "short", shirt = Color(0xFF3F7BD9), glasses = "none", likes = "dino")
    var fixes = 0
    val c = s.childName

    fun heroName(): String {
        val h = when (attr.hair) { "long" -> "긴 머리"; "tied" -> "묶은 머리"; else -> "짧은 머리" }
        val col = when (shirtKey(attr.shirt)) { "red" -> "빨간 옷"; "yellow" -> "노란 옷"; else -> "파란 옷" }
        return if (attr.glasses != "none") "$h 안경 $c" else "$h $col $c"
    }

    suspend fun save() {
        s.heroes += Hero(heroName(), attr)
        s.heroAttr = attr
        log("주인공 확정 → 고정 스프라이트로 도감에 저장. 이야기 중 다시 생성하지 않음 (⭐20 · ⭐26)")
        pause(600)
        go(Scene.BESTIARY)
    }

    suspend fun presetBuilder() {
        s.stage = Stage.HeroBuilder(attr)
        say("머리, 옷, 눈, 안경, 아래옷을 골라 봐!")
        mark("preset")
        buttons(DemoBtn("🖐 (시연) 긴 머리 · 노란 옷 · 별 눈 · 네모 안경 고르고 좋아") { send(Reply.Tapped("demo", "시연")) })
        while (true) {
            val r = awaitReply()
            if (r !is Reply.Tapped) continue
            when {
                r.value == "demo" -> {
                    attr = attr.copy(hair = "long", shirt = Color(0xFFF9B233), eyes = "star", glasses = "square")
                    s.stage = Stage.HeroBuilder(attr)
                    pause(700)
                    save(); return
                }
                r.value == "ok" -> {
                    log("골라서 만들기 확정 · 프리셋 조합(81조합 중 하나) · 생성 없음")
                    save(); return
                }
                r.value.startsWith("set:") -> {
                    val (_, key, v) = r.value.split(":")
                    attr = when (key) {
                        "hair" -> attr.copy(hair = v)
                        "eyes" -> attr.copy(eyes = v)
                        "glasses" -> attr.copy(glasses = v)
                        "bottom" -> attr.copy(bottom = v)
                        else -> attr.copy(shirt = Color(v.toLong(16) or 0xFF000000))
                    }
                    s.reactions++
                    s.stage = Stage.HeroBuilder(attr)
                }
            }
        }
    }

    suspend fun generate() {
        inputs(false, false)
        buttons()
        s.images++
        s.stage = Stage.Making("인형을 만드는 중…")
        say("조금만 기다려!")
        log("속성값 → 영어 키워드 → 우리 서버 ComfyUI → 투명 배경 인형 · 생성 이미지 +1 (발화 원문 · 이름은 보내지 않음)")
        pause(2200)
    }

    suspend fun confirm(): String {
        s.stage = Stage.Confirm(Art.HeroArt(attr), "좋아", "싫어", redraws = fixes, redrawMax = s.redrawMax)
        say("짠! 이렇게 생겼어. 마음에 들어?")
        mark("makehero")
        buttons(
            DemoBtn("🖐 좋아 탭") { send(Reply.Tapped("ok", "좋아")) },
            DemoBtn("🖐 싫어 탭 (수정 ${fixes + 1}/${s.redrawMax})") { send(Reply.Tapped("no", "싫어")) },
        )
        return awaitValue("ok", "no")
    }

    /** 다시 만들기를 다 쓰면 만든 것들을 늘어놓고 아이가 고른다 (결정 29) */
    suspend fun pickFromTries() {
        val tries = s.heroTries.takeLast(3)
        s.stage = Stage.CardsRow(tries.mapIndexed { i, a -> Card("${i + 1}번", Art.HeroArt(a), "$i") })
        say("여태 만든 것 중에 어떤 게 제일 좋아?")
        log("수정 ${s.redrawMax}번 다 씀 → 새로 만들지 않고 만든 ${tries.size}장 중에서 고르게 한다 (결정 29) · 생성 없음")
        buttons(*tries.indices.map { i -> DemoBtn("🖐 ${i + 1}번 고름") { send(Reply.Tapped("$i", "${i + 1}번")) } }.toTypedArray())
        val idx = awaitValue(*tries.indices.map { "$it" }.toTypedArray()).toIntOrNull() ?: 0
        attr = tries[idx]
        s.stage = (s.stage as? Stage.CardsRow)?.copy(picked = "$idx") ?: s.stage
        pause(900)
        save()
    }

    // 부분 질문 3개 — 선택지를 말하지 않는 소크라틱 질문. 답 후보 5개씩, 각 답이 뜻하는 속성값
    data class Q(val text: String, val key: String, val easier: String, val spoken: List<Answer>, val cards: List<Card>)
    val questions = listOf(
        Q("우리 주인공은 머리가 어떻게 생겼을까?", "hair", "거울 속 $c 머리를 떠올려 봐. 어떤 머리야?",
            listOf(Answer("짧아!", "short"), Answer("길어!", "long"), Answer("묶었어!", "tied"), Answer("짧게 짧게!", "short"), Answer("긴 머리가 좋아! 바람에 날려!", "long")),
            listOf(Card("짧아", Art.Img("ic_hair_short", Art.Emoji("💇")), "short"), Card("길어", Art.Img("ic_hair_long", Art.Emoji("👩")), "long"), Card("묶었어", Art.Img("ic_hair_tied", Art.Emoji("🎀")), "tied"))),
        Q("주인공은 무슨 색 옷을 입으면 좋을까?", "shirt", "$c${ga(c)} 제일 좋아하는 색은 뭐야?",
            listOf(Answer("파랑!", "3F7BD9"), Answer("빨간 거!", "F25C4C"), Answer("노랑!", "F9B233"), Answer("파란색! 하늘 색이니까!", "3F7BD9", reason = true), Answer("빨강 빨강!", "F25C4C")),
            listOf(Card("빨강", Art.Img("ic_shirt_red", Art.Emoji("🟥")), "F25C4C"), Card("파랑", Art.Img("ic_shirt_blue", Art.Emoji("🟦")), "3F7BD9"), Card("노랑", Art.Img("ic_shirt_yellow", Art.Emoji("🟨")), "F9B233"))),
        Q("주인공이 안경을 쓰면 어떨까?", "glasses", "안경을 쓰면 뭐가 잘 보일까? 주인공도 쓸까?",
            listOf(Answer("동글 안경!", "round"), Answer("네모 안경!", "square"), Answer("안 써!", "none"), Answer("동그란 거! 멀리 보려고!", "round", reason = true), Answer("안경 싫어!", "none")),
            listOf(Card("동글 안경", Art.Img("ic_glasses_round", Art.Emoji("👓")), "round"), Card("네모 안경", Art.Img("ic_glasses_square", Art.Emoji("🕶️")), "square"), Card("안 써", Art.Img("ic_glasses_none", Art.Emoji("🙂")), "none"))),
    )

    fun apply(key: String, v: String) {
        attr = when (key) {
            "hair" -> attr.copy(hair = v)
            "glasses" -> attr.copy(glasses = v)
            else -> attr.copy(shirt = Color(v.toLong(16) or 0xFF000000))
        }
    }

    suspend fun voiceStep(from: Int) {
        for (i in from until questions.size) {
            val q = questions[i]
            s.stage = Stage.HeroShow(if (i == 0) null else attr, if (i == 0) "주인공 만드는 중 — 마이크로 말해 줘" else "이렇게 되고 있어 — 마이크로 말해 줘")
            val r = ask(Question(text = q.text, kind = Kind.EASY, spoken = q.spoken, easierText = q.easier, easierAsk = "골라 볼래?", choices = q.cards))
            when (r) {
                is Reply.Spoke -> { apply(q.key, r.value); log("Whisper → \"${r.text}\" → 속성값 ${q.key}=${r.value} (발화 원문 아님 · 음성 사본 즉시 삭제)") }
                is Reply.Tapped -> apply(q.key, r.value)
                else -> {}
            }
        }
    }

    suspend fun fixFlow() {
        s.stage = Stage.HeroShow(attr, "어디를 바꿀까 — 마이크로 말해 줘")
        val r = ask(
            Question(
                text = "어디를 바꾸면 더 마음에 들까?",
                kind = Kind.EASY,
                spoken = listOf(
                    Answer("옷! 빨간 거!", "shirt:F25C4C"), Answer("머리 길게!", "hair:long"), Answer("노란 옷!", "shirt:F9B233"),
                    Answer("머리 묶어 줘!", "hair:tied"), Answer("네모 안경 씌워 줘!", "glasses:square"),
                ),
                easierText = "주인공을 잘 봐. 어디가 마음에 안 들어?", easierAsk = "뭘 바꿀까?",
                choices = listOf(Card("머리", Art.Img("ic_hair_short", Art.Emoji("💇")), "hair"), Card("옷", Art.Img("ic_shirt_blue", Art.Emoji("👕")), "shirt"), Card("안경", Art.Img("ic_glasses_round", Art.Emoji("👓")), "glasses")),
            )
        )
        when (r) {
            is Reply.Spoke -> {
                val (k, v) = r.value.split(":")
                apply(k, v)
                log("한 번에 한 가지만 · Whisper → \"${r.text}\" → $k=$v")
            }
            is Reply.Tapped -> {
                val idx = questions.indexOfFirst { it.key == r.value }
                if (idx >= 0) voiceStep(idx)
            }
            else -> {}
        }
    }

    inputs(false, false)
    s.stage = Stage.CardsRow(listOf(Card("말로 만들기", Art.Img("ic_mic", Art.Emoji("🎤")), "voice"), Card("골라서 만들기", Art.Img("ic_dials", Art.Emoji("🎛️")), "preset")))
    say("우리 주인공을 만들자! 말로 만들까, 골라서 만들까?")
    buttons(
        DemoBtn("🖐 말로 만들기 탭") { send(Reply.Tapped("voice", "말로 만들기")) },
        DemoBtn("🖐 골라서 만들기 탭") { send(Reply.Tapped("preset", "골라서 만들기")) },
    )
    when (awaitValue("voice", "preset")) {
        "preset" -> { log("골라서 만들기 — 머리 · 옷 · 눈 · 안경 각 3개 토글 (81조합)"); presetBuilder(); return }
        else -> log("말로 만들기 — 질문 → 녹음 → 속성값 → ComfyUI 생성 (⭐20: 도감에서 한 번만)")
    }

    voiceStep(0)
    s.heroTries.clear()
    while (true) {
        generate()
        s.heroTries += attr
        if (confirm() == "ok") { save(); return }
        if (fixes >= s.redrawMax) { pickFromTries(); return }
        fixes++
        log("싫어 → 수정 $fixes/${s.redrawMax} · 한 번에 한 가지만")
        fixFlow()
    }
}

// ── 장면 1↳ · 오늘 누구랑 함께 하나 (9/17) ──────────────────────────

private suspend fun Director.scenePartner() {
    // 그림 카드를 띄우지 않는다 — 녹음으로 답한다 (9/17 요청). 말한 사람의 그림은 답한 뒤에만 보인다.
    s.stage = Stage.PartnerPick()
    val spoken = listOf(
        Answer("엄마랑!", "mom"), Answer("엄마랑 같이 할 거야.", "mom"),
        Answer("아빠!", "dad"), Answer("아빠랑 할래! 아빠 오늘 쉬는 날이야.", "dad"),
        Answer("이모랑!", "aunt"), Answer("우리 이모!", "aunt"),
        Answer("할머니랑 같이!", "grandma"), Answer("할머니! 할머니 집에 놀러 왔어.", "grandma"),
        Answer("할아버지!", "grandpa"), Answer("할아버지랑 할래.", "grandpa"),
        Answer("친구랑 할 거야!", "friend"), Answer("옆집 친구랑!", "friend"),
    )
    val asks = listOf(
        "오늘은 누구랑 같이 이야기를 만들어? 마이크를 누르고 말해 줘!",
        "옆에 누가 있어? 이름을 불러 줄래?",
        "함께 온 사람한테 손을 흔들어 봐! 누구야?",
    )
    log("함께 하는 사람을 녹음으로 묻는다 — 그림 카드 없음 (9/17). 호칭을 이모로 못 박지 않고, 말한 사람에 맞춰 질문 · 말투(할머니 · 할아버지는 높임, 친구는 친구 말투) · 책 자막 · 기록이 바뀐다")
    var key: String? = null
    var round = 0
    while (key == null) {
        val q = Question(text = asks[round.coerceAtMost(asks.lastIndex)], kind = Kind.EASY, spoken = spoken, noCards = true, id = "partner")
        say(q.text)
        inputs(mic = true, next = true)
        val b = mutableListOf<DemoBtn>()
        b += DemoBtn("🎲 말로 답함 — 후보 ${spoken.size}개 중 무작위") { val a = spoken.random(); send(Reply.Spoke(a.text, a.value, a)) }
        spoken.forEach { a -> b += DemoBtn("🗣 \"${a.text}\"") { send(Reply.Spoke(a.text, a.value, a)) } }
        b += DemoBtn("🤐 대답 없음 (➡️와 같음)") { send(Reply.Silent) }
        buttons(*b.toTypedArray())
        setListening(q)
        when (val r = awaitReply()) {
            is Reply.Spoke -> {
                s.micOn = false
                childSays(r.text)
                key = r.value.takeIf { v -> PARTNERS.any { it.key == v } }
                if (key == null) { say("다시 한번 말해 줄래?"); pause(1200) }
                else log("Whisper \"${r.text}\" → 호칭 사전과 맞춤 → ${partner(key).name}")
            }
            is Reply.Silent -> {
                round++
                log("무응답 → 카드 대신 다른 말로 다시 묻는다 (${round}번째)")
                if (round > asks.lastIndex) {
                    key = "mom"
                    say("그럼 오늘은 엄마랑 함께라고 할게! 나중에 바꿀 수 있어.")
                    log("세 번 다 무응답 → 기본 호칭(엄마)으로 두고 진행 · 부모 모드에서 바꿀 자리")
                    pause(1500)
                }
            }
            else -> {}
        }
    }
    setListening(null)
    inputs(false, false)
    buttons()
    s.partnerKey = key
    s.stage = Stage.PartnerPick(key)
    event("partner", "who" to s.pn, "adult" to s.partner.adult, "mode" to "voice")
    log("함께 하는 사람 = ${s.pn} (${if (s.partner.honor) "높임말" else if (s.partner.adult) "어른" else "또래 친구"}) → 이후 질문 · 자막 · 부모 기록에 \"${s.pn}\"")
    pause(500)
    say(if (s.partner.honor) "${s.pn}${rang(s.pn)} 함께구나! ${s.pn}, 잘 부탁드려요!" else "${s.pn}${rang(s.pn)} 함께구나! 좋아!")
    mark("partner")
    pause(1600)
    if (s.firstDay) go(Scene.MAKEHERO) else go(Scene.BESTIARY)
}

// ── 장면 3′ · 오늘 있었던 일 ────────────────────────────
// 일기 모드·부모 협업 모드의 장면과 질문 엔진은 `DiaryScenes.kt` 에 따로 있다 (이 파일이 너무 길어졌다).

// ── 고정 질문 2개 (기준 질문 · 매번 같다) ──────────────────────────

private val BASE_PLACE = QVariant(
    "base_place", "base", Level.entries.toSet(), Kind.EASY, "기준 질문 1 · 어디 (처음 수준 판단)",
    text = { "오늘은 어디로 가 볼까?" }, easier = { "${it.childName}${eun(it.childName)} 어디에 가 보고 싶었어? 높은 데? 깊은 데?" },
    answers = {
        listOf(
            Answer("우주!", "space", lv = 1), Answer("바다!", "sea", lv = 1), Answer("공룡!", "dino", lv = 1),
            Answer("바닷속에 가 볼래.", "sea", lv = 2), Answer("눈 오는 데 가고 싶어.", "snow", lv = 2), Answer("공룡 나라에 가자!", "dino", lv = 2),
            Answer("로켓 타고 우주 갈래! 별 보러 가고 싶어서.", "space", reason = true, el = setOf("계기"), con = true, lv = 3),
            Answer("바닷속! 거기서 고래를 만나면 같이 헤엄칠 거야.", "sea", el = setOf("시도"), con = true, lv = 3),
            Answer("공룡 나라! 공룡이 진짜 큰지 보고 싶거든.", "dino", reason = true, con = true, lv = 3),
        )
    },
)

private fun basePlaceCards() = THEMES.map { Card(it.label, it.cardArt, it.key) }

private val BASE_WHO = QVariant(
    "base_who", "base", Level.entries.toSet(), Kind.HARD, "기준 질문 2 · 누가 (처음 수준 판단)",
    text = { "누가 흔들었을까?" }, easier = { "창문 밖을 잘 봐. 뭐가 보여?" },
    hint = { "쉿, 창문에서 뭔가 움직였어! 누구일까?" },
    answers = {
        val nc = it.th.newcomers.map { c -> c.value }
        val v = it.th.vehicle
        listOf(
            Answer("${nc[0]}!", nc[0], lv = 1), Answer("${nc[1]}!", nc[1], lv = 1), Answer("${nc[2]}!", nc[2], lv = 1),
            Answer("${nc[0]}${ga(nc[0])} 와서 흔들었어.", nc[0], lv = 2),
            Answer("${nc[2]}${ga(nc[2])} 그랬어! 창문에서 봤어.", nc[2], el = setOf("배경"), lv = 2),
            Answer("${nc[1]}${ga(nc[1])} 쿵 부딪혀서 흔들린 거야.", nc[1], reason = true, el = setOf("결과"), con = true, lv = 3),
            Answer("${nc[0]}${ga(nc[0])} $v${eul(v)} 꽉 잡았어. 그래서 흔들렸어.", nc[0], el = setOf("시도", "결과"), con = true, lv = 3),
        )
    },
    fallback = { val n = it.th.newcomers[0].value; Answer(n, n) },
)

// ── 장면 3 · 어디로 갈까 (고정 1턴) → 거기엔 뭐가 있을까 (배경 속 것이 반짝) ──

private suspend fun Director.scenePlace() {
    val c = s.childName
    s.stage = Stage.HeroShow(s.heroAttr ?: HeroAttr(), "")
    val q = BASE_PLACE.toQuestion(s).copy(choices = basePlaceCards(), noCards = false, easierAsk = "어디로 갈까?", hint = null)
    val r = ask(q)
    val said = when (r) {
        is Reply.Tapped -> r.value
        is Reply.Spoke -> r.value.ifEmpty { "space" }
        else -> "space"
    }
    // 아이 말을 장소 유형에 맞춘다. 유형에 없으면 배경을 새로 만든다 (구현대본 §6)
    if (THEMES.any { it.key == said }) {
        s.themeKey = said; s.placeLabel = null; s.generatedBg = false
    } else {
        s.themeKey = "dino"            // 가장 가까운 유형(땅 위)에서 탈것 · 사건 · 미션 뼈대를 가져온다
        s.placeLabel = "눈 오는 데"
        s.generatedBg = true
    }
    // 장소가 정해지면 **그 장소의 기본값들도 같이** 맞춘다 (9/21).
    // 장면 4 · 7을 건너뛰어도(반응 예산 초과 · 시연 서랍) 엉뚱한 것이 따라오지 않게 —
    // 바닷속에 "외계인 뿌뿌"가 서 있고 우주에 공룡이 따라가던 것이 이것 때문이었다.
    s.dinoKey = s.buddies.first().key
    s.newcomerKind = s.th.defaultNewcomer
    s.place = s.placeName
    judge(BASE_PLACE, r, q.text)
    event("slot_filled", "slot" to "place", "value" to s.placeName, "source" to sourceOf(r))

    buttons()
    if (s.generatedBg) {
        s.stage = Stage.Making("${s.placeName} 배경을 만드는 중…")
        say("${s.placeName}? 그런 데는 처음이야. 그림을 만들어 볼게!")
        log("장소 유형에 없는 답 → 영어 키워드만 ComfyUI로 → 배경 1장 생성 → 폰 소품함에 저장 (구현대본 §6)")
        event("image_request", "type" to "background", "reason" to "no_preset_type", "elapsed" to "2.4s")
        s.images++
        pause(2400)
    } else {
        s.images++
        log("${s.th.label} 배경은 프리셋 — 대기 0초 (⭐26)")
    }
    val base = listOf(
        WorldItem(s.th.vehicleArt, 0.46f, 0.22f, 0.13f),
        WorldItem(hero, 0.30f, 0.30f, 0.11f),
    )
    s.stage = world(base, glow = emptySet())
    say(if (s.generatedBg) "$c${ga(c)} 하얀 ${s.placeName}에 왔어!" else "$c${ga(c)} ${s.th.arrival}")
    pause(1500)
    partnerSays(partnerLine(s, "place"))
    pause(1300)

    // 소크라틱 이어 묻기 — 아이가 말한 것이 배경 그림 속에서 반짝인다 (새 그림을 얹지 않는다 · 9/17)
    val (_, r2) = askSlot("sight") { it.copy(noCards = true, hint = null) }
    val keys = s.hotspots.map { it.key }.toSet()
    val said2 = (r2 as? Reply.Spoke)?.value?.split(",")?.map { it.trim() }?.filter { it in keys }.orEmpty()
    s.mentioned.clear()
    if (said2.isNotEmpty()) {
        s.mentioned += said2
        s.stage = world(base, bump = true)
        val names = joinWords(said2.map { hotspotWord(it) })
        say("정말 ${names}${ga(names)} 있네! 눌러 볼래?")
        log("아이가 말한 것($names)은 새로 그리지 않고 **배경 그림에 이미 그려진 그 자리**가 반짝이고 통 튄다 (9/17 · 생성 0장)")
    } else {
        s.stage = world(base, glow = keys, bump = true)
        say("${s.placeName}에는 이런 것들이 있네! 눌러 볼래?")
        log("무응답 → 배경 속 것들을 모두 한 번 반짝여 보여 준다 (카드 없음 · 새 그림 없음)")
    }
    event("slot_filled", "slot" to "sight", "value" to said2.joinToString("+").ifEmpty { "(없음)" }, "source" to sourceOf(r2))
    buttons(DemoBtn("➡️ 다음으로") { send(Reply.Tapped("go", "다음")) })
    log("배경 속 것(${s.hotspots.map { it.name }.distinct().joinToString(" · ")})은 눌러 볼 수 있다 — 그 부분이 통 튀고 \"반짝!\" 같은 글자가 뜬다 (저장 안 함)")
    mark("place")
    withTimeoutOrNullReply(5.0)
    if (s.mentioned.isEmpty()) s.stage = world(base, glow = emptySet())
    go(Scene.EVENT)
}

// ── 장면 4 · 누가 흔들었을까 (고정 2턴 · 카드 없음) → 그다음 (질문 은행) ──

private suspend fun Director.sceneEvent() {
    val v = s.th.vehicle
    val base = listOf(
        WorldItem(s.th.vehicleArt, 0.44f, 0.20f, 0.15f, shake = true),
        WorldItem(hero, 0.26f, 0.30f, 0.11f),
    )
    s.stage = world(base, quake = true, bump = true)
    say("어? ${s.th.eventAsk}!")
    log("장소별 사건(${s.th.eventAsk}) · 배경 속 것들도 함께 흔들린다 (25 B안 · ⭐26 · 새 그림 없음)")
    pause(1600)
    val q = BASE_WHO.toQuestion(s).copy(
        partnerLine = partnerLine(s, "shake"),
        partnerChildAnswer = BASE_WHO.answers(s).filter { it.lv >= 2 },
    )
    val r = ask(q)
    val nc0 = s.th.newcomers[0].value
    s.newcomerKind = when (r) {
        is Reply.Tapped -> r.value
        is Reply.Spoke -> r.value.ifEmpty { nc0 }
        else -> nc0
    }.let { k -> if (s.th.newcomers.any { it.value == k }) k else nc0 }
    s.newcomerEmoji = (s.th.newcomers.firstOrNull { it.value == s.newcomerKind }?.art as? Art.Img)?.let { (it.fallback as? Art.Emoji)?.text } ?: "👽"
    s.problem = "${s.newcomerKind}${ga(s.newcomerKind)} $v${eul(v)} 흔듦"
    judge(BASE_WHO, r, q.text)
    event("slot_filled", "slot" to "problem", "value" to s.problem, "source" to sourceOf(r))
    // 창문에 새 친구가 나타난다
    val shown = base.map { it.copy(shake = false) } + WorldItem(s.newcomerArt, 0.62f, 0.18f, 0.12f)
    s.stage = world(shown)
    if (r is Reply.Spoke) log("LLM 판정: 이름을 가린 문장({주인공}: ${r.text}) → Anthropic → S1 · S2 표시 JSON → 수준은 규칙이 계산")

    // 질문 은행 — 그다음 (결과 · 대응 · 누가 놀랐나 중 하나)
    val (_, r2) = askSlot("follow")
    (r2 as? Reply.Spoke)?.let { event("slot_filled", "slot" to "follow", "value" to it.text, "source" to "voice") }
    s.slots["follow"] = valueOf(r2) ?: ""
    mark("event")
    pause(800)
    go(Scene.CAUSE)
}

// ── 장면 5 · 왜 그랬을까 (3턴 · 템플릿 확정) → 가정 질문(까닭 짓기만) ──

private suspend fun Director.sceneCause() {
    val nc = s.newcomerKind
    val c = s.childName
    s.stage = world(
        listOf(
            WorldItem(s.newcomerArt, 0.54f, 0.20f, 0.14f),
            WorldItem(s.th.vehicleArt, 0.36f, 0.30f, 0.11f),
            WorldItem(hero, 0.20f, 0.34f, 0.10f),
        )
    )
    val v = s.pick("cause")
    log("질문 은행 [cause] ${v.id} — ${v.probe} · 지금 수준 ${s.level.label}")
    // 까닭은 "마음을 말하는" 질문이지 만들어 내는 질문이 아니다 → 그리기 버튼을 달지 않는다 (9/21)
    val base = v.toQuestion(s).copy(easierAsk = "어떤 기분일까?")
    // 함께 하는 사람이 먼저 말하는 흐름 — 2~3초 뒤 아이에게 되묻는다 (⭐5 · 구현대본 §0-2)
    say("$nc${ga(nc)} 창문에서 쳐다봐. ${base.text}")
    inputs(false, false)
    pause(1800)
    val pl = partnerLine(s, "cause")
    partnerSays(pl)
    s.partnerTurns++
    event("utterance", "speaker" to (if (s.partner.adult) "adult" else "peer"), "who" to s.pn, "mode" to "voice", "text" to pl)
    log("[${s.pn}] 먼저 말함 → 칸을 채우지 않음 → 2.5초 뒤 \"$c${eun(c)} 어떻게 생각해?\" 한 번 (⭐5 · 구현대본 §0-2)")
    mark("partnerfirst")
    pause(2500)
    val q = base.copy(text = "$c${eun(c)} 어떻게 생각해?")
    val r = ask(q)
    val a = (r as? Reply.Spoke)?.answer
    when {
        a != null -> {
            s.causeKind = a.kind.ifEmpty { "lonely" }
            s.causeLine = a.value.ifEmpty { "친구가 없어서 심심했어" }
            s.cause = if (a.value.isEmpty()) "(잘 모르겠대) → 심심해서" else a.text.trimEnd('!', '.')
        }
        r is Reply.Tapped -> {
            val (k, line) = r.value.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "친구가 없어서 심심했어" } }
            s.causeKind = k; s.causeLine = line; s.cause = line
            log("마음 카드로 고른 답 → 수준 신호 아님 (안치영 #4)")
        }
        else -> { s.causeKind = "lonely"; s.causeLine = "친구가 없어서 심심했어"; s.cause = "심심해서 (마스코트가 채움)" }
    }
    judge(v, r, "${v.text(s)} → $c${eun(c)} 어떻게 생각해?")   // 3턴째 → 여기서 템플릿이 정해진다
    if (s.templateKey == null) decideTemplate("장면을 건너뛰어 턴 수가 달라 까닭 질문 뒤에 확정")
    event("slot_filled", "slot" to "cause", "value" to s.causeLine, "kind" to s.causeKind, "source" to sourceOf(r))
    s.template?.let { t ->
        say("아하, ${reported(s.causeLine)}!")
        log("템플릿 ${t.code} ${t.name} — ${t.shape} · 속성 ${s.attribute} · 이야기 잇기 질문 [${t.plot.joinToString(", ")}] · 매듭 [${t.ending.joinToString(", ")}]")
        pause(1200)
    }

    // 소크라틱 가정 질문 — 까닭 짓기일 때만 (역할1 조사2 #5)
    if (s.level == Level.REASON) {
        val (_, r2) = askSlot("hypo") { it.copy(noCards = true) }
        (r2 as? Reply.Spoke)?.answer?.let { if (it.value.isNotEmpty()) s.slots["hypo"] = it.value }
    } else {
        log("가정 질문(\"네가 ○○였다면?\" · \"다른 방법은?\")은 까닭 짓기 수준일 때만 — 지금은 ${s.level.label}라 건너뜀 (역할1 조사2 #5)")
    }
    mark("cause")
    pause(900)
    go(Scene.DRAW)
}

// ── 장면 6 · 그림판 (아이 그림 원본 그대로 · 이름 · 입 위치) ───────

private suspend fun Director.sceneDraw() {
    if (budgetOver()) return
    val nc = s.newcomerKind
    s.drawing.clear()
    s.stage = Stage.DrawPad()
    say("$nc${eun(nc)} 어떻게 생겼을까? 크레용으로 그려 줄래?")
    inputs(false, false)
    buttons(
        DemoBtn("✅ 지금 그린 그림으로 완료") { send(Reply.Tapped("done", "완료")) },
        DemoBtn("🖍 그리기 싫어 → 프리셋 3장") { send(Reply.Tapped("preset", "프리셋")) },
    )
    val v = awaitValue("preset", "done")
    if (v == "preset" || s.drawing.isEmpty()) {
        s.drawing.clear()
        s.stage = Stage.CardsRow((0..2).map { Card(listOf("뿌뿌", "반짝", "동글")[it] + " " + nc, Art.Alien(it), "$it") })
        say("그럼 이 중에 누가 $nc${ga(nc)} 닮았어?")
        log("그리기 싫어함 → 프리셋 3장 (초안 장면 6 ↳) · 프리셋을 골라도 \"아이 것\"으로 취급")
        buttons(DemoBtn("🖐 첫 번째 프리셋 탭") { send(Reply.Tapped("0", "뿌뿌")) })
        s.drawnPreset = awaitValue("0", "1", "2").toInt()
        s.stage = (s.stage as? Stage.CardsRow)?.copy(picked = "${s.drawnPreset}") ?: s.stage
        pause(700)
    } else {
        event("make", "kind" to "draw")
        log("아이가 직접 그림 (${s.drawing.size}획) · 원본 그대로, AI로 다시 그리지 않음 · 흰 오려낸 테두리만 (27)")
    }
    s.reactions++

    s.stage = Stage.Show(s.friendArt, if (s.drawing.isNotEmpty()) "${s.childName}${ga(s.childName)} 그린 $nc" else "${s.childName}${ga(s.childName)} 고른 $nc")
    partnerSays(partnerLine(s, if (s.drawing.isNotEmpty()) "drawn" else "picked"))
    pause(1300)
    val (_, r) = askSlot("name") { it.copy(noCards = true, hint = null) }   // 이름은 말로만 — 그리기 자리가 아니다 (9/21)
    if (r is Reply.Spoke) {
        s.friendName = r.value
        event("slot_filled", "slot" to "name", "value" to r.value, "source" to "voice")
        log("등장인물 이름 \"${s.friendName}\" → 이름 사전에 추가 → 이후 LLM에는 {친구1}로만 (조사3 §3-2)")
    } else if (r is Reply.Tapped) {
        s.friendName = r.value
    } else {
        s.friendName = "$nc 친구"
        log("이름 무응답 → 마스코트가 \"${s.friendName}\"로 부름 (이름 사전에는 안 넣음)")
    }
    s.newcomer = "${s.friendName} (아이 그림)"

    s.stage = Stage.MouthTap(s.friendArt)
    say("${s.friendName}${eun(s.friendName)} 어디로 말할까? 입을 콕 눌러 줘.")
    buttons(DemoBtn("🖐 입 위치 탭") { send(Reply.Tapped("mouth", "입")) })
    awaitValue("mouth")
    log("입 위치 1점만 저장 → 말할 때 그 자리만 움직임 (얼굴 인식 아님)")
    mark("draw")
    pause(900)
    go(Scene.PLOT)
}

// ── 장면 6↳ · 이야기 잇기 — 템플릿이 정한 질문 2개 (질문 은행 · 4~5턴) ──

private suspend fun Director.scenePlot() {
    if (budgetOver()) return
    val t = s.template ?: templateOf(chooseTemplate(s.level, s.causeKind).first).also {
        s.templateKey = it.key; s.attribute = chooseTemplate(s.level, s.causeKind).second
    }
    val f = s.friendName
    s.stage = world(
        listOf(
            WorldItem(s.th.vehicleArt, 0.46f, 0.22f, 0.13f),
            WorldItem(s.friendArt, 0.64f, 0.26f, 0.11f),
            WorldItem(hero, 0.28f, 0.30f, 0.10f),
        )
    )
    say(
        when (t.key) {
            "E" -> "${f}${rang(f)} 같이 여행을 떠나 볼까?"
            "C" -> "그런데 ${f}${ga(f)} 또 ${s.th.vehicle}${eul(s.th.vehicle)} 흔들기 시작했어!"
            "D" -> "${f}${ga(f)} 도움이 필요한가 봐."
            "A" -> "${s.childName}${eun(s.childName)} ${f}${eul(f)} 도와주기로 했어!"
            else -> "${f}${ga(f)} \"내가 제일 세!\" 하고 더 세게 흔들었어!"
        }
    )
    log("이야기 잇기 — 템플릿 ${t.code} ${t.name}의 칸 [${t.plot.joinToString(", ")}]을 질문 은행에서 매번 다른 변형으로 묻는다")
    if (t.key == "C" || t.key == "G") s.stage = world((s.stage as Stage.World).items.map { it.copy(shake = it.art == s.th.vehicleArt) }, quake = true, bump = true)
    pause(1600)
    for (slot in t.plot) {
        if (budgetOver()) return
        val (_, r) = askSlot(slot)
        val value = valueOf(r)
        if (value != null) {
            s.slots[slot] = value
            event("slot_filled", "slot" to slot, "value" to value, "source" to sourceOf(r))
            if (slot == "stop" && s.hotspots.isNotEmpty()) {
                s.stage = world((s.stage as? Stage.World)?.items.orEmpty(), bump = true)
            }
        } else {
            log("[$slot] 답 없음 → 책에서는 기본 문장으로 이어 준다 (벌점 없음)")
        }
        if (s.stage is Stage.World && (s.stage as Stage.World).quake) {
            s.stage = world((s.stage as Stage.World).items.map { it.copy(shake = false) })
        }
        pause(500)
    }
    mark("plot")
    pause(600)
    go(Scene.DINO)
}

// ── 장면 7 · 공룡도 데려갈래 (아이가 먼저 말함 → 되묻기 · 모호한 말 확인) ──

private suspend fun Director.sceneDino() {
    if (budgetOver()) return
    s.stage = world(
        listOf(
            WorldItem(s.th.vehicleArt, 0.46f, 0.22f, 0.13f),
            WorldItem(s.friendArt, 0.62f, 0.26f, 0.11f),
            WorldItem(hero, 0.28f, 0.30f, 0.10f),
        )
    )
    // 아이가 먼저 하는 말도 **장소에 맞춰** 바뀐다 — 우주에 가 놓고 공룡을 데려가던 것을 고쳤다 (9/21)
    childSays(s.buddyCall)
    s.reactions++
    log("아이가 먼저 말함 — 그 말에서 되묻는다 (소크라틱의 기본형) · 뜻이 여럿인 말이라 확인 카드 3장 (구현대본 §0-1)")
    pause(1300)
    val buds = s.buddies
    val q = Question(
        text = s.buddyAsks.random(),
        kind = Kind.CHOICE,
        choices = buds.map { Card(it.label, Art.DinoArt(Color(0xFF6FC276), it.key), it.key) },
        // 생김새로 답하는 말 — 어느 것을 골랐는지는 **생김새 낱말**로 맞춘다 (조사3 §3-1)
        spoken = buildList {
            buds.forEach { b ->
                add(Answer("${b.label}!", b.key, lv = 1))
                add(Answer("${b.said} 거!", b.key, lv = 2))
            }
            add(Answer("${buds[0].said} 친구! ${buds[0].sound} 하고 소리 내.", buds[0].key, el = setOf("배경"), lv = 3))
            add(Answer("${buds[1].label}. ${buds[1].said} 친구니까 나를 도와줄 수 있어.", buds[1].key, reason = true, con = true, lv = 3))
        },
        easierAsk = "어떤 친구야?",
        id = "dino_which",
    )
    val r = ask(q)
    s.dinoKey = when (r) {
        is Reply.Tapped -> r.value
        is Reply.Spoke -> r.value.ifEmpty { buds.first().key }
        else -> buds.first().key
    }
    judge(null, r, q.text)
    if (r is Reply.Spoke) log("Whisper \"${r.text}\" → 생김새 낱말을 화면 선택지와 맞춰 \"${s.dino.label}\" (조사3 §3-1 · 안치영 #11)")
    s.friend = s.dino.name
    s.images++
    s.stage = world(
        listOf(
            WorldItem(s.th.vehicleArt, 0.52f, 0.20f, 0.13f),
            WorldItem(Art.DinoArt(s.dinoColor, s.dinoKey), 0.28f, 0.34f, 0.22f),
            WorldItem(hero, 0.14f, 0.32f, 0.10f),
        ),
        brush = true,
    )
    say("${s.dino.name}${ga(s.dino.name)} ${s.th.vehicle}에 올라탔어!")
    log("폰 소품함 먼저 확인 → 친구 칸 = ${s.dino.name} → 영어 키워드만 ComfyUI로 → 초안 생성 시작 (뒤에서 · 붓 아이콘)")
    mark("dino")
    pause(2000)
    go(Scene.SOUND)
}

// ── 장면 8 · 공룡 소리 (원본 녹음 · 다시 2번 · 폰 밖으로 안 나감) ───

private suspend fun Director.sceneSound() {
    if (budgetOver()) return
    val d = s.dino
    var left = 2
    var retried = false
    var againOnce = false
    // 소리도 친구를 따라간다 — 돌고래에게 "크아아앙!"을 시키지 않는다 (9/21)
    val sounds = when (d.key) {
        "trex" -> listOf("크아아앙!", "어흥!", "쿠오오오!", "크르릉!", "으르렁 쿵!")
        "long" -> listOf("우우웅~", "뿌우우~", "우와아~", "음머어~", "우우 우우~")
        "horn" -> listOf("뿌우우우웅!", "뿌뿌뿌!", "끼야아!", "뿌웅 뿌웅!", "부우우!")
        "alienbud" -> listOf("삐비빅 삐뽀!", "삐용 삐용!", "뾰로롱!", "지지직 삐!", "삐뽀 삐뽀!")
        "robot" -> listOf("위잉 위잉!", "철컥 철컥!", "삐— 삐—", "덜컹 위잉!", "탁 탁 위잉!")
        "babystar" -> listOf("반짝 반짝!", "쨍—", "또롱 또롱!", "사르르 반짝!", "빤짝!")
        "dolphin" -> listOf("끼익 끼익!", "뽀글 끼익!", "휘이익!", "끼끼끼!", "첨벙 끼익!")
        "seahorse" -> listOf("또르르르~", "또록 또록!", "뽀글 또르르~", "쪼르르~", "또옥 또옥!")
        "starfish" -> listOf("살랑 살랑~", "스르륵~", "찰싹 살랑!", "사락 사락~", "말랑 말랑!")
        "snowman" -> listOf("뽀드득 뽀드득!", "사르르~", "뽀도독!", "푹 푹!", "사각 사각!")
        "polarbear" -> listOf("어흐응~", "크응~", "푸우우~", "어흥!", "쿠우웅~")
        else -> listOf("꽥 꽥!", "뒤뚱 뒤뚱!", "끽 끽!", "꽤액!", "첨벙 꽥!")
    }
    fun soundStage(retry: Int) = world(
        listOf(
            WorldItem(Art.DinoArt(s.dinoColor, s.dinoKey), 0.36f, 0.30f, 0.26f),
            WorldItem(hero, 0.14f, 0.34f, 0.10f),
        ),
        retry = retry,
    )
    while (true) {
        s.stage = soundStage(-1)
        val r = ask(
            Question(
                text = if (retried) "한 번 더 해 볼까? 이번엔 어떤 소리가 날까?" else "${d.name}${eun(d.name)} 어떤 소리를 낼까? 마이크를 누르고 내 봐!",
                kind = Kind.EASY,
                noCards = true,
                spoken = sounds.map { Answer(it, it) },
            )
        )
        if (r is Reply.Spoke) {
            s.soundLine = r.text
            s.sound = "${r.text} (원본 녹음)"
            say("와, 멋진 소리야!")
            event("make", "kind" to "sound")
            log("이 녹음은 폰 밖으로 나가지 않음 — 음성 인식 경로를 끄고 녹음, 길이 · 음량만 폰에서 검사 · 말한 횟수에 안 셈 (조사3 §2-4)")
            if (left > 0) {
                s.stage = soundStage(left)
                buttons(
                    DemoBtn("🔁 다시 (${left}번 남음)") { send(Reply.Tapped("retry", "다시")) },
                    DemoBtn("👍 이 소리로") { send(Reply.Tapped("ok", "다음")) },
                )
                if (awaitValue("retry", "ok") == "retry") {
                    left--; retried = true; s.reactions++
                    continue
                }
            }
            break
        }
        if (!againOnce) {
            againOnce = true; retried = true
            log("0.3초 미만이거나 없음 → \"한 번 더!\" 한 번 (초안 장면 8)")
            say("한 번 더!")
            pause(900)
            continue
        }
        s.soundLine = sounds.first()
        s.sound = "기본 효과음 (아이 소리 없음)"
        log("그래도 없으면 기본 효과음")
        break
    }
    mark("sound")
    pause(900)
    go(Scene.CHECK)
}

// ── 장면 9 · 중간 확인 (⭐21 · ⭐26 · 24) ──────────────────────────

private suspend fun Director.sceneCheck() {
    val d = s.dino
    suspend fun show() {
        s.stage = Stage.Confirm(Art.DinoArt(s.dinoColor, s.dinoKey), "좋아", "싫어", world = true, redraws = s.redraws, redrawMax = s.redrawMax)
        say("짠! ${s.placeName}에 온 ${d.name}${if (bat(d.name)) "이야" else "야"}. 마음에 들어?")
        inputs(mic = false, next = false, draw = false)
        buttons(
            DemoBtn("🖐 좋아 탭") { send(Reply.Tapped("ok", "좋아")) },
            DemoBtn("🖐 싫어 탭") { send(Reply.Tapped("no", "싫어")) },
        )
    }

    suspend fun regen(reason: String) {
        if (s.redraws >= s.redrawMax) {
            say("여태 만든 것 중에 어떤 게 제일 좋아?")
            log("다시 그리기 상한 ${s.redrawMax}회 도달 → 새로 만들지 않고, 만든 것 3장 중에서 고르게 한다 (24 · 결정 29)")
            val kinds = (listOf(s.dinoKey) + s.buddies.map { it.key }).distinct().take(3)
            s.stage = Stage.CardsRow(kinds.map { k -> Card(dinoKind(k).label, Art.DinoArt(s.dinoColor, k), k) })
            buttons(*kinds.map { k -> DemoBtn("🖐 ${dinoKind(k).label} 고름") { send(Reply.Tapped(k, dinoKind(k).label)) } }.toTypedArray())
            s.dinoKey = awaitValue(*kinds.toTypedArray())
            s.friend = s.dino.name
            pause(1000)
            go(Scene.SOLUTION)
            return
        }
        s.redraws++
        s.images++
        buttons()
        s.stage = Stage.Making("다른 모양으로 다시 그리는 중…")
        say("다른 모양으로 다시 그려 볼게!")
        event("redraw", "index" to "${s.redraws}/${s.redrawMax}", "reason_text" to reason)
        event("image_request", "type" to "draft", "reason" to reason, "elapsed" to if (s.slowImages) "16s" else "2.4s")
        log("모양 · 종류 바꾸기 → 다시 생성 ${s.redraws}/${s.redrawMax} · 생성 이미지 +1 (색 · 크기 · 표정은 여기에 세지 않는다)")
        if (s.slowImages) {
            pause(8000)
            say("그림은 조금 뒤에 올 거야. 그동안 이야기 계속할까?")
            log("8초 넘음 → 기다리는 동안 말을 건다")
            pause(7000)
            say("오늘은 그림이 늦네! 먼저 만든 걸로 가자.")
            log("15초 넘음 → 생성을 취소하고 프리셋으로 확정 (최민우 §3) · 실패라고 말하지 않는다")
            event("image_request", "type" to "draft", "reason" to "timeout_15s_cancelled")
            pause(1600)
        } else {
            pause(2400)
        }
        show()
    }

    log("장면 7에서 뒤에서 만들던 초안이 나타남 · 초안과 최종은 같은 모델 (조사3 §3-3)")
    show()
    while (true) {
        when (awaitValue("ok", "no", "draw")) {
            "draw" -> {
                s.stage = Stage.DrawPad(forAnswer = true)
                say("좋아, 어떻게 바꿀지 그려 줄래?")
                buttons(DemoBtn("✅ 다 그렸어") { send(Reply.Tapped("done", "완료")) })
                awaitValue("done")
                s.reactions++
                s.modeDraw++
                event("utterance", "speaker" to "child", "mode" to "draw", "text" to "(그림으로 바꿔 달라고 함)")
                log("그림으로 알려줌 → 표정만 바꿔 준다 · 생성 없음 · 다시 그리기 횟수 안 씀 (⭐26 · mode: draw)")
                say("이런 얼굴로 바꿔 볼게!")
                pause(1000)
                show()
            }
            "ok" -> {
                mark("check")
                s.reactions++
                event("utterance", "speaker" to "child", "mode" to "card", "text" to "좋아")
                log("좋아 → 확정. 확정 그림을 최종 책의 참조 이미지로 씀 · 이후 색 · 크기 · 표정만 바꿔 재사용 (⭐26)")
                pause(700)
                go(Scene.SOLUTION); return
            }
            else -> {
                s.reactions++
                s.stage = Stage.Show(Art.DinoArt(s.dinoColor, s.dinoKey))
                val r = ask(
                    Question(
                        text = "어디를 바꾸면 더 좋을까?",
                        kind = Kind.EASY,
                        spoken = listOf(
                            Answer("빨강!", "color", lv = 1),
                            Answer("더 크게 해 줘.", "size", lv = 2),
                            Answer("웃는 얼굴로!", "face", lv = 1),
                            // 더미 답도 장소에 맞춰야 한다 — 우주에 가 놓고 "다른 공룡 할래"가 나오면 안 된다 (9/21)
                            Answer("${d.look} 그래서 제일 멋있어!", "kind", reason = true, lv = 3),
                            Answer("다른 ${d.label} 할래. 이건 너무 작아서.", "kind", reason = true, lv = 3),
                            Answer("색을 바꾸고 싶어.", "color", lv = 2),
                        ),
                        easierText = "${d.name}${eul(d.name)} 잘 봐. 어디가 마음에 안 들어?",
                        easierAnswer = "색!",
                        easierAsk = "뭘 바꿀까?",
                        choices = listOf(
                            Card("색", Art.Img("ic_color", Art.Emoji("🎨")), "color"),
                            Card("크기", Art.Img("ic_size", Art.Emoji("📏")), "size"),
                            Card("표정", Art.Img("ic_face", Art.Emoji("😀")), "face"),
                        ),
                        drawAnswer = Answer("(그림) 이런 얼굴로!", "face"),
                    )
                )
                val what = when (r) {
                    is Reply.Spoke -> r.value.ifEmpty { "color" }
                    is Reply.Tapped -> r.value
                    else -> "color"
                }
                judge(null, r, "어디를 바꾸면 더 좋을까?")
                when (what) {
                    "color" -> {
                        val pick = listOf("빨간" to Color(0xFFF25C4C), "파란" to Color(0xFF63B3ED), "노란" to Color(0xFFF9B233)).random()
                        s.dinoColor = pick.second
                        log("색 → 펠트 그림의 색 영역만 바꿈 · 대기 0초 · 다시 생성 안 함 · 다시 그리기 횟수 안 씀 (⭐26)")
                        say("${pick.first}색으로 바꿔 볼게!")
                        pause(900)
                        show()
                    }
                    "size", "face" -> {
                        log("크기 · 표정 → 레이어 크기 · 눈 스티커 교체. 생성 안 함 · 횟수 안 씀 (⭐26)")
                        say(if (what == "size") "더 크게 만들어 볼게!" else "웃는 얼굴로 바꿔 볼게!")
                        pause(900)
                        show()
                    }
                    else -> regen(if (r is Reply.Spoke) r.text else "모양 바꾸기")
                }
            }
        }
    }
}

// ── 장면 10 · 이야기 매듭짓기 — 해결(질문 은행) → 템플릿 마무리 질문 → 마음 → 함께 하는 사람 ──

private suspend fun Director.sceneSolution() {
    if (budgetOver()) return
    val f = s.friendName
    val t = s.template ?: templateOf("C")
    s.stage = world(
        listOf(
            WorldItem(s.friendArt, 0.56f, 0.24f, 0.14f),
            WorldItem(Art.DinoArt(s.dinoColor, s.dinoKey), 0.28f, 0.36f, 0.22f),
            WorldItem(hero, 0.10f, 0.32f, 0.10f),
        )
    )
    // 템플릿마다 앞 이야기에서 이어지는 한 마디
    say(
        when (t.key) {
            "E" -> "${s.slots["stop"] ?: "여기저기"}${eul(s.slots["stop"] ?: "여기저기")} 지나 왔어! 거의 다 왔나 봐."
            "C" -> "${s.slots["helper"] ?: s.pn}${ga(s.slots["helper"] ?: s.pn)} 도와줘서 흔들림이 멈췄어!"
            "D" -> "${s.slots["role"] ?: "구조대원"} ${s.childName}, 준비됐지?"
            "A" -> "${f}${ga(f)} 아직 고개를 숙이고 있어…"
            else -> "${f}${ga(f)} 이제 미안한 마음이 들었나 봐."
        }
    )
    pause(1500)
    // 값 = 해결 종류 : 건넬 물건 : 마지막 쪽 문장
    var solved = false
    for (slot in t.ending) {
        if (slot == "reflect" && s.level != Level.REASON) {
            log("돌아보기 질문은 까닭 짓기 수준일 때만 — 지금은 ${s.level.label}라 건너뜀 (책은 기본 문장)")
            continue
        }
        val (_, r) = askSlot(slot) {
            if (slot == "resolve") it.copy(easierAsk = "어떻게 할까?") else it   // 그리기 버튼 없음 (9/21)
        }
        val value = valueOf(r)
        if (slot == "resolve") {
            val v = value ?: "play:balloon:풍선을 들고 같이 뛰어놀았어요"
            val parts = v.split(":", limit = 3)
            s.solutionKey = parts.getOrElse(0) { "play" }
            s.solutionItem = parts.getOrElse(1) { "balloon" }
            s.solutionLine = parts.getOrElse(2) { "풍선을 들고 같이 뛰어놀았어요" }
            s.solution = solutionText()
            solved = true
            event("slot_filled", "slot" to "solution", "value" to "${s.solutionKey}/${s.solutionItem}", "source" to sourceOf(r))
            log("해결 방법 → 마지막 쪽 문장 · 미션 2에서 건넬 물건(${s.mission2().itemName})이 여기서 정해진다")
        } else if (value != null) {
            s.slots[slot] = value
            event("slot_filled", "slot" to slot, "value" to value, "source" to sourceOf(r))
        }
    }
    if (!solved) s.solution = solutionText()
    mark("solution")

    // 상대 마음 헤아리기 (수준 신호 아님 · 기록 재료) — 질문 은행
    askSlot("feel") { it.copy(noCards = true, hint = null) }

    // ── 함께 하는 사람 참여 — 누구냐에 따라 말투 · 답이 다르다 (9/17)
    val pn = s.pn
    say(
        if (s.partner.honor) "${f}${wa(f)} 친해지려면 ${pn} 도움도 있으면 좋겠다!"
        else if (s.partner.adult) "${f}${wa(f)} 친해지려면 ${pn} 도움도 있으면 좋겠다!"
        else "${f}${wa(f)} 친해지려면 친구 도움도 있으면 좋겠다!"
    )
    pause(1400)
    val (text, answers) = partnerQuestion(s)
    val help = askPartner(text, answers)
    if (help != null) {
        s.partnerHelp = help.text
        s.partnerHelpLine = help.value
        event("slot_filled", "slot" to "partner", "who" to pn, "value" to help.value, "source" to "voice")
        log("${pn} 참여 칸 = \"${help.value}\" → 마지막 쪽에 한 줄 · 함께하기 기록 재료 (칸 진행 · 수준 판단에는 안 씀)")
        say(if (s.partner.honor) "좋아요! ${pn}도 함께예요!" else "좋아! ${pn}도 함께야!")
    } else {
        s.partnerHelp = null
        s.partnerHelpLine = null
        log("${pn}${ga(pn)} 답하지 않음 → 대신 고르지 않고, 책에도 넣지 않는다")
        say(if (s.partner.honor) "괜찮아요, ${pn}께서는 옆에서 응원해 주세요!" else "괜찮아, ${pn}${eun(pn)} 옆에서 응원해 줘!")
    }
    mark("partnerslot")
    pause(1300)

    val (est, why) = s.ruleEstimate()
    log("세션 누적 판단: ${est.label} — $why · 템플릿 ${t.code}은 3턴째 확정이라 그대로 (다음 세션 시작점 ${(s.nextLevel ?: s.level).label})")
    say("이야기가 다 모였어! 이제 동화책을 만들자!")
    log("필수 칸 6개 완료 → 진행 막대 끝의 별이 켜짐 → 동화책 시작")
    pause(2200)
    go(Scene.MAKING)
}

// ── 장면 11 · 책 만드는 중 (제목은 묻지 않고 지어 준다) ─────────

private suspend fun Director.sceneMaking() {
    inputs(false, false)
    buttons()
    val t = s.template ?: templateOf(chooseTemplate(s.level, s.causeKind).first).also { s.templateKey = it.key }
    say("동화책을 만들고 있어! 조금만 기다려 줘.")
    log(
        "템플릿 ${t.code} ${t.name}(${t.pages.size}쪽) + 모은 칸들(이름은 가림) → Anthropic → 쪽마다 자막(−어요체) · 제목 JSON → " +
            "폰에서 {주인공} → ${s.childName}, {친구1} → ${s.friendName} 복원 · 확정 그림은 다시 그리지 않음 (⭐26)"
    )
    if (s.isDiary) {
        val mascot = listOf("place", "problem", "cause", "solution").filter { s.slotBy[it] == "mascot" }
        log(
            "일기 모드 — 결과물은 동화 모드와 똑같은 ${t.pages.size}쪽 동화책이다 (§0 · §7-1 ①). " +
                if (mascot.isEmpty()) "네 자리를 아이 말로 다 채웠다"
                else "빈 자리 [${mascot.joinToString(" · ")}] 는 LLM이 이야기로 메웠다 → by: mascot (§5)"
        )
        log("아이 말은 씨앗이고 나머지는 원래 이야기다. 메운 문장은 책에만 나오고 부모 리포트 인용에는 안 들어간다 (§5 · §5-1)")
    }
    val filled = s.slots.filterValues { it.isNotEmpty() }.keys
    log("이번 책에 들어가는 이야기 조각: ${filled.joinToString(" · ").ifEmpty { "기본 문장" }} · 까닭 \"${s.causeLine}\" · 해결 \"${s.solutionLine}\"")
    var p = 0f
    while (p < 1f) {
        s.stage = Stage.Making("동화책을 만드는 중… (${t.pages.size}쪽)", p)
        pause(120)
        p += 0.05f
    }
    s.title = s.autoTitleFor()
    s.stage = Stage.Making("『${s.title}』", 1f)
    say("다 만들었어! 제목은 『${s.title}』${if (bat(s.title!!)) "이야" else "야"}.")
    log("제목은 아이에게 묻지 않고 템플릿 · 대화로 지어 준다 → 책장에서 바꿀 수 있다")
    event("book", "template" to "${t.code} ${t.name}", "attribute" to s.attribute, "pages" to t.pages.size, "title" to s.title)
    mark("making")
    pause(2200)
    go(Scene.BOOK)
}

// ── 장면 12 · 책 6~8쪽 · 전체 화면 · 도구 4종 · 대화로 만든 미션 2개 (⭐7) ──

private suspend fun Director.sceneBook() {
    inputs(false, false)
    val d = s.dino.name
    val m1 = s.mission1()
    val m2 = s.mission2()
    val last = s.pageCount
    s.bookPage = 0
    s.m1Result = null; s.m2Result = null
    val rubPage = (1..last).firstOrNull { s.pageKind(it) == PageKind.RUB } ?: -1
    val dragPage = (1..last).firstOrNull { s.pageKind(it) == PageKind.DRAG } ?: -1

    fun show() {
        s.stage = Stage.BookPage(s.bookPage, m1Done = s.m1Result != null, m2Done = s.m2Result != null)
    }

    fun announce() {
        val i = s.bookPage
        s.bookNote = when {
            i == 0 -> "▶ 를 눌러 펼쳐 봐!"
            i == rubPage -> if (s.m1Result == null) s.m1Line() else s.m1Done()
            i == dragPage -> if (s.m2Result == null) s.m2Line(s.m1Result == "helped") else s.m2Done()
            // 일기 모드에는 공룡 소리 칸이 없다 — 묻지 않는 칸이다 (§2-2)
            i == last && s.isDiary -> "인형을 눌러 봐! 오늘 이야기가 여기서 끝나."
            i == last -> "${d}${eul(d)} 눌러 봐! ${s.childName}${ga(s.childName)} 낸 소리가 나와."
            else -> ""
        }
        say(if (i == 0) "『${s.title}』" else s.bookCaption(i))
        when {
            i == 0 -> {}
            i == rubPage && s.m1Result == null -> log(
                if (s.isDiary) "${i}쪽 미션 1 (쉬움 · 문지르기) — 뼈대는 그대로, 소품만 하루에서 나온 것으로 (${m1.blobName} · 도구 ${m1.toolName} · §7-1 ②)"
                else "${i}쪽 미션 1 (쉬움 · 문지르기) — 장면 4의 \"${s.newcomerKind}\"에서 나온 ${m1.blobName} · 도구 ${m1.toolName}"
            )
            i == dragPage && s.m2Result == null -> log("${i}쪽 미션 2 (${if (s.m1Result == "helped") "쉬움 · 탭" else "보통 · 끌어다 놓기"}) — ${if (s.isDiary) "4턴째에 말한" else "장면 10에서 말한"} ${m2.itemName}${eul(m2.itemName)} ${s.friendCallName}에게")
            i == last && s.isDiary -> log("${i}쪽(마지막): 일기 모드도 미션 난이도 신호가 그대로 나온다 (§7-1 ②) · 공룡 소리 칸은 묻지 않았다 (§2-2)")
            i == last -> log("${i}쪽(마지막): ${if (s.partnerHelpLine != null) "${s.pn} 참여 한 줄 들어감" else "${s.pn}${ga(s.pn)} 답하지 않아 그 줄 없음"} · ${d}${eul(d)} 누르면 녹음한 소리")
            else -> log("${i}쪽 [${s.pageKind(i)}] — 템플릿 ${s.template?.code} 칸으로 만든 자막")
        }
    }

    fun refreshButtons() {
        val b = mutableListOf(
            DemoBtn("▶ 다음 쪽") { send(Reply.Tapped("next", "다음")) },
            DemoBtn("◀ 앞 쪽") { send(Reply.Tapped("prev", "앞")) },
        )
        if (s.bookPage == rubPage && s.m1Result == null) {
            b += DemoBtn("🖐 (시연) ${m1.blobName} 3개를 문질러 없앰") { send(Reply.Tapped("mission", "미션1")) }
            b += DemoBtn("😶 (시연) 가만히 있음 → 시연 2번 → 도와주기") { send(Reply.Tapped("helped", "도움")) }
        }
        if (s.bookPage == dragPage && s.m2Result == null) b += DemoBtn("🖐 (시연) ${m2.itemName}${eul(m2.itemName)} ${s.friendCallName}에게 놓음") { send(Reply.Tapped("mission", "미션2")) }
        buttons(*b.toTypedArray())
    }

    show(); announce(); refreshButtons()
    log("책 ${last}쪽 (템플릿 ${s.template?.code} ${s.template?.name}) · 전체 화면 · 미션은 ${rubPage}쪽 · ${dragPage}쪽 · 도구 4종 · 누르면 그 자리 위에 반응 글자")

    while (true) {
        val r = awaitReply() as? Reply.Tapped ?: continue
        val vv = r.value
        when {
            vv == "next" -> {
                if (s.bookPage < last) { s.bookPage++; show(); announce(); refreshButtons() }
                else { go(Scene.FRIENDS); return }
            }
            vv == "prev" -> { if (s.bookPage > 0) { s.bookPage--; show(); announce(); refreshButtons() } }
            vv == "speak" -> log("🔊 자막 낭독 (CLOVA Voice, 이름 없는 문장)")
            vv == "mission" && s.bookPage == rubPage && s.m1Result == null -> {
                s.m1Result = "solo"; s.reactions++
                s.achievements += "${m1.blobName} 치운 손"
                show(); announce(); refreshButtons()
                event("mission", "id" to 1, "motion" to "rub", "result" to "solo")
                log("미션 1 완료 → mission_result: solo → 다음 미션 보통 (안치영 §7 · ⭐7) · 걸린 시간 · 시도 횟수 저장 안 함")
                mark("book")
            }
            vv == "helped" && s.bookPage == rubPage && s.m1Result == null -> {
                s.m1Result = "helped"; s.achievements += "${m1.blobName} 치운 손"
                show(); refreshButtons()
                s.bookNote = "같이 하자! 슥슥~ 퐁! 다 됐어!"
                event("mission", "id" to 1, "motion" to "rub", "result" to "helped")
                log("두 번 시연해도 안 됨 → 마스코트가 도와 반드시 성공 (helped) → 미션 2는 쉬움(탭)")
            }
            vv == "gag" -> log("장난 반응 (미션과 무관 · 저장 안 함)")
            vv == "mission" && s.bookPage == dragPage && s.m2Result == null -> {
                s.m2Result = if (s.m1Result == "helped") "easy" else "solo"; s.reactions++
                s.achievements += "${m2.itemName} 건넨 손"
                show(); announce(); refreshButtons()
                event("mission", "id" to 2, "motion" to "drag", "result" to s.m2Result)
                log("미션 2 완료 — ${s.friendCallName}에게 ${m2.itemName} · 하트가 퐁 (연출은 공통)")
                mark("book")
            }
            vv == "dino" -> log("${d}${eul(d)} 누르면 아이가 녹음한 소리 재생 (폰 안에서만)")
            vv.startsWith("tool:") -> { log("도구 반응 ${vv.removePrefix("tool:")} — 그 자리 위에 글자 (반응 애니메이션은 대상 무관 공통)"); mark("tools") }
        }
    }
}

// ── 장면 13 · 친구 평가 (S10) ────────────────────────────────────

/**
 * 오늘 만난 친구를 하나씩 보며 [또 만날래 💛] [안녕 👋].
 * "또 만날래"로 고른 친구만 다음 이야기의 확인 카드 후보가 된다 (⭐26).
 * "안녕"을 골라도 지워지지 않는다 — 폰에 남고, 책장에서 그 책을 열면 그대로 있다.
 */
private suspend fun Director.sceneFriends() {
    inputs(false, false)
    // 일기 모드에는 공룡(동행 칸)이 없다. 아이가 아무도 그리지 않았으면 평가할 친구도 없다 (§2-2)
    val items = if (s.isDiary) {
        if (s.newcomer == null) {
            log("일기 모드 · 오늘 그린 친구가 없다 → 친구 평가를 건너뛴다 (동행 · 소리 칸은 묻지 않는다 · §2-2)")
            go(Scene.END)
            return
        }
        mutableListOf(RateItem("friend", s.friendCallName, s.friendArt))
    } else {
        mutableListOf(
            RateItem("friend", s.friendName, s.friendArt),
            RateItem("dino", s.dino.name, Art.DinoArt(s.dinoColor, s.dinoKey)),
        )
    }
    say("오늘 만난 친구들이야. 누구를 또 만나고 싶어?")
    log("친구 평가 — 고르지 않아도 넘어갈 수 있다. 지우는 선택지는 없다")
    fun refresh() {
        s.stage = Stage.FriendRate(items.toList())
        val b = mutableListOf<DemoBtn>()
        items.forEach {
            if (it.keep == null) {
                b += DemoBtn("💛 ${it.name} — 또 만날래") { send(Reply.Tapped("keep:${it.id}", it.name)) }
                b += DemoBtn("👋 ${it.name} — 안녕") { send(Reply.Tapped("bye:${it.id}", it.name)) }
            }
        }
        b += DemoBtn("➡️ 다 골랐어") { send(Reply.Tapped("done", "다음")) }
        buttons(*b.toTypedArray())
    }
    refresh()
    while (true) {
        val r = awaitReply() as? Reply.Tapped ?: continue
        if (r.value == "done") break
        val (act, id) = r.value.split(":", limit = 2).let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) continue
        val keep = act == "keep"
        items[i] = items[i].copy(keep = keep)
        s.reactions++
        event("friend_rating", "friend_id" to id, "keep" to keep)
        if (keep) {
            if (items[i].name !in s.keptFriends) s.keptFriends += items[i].name
            say("${items[i].name}${eul(items[i].name)} 또 만나자! 다음 이야기에 나올 수 있어.")
            log("또 만날래 → ${items[i].name}${eun(items[i].name)} 다음 이야기의 확인 카드 후보 (⭐26 · 폰 소품함)")
        } else {
            say("${items[i].name}, 안녕! 오늘 고마웠어.")
            log("안녕 → 지우지 않는다. 폰에 남고 이 책에는 그대로 나온다")
        }
        mark("friends")
        pause(1100)
        refresh()
        if (items.all { it.keep != null }) break
    }
    say("좋아! 이제 선물이 있어.")
    pause(1200)
    go(Scene.END)
}

// ── 장면 14 · 책이 끝난다 · 깜짝 선물 차례로 (⭐2 · ⭐9) ─────────────

private suspend fun Director.sceneEnd() {
    inputs(false, false)
    buttons()
    s.stage = Stage.Gifts(0)
    pause(600)
    s.stage = Stage.Gifts(1)
    say("와, ${s.childName}${ga(s.childName)} 새로운 방법을 찾았어! 친구와 함께 해결하기!")
    if ("해결 방법 도감 · 친구와 함께" !in s.achievements) s.achievements += "해결 방법 도감 · 친구와 함께"
    log("선물 1 — 해결 방법 도감 첫 칸 \"친구와 함께\" (업적 5 · ⭐9). 한 번에 하나씩 (조사3 §1-2)")
    pause(2600)
    // 업적 7은 "그림판 그림을 책에 처음 넣음"이다. 일기 모드에서 아무것도 안 그린 날에는 주지 않는다
    if (!s.isDiary || s.drawing.isNotEmpty()) {
        s.stage = Stage.Gifts(2)
        say("무지개 크레용이 생겼어! 다음에 그려 보자.")
        if ("무지개 크레용" !in s.achievements) s.achievements += "무지개 크레용"
        log("선물 2 — 무지개 크레용 (업적 7: 그림판 그림을 책에 처음 넣음)")
        pause(2600)
    } else {
        log("오늘은 그림을 안 그려서 무지개 크레용은 없다 — 안 한 일에 선물을 주지 않는다 (조사3 §1-3)")
    }
    say("책 다 만들었다! 고생했어~~")
    buttons(DemoBtn("📚 책장에 꽂기") { send(Reply.Tapped("shelf", "책장")) })
    awaitValue("shelf")
    mark("end")
    s.shelf.add(0, ShelfBook(s.title ?: s.autoTitleFor(), s.themeKey, s.bgName, pages = s.pageCount, fresh = true))
    event("session_end", "duration" to "15분", "counted" to s.quotes.size, "total" to (s.quotes.size + 1))
    log("책장에 꽂기 → 책장 화면으로 (다시 읽기는 아직 없음 — 꽂히는 것까지) · 확정 그림은 폰 소품함에, 서버에는 아무것도 안 남김 (⭐26)")
    go(Scene.SHELF)
}

// ── 장면 15 · 책장 ─────────────────────────────────────────────

private suspend fun Director.sceneShelf() {
    inputs(false, false)
    val fromEnd = s.shelf.any { it.fresh }
    s.stage = Stage.Shelf(fromEnd)
    if (fromEnd) {
        val t = s.shelf.first().title
        say("『$t』${if (bat(t)) "이" else "가"} 책장에 꽂혔어! 오늘은 여기까지!")
        log("새 책이 책장 맨 앞에 꽂힘 · 다음 책을 권하지 않음 (끝이 있는 설계 ⭐2) · 책 이름은 ✏️로 바꿀 수 있게 할 자리")
        mark("shelf")
        buttons(
            DemoBtn("🏠 처음으로") { send(Reply.Tapped("home", "처음으로")) },
            DemoBtn("👪 부모 모드") { send(Reply.Tapped("parent", "부모 모드")) },
        )
    } else {
        say("우리가 만든 책들이야!")
        buttons(DemoBtn("◀ 돌아가기") { send(Reply.Tapped("home", "돌아가기")) })
    }
    while (true) {
        when (awaitValue("home", "parent", "book")) {
            "home" -> { if (fromEnd) goHome() else go(Scene.ADULT); return }
            "parent" -> {
                if (pinGate("parent")) { go(Scene.PARENT); return }
                s.stage = Stage.Shelf(fromEnd)
            }
            else -> log("책을 눌렀음 — 다시 읽기는 아직 없다 (살짝 흔들리기만)")
        }
    }
}

// ── 장면 16 · 부모 모드 (16 활동 점수 없앰) ────────────────────────

private suspend fun Director.sceneParent() {
    inputs(false, false)
    var tab = "rec"
    buttons(
        DemoBtn("📋 오늘의 기록") { send(Reply.Tapped("tab:rec", "기록")) },
        DemoBtn("🏅 업적 보기") { send(Reply.Tapped("tab:ach", "업적")) },
        DemoBtn("⚙️ 설정") { send(Reply.Tapped("tab:set", "설정")) },
        DemoBtn("🏠 아이 모드로 (처음으로)") { send(Reply.Tapped("home", "처음으로")) },
    )
    while (true) {
        s.stage = Stage.Parent(tab)
        when (tab) {
            "rec" -> log("오늘의 기록 — 사실만. 등급 · 나이 비교 · 수준 이름 · 또래 · 지연 같은 말은 쓰지 않음 (16 · 안치영 §9)")
            "ach" -> log("업적 — 아이가 한 일로만")
            "set" -> log("설정 — 하루 한도 · 시작 비밀번호 · 그림체 4종 · 데이터")
        }
        mark("parent")
        val r = awaitReply() as? Reply.Tapped ?: continue
        val v = r.value
        when {
            v.startsWith("tab:") -> tab = v.removePrefix("tab:")
            v == "home" -> { goHome(); return }
            v == "set:limit" -> {
                s.limitOn = !s.limitOn
                log("하루 한도 ${if (s.limitOn) "켬 — 하루 ${s.dailyLimit}권" else "끔 — 별을 쓰지 않음"}")
            }
            v == "set:limit+" -> { s.dailyLimit = (s.dailyLimit + 1).coerceAtMost(5); log("하루 ${s.dailyLimit}권") }
            v == "set:limit-" -> { s.dailyLimit = (s.dailyLimit - 1).coerceAtLeast(1); log("하루 ${s.dailyLimit}권") }
            v == "set:pin" -> {
                s.pinToStart = !s.pinToStart
                log("이야기 시작 비밀번호 ${if (s.pinToStart) "켬 — 어른이 비밀번호를 넣어야 시작 (별 소모 · 과몰입 방지)" else "끔 — 아이가 바로 시작"}")
            }
            v.startsWith("set:style:") -> {
                s.artStyle = v.removePrefix("set:style:")
                log("그림체 → ${ART_STYLES.first { it.key == s.artStyle }.name} (다음 책부터 · 세계 그림체만 바뀌고 아이 그림 · 도감은 그대로 · 결정 27)")
            }
        }
    }
}
