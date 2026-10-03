package com.example.finalproject_demo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.min

/*
 * 뼈대로 **휘게** 그리기 — 생성한 캐릭터를 자르지 않고 움직인다 (09-28 · 시연)
 *
 * 1차는 부위를 오려 축대로 돌렸다. 실물 폰에서 보니 **어깨에 잘린 자국**이 났다 — 오려 내는 한 이음매가 남는다.
 * 그래서 Spine · Live2D 가 하는 방식으로 바꿨다:
 *   그림 한 장 + 고운 삼각형 그물 + 그물의 점마다 **어느 뼈를 얼마나 따라갈지(가중치)**
 *   → 뼈 각도대로 점을 옮기고(선형 블렌드 스키닝) → 그림을 그물에 입혀 그린다(`Canvas.drawVertices`)
 * 어깨 근처 점은 팔을 조금만, 팔 쪽으로 갈수록 많이 따라가서 소매와 어깨가 천처럼 늘어나며 휜다.
 *
 * 뼈대는 **폰이 그림을 보고 그 자리에서 붙인다** (09-28 — `RigCore.kt` 의 `RigBuilder`).
 * 아이가 주인공이나 공룡을 고르는 순간 [RigCache] 가 뒤에서 만들기 시작하고, 책이 열릴 때 쓴다.
 * 서버가 캐릭터를 생성하게 되어도 같다 — 받은 그림 한 장에 폰이 뼈대를 붙인다.
 * `assets/rig/<이름>/` 은 마네킹 틀로 뽑은 시연용 둘뿐이다 (`tools/rig_mesh_export.py` · `docs/캐릭터_생성_규격.md` §8)
 */

/**
 * 캐릭터 하나 — 그물(계산은 `RigCore.kt`) + 그릴 그림판.
 * [drawTex] 는 [bitmap] 위의 점 좌표다. 뼈대는 512 로 줄여 붙여도 **그릴 때는 원래 해상도**로 그린다 (10-03 —
 * 줄인 그림을 화면 크기로 다시 키워 그려서, 그림 한 장(원본)에서 움직이는 그림으로 바뀌는 순간 흐려졌다)
 */
class MeshRig(val mesh: RigMesh, val bitmap: Bitmap, val drawTex: FloatArray = mesh.tex) {
    val kind get() = mesh.kind
}

/** `assets/rig/<name>/mesh.json` · 그림을 읽는다 (시연용 — 마네킹 틀로 뽑은 아이 · 공룡). 없거나 깨졌으면 null */
fun loadMeshRig(context: Context, name: String): MeshRig? = runCatching {
    val base = "rig/$name"
    val j = JSONObject(context.assets.open("$base/mesh.json").bufferedReader().use { it.readText() })
    val img = j.getJSONObject("image")
    val bmp = context.assets.open("$base/${img.getString("file")}").use { BitmapFactory.decodeStream(it) }
    val ix = img.getDouble("x").toFloat(); val iy = img.getDouble("y").toFloat()
    val bj = j.getJSONArray("bones")
    val bones = (0 until bj.length()).map { i ->
        val b = bj.getJSONObject(i); val p = b.optJSONArray("pivot")
        Bone(
            b.getString("name"), b.getInt("parent"), p?.getDouble(0)?.toFloat() ?: 0f, p?.getDouble(1)?.toFloat() ?: 0f,
            b.optString("follow").ifEmpty { null }, b.optDouble("ratio", 1.0).toFloat(),
        )
    }
    val vj = j.getJSONArray("vertices"); val n = vj.length()
    val rest = FloatArray(n * 2); val tex = FloatArray(n * 2)
    for (i in 0 until n) {
        val v = vj.getJSONArray(i)
        rest[2 * i] = v.getDouble(0).toFloat(); rest[2 * i + 1] = v.getDouble(1).toFloat()
        tex[2 * i] = rest[2 * i] - ix; tex[2 * i + 1] = rest[2 * i + 1] - iy
    }
    val wj = j.getJSONArray("weights")
    val wBone = Array(n) { i -> val w = wj.getJSONArray(i); IntArray(w.length()) { k -> w.getJSONArray(k).getInt(0) } }
    val wVal = Array(n) { i -> val w = wj.getJSONArray(i); FloatArray(w.length()) { k -> w.getJSONArray(k).getDouble(1).toFloat() } }
    val tj = j.getJSONArray("triangles")
    val idx = ShortArray(tj.length() * 3)
    for (t in 0 until tj.length()) { val tr = tj.getJSONArray(t); for (k in 0..2) idx[3 * t + k] = tr.getInt(k).toShort() }
    val c = j.getJSONArray("canvas")
    MeshRig(RigMesh(j.getString("kind"), c.getDouble(0).toFloat(), c.getDouble(1).toFloat(), bones, rest, tex, wBone, wVal, idx), bmp)
}.getOrNull()

