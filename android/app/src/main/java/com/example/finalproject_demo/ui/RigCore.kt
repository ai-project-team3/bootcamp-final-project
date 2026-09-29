package com.example.finalproject_demo.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * 뼈대의 **계산만** 모은 파일 — 안드로이드에 기대지 않는다 (09-28).
 *
 * 그래서 JVM 검사가 실제 캐릭터 그림을 읽어 이 코드를 그대로 돌려 볼 수 있다 (`RigBuilderTest`).
 * 그리기(Bitmap · Canvas)와 뒤에서 만들어 두기(`RigCache`)는 `Rig.kt` 에 있다.
 *
 * 시연 때는 마네킹 틀의 좌표(손 · 어깨 자리)를 알고 뽑은 그림에만 뼈대를 붙였다(`tools/rig_mesh_export.py`).
 * 실제 서비스에서는 아이가 무엇을 고를지 모르니 **그림만 보고** 뼈대를 찾아야 한다 → [RigBuilder].
 */

/** 가지의 역할 — 동작 틀이 역할마다 다르게 움직인다 */
enum class LimbRole { ARM, LEG, TAIL, NECK, OTHER }

/**
 * 뼈 하나 — 부모 뼈를 따라가고, 자기 축(pivot)을 중심으로 돈다.
 *
 * @param side 몸 가운데보다 오른쪽(+1) · 왼쪽(-1). 팔을 **바깥으로** 올리는 방향이 이것으로 정해진다.
 *   목은 머리가 향한 쪽(+1 = 오른쪽)
 * @param restOut 팔이 쉬고 있을 때 **바로 아래에서 바깥으로 벌어진 각도**(도). 만세처럼 「어디까지 올린다」는
 *   동작은 이만큼을 빼고 돌린다 — 팔을 붙이고 선 아이와 벌리고 선 아이가 같은 높이까지 올라간다
 */
class Bone(
    val name: String,
    val parent: Int,
    val px: Float,
    val py: Float,
    val follow: String? = null,
    val ratio: Float = 1f,
    val role: LimbRole? = roleOf(name),
    val side: Int = if (name.startsWith("armR")) 1 else if (name.startsWith("armL")) -1 else 0,
    val restOut: Float = 35f,
)

/** 이름으로 역할 — 시연용 mesh.json 은 역할을 적어 두지 않았다 */
fun roleOf(name: String): LimbRole? = when {
    name == "root" || name.startsWith("sleeve") -> null
    name.startsWith("arm") -> LimbRole.ARM
    name.startsWith("leg") -> LimbRole.LEG
    name.startsWith("tail") -> LimbRole.TAIL
    name.startsWith("neck") -> LimbRole.NECK
    else -> LimbRole.OTHER
}

/** 그물 — 점 · 그림 좌표 · 가중치 · 삼각형. 그림(Bitmap)은 `Rig.kt` 의 `MeshRig` 가 들고 있다 */
class RigMesh(
    val kind: String,            // human · quad · blob
    val canvasW: Float,
    val canvasH: Float,
    val bones: List<Bone>,
    val rest: FloatArray,        // 점의 쉬는 자리 x0,y0,x1,y1…
    val tex: FloatArray,         // 그림 안 좌표
    val wBone: Array<IntArray>,  // 점마다 따라가는 뼈 번호들
    val wVal: Array<FloatArray>, // 그 가중치 (합 1)
    val indices: ShortArray,     // 삼각형 — 뒤 → 앞 순
    val atlas: IntArray? = null, // 그림판 — 몸에서 팔을 떼어 냈으면 [몸 층 | 팔 층] 을 나란히. null 이면 원래 그림 그대로
    val atlasW: Int = canvasW.toInt(),
) {
    val out = FloatArray(rest.size)
    val vertexCount get() = rest.size / 2
    val triangleCount get() = indices.size / 3
}

/** 동작 틀 — 역할마다 몇 줄씩만 있으면 모든 캐릭터가 같이 쓴다 */
enum class RigMotion(val label: String) {
    IDLE("가만히"),
    WAVE("손 흔들기"),
    HOORAY("만세"),
    WALK("걷기"),
    SAD("시무룩"),
    TAIL("꼬리 흔들기"),
}

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/**
 * 시간 t(초) → 뼈마다 각도(도 · **양수 = 시계 방향**)와 몸 전체의 위아래 흔들림(그림 화소).
 *
 * 팔은 「바깥으로」가 오른쪽 팔은 반시계(음수), 왼쪽 팔은 시계(양수)다 → `out = -side`.
 * 손 흔들기는 위팔을 올려 두고 **팔꿈치 아래**를 흔든다. 꼬리 · 목은 끝 마디가 반 박자 늦게 따라와 물결처럼 휜다.
 */
fun poseAt(bones: List<Bone>, motion: RigMotion, t: Float): Pair<FloatArray, Float> {
    val w = { hz: Float, ph: Float -> sin(2f * PI.toFloat() * hz * t - ph) }
    val rise = min(1f, t / 0.5f)
    // 손은 오른쪽 팔(화면 오른쪽)이 흔든다. 없으면 있는 팔이
    val waver = bones.firstOrNull { it.role == LimbRole.ARM && it.side > 0 }?.side ?: bones.firstOrNull { it.role == LimbRole.ARM }?.side
    val legs = bones.filter { it.role == LimbRole.LEG }.map { it.name }
    val human = bones.count { it.role == LimbRole.ARM && it.parent == 0 } >= 2
    val ang = FloatArray(bones.size)
    bones.forEachIndexed { i, b ->
        val fore = b.parent > 0 && bones[b.parent].role == b.role
        val phase = (i % 3) * 0.9f
        ang[i] = when (b.role) {
            LimbRole.ARM -> {
                val out = -b.side.toFloat()
                // 마디 — 위팔(어깨) · 아래팔(팔꿈치) · 손(손목). 사람 팔처럼 마디마다 따로 굽는다 (09-28)
                val hand = b.name.endsWith("_hand")
                val swing = w(1.2f, if (b.side > 0) 0f else PI.toFloat())
                when {
                    motion == RigMotion.WAVE && b.side == waver -> when {
                        // 위팔을 옆으로 들고 · 아래팔을 세우고 · **손목으로** 흔든다
                        hand -> out * 22f * w(1.8f, 0.6f) * rise
                        fore -> out * (45f + 18f * w(1.8f, 0f)) * rise
                        else -> out * max(0f, 105f - b.restOut) * rise
                    }
                    motion == RigMotion.HOORAY -> when {
                        hand -> out * 8f * w(2f, 0.5f) * rise
                        fore -> out * 15f * rise
                        else -> out * max(0f, 145f - b.restOut) * rise
                    }
                    // 걷기 — 어깨에서 흔들고 팔꿈치가 따라 굽는다 (반 박자 늦게)
                    motion == RigMotion.WALK -> when {
                        hand -> 0f
                        fore -> out * (8f + 8f * w(1.2f, if (b.side > 0) 0.6f else PI.toFloat() + 0.6f))
                        else -> out * (6f + 6f * swing)
                    }
                    motion == RigMotion.SAD -> if (hand || fore) -out * 4f * rise else 0f
                    else -> if (hand) out * 2f * w(0.5f, 1.2f) else if (fore) out * 2f * w(0.5f, 0.6f) else out * 3f * (1f + w(0.5f, 0f)) / 2f
                }
            }
            LimbRole.LEG -> {
                val k = legs.indexOf(b.name)
                val alt = if (k % 2 == 0) 1f else -1f
                if (motion == RigMotion.WALK) alt * (if (human) 7f else 14f) * w(1.2f, 0f) else 0f
            }
            LimbRole.TAIL -> {
                val (a1, a2, hz) = when (motion) {
                    RigMotion.WALK -> Triple(5f, 9f, 2.4f)
                    RigMotion.SAD -> Triple(2f, 3f, 0.5f)
                    RigMotion.IDLE -> Triple(4f, 7f, 1.0f)
                    else -> Triple(8f, 14f, 2.2f)       // 반가움 — 꼬리를 빨리 흔든다
                }
                if (fore) a2 * w(hz, 0.9f) else a1 * w(hz, 0f)
            }
            LimbRole.NECK -> {
                val s = b.side.toFloat()
                when (motion) {
                    RigMotion.SAD -> if (fore) s * 6f * rise else s * 10f * rise
                    RigMotion.WALK -> (if (fore) 3f else 2f) * w(1.2f, if (fore) 0.8f else 0f)
                    RigMotion.HOORAY, RigMotion.WAVE, RigMotion.TAIL -> (if (fore) 6f else 4f) * w(1.6f, if (fore) 0.8f else 0f)
                    RigMotion.IDLE -> (if (fore) 4f else 3f) * w(0.5f, if (fore) 0.8f else 0f)
                }
            }
            LimbRole.OTHER -> when (motion) {
                RigMotion.SAD -> 0f
                RigMotion.HOORAY, RigMotion.WAVE, RigMotion.TAIL -> 7f * w(2.2f, phase)
                else -> 4f * w(1.1f, phase)
            }
            null -> 0f
        }
    }
    val bob = when (motion) {
        RigMotion.IDLE -> 1.5f * w(0.5f, 0f)
        RigMotion.WALK -> -3f * abs(w(1.2f, 0f))
        RigMotion.HOORAY -> -6f * abs(w(1.5f, 0f)) * rise
        else -> 0f
    }
    // 뼈 이름으로 적힌 각도는 캔버스 512 기준으로 잡았다 — 흔들림만 캔버스 크기에 맞춘다
    return ang to bob
}

