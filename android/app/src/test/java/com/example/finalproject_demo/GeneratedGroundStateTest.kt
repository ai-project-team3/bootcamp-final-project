package com.example.finalproject_demo

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.finalproject_demo.demo.scene.Ground
import com.example.finalproject_demo.ui.GroundCache
import com.example.finalproject_demo.ui.rememberGround
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeneratedGroundStateTest {
    @get:Rule val compose = createComposeRule()

    private fun picture(paintedGround: Boolean): String {
        val image = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(80 * 80) { i ->
            if (paintedGround && i / 80 >= 40) 0xFFF2F4F8.toInt() else 0xFF9CC9E8.toInt()
        }
        image.setPixels(pixels, 0, 80, 0, 0, 80, 80)
        val file = File.createTempFile("ground_state", ".png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "local:${file.absolutePath}"
    }

    @Test fun aNewPictureNeverInheritsThePreviousPicturesFloorDecision() {
        val sky = picture(false)
        val snow = picture(true)
        assertFalse(GroundCache.of(sky)!!.hasGround)
        assertTrue(GroundCache.of(snow)!!.hasGround)
        val selected = mutableStateOf(sky)
        val observed = mutableListOf<Pair<String, Ground?>>()
        compose.setContent { observed += selected.value to rememberGround(selected.value) }
        compose.waitForIdle()
        compose.runOnIdle { selected.value = snow }
        compose.waitForIdle()
        val snowFrames = observed.filter { it.first == snow }
        assertTrue("the new picture must be composed", snowFrames.isNotEmpty())
        assertTrue("no frame may use the previous sky's floor decision", snowFrames.all { it.second?.hasGround != false })
    }
}
