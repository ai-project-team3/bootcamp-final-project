package com.example.finalproject_demo.demo.scene

/**
 * Stage floor + pre-made felt pieces (10-03 lead · `docs/배경_조각_목록.md`).
 *
 * Instead of generating one background per place during the session, a place is drawn from a kit:
 * two felt colours for sky and ground, two layers of felt hills, and transparent felt pieces
 * (`res/drawable-nodpi/kit_*.webp`) placed by [layoutScene]. This file is the **one table** of piece tags —
 * the tag columns are copied from the doc's §3-0 (common) and §3-4 (park). When a piece is re-baked or a
 * new place kit is added, change it here and nowhere else.
 *
 * Pure Kotlin on purpose (no Android / Compose types) so the layout can be unit-tested.
 */
enum class PieceRole {
    /** 하늘큰것 — one big piece in the sky (sun, moon) */
    SKY_ANCHOR,
    /** 하늘채움 — spread across the sky, never in a clump (cloud, balloons, kite) */
    SKY_FILL,
    /** 떠있음 — floats at middle height (butterfly) */
    FLOAT,
    /** 먼띠 — small hazy copies along the horizon */
    FAR,
    /** 랜드마크 — 1–2 big things a little behind the actors */
    LANDMARK,
    /** 덮개 — small ground things in clumps of 2–3 */
    COVER,
    /** 납작 — lies flat on the ground (sandbox) */
    FLAT,
    /** 전경 — one big piece cut by a bottom corner, drawn in front of the actors */
    FOREGROUND,
}

/** Where [PlacedPiece.y] sits on the piece: its feet (stands on the ground) or its centre (hangs in the sky). */
enum class PieceBase { FEET, CENTER }

/**
 * One piece tag row.
 *
 * @param res drawable name (`kit_{group}_{piece}`). The same picture may appear twice with different roles
 *   (the doc's 「랜드마크 · 먼띠」), so [name] is the row key and [res] the picture.
 * @param size real height with the hero's height = 1 — the perspective turns it into pixels.
 * @param aspect width / height of the trimmed webp (measured 10-05 when converting). The layout needs it
 *   to score overlaps without loading pictures; `SceneKitShotTest.aspectsInTheTableMatchThePictures` checks it against the files.
 * @param copies sky fill / float: how many copies. Cover: 1 = stands alone (bench, ball), 0 = may clump.
 * @param words what a child says for this piece (doc column 「부르는 말」) — for later 「별 더 많이!」 edits.
 */
data class KitPiece(
    val name: String,
    val res: String,
    val role: PieceRole,
    val size: Float,
    val aspect: Float,
    val base: PieceBase = PieceBase.FEET,
    val flip: Boolean = true,
    val tilt: Float = 0f,
    val copies: Int = 0,
    val words: List<String> = emptyList(),
)

/** A place kit: felt colours (0xAARRGGBB) and the pieces it may use. */
data class SceneKitDef(
    val key: String,
    val skyTop: Long,
    val skyBottom: Long,
    val ground: Long,
    val hillFar: Long,
    val hillNear: Long,
    val pieces: List<KitPiece>,
    /** How many landmarks one scene picks (out of the LANDMARK rows) */
    val landmarks: Int = 2,
) {
    fun byRole(role: PieceRole) = pieces.filter { it.role == role }
}

// ── §3-0 common — 9 pieces (all baked, PR #109) ──────────────────────────────────────────────
private val SUN = KitPiece("sun", "kit_common_sun", PieceRole.SKY_ANCHOR, 0.55f, 1.016f, PieceBase.CENTER, tilt = 8f,
    words = listOf("해", "해님", "햇님"))
private val CLOUD = KitPiece("cloud", "kit_common_cloud", PieceRole.SKY_FILL, 0.35f, 1.434f, PieceBase.CENTER, tilt = 4f,
    copies = 3, words = listOf("구름"))
