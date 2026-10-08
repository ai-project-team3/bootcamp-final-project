package com.example.finalproject_demo.net

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/*
 * ── 문제 신고를 서버로 (#283 · docs/문제신고_서버_설계_283.md §1 · §2 · guidelines/3 §3-6) ─────────────────────────
 *
 * 보내는 것은 분류 · 모드 · 쪽 · 보호자 설명 · 앱 버전, 그리고 **보호자가 체크했을 때만** 그 쪽에서 오또(AI)가 만든 그림 하나 또는
 * 문장 하나. **아이가 그린 그림 · 녹음한 소리 · 목소리 · 화면 캡처는 어떤 길로도 실리지 않는다** — 이 파일은 획 · 소리 · 화면을
 * 매개변수로도 받지 않는다(`ReportPayloadTest` 가 소스를 검사한다). 그림 첨부는 저장된 AI 그림 파일([AiPicture])로만 만든다.
 *
 * 서버에 닿지 못하면(없음 · 연결 실패 · 10초 · 429 · 503 …) 보호자가 [mailIntent] 로 메일을 연다 — 메일에는 첨부를 싣지 않는다
 * (받은편지함에 기한 없이 남아 「처리 후 30일」 약속을 지킬 수 없다).
 */

/** 첨부 그림이 어디서 왔나 — 서버 `attachment.source` 와 같은 낱말 */
enum class AiPictureSource(val wire: String, val label: String) {
    BACKGROUND("background", "배경"),
    HERO("hero", "주인공 인형"),
    FRIEND("friend", "친구 인형"),
    DIARY_OTTO("diary_otto", "오또가 그린 그림"),
}

/** 신고 분류 — 서버 낱말 · 화면 이름(「부적절한 말」 → 「부적절한 표현」 · 이슈 문구) */
enum class ReportCategory(val wire: String, val label: String) {
    IMAGE("image", "부적절한 그림"),
    TEXT("text", "부적절한 표현"),
    ERROR("error", "오류"),
    OTHER("other", "기타"),
}

/**
 * 첨부 후보 그림 하나 — 저장된 AI 그림의 **파일 경로**(동화 · 협업 `story_images/`) 또는 **바이트**(그림일기 오또 그림).
 * 아이 그림(획)에서는 만들 수 없다: 이 클래스에는 획을 받는 길이 없다.
 */
data class AiPicture(val source: AiPictureSource, val path: String? = null, val png: ByteArray? = null) {
    /** 화면 미리 보기 · 보내기에 쓰는 비트맵 — 못 읽으면 null */
    fun bitmap(): Bitmap? = when {
        png != null -> BitmapFactory.decodeByteArray(png, 0, png.size)
        path != null -> BitmapFactory.decodeFile(path)
        else -> null
    }

    /** 보낼 첨부 — 긴 변 1024 · JPEG 85 · 투명은 흰 바탕. 못 읽으면 null */
    fun toAttachment(): ReportAttachment.Picture? = bitmap()?.let { ReportAttachment.Picture(source, reencodeForReport(it)) }

    override fun equals(other: Any?) = other is AiPicture && other.source == source && other.path == path &&
        (other.png?.contentEquals(png) ?: (png == null))
    override fun hashCode() = 31 * (31 * source.hashCode() + (path?.hashCode() ?: 0)) + (png?.contentHashCode() ?: 0)
}

sealed interface ReportAttachment {
    /** AI 그림 — [AiPicture.toAttachment] 로만 만든다(생성자가 internal) */
    class Picture internal constructor(val source: AiPictureSource, val jpeg: ByteArray) : ReportAttachment
    /** 앱에 든 배경 그림(프리셋) — 우리 그림이라 파일 대신 이름만 */
    data class Preset(val name: String) : ReportAttachment
    /** AI 가 쓴 쪽 문장 — 아이 호칭 · 친구 이름을 자리표시로 바꾼 것([maskForReport]) · 500자 */
    class Sentence private constructor(val masked: String) : ReportAttachment {
        companion object {
            fun of(text: String, names: ReportNames): Sentence = Sentence(maskForReport(text, names).take(SENTENCE_MAX))
        }
    }
}

/** 문장에서 가릴 이름 — 오또가 부르는 아이 호칭 · 주인공 이름(「주인공」으로) · 이야기 속 친구 이름(「친구1」 …) */
data class ReportNames(val child: String?, val friends: List<String> = emptyList(), val heroes: List<String> = emptyList())

/**
 * 이름을 자리표시로 — 아이 호칭은 「주인공」, 친구는 「친구1」 · 「친구2」. 긴 이름부터 바꿔 「지민」 안의 「민」 같은 겹침을 피한다.
 * (`NameMask.mask` 는 10-02 부터 이름을 그대로 둔다 — 신고는 운영자 서버에 30일 남으니 여기서 따로 가린다 · 설계 §1-2)
 */
