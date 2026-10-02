package com.example.finalproject_demo.demo

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 그림일기 획 기록 — 조각 묶기 기준값(멈춤 · 거리 · 색 · 배경선 · 색칠 · 반복 무리)을 **실제 아이 그림**으로 정하려고 남긴다.
 *
 * - **디버그 빌드에서만** 켜진다. 출시 빌드 · 단위 테스트는 아무것도 쓰지 않는다
 * - **폰 안에만** — `files/diary_trace/<시작 시각>.jsonl`. 서버로 보내지 않는다(차별점 1). 백업은 매니페스트가 막는다
 * - 아이 말 원문은 남기지 않는다 — 조각 이름(아이가 붙인 이름)과 오또가 물은 질문 · 끝난 까닭만
 * - 꺼내기: `adb shell run-as kr.clap.otto ls files/diary_trace` → `adb exec-out run-as kr.clap.otto cat files/diary_trace/<파일>`
 *
 * 한 줄이 한 일이다(`t` 는 그림판을 연 뒤 ms): `stroke`(획 · 누른/뗀 시각 · 색 · 굵기 · 점) · `crayon` · `pause` · `ask` · `name` · `pieces`(조각 묶음 스냅숏)
 */
object DiaryTrace {
    private var dir: File? = null
    private var file: File? = null
    private var day: DiaryDay? = null
    private var start = 0L

    /** 그림판이 열릴 때 — 디버그 빌드면 이 그림일기의 기록을 연다(같은 판이면 이어 쓴다) */
    fun open(context: Context, d: DiaryDay) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable || android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) return
        if (day === d && file != null) return
        dir = File(context.filesDir, "diary_trace").apply { mkdirs() }
        day = d
        start = System.currentTimeMillis()
        file = File(dir, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".jsonl")
    }

    val on: Boolean get() = file != null

    fun now(): Long = System.currentTimeMillis() - start

    private fun write(type: String, fill: JSONObject.() -> Unit) {
        val f = file ?: return
        runCatching { f.appendText(JSONObject().put("type", type).put("t", now()).apply(fill).toString() + "\n") }
    }

    /** 획 하나 — [index] 는 그림판 획 목록(`s.drawing`)에서의 자리. 좌표는 판 폭 · 높이에 대한 비율 */
    fun stroke(index: Int, downAt: Long, s: Stroke, aspect: Float) = write("stroke") {
        put("i", index).put("down", downAt).put("color", s.color.toArgb()).put("w", s.w.toDouble()).put("aspect", aspect.toDouble())
        put("pts", JSONArray().apply { s.pts.forEach { p -> put(JSONArray().put(round(p.x)).put(round(p.y))) } })
    }

    fun crayon(color: androidx.compose.ui.graphics.Color) = write("crayon") { put("color", color.toArgb()) }

    fun pause(label: String, delivered: Boolean) = write("pause") { put("label", label).put("delivered", delivered) }

    /** 오또가 물은 질문과 끝난 까닭(answered · @moved_on · @quiet …) — 아이 답 원문은 남기지 않는다 */
    fun ask(question: String, outcome: String) = write("ask") { put("q", question).put("outcome", outcome) }

    fun name(pieceId: Int, name: String) = write("name") { put("piece", pieceId).put("name", name) }

    /** 조각 묶음 스냅숏 — 어느 획이 어느 조각인지 · 이름 · 고른 모습 */
    fun pieces(d: DiaryDay, drawing: List<Stroke>) = write("pieces") {
        put("pieces", JSONArray().apply {
            d.pieces.forEach { p ->
                put(JSONObject().put("id", p.id).put("name", p.name ?: JSONObject.NULL).put("look", p.look.name).put("role", p.role.name)
                    .put("strokes", JSONArray(p.strokes.map { s -> drawing.indexOf(s) })))
            }
        })
    }

    private fun round(v: Float) = Math.round(v * 1000) / 1000.0
}