private val TREE = KitPiece("round_tree", "kit_common_round_tree", PieceRole.LANDMARK, 1.7f, 0.869f, words = listOf("나무"))
private val TREE_FAR = TREE.copy(name = "round_tree_far", role = PieceRole.FAR)
private val BUSH = KitPiece("bush", "kit_common_bush", PieceRole.COVER, 0.38f, 1.148f, words = listOf("덤불", "풀숲"))
private val TULIP = KitPiece("tulip", "kit_common_tulip", PieceRole.COVER, 0.34f, 0.740f, words = listOf("꽃", "튤립"))
private val DAISY = KitPiece("daisy", "kit_common_daisy", PieceRole.COVER, 0.25f, 1.004f, words = listOf("꽃", "하얀 꽃"))
private val STONE = KitPiece("stone", "kit_common_stone", PieceRole.COVER, 0.3f, 1.164f, words = listOf("돌", "바위"))
private val GRASS = KitPiece("grass", "kit_common_grass", PieceRole.COVER, 0.3f, 1.140f, words = listOf("풀"))
private val BUTTERFLY = KitPiece("butterfly", "kit_common_butterfly", PieceRole.FLOAT, 0.15f, 1.219f, PieceBase.CENTER,
    tilt = 15f, copies = 2, words = listOf("나비"))
// 「덮개 · 전경」: the foreground copy is bigger than the doc's 0.34 / 0.3 — prototype `tulip_big` 0.55 (10-03)
private val TULIP_FRONT = TULIP.copy(name = "tulip_front", role = PieceRole.FOREGROUND, size = 0.55f)
private val GRASS_FRONT = GRASS.copy(name = "grass_front", role = PieceRole.FOREGROUND, size = 0.5f)

// ── §3-4 park · playground — 10 pieces ─────────────────────────────────────────────────────────
private val SLIDE = KitPiece("slide", "kit_park_slide", PieceRole.LANDMARK, 1.3f, 0.924f, words = listOf("미끄럼틀"))
private val SWING = KitPiece("swing", "kit_park_swing", PieceRole.LANDMARK, 1.5f, 0.969f, words = listOf("그네"))
private val SEESAW = KitPiece("seesaw", "kit_park_seesaw", PieceRole.LANDMARK, 0.8f, 1.510f, words = listOf("시소"))
private val SANDBOX = KitPiece("sandbox", "kit_park_sandbox", PieceRole.FLAT, 0.2f, 1.467f, words = listOf("모래", "모래놀이"))
private val BENCH = KitPiece("bench", "kit_park_bench", PieceRole.COVER, 0.5f, 1.188f, copies = 1, words = listOf("의자", "벤치"))
private val LAMP = KitPiece("street_lamp", "kit_park_street_lamp", PieceRole.LANDMARK, 1.8f, 0.201f, words = listOf("가로등", "불"))
private val BALL = KitPiece("ball", "kit_park_ball", PieceRole.COVER, 0.15f, 0.988f, copies = 1, words = listOf("공"))
private val BALLOONS = KitPiece("balloons", "kit_park_balloons", PieceRole.SKY_FILL, 0.4f, 0.668f, PieceBase.CENTER,
    tilt = 10f, copies = 1, words = listOf("풍선"))
private val KITE = KitPiece("kite", "kit_park_kite", PieceRole.SKY_FILL, 0.3f, 0.738f, PieceBase.CENTER,
    tilt = 20f, copies = 1, words = listOf("연"))
private val TREE_ROW = KitPiece("tree_row", "kit_park_tree_row", PieceRole.FAR, 1.4f, 1.243f, words = listOf("나무"))

/** 공원·놀이터 — sky blue · grass (doc §1 table). Colours from the prototype's green kit (`layout_proto.py`). */
val PARK_KIT = SceneKitDef(
    key = "park",
    skyTop = 0xFF8CC8EC, skyBottom = 0xFFD0EAF6,
    ground = 0xFF78B254,
    hillFar = 0xFF76AA6E, hillNear = 0xFF96C480,
    pieces = listOf(
        SUN, CLOUD, BALLOONS, KITE, BUTTERFLY,
        TREE_FAR, TREE_ROW,
        // LAMP re-baked with its pole and base (10-05) — the #109 piece was only the lantern head and read as a giant lantern
        TREE, SLIDE, SWING, SEESAW, LAMP,
        BUSH, TULIP, DAISY, STONE, GRASS, BENCH, BALL,
        SANDBOX,
        TULIP_FRONT, GRASS_FRONT,
    ),
)