/** 뼈 각도 — 다른 뼈를 따라가는 뼈(소매)는 그 뼈의 각도 × 비율 */
private fun resolved(bones: List<Bone>, raw: FloatArray): FloatArray = FloatArray(bones.size) { i ->
    val f = bones[i].follow
    if (f == null) raw[i] else {
        val j = bones.indexOfFirst { it.name == f }
        if (j >= 0) raw[j] * bones[i].ratio else 0f
    }
}

/** 점 (x, y) 를 축 (px, py) 중심으로 deg 만큼 돌린 자리 — y 가 아래로 자라는 화면 좌표라 양수가 시계 방향 */
private inline fun rot(x: Float, y: Float, px: Float, py: Float, deg: Float, out: (Float, Float) -> Unit) {
    val th = deg * PI.toFloat() / 180f
    val cs = cos(th); val sn = sin(th)
    val dx = x - px; val dy = y - py
    out(px + cs * dx - sn * dy, py + sn * dx + cs * dy)
}

/**
 * 가중치대로 점을 옮긴다 — **각도를 섞어서 마디마다 한 번씩 돌린다** (09-28 7차 · 세 마디로 늘림). 결과는 `mesh.out`.
 *
 * 선형 블렌드 스키닝(뼈마다 따로 돌린 자리를 직선으로 섞기)은 다른 각도로 돈 두 자리의 가운데가 호 안쪽으로 떨어져
 * 휘는 곳이 **가늘어지며 비틀렸다**(사탕 껍질). 같은 축을 도는 뼈들은 각도를 먼저 섞고 한 번만 돌리면 두께가 그대로다.
 *
 *   1단 (어깨 · 엉덩이 · 꼬리 뿌리)  θ1 = Σ 가중치 × (그 뼈의 1단 조상 각도) → 1단 축으로 θ1 회전
 *   2단 (팔꿈치)                     옮겨진 팔꿈치를 축으로, 2단 각도 × (사슬에서 2단 이하 가중치 몫)
 *   3단 (손목)                       옮겨진 손목을 축으로, 3단 각도 × (3단 이하 몫)
 * 두 마디까지는 예전 계산과 같다.
 */
fun skin(mesh: RigMesh, raw: FloatArray) {
    val bones = mesh.bones
    val ang = resolved(bones, raw)
    val nb = bones.size
    val level = IntArray(nb); val anc1 = IntArray(nb)
    for (b in 0 until nb) {
        var lv = 0; var c = b; var top = b
        while (bones[c].parent >= 0) { lv++; top = c; c = bones[c].parent }
        level[b] = lv; anc1[b] = top
    }
    val r = mesh.rest; val o = mesh.out
    val chain = IntArray(8); val px = FloatArray(8); val py = FloatArray(8); val pa = FloatArray(8)
    for (i in 0 until r.size / 2) {
        var x = r[2 * i]; var y = r[2 * i + 1]
        val bs = mesh.wBone[i]; val ws = mesh.wVal[i]
        var th1 = 0f; var deep = -1
        for (k in bs.indices) {
            val b = bs[k]; if (bones[b].parent < 0) continue
            th1 += ws[k] * ang[anc1[b]]
            if (ws[k] > 0f && (deep < 0 || level[b] > level[deep])) deep = b
        }
        if (deep < 0) { o[2 * i] = x; o[2 * i + 1] = y; continue }
        // 사슬 — 1단 조상부터 가장 깊은 뼈까지
        var n = level[deep]; var c = deep
        for (j in n - 1 downTo 0) { chain[j] = c; c = bones[c].parent }
        // 사슬에 든 뼈들의 가중치 합 (소매처럼 사슬 밖 1단 뼈는 뺀다 — 예전 계산과 같게)
        var wChain = 0f
        for (k in bs.indices) for (j in 0 until n) if (bs[k] == chain[j]) wChain += ws[k]
        var ops = 0
        for (j in 0 until n) {
            val b = chain[j]
            val deg = if (j == 0) th1 else {
                var wGe = 0f
                for (k in bs.indices) for (q in j until n) if (bs[k] == chain[q]) wGe += ws[k]
                if (wChain > 0f) wGe / wChain * ang[b] else 0f
            }
            // 이 마디의 축 — 앞 마디들이 돌린 만큼 옮긴다
            var ax = bones[b].px; var ay = bones[b].py
            for (q in 0 until ops) rot(ax, ay, px[q], py[q], pa[q]) { nx, ny -> ax = nx; ay = ny }
            rot(x, y, ax, ay, deg) { nx, ny -> x = nx; y = ny }
            px[ops] = ax; py[ops] = ay; pa[ops] = deg; ops++
        }
        o[2 * i] = x; o[2 * i + 1] = y
    }
}

/**
 * **그림만 보고** 뼈대를 붙인다 (09-28) — 틀도 좌표표도 없이.
 *
 * ```
 * 가. 튀어나온 가지 찾기 (팔 · 다리 · 꼬리 · 목이 몸과 떨어져 있는 그림)
 *   1. 알파로 윤곽을 딴다 (긴 변 ~180 화소로 줄여서 — 폰에서 빠르게)
 *   2. 안쪽 거리(가장자리까지 몇 화소인가)를 잰다. 가장 두꺼운 곳(머리 · 몸통)의 절반보다 두꺼운 곳만 남겨
 *      다시 부풀리면 **몸통**이 된다. 가는 것(팔 · 다리 · 꼬리 · 목)은 사라진다 — 모폴로지 열기
 *   3. 윤곽 − 몸통 = **튀어나온 가지**. 뿌리 = 몸통에 붙은 자리 · 끝 = 뿌리에서 가지 안으로 가장 멀리 간 곳
 *   4. 뻗은 방향으로 역할 — 아래로 · 몸 가운데 높이 → 팔 / 아래로 · 몸 아래 끝 → 다리 / 위로 길게 → 목 / 옆으로 → 꼬리
 *   5. 그물(긴 변 64칸)을 씌우고 점마다 가중치 — 가지 뿌리 쪽 20% 는 서서히, 가운데에서 한 번 더 굽는다
 *
 * 나. 사람형 팔 — 팔이 몸에서 떨어진 A-포즈든, 몸에 붙이고 섰든(앱의 주인공 27장) **같은 길** (09-28 다시)
 *   1. 몸과 떨어진 바깥 구간(팔 · 손)과, 그 줄에서 몸의 바깥 끝 = **팔 뿌리 선**을 찾는다
 *   2. 목 아래 · 팔 뿌리 선 바깥 = 어깨 · 소매 · 팔 → **팔 층**으로 떼어 낸다.
 *      팔이 가리던 몸 자리는 옆 셔츠 무늬를 거울처럼 비춰 메운다 = **몸 층** (안 메우면 팔을 들 때 구멍이 난다)
 *   3. 뼈 자리 · 소매 끝 · 가중치 · 그리는 순서는 **시연 도구(`rig_mesh_export.py`)와 똑같이** — 사용자가 확인한 것
 *      ⚠️ 처음(09-28 오후)에는 팔이 몸통에서 갈라지는 곳(소매 끝)을 어깨로 잡았다. 만세를 하면 소매는 그대로이고
 *      소매 끝에서 가는 팔이 꺾여 나왔다 (사용자: *"전체적으로 뼈대가 이상하게 잡혔다"*)
 *
 * 가지도 팔도 못 찾으면 null — 부르는 쪽이 지금처럼 그림 한 장으로 그린다.
 * 앉은 곰의 다리처럼 몸에 붙은 데다 색도 같은 부위는 떼어 낼 수 없어 몸과 같이 움직인다.
 */
object RigBuilder {
    const val GRID = 64
    /** 실험 — 소매를 셔츠에 두고 맨살 팔만 돌리기. 사용자: *"소매를 그대로 두라는 게 아니다"* → 끈다 */
    private const val KEEP_SLEEVE = false
    private const val ANALYSIS = 180

    class Limb(
        val pixels: IntArray,
        val geo: FloatArray,          // 분석 화소 → 뿌리에서 가지 안으로 간 거리 (가지 밖은 -1)
        val length: Float,
        val baseX: Float, val baseY: Float,
        val tipX: Float, val tipY: Float,
        val midX: Float, val midY: Float,
    ) {
        var role: LimbRole = LimbRole.OTHER
        var side = 0
        var bones = IntArray(0)       // 1 개(위) 또는 2 개(위 · 아래)
    }

    /**
     * 몸에서 떼어 낸 팔 하나 (전체 그림 좌표). 뼈 자리 · 소매 끝은 시연 도구(`rig_mesh_export.py` `human()`)와 같은 규칙
     *
     * @param region 팔 층에 들어가는 화소 (어깨 윗면 · 소매 · 맨살 팔 · 손)
     * @param hemT 소매 끝 — 어깨 축에서 손끝까지의 몇 할인가
     */
    class ArmLayer(
        val side: Int, val region: BooleanArray,
        val px: Float, val py: Float, val tipX: Float, val tipY: Float, val hemT: Float,
        /** 팔이 몸에 붙은 그림 — 소매 속 위팔을 채운다: [팔 폭 왼쪽 끝, 오른쪽 끝, 살색을 빌려 올 줄(t)]. null 이면 소매째 든다 */
        val inner: FloatArray? = null,
    )

