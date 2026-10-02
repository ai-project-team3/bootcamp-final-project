package com.example.finalproject_demo.demo

import com.example.finalproject_demo.net.Server

/** 서버로 진행한 동화 — 대화가 AI 로 돌아 **대본의 공룡이 없다** (#50 2번 · `sceneFriends` 와 같은 기준) */
val DemoState.isLiveStory: Boolean get() = mode == StoryMode.STORY && Server.liveFor(mode)

/**
 * 책에 테마의 기본 공룡을 세우나 (#50 2번). **대본 동화만** — 대본은 장면 「공룡」에서 아이가 공룡을 만난다.
 * 서버 동화는 아이가 정하지 않은 공룡이 표지 · 미션 쪽 · 마지막 쪽에 끼어들었다(트리케라톱스). 일기 · 협업은 원래 없다.
 */
val DemoState.bookShowsDino: Boolean get() = !isDiary && !isLiveStory

/**
 * 마지막 쪽에서 눌러 **아이가 녹음한 소리**를 듣는 인물의 이름 — 대본 동화는 공룡, 서버 동화는 이야기에서 정한 새 친구
 * (서버 동화의 소리 칸은 그 친구의 소리다). 누를 인물이 없으면 null.
 */
val DemoState.soundHolderName: String? get() = when {
    bookShowsDino -> dino.name
    isLiveStory && !slots["newcomer"].isNullOrBlank() -> friendCallName
    else -> null
}

/** 현재 동화 템플릿의 쪽 순서를 그대로 서버에 보낸다. 다른 모드의 책 구성은 각 담당자가 정한다. */
fun DemoState.storyPagePlan(): List<Server.Page> {
    if (mode != StoryMode.STORY) return emptyList()
    val pages = template?.pages ?: return emptyList()
    return pages.map { page ->
        val mission = when (page.kind) {
            PageKind.RUB -> "A6"
            PageKind.DRAG -> if (templateKey in setOf("A", "G")) "A3" else "E1"
            else -> null
        }
        Server.Page(page.kind.name, mission)
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
    if (mode != StoryMode.STORY || storyCaptions == null) return null
    return when (pageKind(i)) {
        PageKind.RUB -> if (m1Result != null) {
            val item = mission1().blobName
            "${item}${ga(item)} 사라졌어요."
        } else null
        PageKind.DRAG -> if (m2Result != null) {
            if (templateKey in setOf("A", "G")) "그림 조각을 모두 맞춰 한 장면을 완성했어요."
            else "${childName}${eun(childName)} ${friendCallName}에게 ${mission2().give}."
        } else null
        else -> null
    }
}