// ── §3-3 공룡 나라 — 12 pieces (#97 2순위 첫째 · 10-06), plus the common sun · cloud · butterfly in the sky ──────
// aspects measured from the trimmed webps (`SceneKitShotTest.aspectsInTheTableMatchThePictures`)
private val VOLCANO = KitPiece("volcano", "kit_dino_volcano", PieceRole.FAR, 2.5f, 0.872f, words = listOf("화산"))
private val PALM = KitPiece("palm_tree", "kit_dino_palm_tree", PieceRole.LANDMARK, 1.9f, 0.813f, words = listOf("야자나무", "나무"))
private val WATERFALL = KitPiece("waterfall", "kit_dino_waterfall", PieceRole.LANDMARK, 1.8f, 1.111f, words = listOf("폭포"))
private val JUNGLE = KitPiece("jungle", "kit_dino_jungle", PieceRole.FAR, 1.4f, 1.002f, words = listOf("숲", "정글"))
private val FERN = KitPiece("fern", "kit_dino_fern", PieceRole.COVER, 0.5f, 0.977f, words = listOf("풀", "잎"))
private val BIG_LEAF = KitPiece("big_leaf", "kit_dino_big_leaf", PieceRole.FOREGROUND, 0.7f, 0.801f, words = listOf("잎", "나뭇잎"))
private val EGG = KitPiece("egg", "kit_dino_egg", PieceRole.COVER, 0.25f, 0.816f, words = listOf("알", "공룡 알"))
private val NEST = KitPiece("nest", "kit_dino_nest", PieceRole.COVER, 0.4f, 1.407f, words = listOf("둥지"))
private val FOOTPRINT = KitPiece("footprint", "kit_dino_footprint", PieceRole.FLAT, 0.1f, 0.980f, words = listOf("발자국"))
private val HIBISCUS = KitPiece("hibiscus", "kit_dino_hibiscus", PieceRole.COVER, 0.35f, 1.044f, words = listOf("꽃"))
private val LOG = KitPiece("log", "kit_dino_log", PieceRole.COVER, 0.35f, 1.759f, copies = 1, words = listOf("나무", "통나무"))
private val RAINBOW = KitPiece("rainbow", "kit_dino_rainbow", PieceRole.SKY_ANCHOR, 0.9f, 1.600f, PieceBase.CENTER, flip = false,
    words = listOf("무지개"))

/** 공룡 나라 — 연노랑 하늘 · 황토 흙 (doc §1 table), jungle-green hills behind */
val DINO_KIT = SceneKitDef(
    key = "dino",
    skyTop = 0xFFF3DF95, skyBottom = 0xFFFCF3D2,
    ground = 0xFFC9A35E,
    hillFar = 0xFF8FB06A, hillNear = 0xFFA9C46E,
    pieces = listOf(
        RAINBOW, SUN, CLOUD, BUTTERFLY,
        VOLCANO, JUNGLE,
        PALM, WATERFALL,
        FERN, EGG, NEST, HIBISCUS, LOG,
        FOOTPRINT,
        BIG_LEAF,
    ),
)

// ── §3-1 우주 — 12 pieces (#97 2순위 둘째 · 10-06). No butterfly, no bird: nothing lives in the sky here ──────────────
private val MOON = KitPiece("moon", "kit_space_moon", PieceRole.SKY_ANCHOR, 0.75f, 0.938f, PieceBase.CENTER, tilt = 12f,
    words = listOf("달", "달님"))
private val RING_PLANET = KitPiece("ring_planet", "kit_space_ring_planet", PieceRole.SKY_ANCHOR, 0.4f, 1.508f, PieceBase.CENTER,
    tilt = 10f, words = listOf("행성", "토성"))
private val SMALL_PLANET = KitPiece("small_planet", "kit_space_small_planet", PieceRole.SKY_FILL, 0.2f, 1.001f, PieceBase.CENTER,
    copies = 2, words = listOf("행성", "별", "지구"))
private val STAR = KitPiece("star", "kit_space_star", PieceRole.SKY_FILL, 0.12f, 1.039f, PieceBase.CENTER, tilt = 25f,
    copies = 4, words = listOf("별", "별님"))