    /** 팔 떼어 내기가 어디서 멈췄나 — 검사가 본다 */
    @Volatile var why = ""

    /** 그물 한 벌 — 몸 · 팔마다 따로 만들어 합친다 */
    private class Part(
        val kind: String,
        val bones: List<Bone>,        // 0 번은 root
        val rest: FloatArray,
        val wB: Array<IntArray>,
        val wV: Array<FloatArray>,
        val tris: List<IntArray>,     // 뒤 → 앞
        val texDx: Float,             // 그림판(atlas)에서 이 그물의 그림이 놓인 가로 자리
        val front: List<IntArray> = emptyList(),   // **몸보다 앞에** 그릴 삼각형 (소매)
        val tex: FloatArray? = null,  // 점마다 그림 좌표가 제자리와 다를 때 (소매 속 위팔은 소매 끝 살색을 늘여 쓴다)
    )

    fun build(input: IntArray, w: Int, h: Int): RigMesh? {
        val argb = cleanHalo(input)
        // 사람형(팔 둘 · 손이 몸 아래쪽 절반)이면 팔을 떼어 낸다 — 팔이 몸에서 떨어진 A-포즈든, 붙어 있든 같은 길
        attachedArms(argb, w, h)?.let { (arms, under) ->
            // 팔을 떼어 낸 몸으로 다시 — 다리 · 꼬리 같은 가지는 그대로 찾는다. 손은 이미 없다
            val body = bodyPart(under, w, h, allowArms = false) ?: rootOnly(under, w, h)
            val armOnly = IntArray(w * h)
            // 거의 투명한 가장자리(불투명도 60 미만)는 팔 층에 넣지 않는다 — 어깨에서 늘어나면 옅은 선 조각으로 남는다
            for (a in arms) for (i in a.region.indices) if (a.region[i] && (argb[i] ushr 24) >= 60) armOnly[i] = argb[i]
            val atlas = IntArray(2 * w * h)
            for (y in 0 until h) {
                System.arraycopy(under, y * w, atlas, y * 2 * w, w)
                System.arraycopy(armOnly, y * w, atlas, y * 2 * w + w, w)
            }
            // 그리는 순서 (시연 도구와 같다): 맨살 팔 → 몸 → 소매. 팔이 몸 뒤로 들어가고, 소매가 어깨 이음매를 덮는다
            return merge(w, h, arms.map { armPart(it, argb, w, h) } + body, atlas, kind = "human")
        }
        return bodyPart(argb, w, h, allowArms = true)?.let { merge(w, h, listOf(it), null) }
    }

    /**
     * 배경을 지울 때 남은 **회색 반투명 그림자**를 지운다 — 시연 도구의 `clean_halo` 와 같다 (09-28 6차).
     * 팔 옆에 이게 남아 있으면 팔을 들 때 옅은 선 조각이 제자리에 남는다. 색이 있는 가장자리(살 · 셔츠)는 그대로
     */
    private fun cleanHalo(src: IntArray): IntArray = IntArray(src.size) { i ->
        val c = src[i]; val a = c ushr 24
        val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
        if (a in 1..219 && maxOf(r, g, b) - minOf(r, g, b) < 28) c and 0x00FFFFFF else c
    }

    /** 뼈 · 점 · 삼각형을 이어 붙인다. 뼈 번호는 root 를 하나로 합치고 나머지를 차례로 민다. 앞 삼각형은 맨 뒤에 */
    private fun merge(w: Int, h: Int, parts: List<Part>, atlas: IntArray?, kind: String? = null): RigMesh? {
        val bones = arrayListOf(Bone("root", -1, 0f, 0f))
        val rest = ArrayList<Float>(); val tex = ArrayList<Float>()
        val wB = ArrayList<IntArray>(); val wV = ArrayList<FloatArray>()
        val idx = ArrayList<Short>(); val frontIdx = ArrayList<Short>()
        for (p in parts) {
            val boneMap = IntArray(p.bones.size) { 0 }
            for (b in 1 until p.bones.size) {
                boneMap[b] = bones.size
                val src = p.bones[b]
                bones += Bone(src.name, if (src.parent <= 0) 0 else boneMap[src.parent], src.px, src.py, src.follow, src.ratio,
                    src.role, src.side, src.restOut)
            }
            val v0 = rest.size / 2
            for (k in 0 until p.rest.size / 2) {
                rest += p.rest[2 * k]; rest += p.rest[2 * k + 1]
                val src = p.tex ?: p.rest
                tex += src[2 * k] + p.texDx; tex += src[2 * k + 1]
                wB += IntArray(p.wB[k].size) { boneMap[p.wB[k][it]] }; wV += p.wV[k]
            }
            if (rest.size / 2 > Short.MAX_VALUE) return null
            for (t in p.tris) for (q in t) idx += (v0 + q).toShort()
            for (t in p.front) for (q in t) frontIdx += (v0 + q).toShort()
        }
        idx += frontIdx
        return RigMesh(kind ?: parts.last().kind, w.toFloat(), h.toFloat(), bones, rest.toFloatArray(), tex.toFloatArray(),
            wB.toTypedArray(), wV.toTypedArray(), idx.toShortArray(), atlas, if (atlas != null) 2 * w else w)
    }

    // ── 가. 튀어나온 가지 ─────────────────────────────────────────

