package com.example.finalproject_demo.demo

import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.missions
import com.example.finalproject_demo.net.Server

/** The mission on a book page — the two mission slots are still the RUB and DRAG pages (design §5-1) */
fun DemoState.missionFor(kind: PageKind): MissionId? = when (kind) {
    PageKind.RUB -> missions().slot1
    PageKind.DRAG -> missions().slot2
    else -> null
}

/** 현재 동화 템플릿의 쪽 순서를 그대로 서버에 보낸다. 다른 모드의 책 구성은 각 담당자가 정한다. */
fun DemoState.storyPagePlan(): List<Server.Page> {
    if (mode != StoryMode.STORY) return emptyList()
    val pages = template?.pages ?: return emptyList()
    return pages.map { page ->
        Server.Page(page.kind.name, missionFor(page.kind)?.name)
    }
}

/** 한 쪽이라도 빠지거나 비었으면 템플릿 책을 사용한다. 이름 복원은 호출 전에 공통 경로가 담당한다. */
fun DemoState.useGeneratedStory(captions: List<String>?): Boolean {
    val expected = if (mode == StoryMode.STORY) template?.pages?.size else null
    val valid = expected != null && captions != null && captions.size == expected && captions.all { it.isNotBlank() }
    storyCaptions = if (valid) captions!!.toList() else null
    return valid
}

/** 서버 문장은 미션 직전에서 끝난다. 아이가 미션을 끝낸 뒤에만 책 자막에 결과를 더한다. */
fun DemoState.storyMissionResult(i: Int): String? {
    if (isCoop && storyCaptions != null) return coopMissionResult(pageKind(i))   // 협업 결과 문장은 CoopScenes.kt (#52 2번)
    if (mode != StoryMode.STORY || storyCaptions == null) return null
    return when (pageKind(i)) {
        PageKind.RUB -> if (m1Result != null) {
            val item = mission1().blobName
            "${item}${ga(item)} 사라졌어요."
        } else null
        PageKind.DRAG -> if (m2Result != null) {
            if (missions().slot2 == MissionId.A3) "그림 조각을 모두 맞춰 한 장면을 완성했어요."
            else "${childName}${eun(childName)} ${friendCallName}에게 ${mission2().give}."
        } else null
        else -> null
    }
}
