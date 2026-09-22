package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Answer
import com.example.finalproject_demo.demo.BANK
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.QVariant
import com.example.finalproject_demo.demo.partnerQuestion
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 평가셋 재료 뽑기 (역할 1) — 앱이 실제로 쓰는 질문 · 더미답 · 신호 표시를 그대로 파일로 내린다.
 *
 * 왜 코드에서 뽑나
 *   평가셋을 손으로 옮겨 적으면 앱을 고칠 때마다 어긋난다. 여기서 뽑은 `build/bank_dump.jsonl`을
 *   `역할1_치영/eval/tools/make_fixtures.py`가 층화 추출해 평가셋 100문항을 만든다.
 *
 * 돌리는 법
 *   ./gradlew :app:testDebugUnitTest --tests "*EvalDumpTest*"
 */
class EvalDumpTest {

    /** JSON 한 줄 만들기 — 값이 글이면 따옴표를 붙이고, 참/거짓 · 숫자 · 배열은 그대로 둔다. */
    private fun line(vararg fields: Pair<String, Any?>): String =
        fields.joinToString(",", "{", "}") { (k, v) ->
            val value = when (v) {
                is String -> "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                is List<*> -> v.joinToString(",", "[", "]") { "\"$it\"" }
                else -> v.toString()
            }
            "\"$k\":$value"
        }

    private fun row(theme: String, v: QVariant, question: String, a: Answer) = line(
        "source" to "bank",
        "theme" to theme,
        "qid" to v.id,
        "slot" to v.slot,
        "kind" to v.kind.toString(),
        "counts" to v.counts,
        "emotion" to v.emotion,
        "probe" to v.probe,
        "question" to question,
        "utterance" to a.text,
        "value" to a.value,
        "lv" to a.lv,
        "s1" to a.reason,
        "s2" to a.el.isNotEmpty(),
        "elements" to a.el.toList(),
        "a1" to a.con,
        "emo" to a.emo,
        "cause_kind" to a.kind,
        "words" to a.words,
    )

    private fun partnerRow(theme: String, question: String, a: Answer) = line(
        "source" to "partner",
        "theme" to theme,
        "qid" to "partner_help",
        "slot" to "partner",
        "kind" to "EASY",
        "counts" to false,
        "emotion" to false,
        "probe" to "함께 하는 사람 참여 (판정 대상 아님)",
        "question" to question,
        "utterance" to a.text,
        "value" to a.value,
        "lv" to 0,
        "s1" to false,
        "s2" to false,
        "elements" to emptyList<String>(),
        "a1" to false,
        "emo" to "",
        "cause_kind" to "",
        "words" to a.words,
    )

    /** 장소마다 문장이 달라지므로 세 세계를 모두 훑는다. 칸은 미리 채워 둔다(이야기 중간 상태). */
    private fun stateFor(theme: String) = DemoState().apply {
        themeKey = theme
        newcomerKind = th.newcomers.first().value
        friendName = "뿌뿌"
        partnerKey = "mom"
        causeLine = "친구가 없어서 심심했어"
        causeKind = "lonely"
        templateKey = "A"
        slots["stop"] = th.stops[0]
        slots["need"] = "지도"
        slots["role"] = "구조대원"
    }

    @Test
    fun dumpQuestionsAndDummyAnswers() {
        val out = StringBuilder()
        var n = 0

        for (theme in listOf("space", "sea", "dino")) {
            val s = stateFor(theme)

            for (v in BANK) {
                val question = v.text(s)
                for (a in v.answers(s)) {
                    out.append(row(theme, v, question, a)).append('\n')
                    n++
                }
            }

            // 함께 하는 사람이 답하는 자리 — 판정 대상이 아니라는 것을 표시해 둔다
            val (partnerQ, partnerAnswers) = partnerQuestion(s)
            for (a in partnerAnswers) {
                out.append(partnerRow(theme, partnerQ, a)).append('\n')
                n++
            }
        }

        File("build").mkdirs()
        File("build/bank_dump.jsonl").writeText(out.toString())
        println("bank_dump.jsonl: ${n}줄 · 질문 변형 ${BANK.size}개")
        assertTrue("덤프가 비었다", n > 300)
    }
}