    private fun bodyPart(argb: IntArray, w: Int, h: Int, allowArms: Boolean): Part? {
        val f = max(1, ceil(max(w, h) / ANALYSIS.toDouble()).toInt())
        val aw = w / f; val ah = h / f
        val n = aw * ah
        // 1. 윤곽 — 칸 안 알파 평균이 절반 넘으면 안
        val mask = BooleanArray(n)
        val any = BooleanArray(n)
        for (y in 0 until ah) for (x in 0 until aw) {
            var s = 0; var mx = 0
            for (dy in 0 until f) for (dx in 0 until f) {
                val a = argb[(y * f + dy) * w + x * f + dx] ushr 24
                s += a; if (a > mx) mx = a
            }
            mask[y * aw + x] = s > 127 * f * f
            any[y * aw + x] = mx > 8
        }
        keepLargest(mask, aw, ah)
        val area = mask.count { it }
        if (area < 300) return null
        var my0 = ah; var my1 = 0
        for (i in 0 until n) if (mask[i]) { val y = i / aw; if (y < my0) my0 = y; if (y > my1) my1 = y }
        val mH = (my1 - my0 + 1).toFloat()

        // 2. 몸통 = 두꺼운 곳만 남겨 다시 부풀리기
        val din = chamfer(aw, ah, edge = true) { !mask[it] }
        val dMax = din.max()
        val r = 0.45f * dMax
        val seeds = BooleanArray(n) { din[it] > r }
        // 씨앗 덩어리 중 **충분히 두꺼운 것**(머리 · 몸통)만 몸통으로. 목 끝의 작은 머리는 가지에 딸린다
        val (sLab, sCount) = label(seeds, aw, ah)
        val keep = BooleanArray(sCount + 1)
        for (i in 0 until n) if (sLab[i] > 0 && din[i] >= 0.75f * dMax) keep[sLab[i]] = true
        val dSeed = chamfer(aw, ah, edge = false) { sLab[it] > 0 && keep[sLab[it]] }
        val core = BooleanArray(n) { mask[it] && dSeed[it] <= r + 1.5f }

        // 3. 가지
        val prot = BooleanArray(n) { mask[it] && !core[it] }
        val (pLab, pCount) = label(prot, aw, ah)
        var cx0 = aw; var cx1 = 0; var cy0 = ah; var cy1 = 0; var csx = 0.0; var cn = 0
        for (i in 0 until n) if (core[i]) {
            val x = i % aw; val y = i / aw
            cx0 = min(cx0, x); cx1 = max(cx1, x); cy0 = min(cy0, y); cy1 = max(cy1, y); csx += x; cn++
        }
        if (cn == 0) return null
        val ccx = (csx / cn).toFloat()
        val coreW = (cx1 - cx0 + 1).toFloat(); val coreH = (cy1 - cy0 + 1).toFloat()

        val limbs = ArrayList<Limb>()
        for (c in 1..pCount) {
            val px = (0 until n).filter { pLab[it] == c }.toIntArray()
            if (px.size < area * 0.006f) continue
            limbOf(px, pLab, c, core, aw, ah)?.let { if (it.length >= 0.09f * mH) limbs += it }
        }

        // 4. 역할
        for (l in limbs) {
            val dx = l.tipX - l.baseX; val dy = l.tipY - l.baseY
            val d = max(1e-3f, hypot(dx, dy))
            val ux = dx / d; val uy = dy / d
            val yb = (l.baseY - cy0) / coreH
            l.side = if (l.tipX >= ccx) 1 else -1
            l.role = when {
                uy > 0.5f && yb > 0.72f -> LimbRole.LEG
                uy > 0.5f && yb >= 0.3f -> LimbRole.ARM
                uy < -0.5f && l.length >= 0.22f * mH -> LimbRole.NECK
                // 꼬리 길이는 키가 아니라 **몸통 폭**에 견준다 — 목이 긴 공룡은 키가 커서 꼬리가 짧게 재진다
                abs(ux) >= 0.5f && l.length >= 0.3f * coreW && abs(l.baseX - ccx) > 0.2f * coreW -> LimbRole.TAIL
                else -> LimbRole.OTHER
            }
        }
        // 양쪽에 같은 높이로 옆으로 뻗은 것 한 쌍 → 팔 (옆으로 벌린 로봇 팔)
        val tails = limbs.filter { it.role == LimbRole.TAIL }
        for (a in tails) for (b in tails) if (a !== b && a.side != b.side && abs(a.baseY - b.baseY) < 0.15f * coreH) {
            a.role = LimbRole.ARM; b.role = LimbRole.ARM
        }
        // 팔은 쪽마다 가장 긴 것 하나, 꼬리 · 목은 하나
        for (side in listOf(-1, 1)) limbs.filter { it.role == LimbRole.ARM && it.side == side }
            .sortedByDescending { it.length }.drop(1).forEach { it.role = LimbRole.OTHER }
        for (role in listOf(LimbRole.TAIL, LimbRole.NECK)) limbs.filter { it.role == role }
            .sortedByDescending { it.length }.drop(1).forEach { it.role = LimbRole.OTHER }
        val arms = limbs.filter { it.role == LimbRole.ARM }
        val human = allowArms && arms.size == 2 && arms[0].side != arms[1].side
        if (!human) arms.forEach { it.role = if (it.length >= 0.18f * mH) LimbRole.TAIL else LimbRole.OTHER }
        limbs.filter { it.role == LimbRole.TAIL }.sortedByDescending { it.length }.drop(1).forEach { it.role = LimbRole.OTHER }
        // 사람 다리는 두 개로 **나뉘어 있을 때만** — 한 덩어리면 걸을 수 없다
        val legs = limbs.filter { it.role == LimbRole.LEG }
        val twoLegged = human || !allowArms
        if (twoLegged && legs.size != 2) legs.forEach { it.role = LimbRole.OTHER }
        val kind = when {
            human -> "human"
            !allowArms -> "human"
            limbs.count { it.role == LimbRole.LEG } >= 2 -> "quad"
            else -> "blob"
        }
        // 한 덩어리 몸의 「다리」 한 개는 흔들지 않는다 — 몸에 붙은 채 휘면 이상하다
        if (kind == "blob") limbs.filter { it.role == LimbRole.LEG }.forEach { it.role = LimbRole.OTHER }
        if (!allowArms) {
            // 팔을 떼어 낸 사람의 몸 — **다리 둘만** 움직인다 (09-28 전수 확인).
            //   머리 방울 · 긴 머리 끝을 흔들면 옅은 복제본이 생겼다 → 흔들지 않는다
            //   다리는 좌우로 나뉘어 **같은 높이에서** 시작할 때만 — 긴바지 가운데에 비스듬한 다리가 잡혀 바지가 어긋났다
            limbs.removeAll { it.role != LimbRole.LEG }
            val lg = limbs.sortedBy { it.baseX }
            val ok = lg.size == 2 && abs(lg[0].baseY - lg[1].baseY) < 0.05f * mH &&
                lg[0].baseX < ccx && lg[1].baseX > ccx && lg.all { it.length <= 0.4f * mH }
            if (!ok) limbs.clear()
        }
        if (limbs.isEmpty()) return null

        // 뼈 만들기 (전체 그림 좌표)
        val bones = arrayListOf(Bone("root", -1, 0f, 0f))
        val ff = f.toFloat()
        var extI = 0
        val sortedLegs = limbs.filter { it.role == LimbRole.LEG }.sortedBy { it.baseX }
        for (l in limbs.sortedBy { it.role.ordinal }) {
            val dx = l.tipX - l.baseX; val dy = l.tipY - l.baseY
            val d = max(1e-3f, hypot(dx, dy))
            // 축은 뿌리에서 몸 안쪽으로 조금 — 어깨 관절은 소매 끝이 아니라 몸 안에 있다
            val pvx = (l.baseX - dx / d * 0.3f * r + 0.5f) * ff
            val pvy = (l.baseY - dy / d * 0.3f * r + 0.5f) * ff
            val restOut = (atan2(dx * l.side, dy) * 180f / PI.toFloat())
            val two = l.role in setOf(LimbRole.ARM, LimbRole.TAIL, LimbRole.NECK) && l.length >= 0.18f * mH
            val base = when (l.role) {
                LimbRole.ARM -> if (l.side > 0) "armR" else "armL"
                LimbRole.LEG -> "leg${sortedLegs.indexOf(l)}"
                LimbRole.TAIL -> "tail"
                LimbRole.NECK -> "neck"
                LimbRole.OTHER -> "ext${extI++}"
            }
            val up = bones.size
            val (n1, n2) = when (l.role) {
                LimbRole.ARM -> "${base}_up" to "${base}_fore"
                LimbRole.TAIL, LimbRole.NECK -> "${base}1" to "${base}2"
                else -> base to "${base}_2"
            }
            bones += Bone(n1, 0, pvx, pvy, role = l.role, side = l.side, restOut = restOut)
            l.bones = if (two) {
                bones += Bone(n2, up, (l.midX + 0.5f) * ff, (l.midY + 0.5f) * ff, role = l.role, side = l.side, restOut = restOut)
                intArrayOf(up, up + 1)
            } else intArrayOf(up)
        }

        // 5. 그물
        val step = ceil(max(w, h) / GRID.toDouble()).toInt()
        val cover = dilate(any, aw, ah, ceil(step / ff).toInt() + 1)
        val (rest, tris) = gridOver(w, h, step) { x, y ->
            val ax = (x / ff).toInt(); val ay = (y / ff).toInt()
            ax in 0 until aw && ay in 0 until ah && cover[ay * aw + ax]
        } ?: return null
        val m = rest.size / 2

        // 가중치 — 점이 놓인 분석 화소가 어느 가지인가. 투명한 틈의 점은 **더 가까운 쪽**(가지 · 몸통)을 따라간다
        val limbOfLabel = HashMap<Int, Limb>()
        for (l in limbs) limbOfLabel[pLab[l.pixels[0]]] = l
        val search = ceil(1.5f * step / ff).toInt()
        val wB = arrayOfNulls<IntArray>(m); val wV = arrayOfNulls<FloatArray>(m)
        val rootW = FloatArray(m)
        for (k in 0 until m) {
            val ax = (rest[2 * k] / ff).toInt().coerceIn(0, aw - 1); val ay = (rest[2 * k + 1] / ff).toInt().coerceIn(0, ah - 1)
            var hit = -1
            val i0 = ay * aw + ax
            if (mask[i0]) hit = if (pLab[i0] > 0) i0 else -2
            else {
                var bl = Int.MAX_VALUE; var bc = Int.MAX_VALUE; var li = -1
                for (dy in -search..search) for (dx in -search..search) {
                    val qx = ax + dx; val qy = ay + dy
                    if (qx !in 0 until aw || qy !in 0 until ah) continue
                    val qi = qy * aw + qx; if (!mask[qi]) continue
                    val dd = dx * dx + dy * dy
                    if (pLab[qi] > 0 && limbOfLabel.containsKey(pLab[qi])) { if (dd < bl) { bl = dd; li = qi } } else if (dd < bc) bc = dd
                }
                if (li >= 0 && bl <= bc) hit = li
            }
            val l = if (hit >= 0) limbOfLabel[pLab[hit]] else null
            if (l == null) {
                wB[k] = intArrayOf(0); wV[k] = floatArrayOf(1f); rootW[k] = 1f; continue
            }
            val t = (l.geo[hit] / l.length).coerceIn(0f, 1f)
            val s0 = smooth(0f, 0.2f, t)
            if (l.bones.size == 2) {
                val e = smooth(0.42f, 0.58f, t)
                wB[k] = intArrayOf(0, l.bones[0], l.bones[1]); wV[k] = floatArrayOf(1 - s0, s0 * (1 - e), s0 * e)
            } else {
                wB[k] = intArrayOf(0, l.bones[0]); wV[k] = floatArrayOf(1 - s0, s0)
            }
            rootW[k] = 1 - s0
        }
        // 뒤 → 앞: 가지(몸 가중치가 작은 삼각형)를 먼저 그려 몸 뒤로 — 관절 이음매를 몸이 덮는다
        val order = tris.sortedBy { tr -> (rootW[tr[0]] + rootW[tr[1]] + rootW[tr[2]]) / 3f }
        return Part(kind, bones, rest, Array(m) { wB[it]!! }, Array(m) { wV[it]!! }, order, 0f)
    }

