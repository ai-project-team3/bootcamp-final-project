package com.example.finalproject_demo.demo

import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.ui.HeroAttr

/** Session-only creation checkpoint; returning home must not buy new redraws. */
class HeroCreationDraft {
    enum class Phase { CHOOSE, PRESET, QUESTIONS, GENERATE, CONFIRM, REPAIR, PICK, NAME, COMPLETE }
    var phase = Phase.CHOOSE
    var questionIndex = 0
    var attr = HeroAttr(hair = "short", shirt = Color(0xFF3F7BD9), glasses = "none", likes = "dino")
    var fixes = 0
    val descriptions = mutableListOf<String>()
    val confirmedChoices = linkedMapOf<String, String>()
    val generatedTries = mutableListOf<Pair<String?, String?>>()
    val generatedDescriptions = mutableListOf<String>()
    var generatedDescription: String? = null
    var generatedImage: String? = null
    var generatedRig: String? = null

    /** Unsaved candidates still belong to the session while parents tidy another book. */
    fun imageReferences(): List<String> = if (phase == Phase.COMPLETE) emptyList()
        else generatedTries.mapNotNull { it.first } + listOfNotNull(generatedImage)
}
