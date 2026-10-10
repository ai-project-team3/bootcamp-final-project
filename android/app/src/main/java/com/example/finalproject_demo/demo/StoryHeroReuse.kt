package com.example.finalproject_demo.demo

/** The shelf is the source of truth, so deleting its last book also removes a reusable hero. */
internal fun recentStoryHeroes(books: List<SavedStoryBook>): List<Hero> = books
    .mapNotNull { it.visuals?.hero }
    .distinctBy { if (it.image != null) listOf(it.name, it.image) else listOf(it.name, it.attr, it.description) }
    .take(3)

/** No server lookup: these are explicit choices among the cards currently on screen. */
internal fun previousHeroChoice(text: String, heroes: List<Hero>): String? {
    val words = text.replace(Regex("[\\s.!?~,，。？！]"), "")
    val prefix = Regex("^(응|웅|네|좋아요?|그래|나는|난)")
    val answers = listOf(words, words.replaceFirst(prefix, "")).filter { it.isNotEmpty() }.distinct()
    val accept = "(?:할래요?|하자|하고싶어요?|할거야)"
    // Check complete names before interpreting an affirmative prefix (for example, "응원").
    for (index in heroes.indices.sortedByDescending { heroes[it].name.length }) {
        val name = Regex.escape(heroes[index].name.replace(Regex("\\s"), ""))
        if (answers.any { Regex("$name(?:이?요|(?:이랑|랑|하고)(?:(?:또|다시)?$accept)?)?").matches(it) }) {
            return "reuse:$index"
        }
        if (answers.any { Regex("$name(?:이랑|랑|하고)(?:안할래요?|싫어요?)").matches(it) }) return "new"
    }
    // Match the whole choice: a prefix alone must not turn a negated or unrelated sentence into consent.
    if (answers.any { Regex("(?:아니(?:요|야)?|싫어요?|(?:새로|새친구|새주인공|다른(?:친구|주인공)?)(?:요|만들래요?|만들자|$accept)?)").matches(it) }) {
        return "new"
    }
    if (heroes.isNotEmpty() && answers.any {
            Regex("(?:응|웅|네|좋아요?|그래|(?:또|다시)(?:$accept)?)").matches(it)
        }) return "reuse:0"
    return null
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
                    buttons(*cards.map { card -> DemoBtn(card.label) { send(Reply.Tapped(card.value, card.label)) } }.toTypedArray())
                    shown = true
                    say(question.text)
                } else {
                    say("${heroes.first().name}${rang(heroes.first().name)} 할까, 새로 만들까?")
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
