package com.example.finalproject_demo.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily


/** 웹 데모와 같은 둥근 한글 글꼴 (Jua · OFL). 모든 Text의 기본 글꼴로 쓴다. */
val Jua = FontFamily(Font(com.example.finalproject_demo.R.font.jua))

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
