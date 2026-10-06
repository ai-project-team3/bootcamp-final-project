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
    /** Two felt hills above the horizon. Off indoors — the sky there is a wall, and hills made it read as outside (10-06 device · #222) */
    val hills: Boolean = true,
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
// 2.5 covered most of the stage from the far band on some seeds (d3 · #173) — 1.7 keeps it a far landmark
private val VOLCANO = KitPiece("volcano", "kit_dino_volcano", PieceRole.FAR, 1.7f, 0.872f, words = listOf("화산"))
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

// ── §3-2 바닷속 — 13 pieces (#97 2순위 셋째 · 10-06). Fish take the butterflies' place, bubbles the clouds' ──────────
private val JELLYFISH = KitPiece("jellyfish", "kit_sea_jellyfish", PieceRole.SKY_ANCHOR, 0.45f, 0.829f, PieceBase.CENTER,
    tilt = 6f, words = listOf("해파리"))
private val BUBBLE = KitPiece("bubble", "kit_sea_bubble", PieceRole.SKY_FILL, 0.08f, 1.003f, PieceBase.CENTER, copies = 5,
    words = listOf("거품", "방울"))
private val YELLOW_FISH = KitPiece("yellow_fish", "kit_sea_yellow_fish", PieceRole.FLOAT, 0.25f, 1.073f, PieceBase.CENTER,
    copies = 2, words = listOf("물고기", "물고기 떼"))
private val CLOWNFISH = KitPiece("clownfish", "kit_sea_clownfish", PieceRole.FLOAT, 0.25f, 1.079f, PieceBase.CENTER,
    copies = 1, words = listOf("물고기", "니모"))
private val PINK_CORAL = KitPiece("pink_coral", "kit_sea_pink_coral", PieceRole.LANDMARK, 1.0f, 0.978f, words = listOf("산호"))
private val FAN_CORAL = KitPiece("fan_coral", "kit_sea_fan_coral", PieceRole.COVER, 0.5f, 1.099f, words = listOf("산호"))
private val SEAWEED = KitPiece("seaweed", "kit_sea_seaweed", PieceRole.LANDMARK, 1.4f, 0.609f, words = listOf("미역", "해초"))
private val SEAWEED_FAR = SEAWEED.copy(name = "seaweed_far", role = PieceRole.FAR)
private val SEAWEED_FRONT = SEAWEED.copy(name = "seaweed_front", role = PieceRole.FOREGROUND, size = 0.9f)
private val CHEST = KitPiece("chest", "kit_sea_chest", PieceRole.LANDMARK, 0.6f, 1.112f, flip = false, words = listOf("보물", "상자"))
private val CLAM = KitPiece("clam", "kit_sea_clam", PieceRole.COVER, 0.3f, 0.973f, words = listOf("진주", "조개"))
private val SCALLOP = KitPiece("scallop", "kit_sea_scallop", PieceRole.COVER, 0.2f, 1.111f, words = listOf("조개"))
private val STARFISH = KitPiece("starfish", "kit_sea_starfish", PieceRole.COVER, 0.2f, 1.023f, words = listOf("불가사리"))
private val SEA_ROCK = KitPiece("sea_rock", "kit_sea_sea_rock", PieceRole.COVER, 0.4f, 1.134f, words = listOf("돌", "바위"))
private val REEF = KitPiece("reef", "kit_sea_reef", PieceRole.FAR, 1.2f, 1.486f, words = listOf("동굴", "바위"))

/** 바닷속 — 청록 물빛 · 모래 (doc §1 table); the far felt hills read as a reef */
val SEA_KIT = SceneKitDef(
    key = "sea",
    skyTop = 0xFF2A7F92, skyBottom = 0xFF7CC6C4,
    ground = 0xFFE4D2A2,
    hillFar = 0xFF4A95A2, hillNear = 0xFF6CB0AE,
    pieces = listOf(
        JELLYFISH, BUBBLE, YELLOW_FISH, CLOWNFISH,
        REEF, SEAWEED_FAR,
        PINK_CORAL, SEAWEED, CHEST,
        FAN_CORAL, CLAM, SCALLOP, STARFISH, SEA_ROCK,
        SEAWEED_FRONT,
    ),
)

// ── §3-6 실내 — 12 pieces (#97 3순위 · 10-06). The sky is a wall: window, picture and clock hang on it ─────────────
// The felt hills read as a wainscot along the wall; no far band (doc §3-6), no wind, no visitors
private val WINDOW = KitPiece("window", "kit_indoor_window", PieceRole.SKY_ANCHOR, 0.8f, 0.980f, PieceBase.CENTER, flip = false,
    words = listOf("창문"))
