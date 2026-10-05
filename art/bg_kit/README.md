# 배경 조각 1순위 — 공용 9종 + 공원·놀이터 10종 (#97 · 10-04)

- 목록 정본: `docs/배경_조각_목록.md` §3-0 · §3-4
- 굽기: `android/tools/gen_kit.py` (krea2 turbo → BiRefNet · `gen_coop.py` 와 같은 파이프라인 · 같은 펠트 그림체)
  - 문구: `a cute felt {굽기 문구}, ` + `gen_room.CUT`(물체 하나 · 흰 배경 · 그림자 없음 · 앱 공통 STYLE)
- 형태: 투명 PNG · 여백 자름 · 긴 변 1024px · 조각 하나에 파일 하나 · `kit_{묶음}_{조각}.png`
- 후보 3장씩 구워 하나씩 골랐다

## 바꾼 굽기 문구 (목록 §3 과 다른 것)

| 조각 | 목록 문구 | 바꾼 문구 | 왜 |
|---|---|---|---|
| 둥근 나무 | round green tree | round green tree with a short brown trunk | 줄기 없이 덤불과 똑같이 나왔다 |
| 모래놀이터 | sandbox with a bucket | low square wooden sandbox filled with sand and a small bucket, no roof | 지붕 달린 가판대가 나왔다 |
| 돌 | round grey stone | plain smooth round grey stone, no spots | 색 점이 박혀 알처럼 보였다 — 다시 구워도 점은 남는다(아래) |

## 알아 둘 것

- 앱 공통 그림체 문구(`gen_room.STYLE`)의 색 목록(크림 · 머스터드 · 코랄 · 청록 · 하늘) 때문에 **물체에 그 색 조각 · 점이 들어간다** — 돌의 점, 「빨강 · 하양 공」이 파스텔 여러 색, 풀이 여러 색. 앱 다른 그림과 맞추려고 그대로 두었다
- 장당 약 52초(그리기 48초 + 배경 빼기 4초 · RTX 4060 노트북). 배경 빼기 실패 0장 / 57장. 문구가 엉뚱하게 나온 것 3종(9장)은 다시 구웠다
