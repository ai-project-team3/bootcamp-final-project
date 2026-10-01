package com.example.finalproject_demo

import com.example.finalproject_demo.net.Voice
import org.junit.Assert.assertEquals
import org.junit.Test

/** The app finds a baked line by the same key eval/bake_lines.py wrote it under (10-01). */
class BakedVoiceKeyTest {
    @Test
    fun theKeyMatchesTheBakeScript() {
        // py -c "import bake_lines as b; print(b.key('  안녕!  오늘은 무슨 일이 있었어? '))"
        assertEquals("12dd542144dcdae8", Voice.bakedKey("  안녕!  오늘은 무슨 일이 있었어? "))
        assertEquals(Voice.bakedKey("안녕! 오늘은 무슨 일이 있었어?"), Voice.bakedKey("안녕!\n오늘은 무슨 일이 있었어?"))
    }
}