private val FRAME = KitPiece("frame", "kit_indoor_frame", PieceRole.SKY_FILL, 0.3f, 0.871f, PieceBase.CENTER, flip = false,
    copies = 1, words = listOf("그림", "액자"))
private val CLOCK = KitPiece("clock", "kit_indoor_clock", PieceRole.SKY_FILL, 0.25f, 1.003f, PieceBase.CENTER, flip = false,
    copies = 1, words = listOf("시계"))
private val SOFA = KitPiece("sofa", "kit_indoor_sofa", PieceRole.LANDMARK, 0.8f, 1.374f, words = listOf("소파", "의자"))
private val BED = KitPiece("bed", "kit_indoor_bed", PieceRole.LANDMARK, 0.9f, 1.169f, words = listOf("침대"))
private val BOOKSHELF = KitPiece("bookshelf", "kit_indoor_bookshelf", PieceRole.LANDMARK, 1.4f, 0.862f, flip = false,
    words = listOf("책", "책장"))
private val TABLE = KitPiece("table", "kit_indoor_table", PieceRole.LANDMARK, 0.6f, 1.328f, words = listOf("탁자", "책상"))
private val TOY_BOX = KitPiece("toy_box", "kit_indoor_toy_box", PieceRole.COVER, 0.4f, 1.112f, copies = 1,
    words = listOf("장난감", "상자"))
private val TEDDY = KitPiece("teddy", "kit_indoor_teddy", PieceRole.COVER, 0.4f, 0.790f, copies = 1, words = listOf("곰 인형", "인형"))
private val BLOCKS = KitPiece("blocks", "kit_indoor_blocks", PieceRole.COVER, 0.2f, 0.722f, flip = false, words = listOf("블록"))
private val POTTED_PLANT = KitPiece("potted_plant", "kit_indoor_potted_plant", PieceRole.COVER, 0.5f, 0.734f,
    copies = 1, words = listOf("화분", "꽃"))
private val RUG = KitPiece("rug", "kit_indoor_rug", PieceRole.FLAT, 0.1f, 3.021f, words = listOf("러그", "카펫"))

/** 실내 — 벽지 · 나무 마루 (doc §1 table) */
val INDOOR_KIT = SceneKitDef(
    key = "indoor",
    skyTop = 0xFFF1E0C0, skyBottom = 0xFFF8EEDB,
    ground = 0xFFC6935E,
    hillFar = 0xFFE9D3AC, hillNear = 0xFFDFC496,
    hills = false,
    pieces = listOf(
        WINDOW, FRAME, CLOCK,
        SOFA, BED, BOOKSHELF, TABLE,
        TOY_BOX, TEDDY, BLOCKS, POTTED_PLANT,
        RUG,
    ),
)

// ── §3-5 숲·시골 — 10 pieces + the common nine (#97 4순위 · 10-06). The bird lives here (doc §6-3) ────────────
private val COTTAGE = KitPiece("cottage", "kit_forest_cottage", PieceRole.LANDMARK, 1.6f, 0.997f, flip = false,
    words = listOf("집", "할머니 집"))
private val PINE = KitPiece("pine", "kit_forest_pine", PieceRole.LANDMARK, 1.9f, 0.679f, words = listOf("소나무", "나무"))
private val PINE_FAR = PINE.copy(name = "pine_far", role = PieceRole.FAR)
private val MOUNTAIN = KitPiece("mountain", "kit_forest_mountain", PieceRole.FAR, 2.0f, 1.221f, words = listOf("산"))
private val MUSHROOM = KitPiece("mushroom", "kit_forest_mushroom", PieceRole.COVER, 0.25f, 0.920f, words = listOf("버섯"))
private val STUMP = KitPiece("stump", "kit_forest_stump", PieceRole.COVER, 0.3f, 1.180f, words = listOf("나무", "그루터기"))
private val FENCE = KitPiece("fence", "kit_forest_fence", PieceRole.COVER, 0.4f, 1.693f, flip = false, copies = 1,
    words = listOf("울타리"))
private val CARROTS = KitPiece("carrots", "kit_forest_carrots", PieceRole.COVER, 0.3f, 0.614f, words = listOf("당근", "밭"))
private val APPLE_TREE = KitPiece("apple_tree", "kit_forest_apple_tree", PieceRole.LANDMARK, 1.6f, 0.893f,
    words = listOf("사과", "사과나무"))