/** 뼈대 기준 크기 — `RigBuilder` 의 문턱값 · 동작 각도가 512 캔버스로 맞춰져 있다 */
const val RIG_CANVAS = 512

/**
 * **공용 입구** (09-30) — 그림 한 장(앱에 든 것이든 서버가 생성한 것이든)에 뼈대를 붙인다. 못 붙이면 null.
 * 긴 변이 [RIG_CANVAS] 보다 크면 줄여서 붙인다(서버 캐릭터는 640 — 문턱값이 512 기준이다). 작으면 그대로.
 */
fun buildMeshRig(src: Bitmap, hint: RigHint = RigHint.AUTO): MeshRig? {
    val bmp0 = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
    val long = maxOf(bmp0.width, bmp0.height)
    val bmp = if (long > RIG_CANVAS) {
        val k = RIG_CANVAS.toFloat() / long
        Bitmap.createScaledBitmap(bmp0, maxOf(1, (bmp0.width * k).toInt()), maxOf(1, (bmp0.height * k).toInt()), true)
    } else bmp0
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val mesh = RigBuilder.build(px, bmp.width, bmp.height, hint) ?: return null
    if (bmp === bmp0) {
        // 줄이지 않았다 — 팔을 떼어 냈으면 [몸 층 | 팔 층] 그림판, 아니면 그림 그대로
        val tex = mesh.atlas?.let { Bitmap.createBitmap(it, mesh.atlasW, bmp.height, Bitmap.Config.ARGB_8888) } ?: bmp
        return MeshRig(mesh, tex)
    }
    val kx = bmp0.width.toFloat() / bmp.width; val ky = bmp0.height.toFloat() / bmp.height
    val drawTex = FloatArray(mesh.tex.size) { i -> mesh.tex[i] * if (i % 2 == 0) kx else ky }
    return MeshRig(mesh, fullTexture(bmp0, px, bmp.width, bmp.height, mesh), drawTex)
}

/**
 * 줄여서 붙인 뼈대에 입힐 **원래 해상도 그림판**. 떼어 낸 팔이 없으면 원래 그림 그대로.
 * 팔을 떼어 냈으면 [몸 층 | 팔 층] 을 원래 해상도로 다시 만든다:
 *   팔 층 = 작은 그림판에서 팔이던 자리의 원래 화소
 *   몸 층 = 작은 그림과 같은 자리는 원래 화소, 팔이 가리던 자리(거울로 메웠거나 비운 곳)는 작은 그림판 값
 * 원래 그림도 뼈대를 붙일 때와 같게 회색 반투명 그림자를 지운다([RigBuilder.cleanHalo]).
 */
private fun fullTexture(big: Bitmap, small: IntArray, w: Int, h: Int, mesh: RigMesh): Bitmap {
    val atlas = mesh.atlas ?: return big
    val bw = big.width; val bh = big.height
    val orig = IntArray(bw * bh).also { big.getPixels(it, 0, bw, 0, 0, bw, bh) }
    val clean = RigBuilder.cleanHalo(orig)
    val smallClean = RigBuilder.cleanHalo(small)
    val aw = mesh.atlasW
    val out = IntArray(2 * bw * bh)
    for (y in 0 until bh) {
        val sy = min(h - 1, y * h / bh)
        for (x in 0 until bw) {
            val sx = min(w - 1, x * w / bw)
            val o = y * bw + x
            val under = atlas[sy * aw + sx]
            out[y * 2 * bw + x] = if (under == smallClean[sy * w + sx]) clean[o] else under
            // 팔 층 — 작은 그림판과 같은 규칙: 거의 투명한 가장자리(60 미만)는 넣지 않는다
            if ((atlas[sy * aw + w + sx] ushr 24) != 0 && (clean[o] ushr 24) >= 60) out[y * 2 * bw + bw + x] = clean[o]
        }
    }
    return Bitmap.createBitmap(out, 2 * bw, bh, Bitmap.Config.ARGB_8888)
}

