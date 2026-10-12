package com.example.finalproject_demo.demo

/** Guardian-entered age band, not an age inferred from language or a birth date. */
data class ChildAge(val years: Int, val half: Int?) {
    init { require(years in 3..7 && (half == null || half in 0..1)) }
}

data class AgeResearchReference(val meanEojeol: Double, val standardDeviation: Double,
    val children: Int = 30, val utterancesPerChild: Int = 50)

const val AGE_RESEARCH_SOURCE = "https://www.e-csd.org/journal/view.php?doi=10.12963/csd.250168"

/** Lee & Lim (2025), Table 2. Published mean/SD, never a normal interval or score. */
internal fun ageResearchReference(age: ChildAge?, eligibleAnswers: Int): AgeResearchReference? {
    if (eligibleAnswers < 50 || age == null || age.years !in 3..5 || age.half == null) return null
    return listOf(
        AgeResearchReference(2.76, .81), AgeResearchReference(3.26, 1.18),
        AgeResearchReference(3.44, 1.09), AgeResearchReference(4.03, 1.16),
        AgeResearchReference(4.28, 1.45), AgeResearchReference(4.58, 1.20),
    )[(age.years - 3) * 2 + age.half]
}
