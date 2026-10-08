package com.example.finalproject_demo

import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.missions.StoryFacts
import com.example.finalproject_demo.demo.missions.blowPropIn
import com.example.finalproject_demo.demo.missions.fixPropIn
import com.example.finalproject_demo.demo.missions.pickMissions
import com.example.finalproject_demo.demo.missions.soundPropIn
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * #321 조장 결정(10-08) — 「현실적인 입력 분포로 재측정」. 레포의 아이 말 문항(판정 100 · 같이 만들기 책 15 · 일기 책 8)으로
 * 미션이 무엇으로 · 어떤 낱말 때문에 골라지는지 센다. 낱말로 골라진 문장은 모두 `build/reports/mission_distribution.md`
 * 에 적는다 — 오탐은 사람이 읽고 본다. 검사는 알려진 오탐이 없는 것과, 차례로 만든 책이 앞 두 권과 겹치지 않는 것만.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionDistributionTest {
    private val eval = listOf("../../eval", "../eval", "eval").map(::File).first { it.isDirectory }

    private fun lines(name: String) = File(eval, name).readLines().filter(String::isNotBlank).map(::JSONObject)

    /** 판정 문항 — 아이 말을 물은 칸에 넣은 사실 하나씩 */
    private fun judgeFacts(): List<Pair<String, StoryFacts>> = lines("fixtures_judge.jsonl").mapNotNull { j ->
        val said = j.optString("utterance").takeIf(String::isNotBlank) ?: return@mapNotNull null
        val f = when (j.optString("asked")) {
            "problem" -> StoryFacts(StoryMode.STORY, "C", said, null, null, false)
            "cause" -> StoryFacts(StoryMode.STORY, "C", null, said, null, false)
            "solution" -> StoryFacts(StoryMode.STORY, "C", null, null, said, false)
            else -> return@mapNotNull null
        }
        "${j.optString("id")} [${j.optString("asked")}] $said" to f
    }

    /** 같이 만들기 · 일기 책 문항 — 칸 전체 */
    private fun bookFacts(): List<Pair<String, StoryFacts>> =
        (lines("fixtures_book_coop.jsonl") + lines("fixtures_book_diary.jsonl")).map { j ->
            val req = j.getJSONObject("req"); val slots = req.getJSONObject("slots")
            fun v(k: String) = slots.optString(k).takeIf { it.isNotBlank() && it != "null" }
            val coop = req.optString("mode") == "coop"
            val real = !coop || req.optString("reason") != "dream"
            "${j.optString("id")} ${listOfNotNull(v("problem"), v("cause"), v("solution")).joinToString(" / ")}" to
                StoryFacts(StoryMode.COOP, null, v("problem"), v("cause"), v("solution"), real, v("detail"), v("try"))
        }

    @Test
    fun realisticInputs() {
        val all = judgeFacts() + bookFacts()
        val report = StringBuilder("| 문항 | 자리 1 | 자리 2 | 낱말이 고른 것 |\n|---|---|---|---|\n")
        val counts = sortedMapOf<String, Int>()
        val recent = mutableListOf<String>()
        var repeats = 0
        all.forEach { (label, f) ->
            val m = pickMissions(f, recent)
            if (m.combo in recent.take(2)) repeats++
            recent.add(0, m.combo)
            counts.merge(m.combo, 1, Int::plus)
            val why = listOfNotNull(
                blowPropIn(f.slot1Words, f.realDay)?.let { "C1 ${it.name}" },
                soundPropIn(f.slot1Words)?.let { "C3 ${it.name}" },
                fixPropIn(f.slot2Words, f.slot1Words)?.let { "${it.mission} ${it.name}" },
            )
            if (why.isNotEmpty()) report.append("| $label | ${m.slot1} | ${m.slot2} | ${why.joinToString(" · ")} |\n")
        }
        report.append("\n조합 (차례로 만든 ${all.size}권, 앞 두 권과 겹친 수 $repeats): $counts\n")
        File("build/reports").mkdirs()
        File("build/reports/mission_distribution.md").writeText(report.toString())
        println(report)
        assertTrue("차례로 만든 책이 앞 두 권과 같은 조합이었다($repeats) — 아이 말이 고른 것만 겹칠 수 있다", repeats <= all.size / 10)
        assertTrue("조합이 몇 개뿐이다: $counts", counts.size >= 6)
    }
}
