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
    A1(setOf(2), MissionInput.TOUCH, "eye-hand: aim and hold"),
    A2(setOf(2), MissionInput.TOUCH, "shape matching"),
    A3(setOf(2), MissionInput.TOUCH, "spatial: put the picture back together", built = true),
    A4(setOf(1, 2), MissionInput.TOUCH, "fine motor: turn in a circle"),
    A5(setOf(2), MissionInput.TOUCH, "balance and patience: stack"),
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
    D4(setOf(2), MissionInput.TILT, "gross motor: tilt to roll"),
    D5(setOf(1), MissionInput.TILT, "gross motor: turn over"),
    E1(setOf(2), MissionInput.TOUCH, "relationship: hand it over", built = true),
    E2(setOf(2), MissionInput.TOUCH, "spatial: fix the broken piece"),
}

/** Sensor and mic missions always keep a tap path on the same screen (design §3-6) */
enum class MissionInput { TOUCH, MIC_LEVEL, SHAKE, TILT }

/** The two missions of one book. Slot 2 is picked first — the resolution matters more (design §6-2) */
data class BookMissions(val slot1: MissionId, val slot2: MissionId)
