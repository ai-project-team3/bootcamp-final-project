package com.example.finalproject_demo.demo

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.finalproject_demo.demo.scene.SceneKitDef

/*
 * ── A book's art style — only the world changes (10-07 종훈 · #223 art styles · decision 27) ──────────────
 *
 * When the parent picks another art style in settings, the NEXT book is drawn in it. Only the world changes:
 * scene-kit pieces · backgrounds · props · co-op elements · friends in the story. The child's drawings, the
 * 도감 dolls, Otto, the room and the icons stay felt.
 *
 *  - Bundled world pictures: `<name>_<style>` when bundled, else the felt `<name>` — the app runs before baking.
 *    Crayon pictures are re-baked on 치영's PC from the same prompts and shipped as e.g. `kit_park_slide_crayon`.
 *  - Pictures the server draws (/image background · friend · redraw) send `style` along (`net/Server.kt`).
 *  - A scene kit is used only when all its pieces exist in that style. Otherwise the kit is off and /image
 *    draws the background in that style — a crayon actor on felt pieces would put two styles on one stage
 *    (docs/무대_배치_규칙.md).
 *  - A saved book keeps the style it was made in (`SavedStoryBook.artStyle` · `SavedDiaryBook.artStyle`) and is
 *    read in it: the story reader provides [LocalWorldStyle], the diary reread sets [reading] (#253 review).
 */
object WorldStyle {
    /** The style of the book being made — `DemoState.resetStory` takes it from the parent's setting (`artStyle`) */
    var current by mutableStateOf("felt")

    /** A saved book being re-read outside composition scope (the diary reread) — null when none */
    var reading by mutableStateOf<String?>(null)

    /** The style to draw in now, outside a [LocalWorldStyle] provider */
    val active: String get() = reading ?: current

    /** Is a picture of that name bundled — attached by MainActivity. Before that (tests) nothing is */
    @Volatile var has: (String) -> Boolean = { false }

    /**
     * World pictures that follow the art style — the same list as the bake tool
     * (`android/tools/rebake_style.py` WORLD_PREFIX · WORLD_NAMES · NOT_WORLD).
     * Not the 도감 (body_ · hair_ …), Otto, the room (room_) or icons (ic_ · pi_)
     */
    val WORLD = listOf("kit_", "bg_", "prop_", "obj_", "coop_el_", "bud_", "nc_", "dino_", "dp_")
    private val WORLD_NAMES = setOf("rocket", "train", "turtle")
    private val NOT_WORLD = setOf("bg_shelf")

    fun isWorld(name: String) = name !in NOT_WORLD && (name in WORLD_NAMES || WORLD.any { name.startsWith(it) })

    /** The picture to draw for [name] in [style] — the felt one when that style has none */
    fun resolve(name: String, style: String = active, exists: (String) -> Boolean = has): String {
        if (style == "felt" || !isWorld(name) || name.endsWith("_$style")) return name
        val styled = "${name}_$style"
        return if (exists(styled)) styled else name
    }

    /**
     * Like [resolve], but with no felt fallback: the picture in [style], or null when that style has none.
     * For a picture that may simply be left out — the kit's baked ground (#265): under a crayon book a felt ground
     * picture would put two styles on one stage, so without a crayon one the ground is colour + fibre only (치영 #265 review)
     */
    fun resolveOrNone(name: String, style: String = active, exists: (String) -> Boolean = has): String? {
        if (style == "felt") return name.takeIf(exists)
        return "${name}_$style".takeIf(exists)
    }

    /** Can this kit be drawn in this style — not if any piece exists only in felt */
    fun kitReady(kit: SceneKitDef, style: String = active, exists: (String) -> Boolean = has): Boolean =
        style == "felt" || kit.pieces.map { it.res }.distinct().all { exists("${it}_$style") }
}

/** The art style of the book this part of the screen draws — a saved book on the shelf provides its own. null = [WorldStyle.active] */
val LocalWorldStyle = compositionLocalOf<String?> { null }
