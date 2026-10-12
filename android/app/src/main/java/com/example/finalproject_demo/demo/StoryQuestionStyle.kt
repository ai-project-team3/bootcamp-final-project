package com.example.finalproject_demo.demo

/** A standalone linking expression is A1 evidence, never proof of a causal answer (S1). */
internal fun storyHasLinkingExpression(text: String): Boolean =
    Regex("(^|[\\s,.!?…])(?:그래서|그리고|그다음에|그러니까|그런데)(?=$|[\\s,.!?…])").containsMatchIn(text)
