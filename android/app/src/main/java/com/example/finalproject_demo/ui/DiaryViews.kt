package com.example.finalproject_demo.ui

import androidx.compose.runtime.Composable
import com.example.finalproject_demo.demo.DiaryStage
import com.example.finalproject_demo.demo.Director

/**
 * 그림일기 화면 (docs/일기모드_흐름.html) — 조각 화이트보드(D1) · 그림일기 한 쪽(D5).
 *
 * `StageView` 가 [DiaryStage] 를 여기로 넘긴다(#28). 이 파일과 [DiaryStage] 는 일기 모드(박진웅) 것이라
 * 그림일기 화면을 더해도 `Screen.kt` 를 다시 고치지 않는다.
 */
@Composable
fun DiaryStageView(d: Director, stage: DiaryStage) {
    // 화면은 3단계(D1 · D5)에서 채운다. 지금은 어떤 흐름도 DiaryStage 를 띄우지 않는다
}
