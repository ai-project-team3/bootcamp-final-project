package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.AiPicture
import com.example.finalproject_demo.net.AiPictureSource
import com.example.finalproject_demo.net.ReportNames

/*
 * ── 신고 첨부 후보 — 출처로만 고른다 (#283 · 설계 §1-1) ─────────────────────────────────────────────
 *
 * 책 한 쪽의 화면은 AI 배경 위에 아이 그림(획)이 겹쳐 그려진다. 그래서 화면을 찍지 않고 **저장된 파일의 출처**로 후보를 만든다.
 *
 * | 모드 | 후보 | 후보가 아닌 것 |
 * | 동화 · 협업 | 서버가 만든 배경(`local:` 파일) · 주인공 / 친구 펠트 인형(생성된 경우) | 아이가 그린 주인공 획 · 앱 프리셋 배경(이름만) |
 * | 그림일기 | 오또가 다시 그린 조각(ottoPng) | 원본 조각 획 · 판 그림 |
 *
 * 배경 · 인형은 책 단위라 모든 쪽에서 같은 후보가 나온다.
 */

/** 신고 시트가 책에서 왔을 때 쓰는 그 책의 맥락 */
data class ReportBook(
    /** story · diary · coop */
    val mode: String,
    /** 쪽 문장(쪽 번호 = 위치 + 1) — 폰 안에서만 보이는 원문. 보낼 때는 이름을 가린다 */
    val pages: List<String>,
    /** 오또(AI)가 만든 그림 후보 */
    val pictures: List<AiPicture>,
    /** 배경이 앱에 든 그림이면 그 이름 — 파일 대신 이름만 보낸다 */
    val presetBackground: String?,
    val names: ReportNames,
)

private fun localPath(name: String?): String? = name?.takeIf { it.startsWith("local:") }?.removePrefix("local:")

/** 저장된 책 한 권의 신고 맥락 — 못 찾으면 null */
fun Director.reportBook(entry: ShelfEntry): ReportBook? = when (entry.mode) {
    StoryMode.STORY -> storyBooks().firstOrNull { it.id == entry.id }?.let { reportBookOf(it, "story", s.childName) }
    StoryMode.COOP -> CoopShelf.books(s).firstOrNull { it.id == entry.id }?.let { reportBookOf(it, "coop", s.childName) }
    StoryMode.DIARY -> DiaryShelf.books(s).firstOrNull { it.id == entry.id }?.let { reportBookOf(it, s.childName) }
}

/** 동화 · 협업 책 — 배경 · 주인공 · 친구 인형 중 서버가 만든 파일만 */
internal fun reportBookOf(book: SavedStoryBook, mode: String, child: String?): ReportBook {
    val v = book.visuals
    val pictures = buildList {
        localPath(book.bgName)?.let { add(AiPicture(AiPictureSource.BACKGROUND, path = it)) }
        localPath(v?.hero?.image)?.let { add(AiPicture(AiPictureSource.HERO, path = it)) }
        localPath(v?.friend?.image)?.let { add(AiPicture(AiPictureSource.FRIEND, path = it)) }
    }
    return ReportBook(
        mode = mode,
        pages = book.pages.map { it.caption },
        pictures = pictures,
        presetBackground = book.bgName.takeIf { !it.startsWith("local:") && it.isNotBlank() },
        names = ReportNames(child, listOfNotNull(v?.friendName), listOfNotNull(v?.hero?.name, v?.hero?.called)),
    )
}

/** 그림일기 — 오또가 다시 그린 조각만. 쪽 문장은 저장된 재료로 다시 짠다 */
internal fun reportBookOf(book: SavedDiaryBook, child: String?): ReportBook = ReportBook(
    mode = "diary",
    pages = runCatching { buildDiaryBook(book.input).map { it.text } }.getOrDefault(emptyList()),
    pictures = book.pieces.mapNotNull { p -> p.ottoPng?.let { AiPicture(AiPictureSource.DIARY_OTTO, png = it) } },
    presetBackground = null,
    names = ReportNames(child),
)