    /** 가지가 하나도 없는 몸 — 뼈는 root 하나. 몸 층을 그리는 데만 쓴다 */
    private fun rootOnly(argb: IntArray, w: Int, h: Int): Part {
        val step = ceil(max(w, h) / GRID.toDouble()).toInt()
        val (rest, tris) = gridOver(w, h, step) { x, y ->
            val xi = x.toInt(); val yi = y.toInt()
            var hit = false
            for (dy in -step..step step 2) for (dx in -step..step step 2) {
                val qx = xi + dx; val qy = yi + dy
                if (qx in 0 until w && qy in 0 until h && (argb[qy * w + qx] ushr 24) > 8) { hit = true; break }
            }
            hit
        }!!
        val m = rest.size / 2
        return Part("human", listOf(Bone("root", -1, 0f, 0f)), rest, Array(m) { intArrayOf(0) }, Array(m) { floatArrayOf(1f) }, tris, 0f)
    }

    /** 격자 그물 — [inside] 가 참인 곳을 덮는 삼각형만. 쓰는 점만 남긴다 */
    private fun gridOver(w: Int, h: Int, step: Int, inside: (Float, Float) -> Boolean): Pair<FloatArray, List<IntArray>>? {
        val cols = w / step + 2; val rows = h / step + 2
        val used = BooleanArray(rows * cols)
        val raw = ArrayList<IntArray>()
        for (rr in 0 until rows - 1) for (cc in 0 until cols - 1) {
            val x = (cc * step).toFloat(); val y = (rr * step).toFloat()
            val corners = listOf(x to y, x + step to y, x to y + step, x + step to y + step)
            if (inside(x + step / 2f, y + step / 2f) || corners.any { (a, b) -> inside(a, b) }) {
                val i = rr * cols + cc
                raw += intArrayOf(i, i + 1, i + cols); raw += intArrayOf(i + 1, i + cols + 1, i + cols)
                used[i] = true; used[i + 1] = true; used[i + cols] = true; used[i + cols + 1] = true
            }
        }
        val remap = IntArray(rows * cols) { -1 }
        var m = 0
        for (i in used.indices) if (used[i]) remap[i] = m++
        if (m == 0 || m > Short.MAX_VALUE) return null
        val rest = FloatArray(m * 2)
        for (i in used.indices) if (used[i]) { rest[2 * remap[i]] = ((i % cols) * step).toFloat(); rest[2 * remap[i] + 1] = ((i / cols) * step).toFloat() }
        return rest to raw.map { t -> IntArray(3) { remap[t[it]] } }
    }

    // ── 나. 몸에 붙은 팔 떼어 내기 ─────────────────────────────────

    /** 한 줄에서 불투명한 구간들 [시작, 끝] */
    private fun runs(argb: IntArray, w: Int, y: Int): List<IntArray> {
        val out = ArrayList<IntArray>(); var x = 0
        while (x < w) {
            if ((argb[y * w + x] ushr 24) > 128) {
                val s = x
                while (x < w && (argb[y * w + x] ushr 24) > 128) x++
                out += intArrayOf(s, x - 1)
            } else x++
        }
        return out
    }

