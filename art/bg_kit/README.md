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
| 가로등 | old street lamp | old street lamp standing on a long thin straight pole with a small round base … (세로 768×1344 판) | 등 머리만 크게 나와 공원 한가운데 거대한 등이 됐다 — 10-05 다시 구움(후보 4장 중 1번째) |

## 살아 있는 배경의 새 (10-05 · `docs/배경_조각_목록.md` §6-3)

- `kit_forest_bird.png`(앉은 새) · `kit_forest_bird_fly.png`(나는 새) — 목록 §3-5 「새」(`little blue bird`)를 **두 자세**로 구웠다. 후보 3장씩 중 0번(배가 흰 쌍)
- 둘 다 **오른쪽을 본다** — 앱이 날아가는 쪽으로 뒤집는다. 문구: `little round blue bird sitting, side view facing right, wings folded …` / `… flying, side view facing right, both wings spread wide open upward …`
- 배치 조각이 아니라 무대에 **들르는 손님**이다(`demo/scene/SceneMotion.kt` `VISITORS_BY_KIT` · 앉을 자리 `PERCHES_BY_RES`). 숲 · 시골 묶음의 나머지 10종은 아직 굽지 않았다

## 2순위 ① 공룡 나라 — 12종 (#97 · 10-06)

- 목록 §3-3 그대로 12종 · `kit_dino_*` · 후보 3장 중 하나씩. 앱 키트는 `SceneKit.kt` `DINO_KIT`(연노랑 하늘 · 황토 흙 · 하늘에는 공용 해 · 구름 · 나비도 같이)
- 세로로 긴 야자나무 · 폭포는 768×1344, 가로로 긴 무지개 · 통나무는 1344×768 판에 구웠다

| 조각 | 목록 문구 | 바꾼 문구 | 왜 |
|---|---|---|---|
| 화산 | small smoking volcano | cone-shaped brown volcano mountain with a dark red crater and a small white puff of smoke rising from its top | 연기 없는 전등갓 모양이 나왔다 |
| 고사리 | green fern plant | clump of green fern fronds growing straight out of the ground, no pot, no soil | 화분에 심긴 채 나왔다 |
| 공룡 발자국 | three-toed footprint | flat brown dinosaur footprint shape with three big toes, top view, a flat cut-out shape lying on the ground | 사람 발 인형이 나왔다 |

## 2순위 ② 우주 — 12종 (#97 · 10-06)

- 목록 §3-1 그대로 12종 · `kit_space_*` · 후보 3장 중 하나씩. 앱 키트 `SceneKit.kt` `SPACE_KIT`(남색 밤하늘 · 회보라 달 표면 · 하늘 조각은 달 · 고리 행성 · 작은 행성 · 별 · 별똥별 — 나비 · 새는 없다)
- 문구는 목록 그대로 썼다. 그대로 두고 알아 둘 것: 「작은 행성」은 대륙이 그려진 지구 모양 · 「달 바위」는 분화구 박힌 둥근 공 · 「분화구」는 고리(튜브) 모양으로 나왔다. 무대에서 어색하면 문구를 바꿔 다시 굽는다
- 세로로 긴 로켓 · 깃발은 768×1344, 가로로 긴 바위산 · 분화구 · 별똥별은 1344×768 판

## 알아 둘 것

- 앱 공통 그림체 문구(`gen_room.STYLE`)의 색 목록(크림 · 머스터드 · 코랄 · 청록 · 하늘) 때문에 **물체에 그 색 조각 · 점이 들어간다** — 돌의 점, 「빨강 · 하양 공」이 파스텔 여러 색, 풀이 여러 색. 앱 다른 그림과 맞추려고 그대로 두었다
- 장당 약 52초(그리기 48초 + 배경 빼기 4초 · RTX 4060 노트북). 배경 빼기 실패 0장 / 57장. 문구가 엉뚱하게 나온 것 3종(9장)은 다시 구웠다
