package com.example.finalproject_demo

import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.ui.LimbRole
import com.example.finalproject_demo.ui.RigHint
import com.example.finalproject_demo.ui.tearScore
import com.example.finalproject_demo.ui.tearScores
import kotlin.math.max
import kotlin.math.min
import com.example.finalproject_demo.ui.buildMeshRig
import com.example.finalproject_demo.ui.buddyActFrom
import com.example.finalproject_demo.ui.heroActFrom
import com.example.finalproject_demo.ui.ridingFrom
import com.example.finalproject_demo.ui.walksInFrom
import com.example.finalproject_demo.ui.RigBuilder
import com.example.finalproject_demo.ui.RigMesh
import com.example.finalproject_demo.ui.RigMotion
import com.example.finalproject_demo.ui.poseAt
import com.example.finalproject_demo.ui.skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 그림만 보고 뼈대 붙이기 — **앱에 든 실제 캐릭터 그림**으로 확인한다 (09-28).
 *
 * 뼈대 계산(`RigCore.kt`)은 순수 코틀린이라 그림 화소만 넘기면 된다. 그림을 읽고 쓰는 데만 Robolectric 을 쓴다
 * (단위 검사 경로에는 자바 그래픽 `java.awt` 가 없다).
 * 결과를 눈으로 보도록 `build/rig_auto/` 에 표를 그린다 — 줄마다 캐릭터, 칸마다
 * 「뼈대 · 가만히 · 손 흔들기 · 만세 · 걷기 두 장면」.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RigBuilderTest {
    private val drawable = File("src/main/res/drawable")
    private val outDir = File("build/rig_auto").apply { mkdirs() }

    private val heroes = listOf("red", "blue", "yellow").flatMap { c ->
        listOf("pants", "shorts", "skirt").flatMap { b -> listOf("", "_long", "_tied").map { h -> "body_${c}_$b$h" } }
    }
    private val others = listOf(
        "dino_long", "dino_horn", "dino_trex", "bud_alien", "bud_robot", "bud_star",
        "bud_dolphin", "bud_seahorse", "bud_starfish", "bud_snowman", "bud_bear", "bud_penguin",
    )

    private class Img(val w: Int, val h: Int, val px: IntArray)

    private fun load(name: String): Img {
        val bmp = BitmapFactory.decodeFile(File(drawable, "$name.png").path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return Img(bmp.width, bmp.height, px)
    }

    private fun build(img: Img): RigMesh? = RigBuilder.build(img.px, img.w, img.h)

    @Test
    fun 주인공_27장은_모두_사람형으로_팔_둘을_찾는다() {
        val bad = heroes.mapNotNull { n ->
            val m = build(load(n))
            val arms = m?.bones?.count { it.role == LimbRole.ARM && it.parent == 0 } ?: 0
            if (m?.kind == "human" && arms == 2) null else "$n(${m?.kind}, 팔 $arms)"
        }
        assertEquals("사람형으로 못 잡은 그림: $bad", 0, bad.size)
    }

    @Test
    fun 목_긴_공룡은_목을_끄덕인다() {
        // 짧고 굵은 다리는 몸통으로 친다 — 가지로 떼어 휘면 몸이 찢어진다. 목(머리)과 꼬리가 움직인다
        val m = build(load("dino_long"))
        assertNotNull(m)
        assertEquals(2, m!!.bones.count { it.role == LimbRole.NECK })
    }

    @Test
    fun 사람으로_보이지_않는_캐릭터는_팔을_떼어_내지_않는다() {
        // 트리케라톱스 발 · 외계인 · 별의 끝을 손으로 잡으면 그림이 찢어졌다 (09-28)
        for (n in listOf("dino_horn", "bud_alien", "bud_star", "bud_bear")) {
            val m = build(load(n))
            assertEquals("$n 에 떼어 낸 팔이 생겼다", null, m?.atlas)
        }
    }

    @Test
    fun 쪽에_적힌_대로_동작을_고른다() {
        val t = { k: PageKind, c: String -> heroActFrom(k, c, ridingFrom(c)) }
        assertEquals(RigMotion.WAVE, t(PageKind.MEET, "친구에게 손을 흔들며 인사했어요"))
        assertEquals(RigMotion.HOORAY, t(PageKind.TOGETHER, "모두 함께 집으로 돌아왔어요"))
        assertEquals(RigMotion.HOORAY, t(PageKind.TALK, "드디어 해냈어요!"))
        // 속상한 일이 먼저 — 넘어져 운 쪽에서 만세를 하면 안 된다
        assertEquals(RigMotion.SAD, t(PageKind.TALK, "달리다가 넘어져서 울었어요"))
        assertEquals(RigMotion.SAD, t(PageKind.FAIL, "문이 열리지 않았어요"))
        assertEquals(RigMotion.WALK, t(PageKind.JOURNEY, "숲속을 뛰어갔어요"))
        // 기차가 흔들린 것은 인사가 아니다
        assertEquals(RigMotion.IDLE, t(PageKind.SHAKE, "기차가 덜컹덜컹 흔들렸어요"))
        // 떠나는 쪽 · 만나는 쪽은 걸어 들어온다. 탈것에 탄 쪽은 걷지 않는다
        assertEquals(true, walksInFrom(PageKind.DEPART, "용감한 아이가 길을 나섰어요", false))
        assertEquals(false, walksInFrom(PageKind.DEPART, "로켓에 올라탔어요", ridingFrom("로켓에 올라탔어요")))
        assertEquals(RigMotion.HOORAY, buddyActFrom(PageKind.TOGETHER, ""))
    }

    /**
     * **공용 입구** (09-30) — 서버가 보내는 모양(640² PNG 바이트 + 몸 종류)을 그대로 넣어도 뼈대가 붙는가.
     * 앱 주인공 그림을 640 으로 늘려 PNG 로 만든 것을 「서버가 보낸 캐릭터」로 쓴다
     */
    @Test
    fun 서버가_보낸_640_PNG도_받아서_뼈대를_붙인다() {
        val src = BitmapFactory.decodeFile(File(drawable, "body_red_pants.png").path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
        val big = Bitmap.createScaledBitmap(src, 640, 640, true)
        val png = java.io.ByteArrayOutputStream().also { big.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val rig = buildMeshRig(png, RigHint.HUMAN)
        assertTrue("640 PNG 에 뼈대가 안 붙었다", rig != null)
        assertEquals("human", rig!!.kind)
        assertEquals("512 캔버스로 줄여 붙여야 한다", 512f, rig.mesh.canvasW, 0.5f)
        assertEquals(RigHint.HUMAN, RigHint.of("human"))
        assertEquals(RigHint.AUTO, RigHint.of("모르는값"))
        assertEquals("깨진 바이트는 null", null, buildMeshRig(byteArrayOf(1, 2, 3), RigHint.HUMAN))
    }

    /**
     * **생성 캐릭터 22장 — 몸 종류대로 뼈대가 붙는가** (09-30 · `src/test/resources/rig_corpus`, 서버와 같은 마네킹 · 자세 · 오려 내기).
     *
     * - 사람형 10: 사람형 · 팔 둘 (한 팔만 든 토끼 · 좌우 높이가 다른 할머니 포함)
     * - 네발형 6: 네발형 · 다리 둘 이상 (짧고 굵은 다리의 아기 공룡 · 강아지 · 사자 포함) · 코끼리 코는 다리가 아니다
     * - 덩어리형 6: 덩어리형 · 팔 · 다리 없음
     * - 전부: 찢어짐이 기준([RigBuilder.MAX_TEAR]) 안
     */
    @Test
    fun 생성_캐릭터는_몸_종류대로_뼈대가_붙는다() {
        val files = File("src/test/resources/rig_corpus").listFiles { f -> f.name.endsWith(".webp") }?.sorted().orEmpty()
        assertEquals("검사 그림 22장이 있어야 한다", 22, files.size)
        val bad = ArrayList<String>()
        for (f in files) {
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val hint = RigHint.of(f.name.substringBefore("__"))
            val m = RigBuilder.build(px, bmp.width, bmp.height, hint)
            if (m == null) { bad += "${f.name}: 뼈대 없음"; continue }
            val arms = m.bones.count { it.role == LimbRole.ARM && it.parent == 0 }
            val legs = m.bones.count { it.role == LimbRole.LEG }
            val tear = tearScore(m, m.atlas ?: px, m.atlasW, bmp.height)
            val ok = when (hint) {
                RigHint.HUMAN -> m.kind == "human" && arms == 2
                RigHint.QUAD -> m.kind == "quad" && legs >= 2 && arms == 0
                RigHint.BLOB -> m.kind == "blob" && arms == 0 && legs == 0
                RigHint.AUTO -> true
            } && tear <= RigBuilder.MAX_TEAR
            if (!ok) bad += "${f.name}: ${m.kind} 팔 $arms 다리 $legs 찢어짐 ${"%.3f".format(tear)}"
        }
        assertEquals("몸 종류대로 안 붙은 그림: " + bad.joinToString(" / "), 0, bad.size)
    }

    /**
     * **생성 캐릭터 모음**(`tools/gen_rig_corpus.py` — 서버와 같은 마네킹 · 자세 · 오려 내기)으로 뼈대를 붙여 본다 (09-30).
     * 파일 이름 앞이 몸 종류(`human__` · `quad__` · `blob__`) — 서버가 보내는 힌트 그대로 넣는다.
     * 확인표 `build/rig_auto/corpus.png` · 줄마다 결과 `corpus.txt`. 폴더는 시스템 속성 `rig.corpus` 로 바꾼다
     */
    @Test
    fun 생성_캐릭터_모음() {
        // 기본은 레포의 22장 — 예전에는 개인 Temp 폴더를 봐서 다른 PC · CI 에서는 아무것도 안 하고 통과했다 (10-03)
        val dir = File(System.getProperty("rig.corpus") ?: System.getenv("RIG_CORPUS") ?: "src/test/resources/rig_corpus")
        val files = dir.listFiles { f -> (f.name.endsWith(".png") || f.name.endsWith(".webp")) && f.name.contains("__") }?.sorted().orEmpty()
        assertTrue("생성 캐릭터 그림이 없다: $dir", files.isNotEmpty())
        fun img(path: String): Img {
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            val sc = 512f / maxOf(bmp.width, bmp.height)
            val b2 = Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true)
            val px = IntArray(b2.width * b2.height); b2.getPixels(px, 0, b2.width, 0, 0, b2.width, b2.height)
            return Img(b2.width, b2.height, px)
        }
        val cols = poses + listOf("꼬리" to { m: RigMesh -> poseAt(m.bones, RigMotion.TAIL, 0.4f).first })
        sheet("corpus", files.map { it.path }, cell = 240, cols = cols, imgOf = { img(it) },
            meshOf = { path, i -> RigBuilder.build(i.px, i.w, i.h, RigHint.of(File(path).name.substringBefore("__"))) })
        // 찢어짐 점수까지 한 줄씩
        val sb = StringBuilder()
        for (f in files) {
            val i = img(f.path)
            val hint = RigHint.of(f.name.substringBefore("__"))
            val m = RigBuilder.build(i.px, i.w, i.h, hint)
            val ts = m?.let { tearScores(it, it.atlas ?: i.px, it.atlasW, i.h) }
            sb.appendLine("${f.name}: 힌트 ${hint.key} → ${m?.kind} · 뼈 ${m?.bones?.drop(1)?.joinToString { it.name }} · " +
                "찢어짐 ${ts?.let { "%.3f · 가지 %.3f".format(it.first, it.second) }} ·${RigBuilder.why}")
        }
        File(outDir, "corpus_tear.txt").writeText(sb.toString())
        println(sb)
    }

    /**
     * 앱에 든 그림들의 찢어짐 점수 — 사용자가 「잘 된다」고 확인한 뼈대들이 기준선이다 (`build/rig_auto/tear.txt`).
     * 10-03: 기록만 하고 단언이 없어 점수가 나빠져도 통과했다 → 모두 뼈대가 붙고 두 기준 안이어야 한다
     */
    @Test
    fun 찢어짐_점수_기준선() {
        val sb = StringBuilder()
        val bad = ArrayList<String>()
        for (n in heroes + others) {
            val img = load(n)
            val m = build(img) ?: run { sb.appendLine("$n: 뼈대 없음"); bad += "$n 뼈대 없음"; null } ?: continue
            val (tear, limb) = tearScores(m, m.atlas ?: img.px, m.atlasW, img.h)
            sb.appendLine("$n: ${m.kind} · 뼈 ${m.bones.size - 1} · 찢어짐 ${"%.4f".format(tear)} · 가지 ${"%.4f".format(limb)}")
            if (tear > RigBuilder.MAX_TEAR || limb > RigBuilder.MAX_TEAR_LIMB) bad += "$n ${"%.3f/%.3f".format(tear, limb)}"
        }
        File(outDir, "tear.txt").writeText(sb.toString())
        println(sb)
        assertEquals("기준을 넘은 그림: $bad", 0, bad.size)
    }

    // ── 10-03 검토에서 더한 검사 ─────────────────────────────────────

    private fun corpus(): List<File> =
        File("src/test/resources/rig_corpus").listFiles { f -> f.name.endsWith(".webp") }?.sorted().orEmpty()

    private fun decode(f: File): Bitmap =
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private fun shape(m: RigMesh?) = m?.let { "${it.kind} 팔 ${it.bones.count { b -> b.role == LimbRole.ARM && b.parent == 0 }} 다리 ${it.bones.count { b -> b.role == LimbRole.LEG }}" }

    @Test
    fun 빈_그림_작은_그림은_예외_없이_null() {
        assertEquals(null, RigBuilder.build(IntArray(64 * 64), 64, 64))
        assertEquals(null, RigBuilder.build(intArrayOf(0xFF336699.toInt()), 1, 1))
        // 화소 200 개 미만 — 몸만 들썩이기도 못 한다
        val tiny = IntArray(64 * 64).also { for (y in 20 until 30) for (x in 20 until 30) it[y * 64 + x] = 0xFF336699.toInt() }
        assertEquals(null, RigBuilder.build(tiny, 64, 64, RigHint.HUMAN))
    }

    /**
     * 몸통 + 팔 · 다리 · 꼬리를 무작위로 붙인 그림 300 장 — 어떤 그림이 와도 **던지지 않고**, 그림이 충분히 크면 ③(몸만)까지는 붙는다.
     * 예전에는 ① 안에서 던지면(좁은 팔 뿌리 · coerceIn) build 전체가 끝나 ③도 못 갔다
     */
    @Test
    fun 이상한_모양도_던지지_않고_뼈대가_붙는다() {
        val rnd = java.util.Random(20261003)
        val w = 256; val h = 256
        var stageErrors = 0
        for (k in 0 until 300) {
            val px = IntArray(w * h)
            fun fill(x0: Int, y0: Int, x1: Int, y1: Int, ellipse: Boolean, col: Int) {
                for (y in max(0, y0) until min(h, y1)) for (x in max(0, x0) until min(w, x1)) {
                    if (ellipse) {
                        val ex = (x - (x0 + x1) / 2f) / ((x1 - x0) / 2f); val ey = (y - (y0 + y1) / 2f) / ((y1 - y0) / 2f)
                        if (ex * ex + ey * ey > 1f) continue
                    }
                    px[y * w + x] = col
                }
            }
            val bw = 40 + rnd.nextInt(80); val bh = 60 + rnd.nextInt(120)
            val bx = (w - bw) / 2; val by = 20 + rnd.nextInt(40)
            val skin = 0xFFF0C8A0.toInt(); val cloth = listOf(0xFF3366CC, 0xFFCC3344, 0xFF55AA55)[rnd.nextInt(3)].toInt()
            fill(bx, by, bx + bw, by + bh, rnd.nextBoolean(), cloth)
            fill(w / 2 - 18, by - 34, w / 2 + 18, by + 4, true, skin)                      // 머리
            repeat(rnd.nextInt(5)) {                                                         // 가지: 몸에 붙었거나 1~3 화소 떨어짐
                val side = if (rnd.nextBoolean()) -1 else 1
                val gap = rnd.nextInt(4)
                val tw = 2 + rnd.nextInt(14); val tl = 10 + rnd.nextInt(70)
                val ty = by + rnd.nextInt(max(1, bh - 10))
                val x0 = if (side < 0) bx - gap - tw else bx + bw + gap
                fill(x0, ty, x0 + tw, min(h, ty + tl), false, if (rnd.nextBoolean()) skin else cloth)
            }
            RigBuilder.why = ""
            val m = RigBuilder.build(px, w, h, listOf(RigHint.AUTO, RigHint.HUMAN, RigHint.QUAD, RigHint.BLOB)[k % 4])
            if (RigBuilder.why.contains("오류")) stageErrors++
            assertNotNull("그림 $k 에 뼈대가 없다 (${RigBuilder.why})", m)
        }
        println("단계 안에서 던졌다가 다음 단계로 넘어간 그림: $stageErrors / 300")
    }

    @Test
    fun 좌우를_뒤집어도_같은_몸으로_붙는다() {
        val bad = ArrayList<String>()
        for (f in corpus()) {
            val b = decode(f); val px = pixels(b); val w = b.width; val h = b.height
            val flip = IntArray(px.size) { i -> px[(i / w) * w + (w - 1 - i % w)] }
            val hint = RigHint.of(f.name.substringBefore("__"))
            val a = shape(RigBuilder.build(px, w, h, hint)); val c = shape(RigBuilder.build(flip, w, h, hint))
            if (a != c) bad += "${f.name}: $a ↔ 뒤집으면 $c"
        }
        assertEquals("뒤집으면 달라진 그림: " + bad.joinToString(" / "), 0, bad.size)
    }

    /**
     * 서버와 같은 길 — 640² PNG 바이트 → `buildMeshRig`(512 로 줄여 붙임). 생성 캐릭터 22장 전부.
     * 그리고 **그릴 그림판은 원래 해상도**여야 한다(10-03 — 512 그림을 화면 크기로 키워 그려 흐려졌다)
     */
    @Test
    fun 서버가_보낸_640_생성_캐릭터도_몸_종류대로_붙고_원래_해상도로_그린다() {
        val bad = ArrayList<String>()
        for (f in corpus()) {
            val big = Bitmap.createScaledBitmap(decode(f), 640, 640, true)
            val png = java.io.ByteArrayOutputStream().also { big.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            val hint = RigHint.of(f.name.substringBefore("__"))
            val rig = buildMeshRig(png, hint)
            if (rig == null) { bad += "${f.name}: 뼈대 없음"; continue }
            val ok = when (hint) {
                RigHint.HUMAN -> rig.kind == "human"
                RigHint.QUAD -> rig.kind == "quad"
                else -> rig.kind == "blob"
            }
            if (!ok) bad += "${f.name}: ${shape(rig.mesh)}"
            val layers = if (rig.mesh.atlas != null) 2 else 1
            if (rig.bitmap.width != 640 * layers || rig.bitmap.height != 640) bad += "${f.name}: 그림판 ${rig.bitmap.width}×${rig.bitmap.height}"
            val maxU = rig.drawTex.filterIndexed { i, _ -> i % 2 == 0 }.max()
            if (maxU > rig.bitmap.width + 64f || maxU < 0.5f * rig.bitmap.width) bad += "${f.name}: 그림 좌표 ${maxU.toInt()} / ${rig.bitmap.width}"
            // 눈으로 보는 확인표 — 원래 해상도 그림판 · 그 좌표로 만세 · 손 흔들기를 640 에 그린다 (`build/rig_auto/server640/`)
            val dir = File(outDir, "server640").apply { mkdirs() }
            val texPx = pixels(rig.bitmap)
            val k = 640f / rig.mesh.canvasW
            val row = IntArray(640 * 3 * 640) { 0xFFF4EFE6.toInt() }
            listOf(null, RigMotion.HOORAY to 1.0f, RigMotion.WAVE to 1.3f).forEachIndexed { c, mo ->
                skin(rig.mesh, mo?.let { poseAt(rig.mesh.bones, it.first, it.second).first } ?: FloatArray(rig.mesh.bones.size))
                val pos = FloatArray(rig.mesh.out.size) { rig.mesh.out[it] * k }
                val pic = draw(texPx, rig.bitmap.width, rig.bitmap.height, rig.drawTex, pos, rig.mesh.indices, 640, 640)
                for (y in 0 until 640) for (x in 0 until 640) { val v = pic[y * 640 + x]; if (v ushr 24 != 0) row[y * 1920 + c * 640 + x] = blend(row[y * 1920 + c * 640 + x], v) }
            }
            File(dir, f.name.replace(".webp", ".png")).outputStream().use { Bitmap.createBitmap(row, 1920, 640, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertEquals("640 길에서 어긋난 그림: " + bad.joinToString(" / "), 0, bad.size)
    }

    @Test
    fun 확인표를_그린다() {
        sheet("heroes", heroes)
        sheet("others", others)
        // 떼어 낸 팔을 몇 도까지 들어도 소매에 붙어 보이나 — 크게
        val fixed = { deg: Float -> { m: RigMesh -> FloatArray(m.bones.size) { if (m.bones[it].name.endsWith("_up")) deg * -m.bones[it].side else 0f } } }
        sheet("angles", listOf("body_red_pants", "body_blue_skirt_long", "body_yellow_pants"), cell = 400,
            cols = listOf("몸층" to { m: RigMesh -> FloatArray(m.bones.size) { if (m.bones[it].name.startsWith("arm")) 999f else 0f } },
                "60" to fixed(60f), "90" to fixed(90f), "110" to fixed(110f), "130" to fixed(130f)))
    }

    /**
     * **시험 — 마스코트 오또에도 뼈대가 붙나** (09-29 사용자 — 「마스코트한테도 뼈대를 붙여서 실제로 움직이게」).
     * 방 · 온보딩에 쓰는 전신 자세 그림 9장 + 기본 마스코트를 같은 자동 방식으로 돌려 확인표를 그린다(`build/rig_auto/otto.png`)
     */
    @Test
    fun 마스코트_오또_시험() {
        val otto = listOf("mascot", "otto_pose_wave", "otto_pose_walk", "otto_pose_point", "otto_pose_talk",
            "otto_pose_listen", "otto_pose_think", "otto_pose_phone", "otto_pose_yawn", "otto_pose_call")
        val cols = poses + listOf("꼬리" to { m: RigMesh -> poseAt(m.bones, RigMotion.TAIL, 0.4f).first })
        sheet("otto", otto, cell = 260, cols = cols)
        // 손 색 규칙을 풀면 — 지금 그림 · A-포즈 시험 그림(있으면)
        run {
            sheet("otto_anyhands", otto, cell = 260, cols = cols, meshOf = { _, i -> RigBuilder.build(i.px, i.w, i.h, RigHint.HUMAN) })
            // 시험 그림 폴더(ComfyUI 결과) — 시스템 속성 `otto.trial` 로 바꿀 수 있다
            val trial = File(System.getProperty("otto.trial") ?: File(System.getProperty("user.home"),
                "AppData/Local/Temp/claude/C--dev-final-project/344838c4-f7ce-48e6-9be7-ad3985d432f5/scratchpad/apose2").path)
            val ap = trial.listFiles { f -> f.name.startsWith("apose_") && f.name.endsWith(".png") }?.sorted().orEmpty()
            if (ap.isNotEmpty()) sheet("otto_apose", ap.map { it.path }, cell = 300, cols = cols, meshOf = { _, i -> RigBuilder.build(i.px, i.w, i.h, RigHint.HUMAN) }, imgOf = { path ->
                val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
                val sc = 512f / maxOf(bmp.width, bmp.height)
                val b2 = Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true)
                val px = IntArray(b2.width * b2.height); b2.getPixels(px, 0, b2.width, 0, 0, b2.width, b2.height)
                Img(b2.width, b2.height, px)
            })
        }
    }

    /** 시험 그림 하나를 시간에 따라 움직여 프레임으로 떨군다 (`build/rig_auto/frames/<동작>_<번호>.png`) — GIF 로 묶어 본다 */
    @Test
    fun 마스코트_오또_움직임_프레임() {
        val path = System.getProperty("otto.frames") ?: File(System.getProperty("user.home"),
            "AppData/Local/Temp/claude/C--dev-final-project/344838c4-f7ce-48e6-9be7-ad3985d432f5/scratchpad/apose2/apose_2.png").path
        // 개인 시험 그림이 없는 PC 에서는 「건너뜀」으로 보이게 — 조용히 통과하지 않는다 (10-03)
        org.junit.Assume.assumeTrue("시험 그림 없음: $path (-Dotto.frames=...)", File(path).exists())
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
        val sc = 512f / maxOf(bmp.width, bmp.height)
        val b2 = Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true)
        val w = b2.width; val h = b2.height
        val px = IntArray(w * h); b2.getPixels(px, 0, w, 0, 0, w, h)
        val m = RigBuilder.build(px, w, h, RigHint.HUMAN) ?: return
        val dir = File(outDir, "frames").apply { mkdirs() }
        for (motion in listOf(RigMotion.IDLE, RigMotion.WAVE, RigMotion.TAIL, RigMotion.WALK, RigMotion.HOORAY)) {
            for (k in 0 until 24) {
                val (angles, bob) = poseAt(m.bones, motion, k / 12f)
                val pic = render(px, w, h, m, angles)
                val out = IntArray(w * h) { 0xFFF4EFE6.toInt() }
                val dy = bob.toInt()
                for (y in 0 until h) for (x in 0 until w) {
                    val sy = y - dy; if (sy !in 0 until h) continue
                    val v = pic[sy * w + x]; if ((v ushr 24) == 0) continue
                    out[y * w + x] = blend(out[y * w + x], v)
                }
                File(dir, "${motion.name.lowercase()}_%02d.png".format(k)).outputStream().use {
                    Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    /** 이전 시연(도구로 만든 뼈대 · 사용자가 「잘 나온다」고 한 것)과 지금 자동 방식을 **같은 그림**에서 나란히 */
    @Test
    fun 이전_시연과_비교한다() {
        val kidDir = File("src/main/assets/rig/kid")
        val kidImg = run {
            val bmp = BitmapFactory.decodeFile(File(kidDir, "full.png").path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            Img(bmp.width, bmp.height, px)
        }
        val demo = run {
            val j = org.json.JSONObject(File(kidDir, "mesh.json").readText())
            val bj = j.getJSONArray("bones")
            val bones = (0 until bj.length()).map { i ->
                val b = bj.getJSONObject(i); val p = b.optJSONArray("pivot")
                com.example.finalproject_demo.ui.Bone(b.getString("name"), b.getInt("parent"), p?.getDouble(0)?.toFloat() ?: 0f,
                    p?.getDouble(1)?.toFloat() ?: 0f, b.optString("follow").ifEmpty { null }, b.optDouble("ratio", 1.0).toFloat())
            }
            val vj = j.getJSONArray("vertices"); val n = vj.length()
            val rest = FloatArray(n * 2) { k -> vj.getJSONArray(k / 2).getDouble(k % 2).toFloat() }
            val wj = j.getJSONArray("weights")
            val wB = Array(n) { i -> val w = wj.getJSONArray(i); IntArray(w.length()) { k -> w.getJSONArray(k).getInt(0) } }
            val wV = Array(n) { i -> val w = wj.getJSONArray(i); FloatArray(w.length()) { k -> w.getJSONArray(k).getDouble(1).toFloat() } }
            val tj = j.getJSONArray("triangles")
            val idx = ShortArray(tj.length() * 3) { k -> tj.getJSONArray(k / 3).getInt(k % 3).toShort() }
            RigMesh("human", 512f, 512f, bones, rest, rest.copyOf(), wB, wV, idx)
        }
        val hero = load("body_red_pants")
        sheet("hero_big", listOf("body_red_pants", "body_blue_skirt_long"), cell = 640,
            cols = listOf(poses[0], poses[2], poses[3], poses[4]))
        sheet("compare", listOf("이전(도구)", "지금(자동)", "주인공(자동)"), cell = 360,
            imgOf = { if (it.startsWith("주인공")) hero else kidImg },
            meshOf = { n, i -> if (n.startsWith("이전")) demo else RigBuilder.build(i.px, i.w, i.h) })
    }

    private val poses = listOf<Pair<String, (RigMesh) -> FloatArray?>>(
        "뼈대" to { _ -> null },
        "가만히" to { m -> poseAt(m.bones, RigMotion.IDLE, 1.3f).first },
        "손흔들기" to { m -> poseAt(m.bones, RigMotion.WAVE, 1.3f).first },
        "만세" to { m -> poseAt(m.bones, RigMotion.HOORAY, 1.0f).first },
        "걷기1" to { m -> poseAt(m.bones, RigMotion.WALK, 0.2f).first },
        "걷기2" to { m -> poseAt(m.bones, RigMotion.WALK, 0.62f).first },
    )

    private fun sheet(
        tag: String, names: List<String>, cell: Int = 220, cols: List<Pair<String, (RigMesh) -> FloatArray?>> = poses,
        imgOf: (String) -> Img = { load(it) },
        meshOf: (String, Img) -> RigMesh? = { _, i -> RigBuilder.build(i.px, i.w, i.h) },
    ) {
        val poses = cols
        val sw = cell * poses.size; val sh = cell * names.size
        val sheet = IntArray(sw * sh) { 0xFFF4EFE6.toInt() }
        val log = StringBuilder()
        names.forEachIndexed { r, n ->
            val img = imgOf(n)
            val w = img.w; val h = img.h
            val px = img.px
            val t0 = System.nanoTime()
            RigBuilder.why = ""
            val m = meshOf(n, img)
            val ms = (System.nanoTime() - t0) / 1_000_000
            log.appendLine("${r + 1}. $n: ${m?.kind ?: "없음"} · ${m?.bones?.drop(1)?.joinToString { "${it.name}(${"%.0f".format(it.restOut)}°)" }} · 점 ${m?.vertexCount} · ${ms}ms · ${RigBuilder.why}")
            poses.forEachIndexed { c, (_, f) ->
                val pic = if (m == null) px else render(px, w, h, m, f(m))
                // 칸에 줄여 넣는다 (가장 가까운 화소)
                for (y in 0 until cell) for (x in 0 until cell) {
                    val sx = x * w / cell; val sy = y * h / cell
                    val v = pic[sy * w + sx]
                    val a = v ushr 24
                    if (a == 0) continue
                    val di = (r * cell + y) * sw + c * cell + x
                    sheet[di] = blend(sheet[di], v)
                }
            }
        }
        val out = Bitmap.createBitmap(sheet, sw, sh, Bitmap.Config.ARGB_8888)
        File(outDir, "$tag.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(outDir, "$tag.txt").writeText(log.toString())
        println(log)
    }

    /** 점 자리 [pos] · 그림 좌표 [uv] 로 삼각형마다 [tex] 를 입혀 outW×outH 에 그린다 (뒤 → 앞 순, 앱의 drawVertices 와 같은 일) */
    private fun draw(tex: IntArray, tw: Int, th: Int, uv: FloatArray, pos: FloatArray, indices: ShortArray, outW: Int, outH: Int): IntArray {
        val out = IntArray(outW * outH)
        for (t in 0 until indices.size / 3) {
            val a = indices[3 * t].toInt(); val b = indices[3 * t + 1].toInt(); val c = indices[3 * t + 2].toInt()
            val x0 = pos[2 * a]; val y0 = pos[2 * a + 1]; val x1 = pos[2 * b]; val y1 = pos[2 * b + 1]; val x2 = pos[2 * c]; val y2 = pos[2 * c + 1]
            val den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
            if (kotlin.math.abs(den) < 1e-6f) continue
            val bx0 = maxOf(0, kotlin.math.floor(minOf(x0, x1, x2)).toInt()); val bx1 = minOf(outW - 1, kotlin.math.ceil(maxOf(x0, x1, x2)).toInt())
            val by0 = maxOf(0, kotlin.math.floor(minOf(y0, y1, y2)).toInt()); val by1 = minOf(outH - 1, kotlin.math.ceil(maxOf(y0, y1, y2)).toInt())
            for (y in by0..by1) for (x in bx0..bx1) {
                val fx = x + 0.5f; val fy = y + 0.5f
                val l0 = ((y1 - y2) * (fx - x2) + (x2 - x1) * (fy - y2)) / den
                val l1 = ((y2 - y0) * (fx - x2) + (x0 - x2) * (fy - y2)) / den
                val l2 = 1 - l0 - l1
                if (l0 < -0.01f || l1 < -0.01f || l2 < -0.01f) continue
                val fu = (l0 * uv[2 * a] + l1 * uv[2 * b] + l2 * uv[2 * c] - 0.5f).coerceIn(0f, tw - 1.001f)
                val fv = (l0 * uv[2 * a + 1] + l1 * uv[2 * b + 1] + l2 * uv[2 * c + 1] - 0.5f).coerceIn(0f, th - 1.001f)
                val v = bilinear(tex, tw, th, fu, fv)
                if (v ushr 24 == 0) continue
                out[y * outW + x] = if (out[y * outW + x] ushr 24 == 0) v else blend(out[y * outW + x], v)
            }
        }
        return out
    }

    /** 미리 곱한 알파로 네 화소를 섞는다 — 투명한 이웃의 검은색이 가장자리에 번지지 않게 */
    private fun bilinear(t: IntArray, tw: Int, th: Int, fu: Float, fv: Float): Int {
        val x0 = fu.toInt(); val y0 = fv.toInt(); val x1 = minOf(x0 + 1, tw - 1); val y1 = minOf(y0 + 1, th - 1)
        val dx = fu - x0; val dy = fv - y0
        var a = 0f; var r = 0f; var g = 0f; var b = 0f
        for ((p, wt) in listOf(t[y0 * tw + x0] to (1 - dx) * (1 - dy), t[y0 * tw + x1] to dx * (1 - dy),
                t[y1 * tw + x0] to (1 - dx) * dy, t[y1 * tw + x1] to dx * dy)) {
            val pa = (p ushr 24) / 255f * wt
            a += pa; r += ((p shr 16) and 255) * pa; g += ((p shr 8) and 255) * pa; b += (p and 255) * pa
        }
        if (a <= 0f) return 0
        return ((a * 255).toInt().coerceIn(0, 255) shl 24) or ((r / a).toInt().coerceIn(0, 255) shl 16) or
            ((g / a).toInt().coerceIn(0, 255) shl 8) or (b / a).toInt().coerceIn(0, 255)
    }

    private fun blend(dst: Int, src: Int): Int {
        val a = (src ushr 24) / 255f
        fun ch(sh: Int) = (((src shr sh) and 255) * a + ((dst shr sh) and 255) * (1 - a)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** 그물의 삼각형마다 그림을 입혀 그린다 — 앱의 `Canvas.drawVertices` 와 같은 일. pose 가 null 이면 뼈대를 그린다 */
    private fun render(src: IntArray, w: Int, h: Int, m: RigMesh, pose: FloatArray?): IntArray {
        if (pose == null) {
            val out = src.copyOf()
            val colors = intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFF8E24AA.toInt(),
                0xFFFB8C00.toInt(), 0xFF00ACC1.toInt(), 0xFFD81B60.toInt(), 0xFFFDD835.toInt())
            fun dot(cx: Int, cy: Int, r: Int, col: Int) {
                for (y in cy - r..cy + r) for (x in cx - r..cx + r)
                    if (x in 0 until w && y in 0 until h && (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) out[y * w + x] = col
            }
            for (i in 0 until m.vertexCount) {
                val k = m.wVal[i].indices.maxBy { m.wVal[i][it] }
                val b = m.wBone[i][k]
                if (b == 0) continue
                dot(m.rest[2 * i].toInt(), m.rest[2 * i + 1].toInt(), 3, colors[(b - 1) % colors.size])
            }
            m.bones.forEachIndexed { i, b ->
                if (i == 0) return@forEachIndexed
                if (b.parent > 0) {
                    val p = m.bones[b.parent]
                    for (s in 0..40) dot((p.px + (b.px - p.px) * s / 40f).toInt(), (p.py + (b.py - p.py) * s / 40f).toInt(), 3, 0xFF000000.toInt())
                }
                dot(b.px.toInt(), b.py.toInt(), 9, 0xFF000000.toInt())
                dot(b.px.toInt(), b.py.toInt(), 5, colors[(i - 1) % colors.size])
            }
            return out
        }
        val hideArms = pose.any { it == 999f }
        skin(m, if (hideArms) FloatArray(pose.size) else pose)
        val out = IntArray(w * h)
        val u = m.tex; val o = m.out
        val tx = m.atlas ?: src; val tw = m.atlasW
        for (t in 0 until m.triangleCount) {
            val a = m.indices[3 * t].toInt(); val b = m.indices[3 * t + 1].toInt(); val c = m.indices[3 * t + 2].toInt()
            val x0 = o[2 * a]; val y0 = o[2 * a + 1]; val x1 = o[2 * b]; val y1 = o[2 * b + 1]; val x2 = o[2 * c]; val y2 = o[2 * c + 1]
            val den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
            if (kotlin.math.abs(den) < 1e-6f) continue
            if (hideArms && m.tex[2 * a] >= w) continue
            val bx0 = maxOf(0, kotlin.math.floor(minOf(x0, x1, x2)).toInt()); val bx1 = minOf(w - 1, kotlin.math.ceil(maxOf(x0, x1, x2)).toInt())
            val by0 = maxOf(0, kotlin.math.floor(minOf(y0, y1, y2)).toInt()); val by1 = minOf(h - 1, kotlin.math.ceil(maxOf(y0, y1, y2)).toInt())
            for (y in by0..by1) for (x in bx0..bx1) {
                val fx = x + 0.5f; val fy = y + 0.5f
                val l0 = ((y1 - y2) * (fx - x2) + (x2 - x1) * (fy - y2)) / den
                val l1 = ((y2 - y0) * (fx - x2) + (x0 - x2) * (fy - y2)) / den
                val l2 = 1 - l0 - l1
                if (l0 < -0.01f || l1 < -0.01f || l2 < -0.01f) continue
                // 이중선형 — 폰의 FILTER_BITMAP 과 같게. 가장 가까운 화소만 집으면 가장자리가 계단처럼 거칠다
                val fu = (l0 * u[2 * a] + l1 * u[2 * b] + l2 * u[2 * c] - 0.5f).coerceIn(0f, tw - 1.001f)
                val fv = (l0 * u[2 * a + 1] + l1 * u[2 * b + 1] + l2 * u[2 * c + 1] - 0.5f).coerceIn(0f, h - 1.001f)
                val v = bilinear(tx, tw, h, fu, fv)
                if (v ushr 24 == 0) continue
                out[y * w + x] = if (out[y * w + x] ushr 24 == 0) v else blend(out[y * w + x], v)
            }
        }
        return out
    }
}
