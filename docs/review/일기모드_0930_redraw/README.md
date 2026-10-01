# 그림일기 「오또가 대신 그려 주기」 그림체 비교 (09-30 · 진웅)

**결론 — 그림일기 redraw 는 「색연필 스케치 · denoise 0.85」로. 모델은 그대로, 프롬프트와 숫자만 바꾼다.**

## 무엇을 쟀나
- 같은 원격 ComfyUI(`192.168.0.52:8188` · RTX 3060 12GB)에서 **지금 redraw 와 같은 모델**(SDXL base + Lightning 8스텝 LoRA)로
- 아이 그림 넷(집 · 해 · 엄마 · 강아지 — 굵은 크레용 선으로 흉내)을 **실제 redraw 와 같은 길**로 돌렸다: `prepare_drawing` → `redraw_workflow` → `cut_and_fit(…, True)`
- 바꾼 것은 **그림체 프롬프트 · 네거티브 · denoise(원본을 얼마나 바꾸나)** 뿐이다. 그림 설명 LLM · 안전 검사는 뺐다(시간은 ComfyUI + 오려 내기만)
- 스크립트: `sketch_bench.py` (backend/ 에서 그 venv 로 실행)

## 결과
| 그림 | 설정 | 판단 |
|---|---|---|
| `1_denoise_0.6-0.9.png` | 지금(종이 오리기 0.9) · 크레용 0.9 / 0.75 / 0.6 · 색연필 0.75 | 0.6 · 0.75 는 **거의 베낌** — 오또가 그려 준 느낌이 없다. 오려 내기가 가장 큰 덩어리만 남겨 **해의 햇살 · 강아지 머리가 잘렸다** |
| `2_crayon_vs_pencil.png` | 크레용 0.8 / 0.85 / 0.9 · 색연필 0.85 / 0.9 | **색연필 0.85** 가 아이 구도(집 윤곽 · 서 있는 엄마 · 옆모습 강아지)를 지키면서 스케치 질감을 더한다. 0.9 는 예쁘지만 구도에서 멀어진다(강아지가 정면). 크레용 0.85 는 들쭉날쭉 |
| `3_pencil_0.85_3seeds.png` | 색연필 0.85 · 씨앗 셋 | **12장 중 11장이 구도 유지.** 해 한 장만 햇살이 빠짐(오려 내기) |

**시간** — 한 장 4.2~6.9초(대부분 4.3~5.0). 지금 설정(종이 0.9)은 5.2~5.4초. **느려지지 않는다.**
**GPU 메모리** — 모델이 같아서 **늘지 않는다**. ComfyUI 가 올려 둔 모델을 그대로 쓰고, 그림마다 바뀌는 것은 글자와 숫자뿐이다.

## 쓴 프롬프트
```python
DIARY_STYLE = (", colored pencil sketch, loose hand-drawn pencil outlines, light colored pencil shading, "
               "gentle storybook sketch, isolated on plain pure white paper background, no shadow, no text")
DIARY_NEG = ("text, letters, watermark, photo, photorealistic, 3d render, cut paper, collage, felt, "
             "blurry, ugly, scary, dark, horror, background scenery, frame, border, card, colored background, "
             "multiple subjects, nudity, blood, weapon, gore")
DIARY_DENOISE = 0.85
```

## 남은 것 (서버 쪽)
1. **그림 설명 LLM** — redraw 가 캐릭터용 프롬프트를 빌려 써서 집 · 해 같은 물건을 「그릴 수 없음」으로 자주 거절한다(#32 댓글 · 집 · 해 거절, 강아지 · 엄마 성공). 일기 redraw 에는 물건도 받는 설명 프롬프트가 필요하다
2. **오려 내기** — 떨어진 조각(햇살 · 떼어 그린 머리)을 버린다. 스케치는 흰 종이 위라서 가장 큰 덩어리만 남기지 말고 흰 바탕만 투명하게 하는 쪽이 맞을 수 있다
3. 샘플은 그림 넷 × 씨앗 셋이다 — 실제 아이 그림(조카 10/3)으로 한 번 더 본다
