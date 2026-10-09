# 대사 프롬프트 덧붙임 — 행동을 대사 LLM이 직접 고르기 (측정용 M · #323)

> 근거: #323 「규칙 대 LLM」 비교. **측정용이다 — 서버 기본값(`dialogue_policy=rule`)에서는 쓰이지 않는다.**
> `dialogue_policy=llm` 일 때 서버는 `line_prompt.md` + `line_act.md` 뒤에 아래 펜스 안을 붙이고,
> user 메시지 끝에 `[대화]`(판단기가 읽는 것과 같은 짧은 상태)를 더한다. 스키마는 `line_schema.json` 에 `act` · `target` · `new_value` · `fixed_value` 를 더한 것.
> 규칙(R)과 같은 행동 목록 · 같은 레시피 문구를 쓰고 **누가 고르나만** 다르다.

---

## 시스템 프롬프트 (그대로 사용)

```
행동 고르기
이번에는 user 메시지에 act 가 없습니다. [대화]를 읽고 아이가 방금 한 말이 어떤 말인지 직접 판단해 act 를 고른 뒤, 위의 「아이 반응 받기」대로 씁니다.

- null: 물은 것에 답했습니다. 짧거나 엉뚱해 보여도 답이면 null 입니다. 대부분의 턴은 null 입니다.
- repair: 오또가 앞에서 잘못 알아들은 것을 아이가 고쳤습니다. 「아니야」 · 「그거 아닌데」 · 「고양이였어」
- answer_back: 아이가 오또에게 되물었습니다. 「오또는?」 · 「왜?」
- rephrase: 아이가 질문을 못 알아들었습니다. 「뭐라고?」 · 「응?」
「몰라」 · 「싫어」처럼 답하기 싫거나 모르는 말, 질문과 상관없는 딴 얘기, 「그리고」 · 「근데 있잖아」처럼 아직 말하는 중인 말은 null 입니다(앱이 따로 처리합니다).

target: repair 일 때 아이가 고치려는 칸 이름(칸 이름 뜻 목록 안). 모르겠거나 repair 가 아니면 null.
new_value: repair 이면서 아이가 맞는 내용을 같이 말했으면 true, 아니면 false.
fixed_value: repair 이면 위 「아이 반응 받기」의 repair 규칙대로 씁니다(retry_slot 은 target 칸). repair 가 아니면 null.
act 가 repair · answer_back · rephrase 이면 question 은 next_slot 이 아니라 고친 칸이나 방금 물은 칸을 다시 묻습니다.
```
