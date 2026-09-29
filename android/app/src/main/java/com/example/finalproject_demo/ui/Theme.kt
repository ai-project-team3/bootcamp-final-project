package com.example.finalproject_demo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * ── 오또 디자인 시스템 — 공통 값은 전부 여기 한 파일 (09-29) ─────────────────────
 *
 * 기준: 레포 `design/otto_design_system.pen` · `design/디자인시스템.md` 의 **09-28 저녁 개편판**
 *       (고양이 오또 · 새 로고 · 양모 펠트 인형극). 디자인이 또 바뀌면 이 파일만 고친다.
 *
 * ⚠️ 예전 색 이름(`Sun` · `Coral` · `Ink` …)은 **그대로 두고 값만 새 토큰을 가리킨다.**
 *    여러 사람의 화면 파일이 이 이름을 쓰고 있어서, 이름을 바꾸면 이번 주 동화 · 일기 작업과 충돌한다.
 *    새 코드는 아래 새 이름(`Wool` · `FeltCoral` …)을 쓴다.
 */

// ── 1. 색 (16개) ─────────────────────────────────────────────

// 무대 — 차분하게
val Wool = Color(0xFFFFF7EC)           // 기본 배경
val WoolCream = Color(0xFFF3E7D3)      // 카드 · 말풍선 · 아이콘 버튼
val StageWood = Color(0xFFD9A56B)      // 무대 바닥 · 책장
val StageWoodDeep = Color(0xFFA8733F)  // 나무 그림자 · 털실
val Curtain = Color(0xFFB8322E)        // 커튼 · 책 표지
val CurtainDeep = Color(0xFF7A1E1E)    // 커튼 주름
val InkBrown = Color(0xFF3A2A20)       // 글자 · 아이콘
val InkSoft = Color(0xFF8A735F)        // 보조 글자 · 비활성

// 펠트 강조 — **누를 수 있는 것과 브랜드에만** 쓴다. 배경 · 장식에 쓰지 않는다
val FeltCoral = Color(0xFFE8604C)      // 주 버튼(다음 · 고름 · 부모 주 버튼) · 로고 「오」
val FeltMustard = Color(0xFFF2B232)    // 진행 방울 · 보상 · 도구 고름 · 로고 「또」 · 후드 단추
val FeltTeal = Color(0xFF4FAF98)       // **마이크 전용** — 오또 후드 색. 「오또와 말한다」
val FeltSky = Color(0xFF5B8ED6)        // 같이 만들기 모드 (후드 안감)
val Kitten = Color(0xFFF2955A)         // 오또 털 · 작은 포인트
val Cheek = Color(0xFFF48C92)          // 좋아 · 칭찬 (발바닥 · 볼)
val FeltWhite = Color(0xFFFFFFFF)      // 펠트 위 아이콘 · 부모 카드

// 예전 이름 → 새 토큰 (값만 바뀐다)
val Bg = Wool
val Sun = FeltMustard
val Sun2 = Color(0xFFF6C862)           // 겨자의 밝은 쪽 — 반짝임 · 윤기 줄
val Orange = Kitten
val Coral = FeltCoral
val Coral2 = Color(0xFFEF8574)         // 코랄의 밝은 쪽
val Ink = InkBrown
val Muted = InkSoft
val CardWhite = FeltWhite
val Green = FeltTeal
val Blue = FeltSky
val Purple = Color(0xFFB39DDB)         // 디자인 시스템에 없음 — 쓰는 곳이 없어지면 지운다
val Pink = Cheek

// ── 2. 글자 ─────────────────────────────────────────────────

/** 아이 화면 — 둥근 획 (펠트 로고 글자와 가장 비슷하다). 아이 화면의 글자는 **읽지 못해도 괜찮은 보조** */
val KidFont = FontFamily(Font(com.example.finalproject_demo.R.font.jua))

/** 부모 화면 — 긴 글을 읽는 본문용 고딕. 안드로이드 기본 한글 글꼴(Noto Sans CJK · 제조사 글꼴)을 쓴다 */
val ParentFont = FontFamily.SansSerif

object TextSize {
    val KidTitle = 40.sp      // 아이 · 큰 제목
    val KidBubble = 22.sp     // 아이 · 말풍선
    val KidCard = 20.sp       // 아이 · 카드 이름
    val ParentTitle = 22.sp   // 부모 · 제목 (굵게)
    val ParentBody = 16.sp    // 부모 · 본문
    val ParentSmall = 13.sp   // 부모 · 작은 글
}

// ── 3. 크기 · 간격 · 모서리 · 테두리 ──────────────────────────

/** 모서리 — 뾰족한 모서리는 쓰지 않는다 */
object Radius {
    val S = 12.dp
    val M = 20.dp
    val L = 28.dp
    val Round = 999.dp
}

/** 예전 기본 모서리 (카드 등) — 새 토큰의 큰 모서리 */
val R = Radius.L

/** 누르는 곳 크기 — 연구 권장(아이 2cm ≈ 126dp)은 가로 폰 높이의 1/3 이라 타협했다 */
object Touch {
    val Hero = 96.dp       // 고르기 방울 · 방 물건 (이상)
    val Kid = 80.dp        // 다음 · 좋아/다시
    val KidMin = 64.dp     // 아이 화면의 모든 누르는 것의 최소
    val Parent = 48.dp     // 부모 화면 최소 (Material)
}

object Space {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}

/** 테두리 두께 */
object Border {
    val TouchInner = 4.dp   // 만질 수 있는 것 — 안쪽 흰 테두리 (09-27 멘토 요청)
    val TouchOuter = 2.dp   // 〃 바깥 겨자 테두리
    val Picked = 5.dp       // 고른 카드
    val Parent = 1.dp       // 부모 화면 카드 선
}

// ── 4. 재료 — 펠트 조각 (아이용 누르는 요소는 모두 같은 네 겹) ─────────────

object Felt {
    val StitchInset = 5.dp      // ③ 바느질선 — 가장자리 안쪽
    val StitchWidth = 1.5.dp
    val StitchDash = 6.dp       // 점선 한 칸
    val StitchColor = Color(0x8CFFFFFF)  // 옅은 흰 선
    val ShadowY = 5.dp          // ④ 그림자 — 아래로
    val ShadowBlur = 10.dp
    val ShadowColor = InkBrown.copy(alpha = 0.20f)
    val PressedShadowY = 2.dp   // 누르면 그림자가 얕아진다
    const val PressedScale = 0.96f
    const val PressMillis = 120
}