    private fun cdist(a: Int, r: Float, g: Float, b: Float): Float {
        val dr = ((a shr 16) and 255) - r; val dg = ((a shr 8) and 255) - g; val db = (a and 255) - b
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    /**
     * 색조 거리 — 밝기보다 **빛깔**(빨강-초록 · 초록-파랑 차이)을 크게 본다.
     * 살색과 노란 셔츠는 밝기 · 빨강이 비슷해 RGB 거리로는 붙어 있다. 파랑 성분(초록-파랑 차이)이 크게 다르다.
     * 그늘진 살은 밝기만 떨어지므로 밝기는 1/3 만 센다
     */
    private fun tone(a: Int, r: Float, g: Float, b: Float): Float {
        val ar = ((a shr 16) and 255).toFloat(); val ag = ((a shr 8) and 255).toFloat(); val ab = (a and 255).toFloat()
        val d1 = (ar - ag) - (r - g); val d2 = (ag - ab) - (g - b)
        val dl = ((ar + ag + ab) - (r + g + b)) / 3f / 3f
        return kotlin.math.sqrt(d1 * d1 + d2 * d2 + dl * dl)
    }

    private fun cdist(a: Int, c: Int) = cdist(a, ((c shr 16) and 255).toFloat(), ((c shr 8) and 255).toFloat(), (c and 255).toFloat())

    /**
     * 사람형 팔 둘을 찾아 **팔 층**으로 떼어 낸다. 둘 다 찾으면 (팔들, 팔을 지우고 메운 몸 그림), 아니면 null.
     *
     * 뼈 자리는 시연 도구(`tools/rig_mesh_export.py` `human()`)의 규칙을 그대로 옮겼다 — 사용자가 실물 폰에서
     * *"이제 잘 나오는 것 같다"* 고 한 것이다(09-28 7차). 그 도구는 마네킹 틀의 좌표(팔 상자 · 손 자리)를 알았다.
     * 여기서는 그 두 가지를 **그림에서** 찾는다:
     *
     *   손 · 팔 = 한 줄에 구간이 셋(팔 · 몸 · 팔)인 줄들의 바깥 구간. A-포즈면 아래팔 전체, 팔을 붙이고 섰으면 손만 잡힌다
     *   팔 뿌리 선(edge) = 그 줄들에서 가운데 구간(몸)의 바깥 끝. 이 선 바깥이 어깨 · 소매 · 팔이다
     *
     * 뒤는 도구와 같다 (길이는 512 캔버스 기준 값을 그림 크기에 맞춘다):
     *   어깨 축 = 팔 뿌리 세로줄(edge 바깥 10화소)에서 팔이 차지한 높이 h 의 **위쪽 40%**, 몸 안쪽으로 max(20, 0.55h) × 0.25
     *   손끝 = 팔에서 축으로부터 가장 먼 곳(끝 12화소)의 평균
     *   소매 끝 = 팔 축을 따라가며 **살색 비율이 60% 를 넘는 첫 자리** (살색 = 손 쪽 25% 의 평균 색)
     *   팔꿈치 = (소매 끝 + 0.8) / 2 — 드러난 팔(소매 끝 ~ 손목)의 한가운데
     */
    private fun attachedArms(argb: IntArray, w: Int, h: Int): Pair<List<ArmLayer>, IntArray>? {
        val n = w * h
        val sc = w / 512f
        var by0 = h; var by1 = 0; var sx = 0.0; var cnt = 0
        for (i in 0 until n) if ((argb[i] ushr 24) > 128) { val y = i / w; by0 = min(by0, y); by1 = max(by1, y); sx += i % w; cnt++ }
        if (cnt == 0) run { why = "R1"; return null }
        val cx = (sx / cnt).toFloat(); val bh = (by1 - by0 + 1).toFloat()
        val arms = ArrayList<ArmLayer>()
        val under = argb.copyOf()
        val hands = ArrayList<IntArray>()        // 쪽마다 [팔 아래 끝, 떨어진 화소 수]
        // 머리카락 색 — 긴 머리가 어깨까지 내려와도 팔과 같이 들리면 안 된다
        var hr = 0f; var hg = 0f; var hb = 0f; var hn = 0
        for (y in by0 + 2..min(h - 1, by0 + 12)) for (x in 0 until w) {
            val c = argb[y * w + x]; if ((c ushr 24) < 220) continue
            hr += (c shr 16) and 255; hg += (c shr 8) and 255; hb += c and 255; hn++
        }
        // 머리카락 = 머리 꼭대기에서 **이어진** 머리색 화소만. 색만 보고 빼면 소매의 어두운 윤곽선까지 빠져
        // 팔을 들 때 윤곽선 조각이 제자리에 남았다 (09-28)
        val hair = BooleanArray(n)
        if (hn > 0) {
            val q = IntArray(n); var qh = 0; var qt = 0
            val isHair = { i: Int -> (argb[i] ushr 24) > 60 && cdist(argb[i], hr / hn, hg / hn, hb / hn) <= 55f }
            for (y in by0..min(h - 1, by0 + 12)) for (x in 0 until w) { val i = y * w + x; if (isHair(i)) { hair[i] = true; q[qt++] = i } }
            while (qh < qt) {
                val i = q[qh++]; val x = i % w; val y = i / w
                for (k in 0..3) {
                    val qx = x + intArrayOf(1, -1, 0, 0)[k]; val qy = y + intArrayOf(0, 0, 1, -1)[k]
                    if (qx !in 0 until w || qy !in 0 until h) continue
                    val j = qy * w + qx
                    if (!hair[j] && isHair(j)) { hair[j] = true; q[qt++] = j }
                }
            }
        }
        for (side in intArrayOf(-1, 1)) {
            // 1. 몸과 떨어진 바깥 구간 (팔 · 손) · 그 줄들에서 몸의 바깥 끝
            val free = ArrayList<Int>()
            val edges = ArrayList<Int>()
            val freeRun = HashMap<Int, IntArray>()     // 줄 → 그 줄에서 몸과 떨어진 팔 구간
            var freeTop = h; var freeBot = 0
            for (y in (by0 + 0.2f * bh).toInt()..by1) {
                val rs = runs(argb, w, y)
                if (rs.size < 3) continue
                val r = if (side < 0) rs.first() else rs.last()
                val body = if (side < 0) rs[1] else rs[rs.size - 2]
                val gap = if (side < 0) body[0] - r[1] else r[0] - body[1]
                if (gap < 3 || r[1] - r[0] + 1 > 0.2f * bh) continue
                if (abs((r[0] + r[1]) / 2f - cx) < 0.12f * bh) continue
                freeRun[y] = r
                edges += if (side < 0) body[0] else body[1]
            }
            // 팔 아래 끝에서 **위로 이어진 줄들만** — 묶은 머리 방울처럼 머리 옆에 떨어져 있는 것이 팔로 섞이면
            // 손 자리가 머리 쪽으로 끌려 팔이 엉뚱하게 잡혔다 (09-28 · 빨강 치마 묶은 머리)
            if (freeRun.isEmpty()) run { why = "R2 side=$side"; return null }
            val rowsDown = freeRun.keys.sortedDescending()
            freeBot = rowsDown.first(); freeTop = freeBot
            for (y in rowsDown) { if (freeTop - y > 4) break; freeTop = y }
            freeRun.keys.retainAll { it in freeTop..freeBot }
            edges.clear()
            for ((y, r) in freeRun) {
                for (x in r[0]..r[1]) free += y * w + x
                val rs = runs(argb, w, y)
                val body = if (side < 0) rs[1] else rs[rs.size - 2]
                edges += if (side < 0) body[0] else body[1]
            }
            if (free.size < 30) run { why = "R2 side=$side free=${free.size}"; return null }
            hands += intArrayOf(freeBot, free.size)
            val edge = edges.sorted()[edges.size / 2]
            val outward = { x: Int -> if (side < 0) x < edge else x > edge }
            // 목 — 머리와 어깨 사이에서 몸이 가장 좁은 줄. 이 아래부터가 어깨
            var neckY = by0; var neckW = Int.MAX_VALUE
            for (y in (by0 + 0.15f * bh).toInt() until freeTop) {
                val rs = runs(argb, w, y); if (rs.isEmpty()) continue
                val wd = rs.last()[1] - rs.first()[0]
                if (wd < neckW) { neckW = wd; neckY = y }
            }
            // 2. 팔 = 목 아래 · 팔 뿌리 선 바깥 · 팔 아래 끝까지 (머리카락 빼고)
            val lump = BooleanArray(n) { i ->
                val x = i % w; val y = i / w
                // 팔이 몸과 떨어진 줄에서는 **떨어진 구간만** — 몸통 옆 가장자리가 팔 뿌리 선보다 조금 바깥에 있으면
                // 팔 층에 딸려 들어가, 팔을 들 때 어깨 높이의 가로줄로 돌아가 버렸다 (09-28)
                y in neckY + 1..freeBot && outward(x) && (argb[i] ushr 24) > 8 && !hair[i] &&
                    (freeRun[y]?.let { r -> x in r[0] - 3..r[1] + 3 } ?: true)
            }
            // 3. 어깨 축 — 팔 뿌리 세로줄(edge 바깥 10화소)에서 팔이 차지한 높이 (도구: 1024 기준 값 → 512 기준으로 반)
            var top = h; var bot = 0
            for (k in 1..10) {
                val xx = if (side < 0) edge - k else edge + k
                if (xx !in 0 until w) continue
                for (y in 0 until h) if (lump[y * w + xx]) { top = min(top, y); bot = max(bot, y) }
            }
            if (top >= bot) run { why = "R6"; return null }
            // 살색 기준 — 몸과 떨어진 바깥 구간의 아래쪽(손) 색
            var sr = 0f; var sg = 0f; var sbb = 0f; var sn = 0
            for (i in free) if ((argb[i] ushr 24) > 200 && i / w >= freeBot - (freeBot - freeTop) / 3) {
                val c = argb[i]; sr += (c shr 16) and 255; sg += (c shr 8) and 255; sbb += c and 255; sn++
            }
            if (sn < 10) run { why = "R3"; return null }
            sr /= sn; sg /= sn; sbb /= sn
            // 손이 **살색**일 때만 사람 팔로 떼어 낸다 — 돌고래 지느러미 · 로봇 팔은 몸과 같은 색이라 소매 끝이 없고,
            // 이 길로 오면 찢어졌다 (09-28 전수 확인). 그런 것은 「튀어나온 가지」로 휘게 하는 쪽이 낫다
            val skinLike = sr > 150f && sr >= sg && sg >= sbb && sr - sbb in 25f..130f
            if (!skinLike) run { why = "R8 살색 아님 (${sr.toInt()},${sg.toInt()},${sbb.toInt()})"; return null }

            val attached = freeBot - freeTop < 0.5f * (freeBot - top)
            val attachedGuess = attached
            /** 팔 뿌리 높이의 아래 끝(겨드랑이)을 주면 → 축 · 손끝 · 소매 끝 */
            fun axis(rootBot: Int): FloatArray {
                val rootH = (rootBot - top + 1).toFloat()
                val rr = max(10f * sc, rootH * 0.55f)
                val px = edge - side * rr * 0.25f
                // 도구는 A-포즈 아이의 팔 뿌리(어깨 꼭대기 ~ 겨드랑이)에서 40% 를 썼다. 팔을 붙인 그림은 뿌리를
                // 소매 끝까지 재므로 같은 40% 면 관절이 소매 가운데까지 내려간다 — 어깨 꼭대기 쪽 28% 로
                val py = top + (if (attachedGuess) 0.28f else 0.4f) * rootH
                // 손끝 — 축에서 가장 먼 곳(끝 6화소)의 평균
                val hx = free.map { it % w }.average().toFloat(); val hy = free.map { it / w }.average().toFloat()
                val d0 = max(1f, hypot(hx - px, hy - py)); val u0x = (hx - px) / d0; val u0y = (hy - py) / d0
                var pmax = -1e9f
                for (i in 0 until n) if (lump[i]) pmax = max(pmax, (i % w - px) * u0x + (i / w - py) * u0y)
                var fx = 0.0; var fy = 0.0; var fn = 0
                for (i in 0 until n) if (lump[i] && (i % w - px) * u0x + (i / w - py) * u0y >= pmax - 6f * sc) { fx += i % w; fy += i / w; fn++ }
                val tipX = (fx / fn).toFloat(); val tipY = (fy / fn).toFloat()
                val len = max(1f, hypot(tipX - px, tipY - py))
                val ux = (tipX - px) / len; val uy = (tipY - py) / len
                // 소매 끝 — 팔 축을 따라가며 살색 비율이 60% 를 넘는 첫 자리. 색은 이것 하나에만 쓴다
                val binN = IntArray(50); val binS = IntArray(50)
                for (i in 0 until n) if (lump[i] && (argb[i] ushr 24) >= 128) {
                    val tb = (((i % w - px) * ux + (i / w - py) * uy) / len * 50).toInt()
                    if (tb !in 0 until 50) continue
                    binN[tb]++; if (cdist(argb[i], sr, sg, sbb) < 70f) binS[tb]++
                }
                val hem = (0 until 30).firstOrNull { binN[it] > 0 && binS[it] >= 0.6f * binN[it] }?.let { it / 50f } ?: 0.25f
                return floatArrayOf(px, py, tipX, tipY, hem)
            }
            // A-포즈(팔이 몸과 떨어짐)면 팔이 떨어지기 시작하는 줄이 곧 겨드랑이다.
            // 팔을 붙이고 선 그림은 떨어진 곳이 손뿐이라 그 줄이 손 바로 위다 — 거기까지 재면 어깨 축이 소매 끝까지 내려간다.
            // 그때는 한 번 재서 **소매 끝**을 찾고, 그 높이를 겨드랑이로 삼아 다시 잰다
            var ax = axis(if (bot > freeTop) min(bot, freeTop) else bot)
            if (attached) {
                val hemY = ax[1] + (ax[3] - ax[1]) * ax[4]
                ax = axis(hemY.toInt().coerceIn(top + 4, bot))
            }
            val px = ax[0]; val py = ax[1]; val tipX = ax[2]; val tipY = ax[3]; val hem = ax[4]
            val len = max(1f, hypot(tipX - px, tipY - py))
            val ux = (tipX - px) / len; val uy = (tipY - py) / len
            val tOf = { i: Int -> ((i % w - px) * ux + (i / w - py) * uy) / len }
            val crossOf = { i: Int -> -(i % w - px) * uy + (i / w - py) * ux }
            if (attached && KEEP_SLEEVE) {
                // ── 팔을 몸에 붙이고 선 그림 (주인공 27장) — **소매는 셔츠에 두고 맨살 팔만** 뗀다 (09-28 다시)
                // 소매까지 팔과 같이 들었더니 셔츠에서 소매가 통째로 뜯겨 몸통 옆이 상자처럼 잘리고, 들린 소매 끝이
                // 너덜하게 늘어났다 (사용자: *"팔 부분에 소매가 같이 붙어서 나오고 팔이 이상하다"*).
                // A-포즈 아이는 소매가 몸통과 떨어져 있어 소매째 드는 게 맞았다 — 그쪽은 아래 그대로 둔다.
                // 맨살 팔 = 소매 끝 너머. 소매 끝 언저리(±6%)는 살색 · 윤곽선만 — 비스듬한 소매 끝이 딸려 가지 않게
                val skin = BooleanArray(n) { i ->
                    if (!lump[i]) return@BooleanArray false
                    val t = tOf(i)
                    if (t >= hem + 0.06f) return@BooleanArray true
                    if (t < hem - 0.06f) return@BooleanArray false
                    val c = argb[i]
                    val lum = (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)) / 3
                    cdist(c, sr, sg, sbb) < 75f || lum < 110
                }
                // 소매 속 위팔 — 그림에 없다(소매에 가려 있다). 소매 끝 바로 아래 살색 줄을 어깨 축까지 늘여 채운다.
                // 가만히 있으면 소매 뒤라 안 보이고, 팔을 들면 소매 밑에서 나오는 위팔이 된다
                var cMin = Float.MAX_VALUE; var cMax = -Float.MAX_VALUE
                for (i in 0 until n) if (skin[i]) { val t = tOf(i); if (t in hem + 0.04f..hem + 0.12f) { val c = crossOf(i); cMin = min(cMin, c); cMax = max(cMax, c) } }
                if (cMin > cMax) run { why = "R9 소매 끝 아래 살이 없음"; return null }
                arms += ArmLayer(side, skin, px, py, tipX, tipY, hem.coerceIn(0.05f, 0.7f), floatArrayOf(cMin, cMax, hem + 0.07f))
                fillUnder(under, argb, skin, outward, side, w, h)
                continue
            }
            // 어깨 윗면 — 몸 쪽이지만 팔 축보다 머리 쪽 · 축에서 26화소 안은 소매와 같이 접힌다 (도구와 같다)
            // 팔이 몸에 붙어 있던 그림 — 팔과 몸통의 경계는 **겨드랑이 → 손 안쪽이 몸과 떨어지는 점** 을 잇는 선이다.
            // 이 선 안쪽(몸 쪽)은 팔이 아니다. 몸통 옆 셔츠가 팔에 딸려 나와 아래팔 안쪽에 빨간 삼각형이 붙었고,
            // 색으로 가르니 가장자리가 들쭉날쭉했다 (09-28 A-포즈 확대) → 곧은 선으로 가른다
            val hemAtEdgeY = (py + uy * len * hem).toInt()
            val bRun = freeRun[freeTop]!!
            val bx = (if (side < 0) bRun[1] else bRun[0]).toFloat(); val byy = freeTop.toFloat()
            val ayy = min(hemAtEdgeY, freeTop - 1).toFloat(); val axx = edge.toFloat()
            val shirtBit = { i: Int ->
                val x = i % w; val y = i / w
                attached && y > ayy && y < byy && run {
                    val xl = axx + (bx - axx) * (y - ayy) / max(1f, byy - ayy)
                    if (side < 0) x > xl else x < xl
                }
            }
            val region = BooleanArray(n) { i ->
                (lump[i] && !shirtBit(i)) || run {
                    val x = i % w; val y = i / w
                    if ((argb[i] ushr 24) <= 8 || y <= neckY) return@run false
                    val cross = (x - px) * uy - (y - py) * ux
                    val above = if (side > 0) cross > 0 else cross < 0
                    above && hypot(x - px, y - py) < 26f * sc && !hair[i]
                }
            }
            arms += ArmLayer(side, region, px, py, tipX, tipY, hem.coerceIn(0.05f, 0.7f))
            // 둥근 어깨 — 어깨 관절을 중심으로 한 둥근 부분은 **몸에도 남긴다**. 안 남기면 팔을 들 때 몸통 옆이
            // 목 아래부터 곧게 잘려 윗몸이 상자처럼 보였다 (사용자: *"실제 사람이 움직이는 것처럼"*).
            // 가만히 있을 때는 팔 층의 소매가 그 위를 똑같이 덮고, 팔을 들면 소매 밑에 둥근 어깨와 겨드랑이 곡선이 남는다
            val ball = (py - top) * 1.2f + 4f * sc
            // 어깨 **윗부분**만 — 팔 축보다 머리 쪽이거나 몸통 선 안쪽. 겨드랑이 쪽 소매까지 남기면 팔을 들 때
            // 소매 조각이 겨드랑이에 삐져나왔다
            // 몸통 선 바깥 4화소까지는 셔츠로 채운다 — 폰이 두 층 경계를 부드럽게 그리며 **흰 틈**이 비쳤다 (09-28 실물 폰)
            fillUnder(under, argb, region, outward, side, w, h, bleed = { x -> abs(x - edge) <= 4 }) { i ->
                val x = i % w; val y = i / w
                val cross = (x - px) * uy - (y - py) * ux
                val above = if (side > 0) cross > 0 else cross < 0
                hypot(x - px, y - py) <= ball && !hair[i] && (above || !outward(x))
            }
        }
        // 사람처럼 **양팔이 같은 높이에 · 비슷한 크기로 · 몸 아래쪽 절반까지** 내려와 있을 때만 — 공룡 다리 · 외계인 귀를
        // 팔로 잡으면 그림이 찢어진다 (09-28 트리케라톱스 · 외계인)
        if (arms.size != 2 || hands.size != 2) return null
        val (l, r) = hands
        val humanLike = abs(l[0] - r[0]) <= 0.08f * bh &&
            hands.all { it[0] in (by0 + 0.45f * bh).toInt()..(by0 + 0.85f * bh).toInt() } &&
            max(l[1], r[1]) <= 4f * min(l[1], r[1])
        if (!humanLike) run { why = "R7 손 ${l.toList()} ${r.toList()}"; return null }
        return arms to under
    }