private val SHOOTING_STAR = KitPiece("shooting_star", "kit_space_shooting_star", PieceRole.SKY_FILL, 0.3f, 1.608f, PieceBase.CENTER,
    tilt = 15f, copies = 1, words = listOf("별똥별", "유성"))
private val ROCKET = KitPiece("rocket", "kit_space_rocket", PieceRole.LANDMARK, 1.3f, 0.627f, words = listOf("로켓", "우주선"))
private val DOME = KitPiece("dome", "kit_space_dome", PieceRole.LANDMARK, 1.1f, 1.070f, words = listOf("우주 집", "기지", "집"))
private val ROCK_HILL = KitPiece("rock_hill", "kit_space_rock_hill", PieceRole.FAR, 1.2f, 1.401f, words = listOf("산"))
private val MOON_ROCK = KitPiece("moon_rock", "kit_space_moon_rock", PieceRole.COVER, 0.3f, 1.008f, words = listOf("돌", "바위"))
private val MOON_ROCK_FRONT = MOON_ROCK.copy(name = "moon_rock_front", role = PieceRole.FOREGROUND, size = 0.5f)
private val CRYSTAL = KitPiece("crystal", "kit_space_crystal", PieceRole.COVER, 0.35f, 0.938f, words = listOf("보석", "수정"))
private val CRATER = KitPiece("crater", "kit_space_crater", PieceRole.FLAT, 0.1f, 1.988f, words = listOf("구멍", "분화구"))
private val FLAG = KitPiece("flag", "kit_space_flag", PieceRole.COVER, 0.6f, 0.627f, flip = false, copies = 1, words = listOf("깃발"))

/** 우주 — 남색 밤하늘 · 회보라 달 표면 (doc §1 table · the prototype's space colours in `eval/layout_proto.py`) */
val SPACE_KIT = SceneKitDef(
    key = "space",
    skyTop = 0xFF141C44, skyBottom = 0xFF343876,
    ground = 0xFFC8C4DE,
    hillFar = 0xFF8A86B4, hillNear = 0xFFA8A4C4,
    pieces = listOf(
        MOON, RING_PLANET, SMALL_PLANET, STAR, SHOOTING_STAR,
        ROCK_HILL,
        ROCKET, DOME,
        MOON_ROCK, CRYSTAL, FLAG,
        CRATER,
        MOON_ROCK_FRONT,
    ),
)

object SceneKits {
    /**
     * **The one switch** (10-05). On: a live story whose place is not one of the three app themes is drawn
     * from a kit, and no `/image` background is requested for it. Off: the documented alternative —
     * a background generated during the session (`StoryLiveFlow.updateBackground`, rule 8 as written today).
     * A `var` so tests of the generated-background path can turn it off.
     */
    @Volatile var liveStory: Boolean = true

    val all: Map<String, SceneKitDef> = mapOf(PARK_KIT.key to PARK_KIT, DINO_KIT.key to DINO_KIT, SPACE_KIT.key to SPACE_KIT)

    /**
     * Which kit draws [place], or null for the app's own theme pictures.
     * Only the park kit is baked so far; the doc's other seven groups (space, sea, …) and Jev's choice among
     * them come later. Until then **every non-theme place → park** — the same fallback the doc gives for a
     * low-confidence choice (10-03 lead decision).
     */
    @Suppress("UNUSED_PARAMETER")
    fun forPlace(place: String): SceneKitDef = PARK_KIT

    /** The kit whose places the child named, or null — then the place is generated as before (rule 8).
     *  10-05 device: 「미래 도시」 came out as a park; only the park kit exists yet, so only park words reach it */
    fun matching(place: String): SceneKitDef? = when {
        listOf("공원", "놀이터", "운동장", "잔디", "산책").any { it in place } -> PARK_KIT
        // 공룡 나라 is also an app theme: a live story's theme check runs first (`StoryLiveFlow`), so the kit
        // reaches the stage only when the lead routes the theme places here (10-06 · #97)
        listOf("공룡", "정글", "화산", "쥬라기").any { it in place } -> DINO_KIT
        listOf("우주", "달나라", "별나라", "행성", "로켓").any { it in place } -> SPACE_KIT
        else -> null
    }
}
