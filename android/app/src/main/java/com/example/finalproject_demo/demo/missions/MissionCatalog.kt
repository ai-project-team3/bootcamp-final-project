package com.example.finalproject_demo.demo.missions

/**
 * Every mission the book can hold — `docs/맞춤미션_설계.md` §4 (IDs from `docs/미션_구상.md` §3).
 *
 * A book has two mission slots (10-01 decision): slot 1 right after the trouble (one repeated motion,
 * 5–10 s), slot 2 the resolution (two steps or more, 10–20 s). [built] says the screen exists; the picker
 * only hands out built missions, and the server only accepts IDs it knows (`backend/app/schemas/story.py`
 * `MissionId` — A7 · A8 · A9 · C3 · D4 · D5 are not there yet).
 */
enum class MissionId(
    val slots: Set<Int>,
    val input: MissionInput,
    /** One line: what this motion makes the child use (principle 5 — no line, no mission) */
    val goal: String,
    val built: Boolean = false,
) {
    A1(setOf(2), MissionInput.TOUCH, "eye-hand: aim and hold", built = true),
    A2(setOf(2), MissionInput.TOUCH, "shape matching"),
    A3(setOf(2), MissionInput.TOUCH, "spatial: put the picture back together", built = true),
    A4(setOf(1, 2), MissionInput.TOUCH, "fine motor: turn in a circle", built = true),
    A5(setOf(2), MissionInput.TOUCH, "balance and patience: stack", built = true),
    A6(setOf(1), MissionInput.TOUCH, "fine motor: rub", built = true),
    A7(setOf(1), MissionInput.TOUCH, "waiting: press and hold"),
    A8(setOf(2), MissionInput.TOUCH, "hand control: follow a line"),
    A9(setOf(2), MissionInput.TOUCH, "two-hand: pinch open"),
    B1(setOf(2), MissionInput.TOUCH, "sorting"),
    B2(setOf(2), MissionInput.TOUCH, "recall: put the story in order"),
    B3(setOf(1), MissionInput.TOUCH, "search: light up the dark"),
    C1(setOf(1), MissionInput.MIC_LEVEL, "breath: blow", built = true),
    C2(setOf(2), MissionInput.MIC_LEVEL, "voice: call out"),
    C3(setOf(1), MissionInput.MIC_LEVEL, "voice: make the sound", built = true),
    D1(setOf(2), MissionInput.TOUCH, "persistence: tap fast to push away"),
    D2(setOf(1), MissionInput.SHAKE, "gross motor: shake"),
    D3(setOf(1), MissionInput.TOUCH, "rhythm: tap on the beat"),
    D4(setOf(2), MissionInput.TILT, "gross motor: tilt to roll", built = true),
    D5(setOf(1), MissionInput.TILT, "gross motor: turn over"),
    E1(setOf(2), MissionInput.TOUCH, "relationship: hand it over", built = true),
    E2(setOf(2), MissionInput.TOUCH, "spatial: fix the broken piece", built = true),
}

/** Sensor and mic missions always keep a tap path on the same screen (design §3-6) */
enum class MissionInput { TOUCH, MIC_LEVEL, SHAKE, TILT }

/**
 * The two missions of one book. Slot 2 is picked first — the resolution matters more (design §6-2).
 * [slot1FromChild] · [slot2FromChild]: picked from the child's own words; false when the slot fell to the frame
 * default or was rotated (#259) — then its prop is not the child's and the book does not say the child named it
 */
data class BookMissions(
    val slot1: MissionId,
    val slot2: MissionId,
    val slot1FromChild: Boolean = true,
    val slot2FromChild: Boolean = true,
) {
    /** 「A6:E1」 — what [MissionHistory] compares */
    val combo: String get() = "${slot1.name}:${slot2.name}"

    /** Saved with the book — 「A6-:E1-」 (+ = from the child's words) */
    fun encode(): String = "${slot1.name}${if (slot1FromChild) "+" else "-"}:${slot2.name}${if (slot2FromChild) "+" else "-"}"

    companion object {
        fun decode(s: String?): BookMissions? = runCatching {
            val (a, b) = s!!.split(":")
            BookMissions(MissionId.valueOf(a.dropLast(1)), MissionId.valueOf(b.dropLast(1)), a.endsWith("+"), b.endsWith("+"))
                .takeIf { it.slot1.built && it.slot2.built }
        }.getOrNull()
    }
}
