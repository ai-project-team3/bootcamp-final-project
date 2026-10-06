package com.example.finalproject_demo

import com.example.finalproject_demo.net.Voice
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 방(대기 화면)에서 오또가 하는 말 — 누를 때 리액션 · 물건을 고를 때 묻는 말 · 처음 둘러보기 — 은 앱에 구운 목소리로
 * 바로 나간다. `/tts` 로 가지 않는다 (#223 · 10-06). 아이는 말풍선을 못 읽는다.
 * `ui/shell/Room.kt` 의 문구를 바꾸면 이 테스트가 깨진다 — `eval/bake_lines.py` 로 다시 굽고(지금 목록 + 새 줄) 여기도 고친다.
 */
class RoomBakedLinesTest {
    private val lines = listOf(
        // Poke — 오또를 누를 때
        "야호! 폴짝!", "빙글빙글~", "랄라~ 같이 춤출래?", "헤헤, 간지러워!", "안녕! 나 오또야", "너 좋아!",
        // Thing.question — 물건을 고를 때
        "오늘 있었던 일로 그림일기 만들래?", "오또랑 새 동화 만들래?", "부모님이 준비한 이야기 들어 볼래?", "내가 만든 책 보러 갈래?",
        "만들던 이야기 이어서 할까?", "아직 준비된 이야기가 없어!",
        // TOUR — 처음 둘러보기
        "여기는 창문이야! 오늘 있었던 일을 말하면 그림일기가 돼",
        "소파에선 엄마 아빠가 준비한 이야기를 같이 만들어",
        "책장엔 우리가 만든 책이 모여. 언제든 다시 볼 수 있어!",
        "마지막! 무대에선 상상한 동화를 만들어. 같이 해 보자!",
    )

    @Test
    fun theRoomsLinesAreBaked() {
        val missing = lines.filterNot { File("src/main/assets/voice/${Voice.bakedKey(it)}.mp3").isFile }
        assertTrue("구운 목소리가 없다 — $missing", missing.isEmpty())
    }

    @Test
    fun theTestListMatchesTheRoom() {
        val room = File("src/main/java/com/example/finalproject_demo/ui/shell/Room.kt").readText()
        val gone = lines.filterNot { "\"$it\"" in room }
        assertTrue("Room.kt 에 없는 줄 — 문구가 바뀌었으면 다시 굽는다: $gone", gone.isEmpty())
    }
}
