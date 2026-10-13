package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.CallLimits
import com.example.finalproject_demo.net.Server

/**
 * Remaining new starts, never a count of completed books. Null means that limit is disabled.
 * [parent] is the guardian's 「하루에 쓸 수 있는 별」, [service] the stars the device holds (`CallLimits` · #445).
 */
data class DailyStartAllowance(val parent: Int?, val service: Int?) {
    val remaining: Int? get() = listOfNotNull(parent, service).minOrNull()
    val parentCanAllowExtra: Boolean get() = parent == 0 && (service == null || service > 0)
}

/** The room shows the effective allowance; a blocked mode uses its own live flag. */
fun DemoState.newStoryAllowance(mode: StoryMode? = null): DailyStartAllowance {
    val live = if (mode == null) StoryMode.entries.any { Server.liveFor(it) } else Server.liveFor(mode)
    return DailyStartAllowance(if (limitOn) dayStars else null,
        if (CallLimits.enabled && live) CallLimits.starsLeft() else null)
}

/** Guardian approval restores only their own daily allowance — it never adds a star. */
fun DemoState.allowExtraParentStart(): Boolean {
    if (!newStoryAllowance(noticeMode).parentCanAllowExtra) return false
    usedToday = (usedToday - 1).coerceAtLeast(0)
    return true
}
