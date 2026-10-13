package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import org.junit.Assert.*
import org.junit.Test

class AgeResearchReferenceTest {
    @Test fun literatureNumbersMatchAllSixPublishedGroups() {
        val expected = listOf(2.76 to .81, 3.26 to 1.18, 3.44 to 1.09,
            4.03 to 1.16, 4.28 to 1.45, 4.58 to 1.20)
        expected.forEachIndexed { index, (mean, sd) ->
            val reference = ageResearchReference(ChildAge(3 + index / 2, index % 2), 50)!!
            assertEquals(mean, reference.meanEojeol, 0.00001)
            assertEquals(sd, reference.standardDeviation, 0.00001)
            assertEquals(30, reference.children)
            assertEquals(50, reference.utterancesPerChild)
        }
    }

    @Test fun noSourceIsInventedForUnknownAgeOrUnsupportedYears() {
        assertNull(ageResearchReference(null, 50))
        assertNull(ageResearchReference(ChildAge(3, null), 50))
        assertNull(ageResearchReference(ChildAge(6, 0), 50))
        assertNull(ageResearchReference(ChildAge(7, 1), 50))
    }

    @Test fun fiftyAnswersIsAnExplicitDisplayGateNotAScore() {
        assertNull(ageResearchReference(ChildAge(4, 0), 49))
        assertNotNull(ageResearchReference(ChildAge(4, 0), 50))
        assertNotNull(ageResearchReference(ChildAge(4, 0), 51))
    }

    @Test fun invalidGuardianInputIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ChildAge(2, 0) }
        assertThrows(IllegalArgumentException::class.java) { ChildAge(8, 0) }
        assertThrows(IllegalArgumentException::class.java) { ChildAge(3, -1) }
        assertThrows(IllegalArgumentException::class.java) { ChildAge(3, 2) }
    }
}