/** 서버가 보낸 PNG 바이트 → 뼈대. 그림을 못 읽으면 null */
fun buildMeshRig(png: ByteArray, hint: RigHint = RigHint.AUTO): MeshRig? {
    val bmp = BitmapFactory.decodeByteArray(png, 0, png.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
        ?: return null
    return buildMeshRig(bmp, hint)
}

/** 앱에 든 그림(`res/drawable/<name>`)을 읽어 **그 자리에서** 뼈대를 붙인다. 못 붙이면 null */
fun buildMeshRig(context: Context, name: String): MeshRig? {
    @Suppress("DiscouragedApi")
    val id = context.resources.getIdentifier(name, "drawable", context.packageName)
    if (id == 0) return null
    val opt = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val bmp = BitmapFactory.decodeResource(context.resources, id, opt) ?: return null
    return buildMeshRig(bmp)
}

/**
 * **뒤에서 뼈대를 붙여 두는 곳** (09-28).
 *
 * 아이가 대화 중에 주인공(머리 · 옷 · 색)이나 공룡을 고르면 그 그림 이름이 정해진다. 그 순간 [prefetch] 로
 * 다른 스레드에서 뼈대를 붙이기 시작한다 — 책은 대화가 끝나야 열리니 그때는 이미 끝나 있다.
 * 같은 그림은 한 번만 만든다. 못 붙였거나 아직이면 [peek] 이 null 이고, 부르는 쪽은 지금처럼 그림 한 장으로 그린다.
 *
 * 검사 환경(`motionFrozen`)에서는 아무것도 만들지 않는다 — 언제 끝날지 모르는 일이 화면 검사를 흔들면 안 된다.
 */
object RigCache {
    private val done = mutableStateMapOf<String, MeshRig?>()
    private val started = java.util.Collections.synchronizedSet(HashSet<String>())
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private val pool by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "rig-builder").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }
    }

    /** 만드는 데 걸린 시간(ms) — 시연 서랍이 보여 준다 */
    val timings = mutableStateMapOf<String, Long>()

    /** 다 만들었으면 뼈대, 아니면 null (읽는 쪽 화면은 다 만들어지는 순간 다시 그려진다) */
    fun peek(name: String): MeshRig? = done[name]

    fun prefetch(context: Context, name: String) {
        val app = context.applicationContext
        run(name) { buildMeshRig(app, name) }
    }

    /**
     * **서버가 생성한 캐릭터** (09-30 · `Server.character()` 결과) — 받은 PNG 와 몸 종류로 뒤에서 뼈대를 붙인다.
     * [key] 는 부르는 쪽이 정하는 이름(예: `"gen:친구1"`). 같은 key 는 한 번만 만든다 — 새 그림이면 새 key 를 쓴다.
     * 다 되면 [peek] (key) 로 꺼낸다. 못 붙이면 null 이고, 부르는 쪽은 그 PNG 를 그림 한 장으로 그리면 된다.
     */
    fun prefetch(key: String, png: ByteArray, hint: RigHint) = run(key) { buildMeshRig(png, hint) }

    private fun run(key: String, make: () -> MeshRig?) {
        if (motionFrozen || key.isEmpty() || !started.add(key)) return
        pool.execute {
            val t0 = System.nanoTime()
            val result = runCatching { make() }
            // 못 붙인 것(null)은 다시 해도 같다. 던진 것(메모리 부족 같은 일시적인 일)은 다음 부를 때 다시 해 본다 (10-03)
            if (result.isFailure) started.remove(key)
            val rig = result.getOrNull()
            val ms = (System.nanoTime() - t0) / 1_000_000
            android.util.Log.i("Rig", "$key → ${rig?.kind ?: "실패"} · 뼈 ${rig?.mesh?.bones?.size ?: 0} · 점 ${rig?.mesh?.vertexCount ?: 0} · ${ms}ms")
            main.post { done[key] = rig; timings[key] = ms }
        }
    }
}

/** 그림 이름의 뼈대 — 다 되었으면 돌려주고, 없으면 뒤에서 만들기 시작한다 (다 되면 다시 그려진다) */
@Composable
fun rememberRig(name: String?): MeshRig? {
    if (name == null || motionFrozen) return null
    val ctx = LocalContext.current
    LaunchedEffect(name) { RigCache.prefetch(ctx, name) }
    return RigCache.peek(name)
}

/**
 * 캐릭터를 뼈대로 휘게 그린다. 검사 환경(`motionFrozen`)에서는 움직이지 않고 t = 1초 장면을 그린다.
 * 자리는 그림 한 장(`ContentScale.Fit`)과 같다 — 가운데 맞추고 짧은 쪽에 맞춘다. 그래서 그림 한 장과 바꿔 끼울 수 있다.
 *
 * @param colorFilter 공룡 색 바꾸기(색상환 돌리기) 같은 것 — 그림 한 장일 때와 같은 필터
 * @param bobbing 몸 전체를 위아래로 들썩일 것인가. 주인공은 눈 · 안경을 위에 얹으므로 false (얹은 것과 어긋난다)
 */
