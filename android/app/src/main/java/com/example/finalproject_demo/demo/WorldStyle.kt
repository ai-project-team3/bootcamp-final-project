package com.example.finalproject_demo.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.finalproject_demo.demo.scene.SceneKitDef

/*
 * ── 책의 그림체 — 세계 그림만 바뀐다 (10-07 종훈 · #223 그림체 · 결정 27) ─────────────────────────────
 *
 * 부모가 설정에서 그림체를 바꾸면 **다음 책부터** 그 그림체로 그린다. 바뀌는 것은 세계뿐이다:
 * 장소 키트 조각 · 배경 · 소품 · 협업 요소 · 이야기 속 친구. 아이 그림 · 도감 인형 · 오또 · 방 · 아이콘은 펠트 그대로.
 *
 *  - 앱에 넣은 세계 그림: `<이름>_<그림체>` 그림이 있으면 그것, 없으면 펠트(`<이름>`) — 굽기 전에도 앱은 돈다.
 *    크레용 그림은 치영 PC 에서 같은 문장으로 다시 구워 `kit_park_slide_crayon` 처럼 넣는다.
 *  - 서버가 그리는 그림(/image 배경 · 친구 · 다시 그리기)은 `style` 을 같이 보낸다(`net/Server.kt`).
 *  - 장소 키트는 그 그림체 조각이 다 있을 때만 쓴다. 없으면 키트를 끄고 /image 로 그 그림체 배경을 받는다 —
 *    펠트 조각 위에 크레용 인물이 서면 한 화면에 두 그림체가 섞인다(docs/무대_배치_규칙.md).
 */
object WorldStyle {
    /** 지금 책의 그림체 — 책을 시작할 때 `DemoState.resetStory` 가 부모 설정(`artStyle`)에서 가져온다 */
    var current by mutableStateOf("felt")

    /** 앱에 그 이름의 그림이 있나 — MainActivity 가 붙인다. 붙이기 전(테스트)에는 없다고 본다 */
    @Volatile var has: (String) -> Boolean = { false }

    /**
     * 그림체를 따르는 세계 그림 — 굽는 도구(`android/tools/rebake_style.py` WORLD_PREFIX · WORLD_NAMES · NOT_WORLD)와 같은 목록.
     * 도감(body_ · hair_ …) · 오또 · 방(room_) · 아이콘(ic_ · pi_)은 없다
     */
    val WORLD = listOf("kit_", "bg_", "prop_", "obj_", "coop_el_", "bud_", "nc_", "dino_", "dp_")
    private val WORLD_NAMES = setOf("rocket", "train", "turtle")
    private val NOT_WORLD = setOf("bg_shelf")

    fun isWorld(name: String) = name !in NOT_WORLD && (name in WORLD_NAMES || WORLD.any { name.startsWith(it) })

    /** 이 책에서 [name] 대신 그릴 그림 이름 — 그림체판이 없으면 그대로 */
    fun resolve(name: String, style: String = current, exists: (String) -> Boolean = has): String {
        if (style == "felt" || !isWorld(name) || name.endsWith("_$style")) return name
        val styled = "${name}_$style"
        return if (exists(styled)) styled else name
    }

    /** 이 키트를 이 그림체로 그릴 수 있나 — 조각이 하나라도 펠트뿐이면 아니다 */
    fun kitReady(kit: SceneKitDef, style: String = current, exists: (String) -> Boolean = has): Boolean =
        style == "felt" || kit.pieces.map { it.res }.distinct().all { exists("${it}_$style") }
}
