package com.example.finalproject_demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.AnswerHistory
import com.example.finalproject_demo.demo.SpeechObservations
import com.example.finalproject_demo.demo.summarizeSpeech
import java.util.Locale

@Composable
internal fun SpeechObservationsView(summary: SpeechObservations, title: String, modifier: Modifier = Modifier) {
    val ink = Color(0xFF4A3428)
    val sub = Color(0xFF7A6455)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontFamily = KidFont, fontSize = 19.sp, color = ink)
        if (summary.meanWords == null) {
            Text("열린·까닭 질문의 말 기록을 모으고 있어요.", fontSize = 14.sp, color = sub)
        } else {
            Text("열린·까닭 질문에 직접 답한 말 ${summary.recordedAnswers}개", fontSize = 14.sp, color = ink)
            if (summary.recordedAnswers > summary.windowAnswers)
                Text("최근 ${summary.windowAnswers}개 답을 계산했어요.", fontSize = 13.sp, color = sub)
            val average = String.format(Locale.KOREAN, "%.1f", summary.meanWords)
            Text("답당 평균 ${average}낱말 · 서로 다른 낱말 ${summary.distinctWords}개", fontSize = 14.sp, color = ink)
            Text("까닭 표현이 들어간 답 ${summary.reasonAnswers}개", fontSize = 14.sp, color = ink)
        }
        if (summary.choices.isNotEmpty()) {
            Text("고른 답", fontSize = 14.sp, color = ink)
            summary.choices.take(3).forEach { Text("“${it.text}” · ${it.count}번", fontSize = 14.sp, color = ink) }
            Text("선택한 답은 말 길이에 넣지 않아요.", fontSize = 12.sp, color = sub)
        }
        Text("받아쓴 말의 띄어쓰기를 기준으로 세요. 같은 낱말도 조사가 붙으면 다른 낱말로 세며, 까닭 표현은 왜냐·때문에·니까·라서를 찾아요.",
            fontSize = 12.sp, lineHeight = 18.sp, color = sub)
        Text("놀이 대화 기록의 요약이에요.", fontSize = 12.sp, color = sub)
    }
}

/** Read only the current child's story records. Other mode owners connect their own metrics. */
@Composable
internal fun StorySpeechHistoryView(onSettings: () -> Unit = {}) {
    AnswerHistoryNotice()
    if (!AnswerHistory.readFailed) {
        val lines = AnswerHistory.all().filter { it.mode == "story" }.flatMap { it.answers }
        val summary = summarizeSpeech(lines)
        Column {
            SpeechObservationsView(summary, "동화 말 기록")
            AgeResearchView(summary.recordedAnswers, onSettings)
        }
    }
}

@Composable
internal fun AnswerHistoryNotice() {
    if (AnswerHistory.readFailed) Text("말 기록을 읽지 못했어요. 기존 자료는 보존했고, 새 기록 저장과 책 삭제는 잠시 멈췄어요.", fontSize = 13.sp, color = Curtain)
    if (AnswerHistory.writeFailed) Text("말 기록을 저장하지 못했어요. 저장 공간을 확인해 주세요.", fontSize = 13.sp, color = Curtain)
}
