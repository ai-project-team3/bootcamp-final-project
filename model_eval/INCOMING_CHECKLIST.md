# 팀 자료 도착 체크리스트

## 박진웅에게 받기
- [ ] `eval/judge_prompt.md`
- [ ] `eval/judge_schema.json`
- [ ] 판정 100개의 gold 라벨(안치영 평가셋에 병합 가능한 형태)
- [ ] `eval/story_prompt.md`
- [ ] `eval/story_schema.json`
- [ ] `eval/forbidden_words.txt` 또는 공유 금지어 목록

## 안치영에게 받기
- [ ] `eval/fixtures_judge.jsonl` 100줄
  - `id`, `slots`, `asked`, `template`, `utterance`
  - gold는 없어도 예측 수집 가능
- [ ] `eval/fixtures_story.jsonl` 3벌

## 조장에게 받기
- [ ] `OPENAI_API_KEY`
- [ ] `MISTRAL_API_KEY`
- [ ] Anthropic 실행 환경 확인 (`ANTHROPIC_API_KEY`, 이미 보유 중이면 그대로)
- [ ] 실제 호출비를 어느 팀 계정/크레딧으로 처리할지 확인

## 다 받으면
```bash
python preflight.py
python run_team_eval.py --yes-spend
```

Gold가 늦게 오면:
```bash
python score_existing.py
```
