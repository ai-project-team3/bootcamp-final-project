package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server

/**
 * Who sits with the child, as the server reads it (#303) — "adult" · "peer" · "none".
 * "none" keeps the judge off the adult slot: nobody is there to ask (#296).
 * Co-op always has the parent; the diary asks no one, so it sends nothing (the server keeps its old behaviour).
 */
internal fun DemoState.partnerWire(mode: String): String? = when (mode) {
    "story" -> when {
        PARTNERS.none { it.key == partnerKey } -> "none"      // solo · unknown (#300)
        partner.adult -> "adult"
        else -> "peer"
    }
    "coop" -> "adult"
    else -> null
}

/**
 * The answer to 「오늘은 누구랑 같이 이야기를 만들어?」 → (kind, what the child called them), or null to ask again.
 *
 * With the server on, one Jev choice question reads the whole answer (`/partner`, #303): measured 10-07 on 51
 * answers, Jev 153/153 against the word list's 48/51 — the list still misses relatives and names it has not
 * listed (「아부지」 · 「하윤이랑 같이 왔어」) (`eval/results.md`). The word list ([partnerIn]) stays for when the server is away,
 * failed or unsure; what the child called the person (「할미」 · 「민수」) still comes from the words.
 */
suspend fun readPartner(
    text: String,
    live: Boolean,
    ask: suspend (String) -> String? = { Server.partner(it) },
): Pair<String, String>? {
    val words = partnerIn(text)
    if (!live) return words
    val kind = ask(text) ?: return words
    return when {
        kind == "unknown" -> null
        kind == words?.first -> words
        kind == "solo" -> "solo" to "혼자"
        PARTNERS.any { it.key == kind } -> kind to (if (kind == "friend") "친구" else partner(kind).name)
        else -> words
    }
}
