package com.example.finalproject_demo

import android.content.Context
import com.example.finalproject_demo.ui.FeelPrefs
import com.example.finalproject_demo.ui.Sfx
import com.example.finalproject_demo.ui.Sound
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 부모 설정의 **효과음 · 진동** 스위치가 앱을 껐다 켜도 남는가 (9/25).
 * 「껐다 켜기」는 [FeelPrefs.unload] 로 메모리를 비우고 [FeelPrefs.load] 로 폰에서 다시 읽어 흉내 낸다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeelPrefsTest {
    /**
     * 화면을 그리지 않는데도 Compose 규칙을 단다 (9/25).
     *
     * 여기서 바꾸는 값은 Compose 상태(`mutableStateOf`)다. 규칙 없이 바꾸면 「바뀌었다」 알림이
     * **앞서 돈 화면 검사의 끝난 자리로** 가서 걸리고, 그 **다음 화면 검사가 조용해지기를 60초 기다리다 떨어진다.**
     * 순서에 따라 나타났다 사라져서 찾기 어려웠다 — 화면 검사 9개가 그렇게 멈췄다.
     */
    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun restart() {
        FeelPrefs.unload()
        FeelPrefs.load(ctx)
    }

    @Before
    fun fresh() {
        ctx.getSharedPreferences("feel", Context.MODE_PRIVATE).edit().clear().commit()
        restart()
    }

    @After
    fun release() = FeelPrefs.unload()

    @Test
    fun soundAndBuzzAreOnAtFirst() {
        assertTrue(FeelPrefs.soundOn)
        assertTrue(FeelPrefs.buzzOn)
    }

    @Test
    fun turningThemOffSurvivesRestart() {
        FeelPrefs.setSound(false)
        FeelPrefs.setBuzz(false)
        restart()
        assertFalse("효과음을 끈 것이 켤 때 되살아나면 안 된다", FeelPrefs.soundOn)
        assertFalse("진동을 끈 것이 켤 때 되살아나면 안 된다", FeelPrefs.buzzOn)
    }

    @Test
    fun theTwoSwitchesAreIndependent() {
        FeelPrefs.setSound(false)
        restart()
        assertFalse(FeelPrefs.soundOn)
        assertTrue("효과음만 껐는데 진동까지 꺼지면 안 된다", FeelPrefs.buzzOn)
    }

    /** 치익은 뿌리는 동안 계속 난다 — 떨면 거슬린다. 나머지는 떤다 */
    @Test
    fun onlyTheContinuousHissDoesNotBuzz() {
        assertNull(Sfx.buzz(Sound.HISS))
        listOf(Sound.POP, Sound.THUD, Sound.SPARKLE).forEach { assertNotNull("$it 는 떨어야 한다", Sfx.buzz(it)) }
    }
}
