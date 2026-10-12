package com.example.finalproject_demo

import com.example.finalproject_demo.net.Voice
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 그림일기에서 오또가 늘 같은 말로 하는 대사는 앱에 구운 목소리로 바로 나간다 — `/tts` 로 가지 않는다 (10-06 진웅).
 * 「여기는 어디야?」는 주석에 「구운 목소리」라고 적혀 있었지만 실제로는 구워져 있지 않았다.
 * 문구를 바꾸면 이 테스트가 깨진다 — `eval/bake_lines.py` 로 다시 굽고 여기 목록도 고친다.
 */
class DiaryBakedLinesTest {
    private val lines = listOf(
        "여기는 어디야?",
        "잘 못 들었어. 뭐 그린 거야?",
        "이건 뭐야? 다시 말해 줘!",
        "내일은 뭐 하고 싶어?",
        "내일 또 하고 싶은 거 있어?",
        "오늘 이야기 정말 많이 했다! 이제 그림일기로 만들어 볼까?",
        "그렇구나!",
        "그렇구나! 계속 그려 봐.",
        "그래, 계속 그려 봐.",
        "괜찮아, 계속 그려 봐!",
        "좋아, 더 그려 봐!",
        "좋아, 더 이야기해 줘!",
        "그래, 그대로 둘게!",
        "그랬구나!",
        "응응!",
        "오또 그림은 조금 뒤에 올 거야! 계속 그리고 있어.",
        "그림을 먼저 그려 줘! 그다음에 나도 그려 볼게.",
        "이름표가 붙은 그림이 아직 없어!",
        // 대화 수선 (#323) — 새 음성 상한을 넘거나 대사가 없을 때 받는 말과, 그 뒤의 쉬운 질문 · 앱 질문
        com.example.finalproject_demo.demo.SORRY_LINE,
        com.example.finalproject_demo.demo.ASK_AGAIN_LINE,
        "그때 마음이 어땠어?",
        "누구랑 같이 있었어?",
        "혼자 있었어, 아니면 같이 있었어?",
    )

    @Test
    fun theDiarysFixedLinesAreBaked() {
        val missing = lines.filterNot { File("src/main/assets/voice/${Voice.bakedKey(it)}.mp3").isFile }
        assertTrue("구운 목소리가 없다 — $missing", missing.isEmpty())
    }
}
