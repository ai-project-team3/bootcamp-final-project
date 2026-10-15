package com.example.finalproject_demo

import com.example.finalproject_demo.net.Voice
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 서버 모드에서 소리로 나가는 고정 대사 — 앱에 구운 목소리로 나간다. `/tts` 로 가지 않는다 (10-14).
 * 전의 구운 목록(10-06)에 빠져 있어 매번 값을 냈다: 못 들었을 때 다시 묻기 · 동화 같이 하는 사람 · 주인공 만들기 ·
 * 이야기책 만드는 중 · 친구 그림 평 · 협업 되묻기. 이 문구를 바꾸면 이 테스트가 깨진다 — `eval/bake_lines.py` 로 다시 굽고
 * (지금 목록 + 새 줄) 여기도 고친다.
 */
class LiveBakedLinesTest {
    private val lines = mapOf(
        "demo/Director.kt" to listOf(
            "어? 소리가 작았나 봐. 한 번 더 말해 줄래?", "미안, 또 못 들었어. 천천히 한 번만 더 말해 줄래?",
            "앗, 오또 귀가 잠깐 멍해졌어. 한 번 더 말해 줄래?",
        ),
        "demo/Scenes.kt" to listOf(
            "누구랑 할까? 혼자라면 혼자라고 말해 줘도 돼.", "혼자 하는구나! 오또랑 이야기를 만들어 보자!",
            "주인공은 어떤 옷을 입고 있을까?",
            "오늘 만난 친구 그림이야. 어땠어?", "마음에 들었구나! 알려 줘서 고마워.", "그랬구나! 알려 줘서 고마워.",
        ),
        "demo/PictureDiary.kt" to listOf("내 그림을 맞춰 볼까? 조각을 끌어다 제자리에 놓아 봐!"),
    )

    @Test
    fun liveFixedLinesAreBaked() {
        val missing = lines.values.flatten().filterNot { File("src/main/assets/voice/${Voice.bakedKey(it)}.mp3").isFile }
        assertTrue("구운 목소리가 없다 — $missing", missing.isEmpty())
    }

    @Test
    fun theTestListMatchesTheCode() {
        val gone = lines.flatMap { (file, ls) ->
            val src = File("src/main/java/com/example/finalproject_demo/$file").readText()
            ls.filterNot { "\"$it\"" in src }.map { "$file: $it" }
        }
        assertTrue("코드에 없는 줄 — 문구가 바뀌었으면 다시 굽는다: $gone", gone.isEmpty())
    }
}
