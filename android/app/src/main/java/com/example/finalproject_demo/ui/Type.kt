package com.example.finalproject_demo.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle


/** 아이 화면 기본 글꼴 (Jua · OFL) — 값은 `Theme.kt` 의 [KidFont]. 모든 Text의 기본 글꼴로 쓴다. */
val Jua = KidFont

val PuppetTypography = Typography().let { t ->
    t.copy(
        bodyLarge = t.bodyLarge.copy(fontFamily = Jua),
        bodyMedium = t.bodyMedium.copy(fontFamily = Jua),
        bodySmall = t.bodySmall.copy(fontFamily = Jua),
        titleLarge = t.titleLarge.copy(fontFamily = Jua),
        labelLarge = t.labelLarge.copy(fontFamily = Jua),
    )
}

val JuaStyle = TextStyle(fontFamily = Jua)
