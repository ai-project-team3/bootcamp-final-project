package com.example.finalproject_demo.demo

/*
 * 같이 만들기 · 곧 해요 책의 시제 — 미션 결과 문장을 「-ㄹ 거예요」로.
 *
 * 10-06 실기기(#172 · 소방관 곧 체험해요): 서버가 쓴 쪽은 「훈련장에서 소방차를 타고 호스로 물을 뿌릴 거예요」인데
 * 앱이 붙인 결과는 「소방차가 힘차게 출동했어요」 · 「불이 다 꺼졌어요」 — 한 쪽 안에서 시제가 갈렸다.
 * 책은 시제가 하나다(eval/story_prompt_coop.md 규칙 19). 다녀왔어요 · 상상 책은 과거형이라 그대로 두고, 곧 해요만 바꾼다.
 *
 * 소품의 결과 문장(`missions/` 의 BlowProp · FixProp · SoundProp)은 몇 개 안 되고 다 「-었어요」꼴이라, 문장 끝 서술어 한 어절만 규칙으로 바꾼다.
 * 규칙에 안 걸리는 문장은 손대지 않는다 — 틀린 꼴을 책에 넣느니 과거형이 낫다. 전체 소품은 CoopTenseTest 가 돈다.
 */

/** 줄어든 과거 음절 → 미래 어간 음절. 「했」→「할」 · 「졌」→「질」 */
private val PAST_TO_FUTURE = mapOf(
    '했' to '할', '됐' to '될', '갔' to '갈', '왔' to '올', '났' to '날', '냈' to '낼', '섰' to '설', '췄' to '출', '쳤' to '칠',
    '졌' to '질', '줬' to '줄', '봤' to '볼', '웠' to '울', '껐' to '끌', '탔' to '탈', '샀' to '살', '잤' to '잘', '팠' to '팔',
    '켰' to '켤', '폈' to '펼', '뗐' to '뗄', '뺐' to '뺄', '댔' to '댈', '꼈' to '낄', '쌌' to '쌀', '떴' to '뜰', '썼' to '쓸', '컸' to '클',
)

/** 음절표로 안 되는 것 — 「잠갔」은 「잠가+았」(잠그다)이라 「잠갈」이 아니라 「잠글」 */
private val PAST_STEM_EXCEPTIONS = mapOf("잠갔" to "잠글", "담갔" to "담글")

/** 「불이 다 꺼졌어요.」 → 「불이 다 꺼질 거예요.」 문장 끝 서술어만 본다. 못 바꾸면 원문 */
internal fun soonTense(line: String): String {
    val t = line.trimEnd()
    val punct = t.takeLastWhile { it in ".!~" }
    val body = t.dropLast(punct.length)
    val word = body.substringAfterLast(' ')
    if (!word.endsWith("어요") || word.length < 3) return line
    val stem = word.dropLast(2)
    val future = PAST_STEM_EXCEPTIONS.entries.firstOrNull { stem.endsWith(it.key) }?.let { stem.dropLast(it.key.length) + it.value }
        ?: PAST_TO_FUTURE[stem.last()]?.let { stem.dropLast(1) + it }
        ?: if (stem.last() == '었' || stem.last() == '았') futureOfStem(stem.dropLast(1)) else null
        ?: return line
    return body.dropLast(word.length) + future + " 거예요" + punct
}

/** 「넣」→「넣을」 · 「불」→「불」(ㄹ받침은 그대로) · 「주」→「줄」(받침 없으면 ㄹ을 붙인다) */
private fun futureOfStem(stem: String): String? {
    val c = stem.lastOrNull() ?: return null
    if (c !in '가'..'힣') return null
    return when ((c - '가') % 28) {
        8 -> stem
        0 -> stem.dropLast(1) + (c + 8)
        else -> stem + "을"
    }
}
