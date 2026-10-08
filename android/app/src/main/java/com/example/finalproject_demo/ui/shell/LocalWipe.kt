package com.example.finalproject_demo.ui.shell

import android.content.Context
import java.io.File

/*
 * ── 탈퇴할 때 「폰 안의 책 · 그림 · 녹음도 함께 지우기」 (10-05 · 치영) ─────────────────────────────
 *
 * 전에는 화면의 책 목록(`d.s.shelf`)만 비웠다. 그런데 책은 이제 폰에 **파일로 저장**되어(#37 · #42 · #83)
 * 앱을 다시 켜면 그대로 돌아왔다 — 탈퇴 화면이 약속한 것과 달랐다. 여기서 앱이 폰에 두는 아이 데이터를 **전부** 지운다.
 *
 * 저장하는 곳이 새로 생기면 [PREFS] · [FILES] · [NO_BACKUP] 에 한 줄 더한다 — `LocalWipeTest` 가 지금 목록을 지킨다.
 * 계정 · 동의 · 앱 틀 설정은 각자의 저장소가 지운다(`Accounts.withdraw` · `ConsentStore.withdraw` · `Shell.resetToFirstRun`).
 * 효과음 설정(`feel`)은 아이 데이터가 아니라서 남긴다.
 * 서버 호출 한도(`call_limits` · 날짜와 숫자뿐)도 남긴다 — 지우면 탈퇴 · 재가입으로 하루 한도가 풀린다 (10-06 · net/CallLimits.kt).
 */
object LocalWipe {
    /** SharedPreferences — 책 목록 · 같이 만들기 질문 · 아이 이름 */
    val PREFS = listOf(
        "story_books",   // 동화 (demo/StoryBookStore.kt)
        "coop_books",    // 같이 만들기 (demo/CoopBookStore.kt)
        "diary_books",   // 그림일기 (demo/DiaryBookStore.kt)
        "coop_plan",     // 부모가 준비한 같이 만들기 질문 (demo/CoopPlanStore.kt)
        "child_call",    // 오또가 부르는 아이 이름 (net/ChildCall.kt)
        "rewards",       // 업적 보상 · 그림판 도구 (demo/Rewards.kt)
        "session_reports", // 부모 리포트 · 아이가 한 말 그대로 (demo/SessionReport.kt)
        "sent_reports",  // 보낸 문제 신고의 접수 번호 · 날짜 · 분류 (ui/Consent.kt SentReports · #283)
    )

    /** filesDir 아래 폴더 — 그림 */
    val FILES = listOf(
        "story_images",  // 동화 그림 (demo/StoryImageStore.kt)
        "diary_images",  // 그림일기 그림 (demo/DiaryBookStore.kt)
        "diary_voices",  // 그림일기 쪽 목소리 (demo/DiaryBookStore.kt · #179)
        "diary_trace",   // 그림일기 대화 기록 (demo/DiaryTrace.kt)
        "session_draft", // 앱이 꺼졌을 때 만들던 이야기 — 칸 · 한 말 · 그림 획 (demo/SessionDraft.kt · #336)
    )

    /** noBackupFilesDir 아래 폴더 — 아이가 녹음한 소리 (sound/ChildSound.kt) */
    val NO_BACKUP = listOf("child_sounds")

    /** 지운다. 하나라도 못 지웠으면 false (그래도 나머지는 끝까지 지운다) */
    fun wipe(ctx: Context): Boolean {
        val app = ctx.applicationContext
        var ok = true
        PREFS.forEach { name ->
            ok = app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() && ok
            runCatching { app.deleteSharedPreferences(name) }
        }
        com.example.finalproject_demo.demo.SessionReports.reload()   // the copy in memory goes with the file
        com.example.finalproject_demo.demo.Rewards.reload()   // the copy in memory goes with the file
        com.example.finalproject_demo.ui.SentReports.reload()   // the copy in memory goes with the file
        FILES.forEach { ok = deleteTree(File(app.filesDir, it)) && ok }
        NO_BACKUP.forEach { ok = deleteTree(File(app.noBackupFilesDir, it)) && ok }
        // 오또 목소리를 틀려고 잠깐 둔 파일 (net/Voice.kt) — 대사에 아이 말이 들어갈 수 있다
        app.cacheDir.listFiles()?.filter { it.name.startsWith("mascot_line_") }?.forEach { ok = it.delete() && ok }
        return ok
    }

    private fun deleteTree(f: File): Boolean = !f.exists() || f.deleteRecursively()
}
