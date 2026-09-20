# Demo judge prompt — 새 판정 구조 검증용

실제 팀의 `judge_prompt.md`가 도착하기 전까지 무료 mock 데모에서만 사용한다.

입력에는 다음이 함께 들어온다.
- 현재까지 채운 `slots` 전체
- 지금 질문한 칸 `asked`
- 템플릿 `template`
- 아이 발화 `utterance`

출력은 `judge_schema_demo.json`의 1단 평면 JSON을 따른다.
핵심은 한 발화에서 최대 두 슬롯을 채우는 `slot_1/value_1`, `slot_2/value_2`,
필요 없어진 필드를 표시하는 `no_longer_needed`, 다음 질문 후보인 `next_slot`,
그리고 `s1_reason`, `s2_addition`, `story_ready` 등을 함께 판정하는 것이다.

JSON 외 출력은 하지 않는다.
