package com.example.finalproject_demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryBookInput
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.GeneratedFriend
import com.example.finalproject_demo.demo.Hero
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.SavedDiaryBook
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.SavedStoryPage
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.captureStoryVisuals
import com.example.finalproject_demo.demo.reportBookOf
import com.example.finalproject_demo.net.AiPicture
import com.example.finalproject_demo.net.AiPictureSource
import com.example.finalproject_demo.net.ReportAttachment
import com.example.finalproject_demo.net.ReportCategory
import com.example.finalproject_demo.net.ReportNames
import com.example.finalproject_demo.net.ReportPayload
import com.example.finalproject_demo.net.ReportUpload
import com.example.finalproject_demo.net.maskForReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * #283 ② — 신고에 실리는 것 (설계 §7-1 A1 · A2 · A3 · A4 · A5 · A6).
 * 아이가 그린 그림 · 녹음 · 목소리는 **어떤 길로도** 실리지 않는다: 출처(후보) · 키(본문) · 코드(파일) 세 겹으로 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportPayloadTest {
    private val topKeys = setOf("category", "mode", "page", "note", "app_version", "attachment")
    private val attachmentKeys = setOf("kind", "source", "data_base64", "name", "text")

    private fun payload(att: ReportAttachment?) =
        ReportPayload(ReportCategory.IMAGE, "story", 3, "주인공 얼굴이 무서워요", "0.5-closed", att)

    private fun keys(o: org.json.JSONObject) = o.keys().asSequence().toSet()

    /** A1 — 체크하지 않으면 첨부는 null */
    @Test
    fun noTickNoAttachment() {
        val j = payload(null).toJson()
        assertTrue(j.isNull("attachment"))
        assertEquals("image", j.getString("category"))
        assertEquals(3, j.getInt("page"))
    }

    /** A4 — 어떤 경우에도 키는 서버가 받는 것뿐 */
    @Test
    fun onlyTheKeysTheServerTakes() {
        val png = File.createTempFile("bgx", ".png").apply { deleteOnExit() }
        Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
            .compress(Bitmap.CompressFormat.PNG, 100, png.outputStream())
        val cases = listOf(
            null,
            ReportAttachment.Preset("forest_day"),
            ReportAttachment.Sentence.of("지민이는 숲에 갔어요", ReportNames("지민")),
            AiPicture(AiPictureSource.BACKGROUND, path = png.path).toAttachment(),
        )
        cases.forEach { a ->
            val j = payload(a).toJson()
            assertEquals(topKeys, keys(j))
            if (!j.isNull("attachment")) assertTrue(attachmentKeys.containsAll(keys(j.getJSONObject("attachment"))))
        }
    }

    /** A2 — 그림 첨부는 저장된 AI 그림을 다시 구운 JPEG(긴 변 1024 · 투명은 흰 바탕) */
    @Test
    fun aPictureIsTheSavedAiPictureReencoded() {
        val png = File.createTempFile("otto", ".png").apply { deleteOnExit() }
        val src = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.TRANSPARENT) }
        for (x in 0 until 800) for (y in 0 until 900) src.setPixel(x, y, android.graphics.Color.RED)
        src.compress(Bitmap.CompressFormat.PNG, 100, png.outputStream())
        val att = AiPicture(AiPictureSource.DIARY_OTTO, path = png.path).toAttachment()!!
        val back = BitmapFactory.decodeByteArray(att.jpeg, 0, att.jpeg.size)
        assertEquals(1024, maxOf(back.width, back.height))
        assertEquals(576, back.height)
        val left = back.getPixel(100, 300); val right = back.getPixel(900, 300)
        assertTrue("빨간 쪽", android.graphics.Color.red(left) > 200 && android.graphics.Color.green(left) < 60)
        assertTrue("투명은 흰 바탕", android.graphics.Color.red(right) > 230 && android.graphics.Color.green(right) > 230)
        assertEquals("diary_otto", payload(att).toJson().getJSONObject("attachment").getString("source"))
    }

    /** A6 — 문장의 아이 호칭 · 주인공 · 친구 이름은 자리표시로, 500자까지 */
    @Test
    fun namesBecomePlaceholders() {
        val names = ReportNames("지민", friends = listOf("뽀삐", "친구"), heroes = listOf("지민이"))
        assertEquals("주인공는 친구1과 친구랑 놀았어요", maskForReport("지민이는 뽀삐과 친구랑 놀았어요", names))
        val long = ReportAttachment.Sentence.of("지민".repeat(400), names)
        assertEquals(500, long.masked.length)
        assertFalse(long.masked.contains("지민"))
    }

    /** A3 — 후보는 출처 표뿐: 서버가 만든 파일 · 프리셋 이름 · 오또 그림. 아이 획은 아무리 있어도 후보가 아니다 */
    @Test
    fun candidatesComeFromSavedAiFilesOnly() {
        val d = Director(CoroutineScope(SupervisorJob()))
        val child = listOf(Stroke(Color.Red, listOf(Offset(0f, 0f), Offset(10f, 10f))))
        d.s.templateKey = "A"
        d.s.drawing.addAll(child)
        val v = d.s.captureStoryVisuals().copy(
            hero = Hero("지민", HeroAttr(), image = "local:/data/story_images/hero1.png"),
            friend = GeneratedFriend("공룡", "local:/data/story_images/friend1.png", null),
            drawing = child,
            friendName = "뽀삐",
        )
        val story = SavedStoryBook("b1", "숲 이야기", "forest", "local:/data/story_images/bg1.png",
            listOf(SavedStoryPage(PageKind.DEPART, "지민이는 뽀삐랑 숲에 갔어요")), v)
        val r = reportBookOf(story, "story", "지민")
        assertEquals(listOf(AiPictureSource.BACKGROUND, AiPictureSource.HERO, AiPictureSource.FRIEND), r.pictures.map { it.source })
        assertTrue(r.pictures.all { it.path!!.contains("story_images") && it.png == null })
        assertNull(r.presetBackground)
        val preset = reportBookOf(story.copy(bgName = "bg_forest", visuals = v.copy(hero = Hero("지민", HeroAttr()), friend = null)), "coop", "지민")
        assertEquals(emptyList<AiPicture>(), preset.pictures)
        assertEquals("bg_forest", preset.presetBackground)

        val otto = byteArrayOf(1, 2, 3)
        val diary = SavedDiaryBook("d1", "오늘", "2026-10-07", 2, DiaryBookInput(mapOf("what" to "놀이터에 갔어")),
            listOf(DiaryPiece(1, child, "미끄럼틀"), DiaryPiece(2, child, "엄마", ottoPng = otto)))
        val dr = reportBookOf(diary, "지민")
        assertEquals(1, dr.pictures.size)
        assertEquals(AiPictureSource.DIARY_OTTO, dr.pictures[0].source)
        assertTrue(dr.pictures[0].png!!.contentEquals(otto))
    }

    /** A5 — 신고 코드가 아이 원본 · 화면을 부르지 않는다(이 목록을 늘리면 검사도 늘린다) */
    @Test
    fun theReportCodeNeverTouchesTheChildsOriginals() {
        val root = listOf("src/main/java/com/example/finalproject_demo", "app/src/main/java/com/example/finalproject_demo").map(::File).first { it.isDirectory }
        val forbidden = listOf("drawing", "sceneDrawing", "strokes", "ChildSound", "soundClip", "captureToImage", "PixelCopy")
        val upload = File(root, "net/ReportUpload.kt").readText()
        forbidden.forEach { assertFalse("net/ReportUpload.kt 가 $it 를 쓴다", upload.contains(it)) }
        // 후보 쪽은 획을 「후보가 아닌 것」으로 설명만 한다 — 코드에서 획 · 소리 값을 읽지 않는다
        val cand = File(root, "demo/ReportCandidates.kt").readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }
            .joinToString("\n")
        listOf(".drawing", ".strokes", "sceneDrawing", "ChildSound", "soundClip", "PixelCopy").forEach {
            assertFalse("demo/ReportCandidates.kt 가 $it 를 읽는다", cand.contains(it))
        }
    }

    /** A7 일부 — 메일 대체에는 첨부가 실리지 않는다 */
    @Test
    fun mailCarriesNoAttachment() {
        val p = payload(ReportAttachment.Sentence.of("비밀 문장 지민", ReportNames("지민")))
        val body = ReportUpload.mailBody(p)
        assertTrue("부적절한 그림" in body && "동화 · 3쪽" in body && "주인공 얼굴이 무서워요" in body)
        assertFalse("비밀 문장" in body)
    }
}
