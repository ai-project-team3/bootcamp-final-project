package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.ui.shell.LocalWipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 탈퇴 「폰 안의 책 · 그림 · 녹음도 함께 지우기」 (10-05) — 파일째 지워지는가 · 새 저장소가 목록에서 빠지지 않았는가.
 * 전에는 화면 목록만 비워서, 앱을 다시 켜면 저장된 책이 그대로 돌아왔다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalWipeTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun wipesBooksPicturesSoundsAndTheChildsName() {
        LocalWipe.PREFS.forEach { ctx.getSharedPreferences(it, Context.MODE_PRIVATE).edit().putString("books", "[{\"id\":\"b1\"}]").commit() }
        val files = LocalWipe.FILES.map { File(File(ctx.filesDir, it).apply { mkdirs() }, "page.png").apply { writeBytes(byteArrayOf(1, 2, 3)) } }
        val sound = File(File(ctx.noBackupFilesDir, "child_sounds/books/b1").apply { mkdirs() }, "roar.wav").apply { writeBytes(byteArrayOf(4)) }
        val voice = File(ctx.cacheDir, "mascot_line_123").apply { writeBytes(byteArrayOf(5)) }
        // 남겨야 하는 것 — 효과음 설정은 아이 데이터가 아니다
        ctx.getSharedPreferences("feel", Context.MODE_PRIVATE).edit().putBoolean("sfx", false).commit()

        assertTrue(LocalWipe.wipe(ctx))

        LocalWipe.PREFS.forEach { assertTrue("$it 가 남았다", ctx.getSharedPreferences(it, Context.MODE_PRIVATE).all.isEmpty()) }
        files.forEach { assertFalse("${it.parentFile?.name} 그림이 남았다", it.exists()) }
        assertFalse("아이 녹음이 남았다", sound.exists())
        assertFalse("오또 목소리 임시 파일이 남았다", voice.exists())
        assertEquals(false, ctx.getSharedPreferences("feel", Context.MODE_PRIVATE).getBoolean("sfx", true))
    }

    @Test fun wipingTwiceIsFine() {
        assertTrue(LocalWipe.wipe(ctx))
        assertTrue(LocalWipe.wipe(ctx))
    }

    /**
     * 앱 코드가 쓰는 SharedPreferences · 폴더가 **모두** 지우는 목록이나 「일부러 남기는 목록」 에 있어야 한다.
     * 누가 새 저장소를 만들고 여기 적지 않으면 이 검사가 깨진다 — 탈퇴해도 남는 데이터가 생기지 않게.
     */
    @Test fun everyStoreInTheAppIsListed() {
        val src = File("src/main/java").takeIf { it.exists() } ?: File("app/src/main/java")
        val code = src.walk().filter { it.extension == "kt" }.map { it.readText() }.joinToString("\n")
        val prefs = Regex("""getSharedPreferences\("([a-z_]+)"""").findAll(code).map { it.groupValues[1] } +
            Regex("""PREFS\s*=\s*"([a-z_]+)"""").findAll(code).map { it.groupValues[1] }
        val dirs = Regex("""File\([^,]*(?:filesDir|noBackupFilesDir)[^,]*,\s*"([a-z_]+)"""").findAll(code).map { it.groupValues[1] }
        // 각자의 탈퇴 경로가 지우는 것 · 아이 데이터가 아닌 것
        // call_limits: a date and counts, not the child's — kept so withdrawing does not lift the day's limit
        val handledElsewhere = setOf("otto_account", "otto_shell", "consent", "feel", "call_limits")
        val missingPrefs = prefs.toSet() - LocalWipe.PREFS.toSet() - handledElsewhere
        val missingDirs = dirs.toSet() - LocalWipe.FILES.toSet() - LocalWipe.NO_BACKUP.toSet()
        assertTrue("탈퇴할 때 안 지우는 SharedPreferences: $missingPrefs — ui/shell/LocalWipe.kt 에 더하세요", missingPrefs.isEmpty())
        assertTrue("탈퇴할 때 안 지우는 폴더: $missingDirs — ui/shell/LocalWipe.kt 에 더하세요", missingDirs.isEmpty())
        assertTrue("검사가 저장소를 하나도 못 찾았다 — 정규식이 낡았다", prefs.count() >= 5)
    }
}
