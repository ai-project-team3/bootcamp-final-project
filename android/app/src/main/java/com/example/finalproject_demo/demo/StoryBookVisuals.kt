package com.example.finalproject_demo.demo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import com.example.finalproject_demo.ui.HeroAttr
import org.json.JSONArray
import org.json.JSONObject

data class SavedStoryVisuals(
    val templateKey: String,
    val persona: Persona,
    val hero: Hero,
    val drawing: List<Stroke>,
    val drawnPreset: Int,
    val drawingAspect: Float,
    val dinoKey: String,
    val dinoColor: Color,
    val solutionKey: String,
    val solutionItem: String,
    val friendName: String,
    val solutionLine: String,
    val placeLabel: String?,
    val newcomerKind: String = "외계인",
    val soundLine: String = "뿌우우우웅!",
    val causeLine: String = "친구가 없어서 심심했어",
)

fun DemoState.captureStoryVisuals() = SavedStoryVisuals(
    // 같이 만들기는 templateKey 가 없고 일기 틀(N)을 쓴다 (10-02)
    templateKey ?: template?.key ?: "N", persona, Hero(childName, heroAttr ?: HeroAttr(), storyHeroImage, storyHeroRig),
    drawing.map { it.copy(pts = it.pts.toList()) }, drawnPreset, drawingAspect,
    dinoKey, dinoColor, solutionKey, solutionItem, friendName, solutionLine, placeLabel,
    newcomerKind, soundLine, causeLine,
)

/** Build a separate reading state; reopening a book must not overwrite the current conversation. */
fun DemoState.restoreStoryBook(book: SavedStoryBook): Boolean {
    val visual = book.visuals ?: return false
    // 같이 만들기 책은 같이 만들기로 연다 — 동화 틀 목록에 없는 일기 틀(N)이라 STORY 로 열면 쪽을 못 찾는다 (10-02)
    mode = book.mode
    templateKey = visual.templateKey.takeIf { book.mode == StoryMode.STORY }
    persona = visual.persona
    title = book.title
    themeKey = book.themeKey
    storyBackground = book.bgName
    placeLabel = visual.placeLabel
    heroAttr = visual.hero.attr
    storyHeroImage = visual.hero.image
    storyHeroRig = visual.hero.rig
    drawing.clear()
    drawing.addAll(visual.drawing.map { it.copy(pts = it.pts.toList()) })
    drawnPreset = visual.drawnPreset
    drawingAspect = visual.drawingAspect
    dinoKey = visual.dinoKey
    dinoColor = visual.dinoColor
    solutionKey = visual.solutionKey
    solutionItem = visual.solutionItem
    friendName = visual.friendName
    solutionLine = visual.solutionLine
    newcomerKind = visual.newcomerKind
    soundLine = visual.soundLine
    causeLine = visual.causeLine
    // The stored captions already include mission results. Do not append them twice.
    m1Result = null
    m2Result = null
    storyCaptions = book.pages.map { it.caption }
    return true
}

internal fun SavedStoryVisuals.toJson(): JSONObject {
    val strokes = JSONArray()
    drawing.forEach { stroke ->
        val points = JSONArray()
        stroke.pts.forEach { points.put(JSONArray().put(it.x).put(it.y)) }
        strokes.put(JSONObject().put("color", stroke.color.toArgb()).put("width", stroke.w).put("points", points))
    }
    val attr = hero.attr
    return JSONObject().put("version", 1).put("template", templateKey).put("persona", persona.name)
        .put("hero", JSONObject().put("name", hero.name).put("image", hero.image ?: JSONObject.NULL)
            .put("rig", hero.rig ?: JSONObject.NULL).put("hair", attr.hair).put("shirt", attr.shirt.toArgb())
            .put("eyes", attr.eyes).put("glasses", attr.glasses).put("likes", attr.likes).put("bottom", attr.bottom))
        .put("drawing", strokes).put("drawnPreset", drawnPreset).put("drawingAspect", drawingAspect)
        .put("dinoKey", dinoKey).put("dinoColor", dinoColor.toArgb())
        .put("solutionKey", solutionKey).put("solutionItem", solutionItem)
        .put("friendName", friendName).put("solutionLine", solutionLine)
        .put("placeLabel", placeLabel ?: JSONObject.NULL)
        .put("newcomerKind", newcomerKind).put("soundLine", soundLine).put("causeLine", causeLine)
}

internal fun storyVisualsFromJson(obj: JSONObject): SavedStoryVisuals {
    check(obj.getInt("version") == 1)
    val hero = obj.getJSONObject("hero")
    val strokes = obj.getJSONArray("drawing")
    return SavedStoryVisuals(
        obj.getString("template"), Persona.valueOf(obj.getString("persona")),
        Hero(hero.getString("name"), HeroAttr(
            hero.getString("hair"), Color(hero.getInt("shirt")), hero.getString("eyes"),
            hero.getString("glasses"), hero.getString("likes"), hero.getString("bottom")),
            hero.nullableString("image"), hero.nullableString("rig")),
        (0 until strokes.length()).map { index ->
            val stroke = strokes.getJSONObject(index)
            val points = stroke.getJSONArray("points")
            Stroke(Color(stroke.getInt("color")), (0 until points.length()).map { point ->
                val xy = points.getJSONArray(point)
                Offset(xy.getDouble(0).toFloat(), xy.getDouble(1).toFloat())
            }, stroke.getDouble("width").toFloat())
        },
        obj.getInt("drawnPreset"), obj.getDouble("drawingAspect").toFloat(),
        obj.getString("dinoKey"), Color(obj.getInt("dinoColor")),
        obj.getString("solutionKey"), obj.getString("solutionItem"), obj.getString("friendName"),
        obj.getString("solutionLine"), obj.nullableString("placeLabel"),
        obj.optString("newcomerKind", "외계인"), obj.optString("soundLine", "뿌우우우웅!"),
        obj.optString("causeLine", "친구가 없어서 심심했어"),
    )
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
