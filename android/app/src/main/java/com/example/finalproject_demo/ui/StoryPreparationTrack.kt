package com.example.finalproject_demo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.StoryPreparationProgress

@Composable
fun StoryPreparationTrack(progress: StoryPreparationProgress, modifier: Modifier = Modifier) {
    Column(
        modifier.width(320.dp).semantics(mergeDescendants = true) {
            stateDescription = progress.phase.label
            progressBarRangeInfo = ProgressBarRangeInfo(progress.filled.toFloat(), 0f..progress.total.toFloat(), progress.total - 1)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Keep the common felt track; only a live story labels its preparation milestones.
        ProgressTrack(progress.filled, progress.total)
        Text(
            progress.phase.label,
            color = InkSoft,
            fontSize = 12.sp,
            modifier = Modifier.felt(FeltWhite.copy(alpha = 0.94f), RoundedCornerShape(Radius.Round), lift = 1.dp)
                .padding(horizontal = 8.dp, vertical = 1.dp),
        )
    }
}