private val POND = KitPiece("pond", "kit_forest_pond", PieceRole.FLAT, 0.15f, 1.996f, words = listOf("연못", "물"))
private val SUNFLOWER = KitPiece("sunflower", "kit_forest_sunflower", PieceRole.COVER, 0.6f, 0.607f, words = listOf("해바라기", "꽃"))

/** 숲·시골 — 하늘색 · 짙은 풀밭 (doc §1 table) */
val FOREST_KIT = SceneKitDef(
    key = "forest",
    skyTop = 0xFF8CC8EC, skyBottom = 0xFFD0EAF6,
    ground = 0xFF5F9A49,
    hillFar = 0xFF5E8F58, hillNear = 0xFF7AA867,
    pieces = listOf(
        SUN, CLOUD, BUTTERFLY,
        MOUNTAIN, PINE_FAR, TREE_FAR,
        COTTAGE, PINE, APPLE_TREE, TREE,
        MUSHROOM, STUMP, FENCE, CARROTS, SUNFLOWER, BUSH, TULIP, DAISY, STONE, GRASS,
        POND,
        GRASS_FRONT,
    ),
)

// ── §3-8 바닷가 — 8 pieces (#97 5순위 · 10-06), with the sun · cloud, the sea's shells and the dinosaur land's palm ───
// The far felt hills are the sea (doc §3-8 「먼 띠가 바다」)
private val UMBRELLA = KitPiece("umbrella", "kit_beach_umbrella", PieceRole.LANDMARK, 1.4f, 0.978f, words = listOf("파라솔", "우산"))
private val SANDCASTLE = KitPiece("sandcastle", "kit_beach_sandcastle", PieceRole.COVER, 0.5f, 0.927f, flip = false, copies = 1,
    words = listOf("모래성", "성"))
private val BUCKET = KitPiece("bucket", "kit_beach_bucket", PieceRole.COVER, 0.25f, 0.828f, copies = 1, words = listOf("양동이", "삽"))
private val BEACH_BALL = KitPiece("beach_ball", "kit_beach_beach_ball", PieceRole.COVER, 0.2f, 1.009f, copies = 1, words = listOf("공"))
private val SWIM_RING = KitPiece("swim_ring", "kit_beach_swim_ring", PieceRole.COVER, 0.3f, 1.008f, copies = 1, words = listOf("튜브"))
private val LIGHTHOUSE = KitPiece("lighthouse", "kit_beach_lighthouse", PieceRole.LANDMARK, 2.0f, 0.487f, flip = false,
    words = listOf("등대"))
private val SAILBOAT = KitPiece("sailboat", "kit_beach_sailboat", PieceRole.FAR, 0.8f, 0.836f, words = listOf("배", "돛단배"))
private val SEAGULL = KitPiece("seagull", "kit_beach_seagull", PieceRole.SKY_FILL, 0.15f, 2.365f, PieceBase.CENTER, copies = 2,
    words = listOf("갈매기", "새"))
private val SCALLOP_BEACH = SCALLOP.copy(name = "scallop_beach")
private val STARFISH_BEACH = STARFISH.copy(name = "starfish_beach")

/** 바닷가 — 하늘색 · 모래, 먼 띠가 바다 (doc §1 table) */
val BEACH_KIT = SceneKitDef(
    key = "beach",
    skyTop = 0xFF8CC8EC, skyBottom = 0xFFD6EEF7,
    ground = 0xFFEBD8A6,
    hillFar = 0xFF4E9CC4, hillNear = 0xFF79BCD8,
    pieces = listOf(
        SUN, CLOUD, SEAGULL,
        SAILBOAT,
        UMBRELLA, LIGHTHOUSE, PALM,
        SANDCASTLE, BUCKET, BEACH_BALL, SWIM_RING, SCALLOP_BEACH, STARFISH_BEACH,
    ),
)

// ── §3-7 눈 나라 — 10 pieces (#97 5순위 · 10-06), with the common sun and stone ─────────────────────────────────
private val SNOWFLAKE = KitPiece("snowflake", "kit_snow_snowflake", PieceRole.SKY_FILL, 0.1f, 0.908f, PieceBase.CENTER,
    tilt = 30f, copies = 6, words = listOf("눈", "눈송이"))
