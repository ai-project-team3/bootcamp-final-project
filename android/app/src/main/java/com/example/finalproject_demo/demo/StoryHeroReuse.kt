package com.example.finalproject_demo.demo

/** The shelf is the source of truth, so deleting its last book also removes a reusable hero. */
internal fun recentStoryHeroes(books: List<SavedStoryBook>): List<Hero> = books
    .mapNotNull { it.visuals?.hero }
    .distinctBy { if (it.image != null) listOf(it.name, it.image) else listOf(it.name, it.attr, it.description) }
    .take(3)

/** No server lookup: these are explicit choices among the cards currently on screen. */
internal fun previousHeroChoice(text: String, heroes: List<Hero>): String? {
    val words = text.trim().replace(Regex("[.!?~]"), "")
    if (Regex("^(새로|새 친구|새 주인공)").containsMatchIn(words)) return "new"
    heroes.indexOfFirst { words == it.name || words.startsWith("${it.name}랑") || words.startsWith("${it.name}이랑") }
        .takeIf { it >= 0 }?.let { return "reuse:$it" }
    return if (words in setOf("또 할래", "또할래", "응", "좋아", "다시 할래")) "reuse:0" else null
}

internal class HeroSetupLog {
    private var started = 0L
    private var replies = 0
    private var eligible = false
    private var active = false

    fun begin(hasPrevious: Boolean) {
        started = System.nanoTime()
        replies = 0
        eligible = hasPrevious
        active = true
    }

    fun received(reply: Reply, state: DemoState) {
        if (active && state.mode == StoryMode.STORY && state.scene in setOf(Scene.MAKEHERO, Scene.BESTIARY) &&
            (reply is Reply.Spoke || reply is Reply.Tapped && !reply.byMascot)) replies++
    }

    fun finish(choice: String): String? {
        if (!active) return null
        active = false
        val elapsed = (System.nanoTime() - started) / 1_000_000
        return "hero_setup choice=$choice eligible=$eligible turns=$replies elapsed_ms=$elapsed"
    }
}

/** Returns true only when a saved hero was applied and creation was skipped. */
internal suspend fun Director.choosePreviousStoryHero(): Boolean {
    val heroes = previousStoryHeroes()
    heroSetupLog.begin(heroes.isNotEmpty())
    if (heroes.isEmpty()) {
        log("hero_choice choice=new eligible=false input=automatic")
        return false
    }
    val cards = heroes.mapIndexed { i, hero ->
        Card(hero.name, hero.image?.let { Art.Img(it, Art.HeroArt(hero.attr), hero.rig) } ?: Art.HeroArt(hero.attr), "reuse:$i")
    } + Card("새로 만들기", Art.Img("ic_mic", Art.Emoji("🎤")), "new")
    val question = Question(
        text = "지난번 ${heroes.first().name}${rang(heroes.first().name)} 또 모험할래? 새 친구를 만들래?",
        kind = Kind.CHOICE, choices = cards,
        spoken = listOf(Answer("또 할래"), Answer("새로")), id = "story_hero_reuse",
    )
    var shown = false
    try {
        setListening(question)
        while (true) {
            val reply = awaitReplyShowing {
                if (!shown) {
                    s.stage = Stage.CardsRow(cards)
                    inputs(mic = true, next = false)
                    say(question.text)
                    buttons(*cards.map { card -> DemoBtn(card.label) { send(Reply.Tapped(card.value, card.label)) } }.toTypedArray())
                    shown = true
                }
            }
            val choice = when (reply) {
                is Reply.Tapped -> reply.value
                is Reply.Spoke -> previousHeroChoice(reply.text, heroes)
                else -> null
            }
            val source = if (reply is Reply.Spoke) "voice" else "card"
            if (choice == "new") {
                log("hero_choice choice=new eligible=true input=$source")
                return false
            }
            val index = choice?.removePrefix("reuse:")?.toIntOrNull()
            val hero = index?.let(heroes::getOrNull) ?: continue
            s.heroAttr = hero.attr
            s.storyHeroImage = hero.image
            s.storyHeroRig = hero.rig
            s.storyHeroCall = hero.called ?: hero.name
            s.storyHeroDescription = hero.description
            log("hero_choice choice=reuse eligible=true input=$source")
            heroSetupLog.finish("reuse")?.let(::log)
            go(Scene.PLACE)
            return true
        }
    } finally {
        setListening(null)
        inputs(mic = false, next = false)
        buttons()
    }
}
