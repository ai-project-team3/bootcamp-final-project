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

## 2순위 ③ 바닷속 — 13종 (#97 · 10-06)

- 목록 §3-2 그대로 13종 · `kit_sea_*`. 앱 키트 `SceneKit.kt` `SEA_KIT`(청록 물빛 · 모래). 물고기가 나비 자리(산호 사이를 돌며 헤엄 · 가는 쪽을 봄), 거품이 구름 자리(바닥에서 떠오름)
- 세로로 긴 미역은 768×1344, 가로로 긴 먼 바위 언덕은 1344×768 판

| 조각 | 목록 문구 | 바꾼 문구 | 왜 |
|---|---|---|---|
| 거품 | clear water bubble | single round transparent soap-bubble-like water bubble, pale see-through light blue with a small white shine highlight, hollow and empty inside | 색 조각 박힌 펠트 공이 나왔다 — 바꾼 뒤에도 테두리는 펠트 조각(3장 중 2번째) |

## 3순위 실내 — 12종 · 4순위 숲·시골 — 10종 (#97 · 10-06)

- 목록 §3-6 · §3-5 문구 그대로 구웠다(후보 3장씩, 바꾼 문구 없음). 숲·시골의 새는 10-05 살아 있는 배경 때 먼저 구웠다(위)
- 실내 키트 `INDOOR_KIT` — 하늘 자리가 벽(창문 · 액자 · 시계를 건다), 바닥은 나무 마루, 언덕 두 겹은 벽 아래 판벽처럼 보인다. 먼 띠 · 바람 · 손님 없음
- 숲·시골 키트 `FOREST_KIT` — 공용 9종과 같이 쓴다. 소나무 · 사과나무 · 해바라기 · 당근이 바람에 휘고, 새가 굴뚝 · 소나무 · 사과나무 · 울타리 · 그루터기에 앉는다
- 고르기: 「할머니 집 · 농장 · 숲 · 산」은 숲·시골, 그냥 「집 · 방 · 어린이집 · 유치원 · 교실」은 실내

## 5순위 바닷가 — 8종 · 눈 나라 — 10종 (#97 · 10-06)

- 목록 §3-8 · §3-7 문구 그대로, 갈매기만 바꿨다(아래). 바닷가는 해 · 구름 · 바닷속 조개 · 불가사리 · 공룡 나라 야자나무를, 눈 나라는 해 · 돌을 같이 쓴다
- 바닷가 키트 `BEACH_KIT` — 먼 띠가 바다(언덕 두 겹을 바다색으로), 돛단배가 둥실, 갈매기가 하늘을 가로지른다. 「바다」라는 말은 여기로(바닷속은 「바닷속 · 물속 · 용궁」)
- 눈 나라 키트 `SNOW_KIT` — 연회색 하늘 · 눈밭, 눈송이가 내린다(새 움직임 `FALL`), 새가 눈사람 · 굴뚝 · 소나무에 앉는다
- 썰매는 의자 모양으로 나왔지만 썰매 날이 있어 그대로 두었다

| 조각 | 목록 문구 | 바꾼 문구 | 왜 |
|---|---|---|---|
| 갈매기 | white seagull | white seagull flying with both wings spread wide, side view, gliding in the air | 앉은 갈매기 인형만 나왔다 — 하늘채움이라 나는 모습이 필요 |

## 다시 구운 셋 (#222 · 10-06)

| 조각 | 목록 문구 | 바꾼 문구 | 왜 |
|---|---|---|---|
| 우주 작은 행성 | small round blue planet | small round planet with craters and soft stripes, no continents, no oceans | 대륙 그려진 지구가 나왔다 |
| 우주 분화구 | shallow round crater | a patch of flat grey moon ground with a shallow bowl-shaped hollow pressed into it, a low raised rim of the same grey felt, all grey and pale lilac, no stripes, no colors, seen from the side at a low angle | 알록달록 튜브(고리)가 나왔다 · 「round · ring」을 넣은 두 번째 문구도 튜브 — 「바닥에 눌린 구덩이」로 바꾸니 회색 달 바닥이 됐다(공통 그림체의 색 점은 가장자리에 조금 남음) |
| 숲 연못 | small round pond | small round blue pond lying flat on green grass, seen from the side, flat water surface, no ring | 튜브 같은 둥근 테두리가 나왔다 |

## 알아 둘 것

- 앱 공통 그림체 문구(`gen_room.STYLE`)의 색 목록(크림 · 머스터드 · 코랄 · 청록 · 하늘) 때문에 **물체에 그 색 조각 · 점이 들어간다** — 돌의 점, 「빨강 · 하양 공」이 파스텔 여러 색, 풀이 여러 색. 앱 다른 그림과 맞추려고 그대로 두었다
- 장당 약 52초(그리기 48초 + 배경 빼기 4초 · RTX 4060 노트북). 배경 빼기 실패 0장 / 57장. 문구가 엉뚱하게 나온 것 3종(9장)은 다시 구웠다