private val SNOWMAN = KitPiece("snowman", "kit_snow_snowman", PieceRole.LANDMARK, 1.0f, 0.869f, words = listOf("눈사람"))
private val IGLOO = KitPiece("igloo", "kit_snow_igloo", PieceRole.LANDMARK, 1.1f, 1.234f, words = listOf("이글루", "얼음집"))
private val SNOW_PINE = KitPiece("snow_pine", "kit_snow_snow_pine", PieceRole.LANDMARK, 1.9f, 0.668f, words = listOf("나무", "소나무"))
private val SNOW_PINE_FAR = SNOW_PINE.copy(name = "snow_pine_far", role = PieceRole.FAR)
private val SNOW_MOUNTAIN = KitPiece("snow_mountain", "kit_snow_snow_mountain", PieceRole.FAR, 2.0f, 1.440f, words = listOf("산"))
private val SLED = KitPiece("sled", "kit_snow_sled", PieceRole.COVER, 0.4f, 1.036f, copies = 1, words = listOf("썰매"))
private val SNOWBALLS = KitPiece("snowballs", "kit_snow_snowballs", PieceRole.COVER, 0.2f, 1.042f, words = listOf("눈덩이", "눈"))
private val ICE_ROCK = KitPiece("ice_rock", "kit_snow_ice_rock", PieceRole.COVER, 0.35f, 1.029f, words = listOf("얼음", "바위"))
private val SNOW_BUSH = KitPiece("snow_bush", "kit_snow_snow_bush", PieceRole.COVER, 0.4f, 1.203f, words = listOf("덤불"))
private val SNOW_BUSH_FRONT = SNOW_BUSH.copy(name = "snow_bush_front", role = PieceRole.FOREGROUND, size = 0.6f)
private val CABIN = KitPiece("cabin", "kit_snow_cabin", PieceRole.LANDMARK, 1.5f, 0.998f, flip = false, words = listOf("집"))

/** 눈 나라 — 연회색 하늘 · 눈밭 (doc §1 table) */
val SNOW_KIT = SceneKitDef(
    key = "snow",
    // blue-grey, not grey — white flakes and snow pieces were lost on a grey sky (10-06 device · #222)
    skyTop = 0xFF93ABC8, skyBottom = 0xFFCCD9E8,
    ground = 0xFFF4F6FA,
    hillFar = 0xFFCBD6E4, hillNear = 0xFFDDE5EF,
    pieces = listOf(
        SUN, SNOWFLAKE,
        SNOW_MOUNTAIN, SNOW_PINE_FAR,
        SNOWMAN, IGLOO, SNOW_PINE, CABIN,
        SLED, SNOWBALLS, ICE_ROCK, SNOW_BUSH, STONE,
        SNOW_BUSH_FRONT,
    ),
)

/** 「산」 as a word — 「산에 갔어」 · 「뒷산」 · 「등산」, not 「우산」 · 「산타」 · 「산책」(park) (10-06 · d3) */
private val MOUNTAIN_WORD = Regex("(^|\\s|뒷|앞|등)산($|\\s|에|으로|속|길|꼭대기|위|이|을)")

object SceneKits {
    /**
     * **The one switch** (10-05). On: a live story whose place is not one of the three app themes is drawn
     * from a kit, and no `/image` background is requested for it. Off: the documented alternative —
     * a background generated during the session (`StoryLiveFlow.updateBackground`, rule 8 as written today).
     * A `var` so tests of the generated-background path can turn it off.
     */
    @Volatile var liveStory: Boolean = true

    val all: Map<String, SceneKitDef> = mapOf(PARK_KIT.key to PARK_KIT, DINO_KIT.key to DINO_KIT, SPACE_KIT.key to SPACE_KIT, SEA_KIT.key to SEA_KIT,
        INDOOR_KIT.key to INDOOR_KIT, FOREST_KIT.key to FOREST_KIT, BEACH_KIT.key to BEACH_KIT, SNOW_KIT.key to SNOW_KIT)

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
        // 「바다」 alone is the beach group in the doc (§1 — 바닷가), not under water
        listOf("바닷속", "바다 밑", "바다 속", "물속", "용궁", "해저").any { it in place } -> SEA_KIT
        listOf("눈 나라", "눈나라", "겨울", "북극", "눈사람", "이글루", "남극").any { it in place } -> SNOW_KIT
        listOf("바닷가", "해변", "바다", "모래사장", "해수욕").any { it in place } -> BEACH_KIT
        // 할머니 집 · 농장 is the countryside (doc §1), checked before the plain 「집」 of indoor
        listOf("숲", "할머니", "농장", "시골", "밭", "목장").any { it in place } || MOUNTAIN_WORD.containsMatchIn(place) -> FOREST_KIT
        listOf("집", "방", "어린이집", "유치원", "교실", "거실", "학교").any { it in place } -> INDOOR_KIT
        else -> null
    }
}