@Composable
fun RigView(
    rig: MeshRig,
    motion: RigMotion,
    modifier: Modifier = Modifier,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
    bobbing: Boolean = true,
) {
    var t by remember(motion) { mutableFloatStateOf(1f) }
    LaunchedEffect(motion) {
        if (motionFrozen) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> t = (now - start) / 1e9f }
    }
    val paint = remember(rig, colorFilter) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(rig.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            this.colorFilter = colorFilter?.asAndroidColorFilter()
        }
    }
    val mesh = rig.mesh
    val (angles, bob) = poseAt(mesh.bones, motion, t)
    Canvas(modifier) {
        skin(mesh, angles)
        val sc = min(size.width / mesh.canvasW, size.height / mesh.canvasH)
        val ox = (size.width - mesh.canvasW * sc) / 2f
        val oy = (size.height - mesh.canvasH * sc) / 2f
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.translate(ox, oy)
            nc.scale(sc, sc)
            // 흔들림은 캔버스 512 기준 화소로 잡았다
            if (bobbing) nc.translate(0f, bob * mesh.canvasH / 512f)
            nc.drawVertices(
                android.graphics.Canvas.VertexMode.TRIANGLES, mesh.out.size, mesh.out, 0, rig.drawTex, 0,
                null, 0, mesh.indices, 0, mesh.indices.size, paint,
            )
            nc.restore()
        }
    }
}

/**
 * 시연 서랍의 「🦴 관절」 탭 — **앱에 든 캐릭터 그림에 그 자리에서 뼈대를 붙여** 동작 틀로 움직여 본다 (09-28).
 * 앞의 두 개(kid · dino)는 마네킹 틀로 뽑아 도구로 붙인 시연용, 나머지는 `RigBuilder` 가 그림만 보고 붙인 것이다.
 */
@Composable
fun RigDemoPanel() {
    val ctx = LocalContext.current
    val names = remember {
        listOf("demo:kid", "demo:dino") +
            listOf("red", "blue", "yellow").flatMap { c ->
                listOf("pants", "shorts", "skirt").flatMap { b -> listOf("", "_long", "_tied").map { h -> "body_${c}_$b$h" } }
            } +
            listOf("dino_long", "dino_horn", "dino_trex", "bud_alien", "bud_robot", "bud_star", "bud_dolphin", "bud_seahorse",
                "bud_starfish", "bud_snowman", "bud_bear", "bud_penguin")
    }
    var pick by remember { mutableStateOf("body_red_pants") }
    var motion by remember { mutableStateOf(RigMotion.WAVE) }
    val demo = remember(pick) { if (pick.startsWith("demo:")) loadMeshRig(ctx, pick.removePrefix("demo:")) else null }
    val rig = if (pick.startsWith("demo:")) demo else rememberRig(pick)
    Text("그림만 보고 뼈대 붙이기 (자동)", fontSize = 14.sp, color = Color.White)
    Spacer(Modifier.height(4.dp))
    Text(
        "고르는 순간 뒤에서 붙인다 — 몸통보다 가는 가지(팔 · 다리 · 꼬리 · 목)를 찾아 휘게 한다. 못 찾으면 그림 한 장 그대로",
        fontSize = 11.sp, color = Color(0xFFB8AC9C), lineHeight = 16.sp,
    )
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(14.dp)).background(Bg).padding(6.dp)) {
        if (rig != null) RigView(rig, motion, Modifier.fillMaxSize())
        else AssetImage(pick.removePrefix("demo:"), Modifier.fillMaxSize())
    }
    Text(
        if (rig == null) "뼈대 없음 (만드는 중이거나 못 찾음)"
        else "${rig.kind} · 뼈 ${rig.mesh.bones.size - 1}개 [${rig.mesh.bones.drop(1).joinToString { it.name }}] · 점 ${rig.mesh.vertexCount}" +
            (RigCache.timings[pick]?.let { " · ${it}ms" } ?: ""),
        fontSize = 11.sp, color = Color(0xFFE8DCC8), lineHeight = 15.sp, modifier = Modifier.padding(vertical = 4.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RigMotion.entries.forEach { m ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (m == motion) Sun else Color(0xFF3A332C))
                    .clickable { motion = m }.padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(m.label, fontSize = 11.sp, color = if (m == motion) Ink else Color.White) }
        }
    }
    Spacer(Modifier.height(6.dp))
    names.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            row.forEach { nm ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (nm == pick) Sun else Color(0xFF3A332C))
                        .clickable { pick = nm }.padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(nm.removePrefix("body_").removePrefix("demo:"), fontSize = 10.sp, color = if (nm == pick) Ink else Color.White, maxLines = 1) }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