fun maskForReport(text: String, names: ReportNames): String {
    val marks = buildList {
        val heroes = (listOfNotNull(names.child) + names.heroes).map(String::trim).filter(String::isNotEmpty).distinct()
        heroes.forEach { add(it to "주인공") }
        names.friends.map(String::trim).filter { it.isNotEmpty() && it !in heroes && it !in GENERIC }.distinct()
            .forEachIndexed { i, n -> add(n to "친구${i + 1}") }
    }.sortedByDescending { it.first.length }
    var out = text
    marks.forEach { (name, mark) -> out = out.replace(name, mark) }
    return out
}

/** 이름이 아닌 부름말 — 가리지 않는다(「친구」를 「친구1」로 바꾸면 오히려 이상하다) */
private val GENERIC = setOf("친구", "친구들", "주인공", "아이", "오또")

const val SENTENCE_MAX = 500
const val NOTE_MAX = 1000

data class ReportPayload(
    val category: ReportCategory,
    /** story · diary · coop — 설정에서 왔으면 null */
    val mode: String?,
    val page: Int?,
    val note: String,
    val appVersion: String,
    val attachment: ReportAttachment?,
) {
    /** 서버 `POST /report` 본문 — 키는 이 여섯과 첨부 안의 다섯뿐(그 밖의 키는 서버가 422) */
    fun toJson(): JSONObject = JSONObject().apply {
        put("category", category.wire)
        put("mode", mode ?: JSONObject.NULL)
        put("page", page ?: JSONObject.NULL)
        put("note", note.take(NOTE_MAX))
        put("app_version", appVersion.take(32).ifBlank { "?" })
        put("attachment", attachment?.let(::attachmentJson) ?: JSONObject.NULL)
    }

    private fun attachmentJson(a: ReportAttachment): JSONObject = when (a) {
        is ReportAttachment.Picture -> JSONObject().put("kind", "image").put("source", a.source.wire)
            .put("data_base64", Base64.encodeToString(a.jpeg, Base64.NO_WRAP))
        is ReportAttachment.Preset -> JSONObject().put("kind", "preset").put("name", a.name)
        is ReportAttachment.Sentence -> JSONObject().put("kind", "text").put("text", a.masked)
    }
}

/** 보낸 결과 — 접수 번호, 또는 서버에 닿지 못함(메일 길로) */
sealed interface ReportResult {
    data class Accepted(val id: String) : ReportResult
    data object Unreached : ReportResult
}

object ReportUpload {
    /** 신고를 받는 메일 — 처리방침 · 스토어 등록정보의 연락처와 같다 */
    const val MAIL_TO = "ljh11442@gmail.com"

    /** 테스트가 서버 대신 넣는다 — null 이면 진짜 서버(`Server.report`) */
    @Volatile internal var sendOverride: (suspend (JSONObject) -> String?)? = null

    suspend fun send(p: ReportPayload): ReportResult {
        val id = (sendOverride ?: { body -> Server.report(body) })(p.toJson())
        return if (id.isNullOrBlank()) ReportResult.Unreached else ReportResult.Accepted(id)
    }

    /**
     * 서버에 닿지 못했을 때 보호자가 누르면 여는 메일 — 분류 · 모드 · 쪽 · 설명 · 앱 버전만. **첨부는 읽지도 않는다**
     */
    fun mailIntent(p: ReportPayload): Intent {
        val subject = "[오또 신고] ${p.category.label}"
        val body = mailBody(p)
        return Intent(Intent.ACTION_SENDTO).apply {
            // 일부 메일 앱은 EXTRA_* 를 무시하고 mailto 주소의 질의만 읽는다 — 둘 다 싣는다
            data = Uri.parse("mailto:$MAIL_TO?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(MAIL_TO))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }

    internal fun mailBody(p: ReportPayload): String = buildString {
        appendLine("사유: ${p.category.label}")
        if (p.mode != null) appendLine("어디서: ${modeLabel(p.mode)}${p.page?.let { " · ${it}쪽" } ?: ""}")
        appendLine()
        appendLine(p.note.ifBlank { "(적은 내용 없음)" })
        appendLine()
        append("앱 버전: ${p.appVersion}")
    }

    fun modeLabel(mode: String?): String = when (mode) {
        "story" -> "동화"
        "diary" -> "그림일기"
        "coop" -> "같이 만들기"
        else -> "책 밖(설정)"
    }
}

/** 긴 변 1024 · JPEG 85 · 투명은 흰 바탕에 — 오또 그림(투명 PNG)도, 배경도 같은 모양으로 (설계 §2-5) */
internal fun reencodeForReport(src: Bitmap, longSide: Int = 1024, quality: Int = 85): ByteArray {
    val scale = minOf(1f, longSide.toFloat() / maxOf(src.width, src.height))
    val w = maxOf(1, (src.width * scale).toInt()); val h = maxOf(1, (src.height * scale).toInt())
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(out).apply {
        drawColor(Color.WHITE)
        drawBitmap(Bitmap.createScaledBitmap(src, w, h, true), 0f, 0f, null)
    }
    return ByteArrayOutputStream().use { o -> out.compress(Bitmap.CompressFormat.JPEG, quality, o); o.toByteArray() }
}
