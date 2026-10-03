package com.example.finalproject_demo

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 실기기 QA(10-03): 같이 만들기 책을 끝까지 만들고 [책장에 꽂기]를 누르면 「책을 기기에 저장하지 못했어」가 되풀이됐다.
 * 메모리 저장소로만 검사해서 폰 안 저장(JSON)으로 가는 길을 한 번도 안 지났다 — 이 검사가 그 길을 지난다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopBookLocalSaveTest {
    @Test
    fun aRealCoopBookIsSavedToThePhoneStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("coop_books", Context.MODE_PRIVATE).edit().clear().commit()
        val made = DemoState().apply {
            mode = StoryMode.COOP
            coopPick = CoopPick("place", "동물원", "done")
            place = "맨 안쪽"; placeLabel = "동물원 맨 안쪽"; slots["place"] = "동물원 맨 안쪽까지 가 봤어요"
            companionKind = "할머니"; friend = "할머니"; slots["companion"] = "할머니와 함께 갔어요"
            problem = "길을 잃어버렸어"; slots["problem"] = "길을 잃어버렸어요"
            reaction = "무서웠어"; slots["reaction"] = "무서웠던 마음"
            // 아이가 화이트보드에 그린 할머니 (책장 그림)
            drawing.add(Stroke(Color.Red, listOf(Offset(0.1f, 0.1f), Offset(0.5f, 0.4f), Offset(0.2f, 0.8f))))
            drawingAspect = 1.6f
            title = "친구의 동물원 맨 안쪽 하루"
        }
        made.storyCaptions = (1..made.pageCount).map { "${it}쪽 문장" }
        CoopShelf.attach(made, LocalCoopBookStore(context))
        val result = CoopShelf.shelve(made)
        assertEquals(CoopShelved.SAVED, result)
        assertEquals(1, LocalCoopBookStore(context).load().size)
    }

    /**
     * 실기기에서 실제로 난 일 — 앞 빌드(글자만 저장)가 남긴 책이 있으면 새 빌드가 그 책을 못 읽어
     * 「덮어쓰면 지워진다」며 저장을 거절했다. 예전 모양 책도 읽어서 책장에 남기고, 새 책도 저장돼야 한다
     */
    @Test
    fun anOlderTextOnlyBookIsKeptAndNewBooksStillSave() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("coop_books", Context.MODE_PRIVATE)
        prefs.edit().putString("books", """[{"id":"old-1","title":"지호의 운동장 하루","themeKey":"space","bgName":"bg_today",""" +
            """"pages":[{"kind":"DEPART","caption":"운동장에 있었어요."},{"kind":"MEET","caption":"친구와 함께였어요."}]}]""").commit()
        val store = LocalCoopBookStore(context)
        val old = store.load().single()
        assertEquals("old-1", old.book.id)
        assertEquals(null, old.snapshot)

        val made = DemoState().apply {
            mode = StoryMode.COOP
            coopPick = CoopPick("place", "동물원", "done")
            place = "사자 우리"; placeLabel = "사자 우리"; slots["place"] = "사자 우리에 갔어요"
            title = "새 책"
        }
        made.storyCaptions = (1..made.pageCount).map { "${it}쪽 문장" }
        CoopShelf.attach(made, store)
        assertEquals(CoopShelved.SAVED, CoopShelf.shelve(made))
        assertEquals(listOf("새 책", "지호의 운동장 하루"), LocalCoopBookStore(context).load().map { it.book.title })
    }
}