    /** 6. 몸 층 — 팔이 가리던 자리. 팔 뿌리 선 안쪽은 셔츠 무늬를 거울처럼 비춰 메우고, 바깥은 비운다 */
    private fun fillUnder(
        under: IntArray, argb: IntArray, region: BooleanArray, outward: (Int) -> Boolean, side: Int, w: Int, h: Int,
        bleed: (Int) -> Boolean = { false },
        keep: (Int) -> Boolean = { false },
    ) {
        val inward = if (side < 0) 1 else -1
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!region[i] || keep(i)) continue
            if (outward(x) && !bleed(x)) { under[i] = 0; continue }
            var xin = x
            while (xin in 0 until w && region[y * w + xin]) xin += inward
            if (xin !in 0 until w) { under[i] = 0; continue }
            var src = xin + (xin - x)
            if (src !in 0 until w || region[y * w + src] || (argb[y * w + src] ushr 24) < 200) src = xin
            under[i] = argb[y * w + src] or (0xFF shl 24)
        }
    }

    /**
     * 떼어 낸 팔 하나의 그물 — 그림은 그림판 오른쪽 절반(팔 층)에서 읽는다. 가중치는 시연 도구와 같다:
     *   s0 = smooth(-0.06, 0.10, t)                 몸통에 붙는 곳 — 같은 천이 늘어난다
     *   소매(t < 소매 끝)   위팔을 s0 만큼 (소매 뼈는 위팔 각도를 100% 따라가므로 위팔과 같다)
     *   맨살(t ≥ 소매 끝)   a = smooth(끝-0.02, 끝+0.10) · 위팔 a(1-e) · 아래팔 a·e · 소매 (1-a)s0
     *   e = smooth(팔꿈치 ± 0.07)                    굽는 구간은 좁게 — 위팔 · 아래팔이 각각 단단하게
     * 소매 쪽 삼각형은 **몸 앞에**, 맨살 쪽은 **몸 뒤에** 그린다
     */
    private fun armPart(a: ArmLayer, argb: IntArray, w: Int, h: Int): Part {
        a.inner?.let { return innerArmPart(a, it, w, h) }
        val step = max(4, ceil(max(w, h) / GRID.toDouble()).toInt())
        val d = chamfer(w, h, edge = false) { a.region[it] }
        val (rest, tris) = gridOver(w, h, step) { x, y ->
            val xi = x.toInt().coerceIn(0, w - 1); val yi = y.toInt().coerceIn(0, h - 1)
            d[yi * w + xi] <= step
        }!!
        val m = rest.size / 2
        val dx = a.tipX - a.px; val dy = a.tipY - a.py
        val len = max(1f, hypot(dx, dy)); val ux = dx / len; val uy = dy / len
        val restOut = atan2(dx * a.side, dy) * 180f / PI.toFloat()
        // 팔꿈치 — 도구는 (소매 끝 + 0.8) / 2 였다. 어깨 관절을 어깨 꼭대기로 올리니 소매 끝이 팔 길이의 절반을 넘어
        // 팔꿈치가 손목 바로 위까지 내려갔다 → 사람 비율대로 팔 길이의 절반 근처, 소매 끝보다는 아래로
        val tEl = max(a.hemT + 0.03f, min(0.52f, (a.hemT + 0.8f) / 2f))
        val tWr = 0.80f                              // 손목 — 손 길이 ≈ 팔 전체의 20% (도구와 같다)
        val base = if (a.side > 0) "armR" else "armL"
        val bones = listOf(
            Bone("root", -1, 0f, 0f),
            Bone("${base}_up", 0, a.px, a.py, role = LimbRole.ARM, side = a.side, restOut = restOut),
            Bone("${base}_fore", 1, a.px + ux * len * tEl, a.py + uy * len * tEl, role = LimbRole.ARM, side = a.side, restOut = restOut),
            Bone("${base}_hand", 2, a.px + ux * len * tWr, a.py + uy * len * tWr, role = LimbRole.ARM, side = a.side, restOut = restOut),
        )
        val tv = FloatArray(m) { k -> ((rest[2 * k] - a.px) * ux + (rest[2 * k + 1] - a.py) * uy) / len }
        val wB = Array(m) { intArrayOf(0, 1, 2, 3) }
        val wV = Array(m) { k ->
            val t = tv[k]
            val s0 = smooth(-0.06f, 0.10f, t)
            val e = smooth(tEl - 0.07f, tEl + 0.07f, t)
            val g = smooth(tWr - 0.04f, tWr + 0.04f, t)   // 손목은 좁게 — 손이 단단하게 까딱인다
            if (t < a.hemT) floatArrayOf(1 - s0, s0, 0f, 0f)
            else {
                val aa = smooth(a.hemT - 0.02f, a.hemT + 0.10f, t)
                val ws = (1 - aa) * s0
                floatArrayOf(1 - ws - aa, ws + aa * (1 - e), aa * e * (1 - g), aa * e * g)
            }
        }
        val (sleeve, skin) = tris.partition { tr -> tr.sumOf { tv[it].toDouble() } / 3 < a.hemT }
        return Part("human", bones, rest, wB, wV, skin, w.toFloat(), front = sleeve)
    }

    /**
     * 팔을 몸에 붙이고 선 그림의 팔 — 맨살 팔 + **소매 속 위팔**(소매 끝 살색을 늘여 채운 띠).
     * 통째로 어깨 축을 돌고 팔꿈치에서 한 번 더 굽는다. 전부 **몸 뒤에** 그린다 — 소매가 팔 뿌리를 덮는다
     */
    private fun innerArmPart(a: ArmLayer, inner: FloatArray, w: Int, h: Int): Part {
        val step = max(4, ceil(max(w, h) / GRID.toDouble()).toInt())
        val d = chamfer(w, h, edge = false) { a.region[it] }
        val dx = a.tipX - a.px; val dy = a.tipY - a.py
        val len = max(1f, hypot(dx, dy)); val ux = dx / len; val uy = dy / len
        val (cMin, cMax, tCut) = Triple(inner[0], inner[1], inner[2])
        val tOf = { x: Float, y: Float -> ((x - a.px) * ux + (y - a.py) * uy) / len }
        val cOf = { x: Float, y: Float -> -(x - a.px) * uy + (y - a.py) * ux }
        val (rest, tris) = gridOver(w, h, step) { x, y ->
            val xi = x.toInt().coerceIn(0, w - 1); val yi = y.toInt().coerceIn(0, h - 1)
            d[yi * w + xi] <= step || (tOf(x, y) in -0.08f..tCut && cOf(x, y) in cMin - step..cMax + step)
        }!!
        val m = rest.size / 2
        // 그림 좌표 — 소매 끝 줄(tCut)보다 어깨 쪽 점은 **그 줄의 같은 옆자리** 살색을 쓴다 (띠 모양으로 늘인다)
        val tex = FloatArray(m * 2) { k -> rest[k] }
        for (k in 0 until m) {
            val x = rest[2 * k]; val y = rest[2 * k + 1]
            if (tOf(x, y) < tCut) {
                val c = cOf(x, y).coerceIn(cMin, cMax)
                tex[2 * k] = a.px + ux * len * tCut - uy * c
                tex[2 * k + 1] = a.py + uy * len * tCut + ux * c
            }
        }
        val restOut = atan2(dx * a.side, dy) * 180f / PI.toFloat()
        val tEl = (a.hemT + 0.8f) / 2f
        val base = if (a.side > 0) "armR" else "armL"
        val bones = listOf(
            Bone("root", -1, 0f, 0f),
            Bone("${base}_up", 0, a.px, a.py, role = LimbRole.ARM, side = a.side, restOut = restOut),
            Bone("${base}_fore", 1, a.px + ux * len * tEl, a.py + uy * len * tEl, role = LimbRole.ARM, side = a.side, restOut = restOut),
        )
        val wB = Array(m) { intArrayOf(0, 1, 2) }
        val wV = Array(m) { k ->
            val e = smooth(tEl - 0.07f, tEl + 0.07f, tOf(rest[2 * k], rest[2 * k + 1]))
            floatArrayOf(0f, 1 - e, e)
        }
        return Part("human", bones, rest, wB, wV, tris, w.toFloat(), tex = tex)
    }

    /** 가지 하나 — 몸통에 붙은 자리(가장 큰 덩어리)에서 가지 안으로 퍼져 나가며 거리를 잰다 */
    private fun limbOf(px: IntArray, lab: IntArray, c: Int, core: BooleanArray, aw: Int, ah: Int): Limb? {
        val n = aw * ah
        val touch = BooleanArray(n)
        for (i in px) {
            val x = i % aw; val y = i / aw
            if ((x > 0 && core[i - 1]) || (x < aw - 1 && core[i + 1]) || (y > 0 && core[i - aw]) || (y < ah - 1 && core[i + aw])) touch[i] = true
        }
        val (tLab, tCount) = label(touch, aw, ah)
        if (tCount == 0) return null
        val sizes = IntArray(tCount + 1); for (i in px) if (tLab[i] > 0) sizes[tLab[i]]++
        val best = (1..tCount).maxBy { sizes[it] }
        val geo = FloatArray(n) { -1f }
        val q = ArrayDeque<Int>()
        var bx = 0.0; var by = 0.0; var bn = 0
        for (i in px) if (tLab[i] == best) { geo[i] = 0f; q.add(i); bx += i % aw; by += i / aw; bn++ }
        // 8방향 거리 — 대각선은 1.414. 가지 안에서만 퍼진다 (한 번 정한 값도 더 짧으면 고친다)
        while (q.isNotEmpty()) {
            val i = q.removeFirst(); val x = i % aw; val y = i / aw
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val qx = x + dx; val qy = y + dy
                if (qx !in 0 until aw || qy !in 0 until ah) continue
                val j = qy * aw + qx
                if (lab[j] != c) continue
                val nd = geo[i] + if (dx != 0 && dy != 0) 1.4142f else 1f
                if (geo[j] < 0f || nd < geo[j] - 1e-3f) { geo[j] = nd; q.add(j) }
            }
        }
        var tip = px[0]; var L = 0f
        for (i in px) if (geo[i] > L) { L = geo[i]; tip = i }
        if (L <= 0f) return null
        var mx = 0.0; var my = 0.0; var mn = 0
        for (i in px) if (geo[i] in 0.45f * L..0.55f * L) { mx += i % aw; my += i / aw; mn++ }
        if (mn == 0) { mx = (tip % aw).toDouble(); my = (tip / aw).toDouble(); mn = 1 }
        return Limb(px, geo, L, (bx / bn).toFloat(), (by / bn).toFloat(), (tip % aw).toFloat(), (tip / aw).toFloat(),
            (mx / mn).toFloat(), (my / mn).toFloat())
    }

    /** 가장 큰 덩어리만 남긴다 — 배경을 지울 때 남은 티끌을 버린다 */
    private fun keepLargest(m: BooleanArray, w: Int, h: Int) {
        val (lab, count) = label(m, w, h)
        if (count <= 1) return
        val sizes = IntArray(count + 1); for (v in lab) if (v > 0) sizes[v]++
        val best = (1..count).maxBy { sizes[it] }
        for (i in m.indices) m[i] = lab[i] == best
    }

    /** 8방향으로 이어진 덩어리마다 번호 (0 = 밖) */
    private fun label(m: BooleanArray, w: Int, h: Int): Pair<IntArray, Int> {
        val lab = IntArray(m.size); var c = 0
        val stack = IntArray(m.size)
        for (s in m.indices) {
            if (!m[s] || lab[s] != 0) continue
            c++; var top = 0; stack[top++] = s; lab[s] = c
            while (top > 0) {
                val i = stack[--top]; val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    val qx = x + dx; val qy = y + dy
                    if (qx !in 0 until w || qy !in 0 until h) continue
                    val j = qy * w + qx
                    if (m[j] && lab[j] == 0) { lab[j] = c; stack[top++] = j }
                }
            }
        }
        return lab to c
    }

    /**
     * 가장 가까운 「원점」까지의 거리 (두 번 훑는 모따기 거리 — 유클리드에 가깝다).
     * @param edge 그림 밖도 원점으로 칠 것인가 — 윤곽 안쪽 거리를 잴 때만 true
     */
    private fun chamfer(w: Int, h: Int, edge: Boolean, origin: (Int) -> Boolean): FloatArray {
        val big = 1e6f
        val d = FloatArray(w * h) { if (origin(it)) 0f else big }
        val a = 1f; val b = 1.4142f
        val outside = if (edge) 0f else big
        fun at(x: Int, y: Int) = if (x < 0 || y < 0 || x >= w || y >= h) outside else d[y * w + x]
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; if (d[i] == 0f) continue
            d[i] = minOf(d[i], at(x - 1, y) + a, at(x, y - 1) + a, min(at(x - 1, y - 1), at(x + 1, y - 1)) + b)
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x; if (d[i] == 0f) continue
            d[i] = minOf(d[i], at(x + 1, y) + a, at(x, y + 1) + a, min(at(x + 1, y + 1), at(x - 1, y + 1)) + b)
        }
        return d
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val d = chamfer(w, h, edge = false) { m[it] }
        return BooleanArray(w * h) { d[it] <= r }
    }
}
