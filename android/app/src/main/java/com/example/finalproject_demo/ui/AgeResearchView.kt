package com.example.finalproject_demo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.*
import java.util.Locale

internal const val UNSUPPORTED_AGE_NOTICE = "만 6~7세는 아직 연구 자료가 없어요. 말 기록은 그대로 볼 수 있어요."

/** Optional local data entry, shared by guardian setup and protected settings. */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ChildAgeSection() {
    val stored = AnswerHistory.childAge
    val owner = AnswerHistory.profileId
    val years = stored?.years
    val half = stored?.half
    var notice by remember(owner) { mutableStateOf("") }
    fun save(age: ChildAge?) {
        notice = if (AnswerHistory.setChildAge(age)) {
            if (age == null) "저장한 나이를 지웠어요." else "나이를 이 기기에 저장했어요."
        } else "저장하지 못했어요. 다시 시도해 주세요."
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("아이 만 나이 · 선택", fontSize = 16.sp, color = Ink)
        Text("연령별 연구 참고자료를 고르는 데만 써요. 기기에만 저장하며 서버에 보내지 않아요. 입력하지 않아도 이야기와 말 기록은 그대로 쓸 수 있어요.",
            fontSize = 12.sp, lineHeight = 18.sp, color = InkSoft)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (3..7).forEach { value ->
                FilterChip(selected = years == value, enabled = !AnswerHistory.readFailed,
                    onClick = { save(ChildAge(value, half.takeIf { years == value })) },
                    label = { Text("만 ${value}세") })
            }
        }
        if (years != null && years >= 6) {
            val visibleNotice = remember { BringIntoViewRequester() }
            LaunchedEffect(years) {
                withFrameNanos { }
                visibleNotice.bringIntoView()
            }
            Text(UNSUPPORTED_AGE_NOTICE, fontSize = 12.sp, lineHeight = 18.sp, color = Ink,
                modifier = Modifier.bringIntoViewRequester(visibleNotice))
        }
        if (years in 3..5) {
            Text("최근 생일 뒤로 얼마나 지났나요?", fontSize = 13.sp, color = Ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "0~5개월", 1 to "6~11개월", null to "잘 모르겠어요").forEach { (value, label) ->
                    FilterChip(selected = half == value, enabled = !AnswerHistory.readFailed,
                        onClick = { save(ChildAge(years!!, value)) }, label = { Text(label) })
                }
            }
        }
        Text("생년월일은 받지 않아요. 나이는 자동으로 바뀌지 않으니 생일이나 6개월 구간이 바뀌면 고쳐 주세요.",
            fontSize = 12.sp, lineHeight = 18.sp, color = InkSoft)
        if (stored != null) TextButton(onClick = { save(null) }) { Text("나이 정보 지우기") }
        if (notice.isNotEmpty()) Text(notice, fontSize = 12.sp, color = Ink)
    }
}

/** Separate literature context. Never compute a difference from answer-level app observations. */
@Composable
internal fun AgeResearchView(eligibleAnswers: Int, onSettings: () -> Unit = {}) {
    val age = AnswerHistory.childAge
    if (age != null && age.years >= 6) {
        Text(UNSUPPORTED_AGE_NOTICE, fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    val reference = ageResearchReference(age, eligibleAnswers)
    val uri = LocalUriHandler.current
    var linkFailed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("입력한 나이의 연구 참고", fontSize = 18.sp, color = Ink)
        when {
            age == null -> {
                Text("아이 만 나이를 선택하면 해당 연령의 연구 자료를 볼 수 있어요.", fontSize = 13.sp, color = InkSoft)
                TextButton(onClick = onSettings) { Text("설정에서 나이 고치기") }
            }
            eligibleAnswers < 50 -> Text("기록 쌓는 중 · 동화의 열린·까닭 질문에 직접 답한 말 ${eligibleAnswers}개 / 50개", fontSize = 13.sp, color = InkSoft)
            age.half == null -> Text("이 연구는 6개월 구간으로 나뉘어요. 부모 설정에서 생일 뒤 지난 구간을 선택해 주세요.", fontSize = 13.sp, color = InkSoft)
            reference != null -> {
                Text("만 ${age.years}세 ${if (age.half == 0) "0~5" else "6~11"}개월 · 연구 참여 아동 ${reference.children}명", fontSize = 14.sp, color = Ink)
                Text(String.format(Locale.KOREAN, "연구 대화의 발화당 평균 %.2f어절 · 표준편차 %.2f", reference.meanEojeol, reference.standardDeviation), fontSize = 14.sp, color = Ink)
                Text("표준편차는 연구 참여자 값이 퍼진 정도이며 정상 범위가 아니에요.", fontSize = 12.sp, color = InkSoft)
                Text("가족·어린이집 주제의 대화에서 아이마다 50발화를 나누고 반복·불명확한 말 등을 제외한 연구예요.", fontSize = 12.sp, lineHeight = 18.sp, color = InkSoft)
            }
        }
        Text("앱은 마이크 답 한 번의 낱말을 세고, 연구는 정해진 규칙으로 나눈 발화의 어절을 세요. 수집·계산 방식이 달라 숫자를 직접 비교할 수 없어요.",
            fontSize = 12.sp, lineHeight = 18.sp, color = InkSoft)
        Text("놀이 대화 요약과 연구 참고자료이며 진단이 아니에요.", fontSize = 12.sp, color = InkSoft)
        if (reference != null) {
            TextButton(onClick = { linkFailed = runCatching { uri.openUri(AGE_RESEARCH_SOURCE) }.isFailure }) {
                Text("출처: 이윤경·임수아(2025), 표 2")
            }
            if (linkFailed) Text("출처를 열지 못했어요. DOI: 10.12963/csd.250168", fontSize = 12.sp, color = InkSoft)
        }
    }
}
