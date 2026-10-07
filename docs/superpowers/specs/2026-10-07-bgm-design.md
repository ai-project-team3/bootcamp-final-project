# 동화책 배경음악 설계 (#221 · 2026-10-07 · 박진웅)

## 목적
동화책을 읽을 때 **장면(쪽)마다 분위기에 맞는 배경음악**이 흐르게 한다. 오또 목소리와 아이 녹음을 방해하지 않고, 앱 용량은 최소로.

## 정한 것 (진웅 10-07)
- **어디서**: 동화책 첫 읽기(`sceneBook`) + 책장에서 다시 읽기(`SavedStoryView`). 그림일기 · 협업 책은 이번 범위 밖
- **분위기 고르기**: 앱 안에서 **쪽 종류(`PageKind`)로** 정한다. 서버 · 프롬프트 · API 는 바꾸지 않는다(비용 0 · 측정 불필요). 들어 보고 어긋나면 다음 단계로 서버가 장면 분위기를 고르는 안을 검토
- **분위기 여섯 · 곡 열둘**(분위기마다 2곡, 아래 표)
- **음원**: 로컬 ACE-Step 1.5 로 만든 곡을 앱에 넣는다. 형식 **Opus 48kbps · `.webm`**(12곡 약 5MB)
- **페이드**: 시작 페이드 인 · 멈춤 페이드 아웃 · 분위기가 바뀌면 크로스페이드
- 오또가 말할 때 줄이고, 🎤 녹음 중에는 멈춘다 · ⏸ · 화면 꺼짐에 같이 멈춘다 · 부모 화면에서 켜고 끈다

## 분위기와 곡
생성: ACE-Step 1.5 `acestep-v15-turbo` + `acestep-5Hz-lm-0.6B` · 코드 `ace-step/ACE-Step-1.5@ca1e85f` · 60초 · 반주만(`[Instrumental]`) · `shift 3.0` · seed = 1000 + take · 프롬프트와 BPM 은 `D:\bgm_test\moods.json`(같은 내용을 출처 문서에 옮긴다). 고르기는 객관 지표(곡 중 무음 · 클리핑 · 반복 이음새 크기 차) + 진웅 귀 확인.

| 분위기 (`BgmMood`) | 쓰는 곡 | 묶음 |
|---|---|---|
| `DISCOVERY` 신나는 발견 / 즐거움 | `happy_discovery_0` · `happy_discovery_2` | 초반 베이스 |
| `PLAYFUL` 장난스러운 / 코믹 | `playful_comic_2` · `playful_comic_0` | 초반 베이스 |
| `ADVENTURE` 숲속 모험 / 출발 | `forest_adventure_2` · `forest_adventure_1` | 긴장 걸기 |
| `TENSE` 긴장 / 살짝 무서운 | `tense_moment_0` · `tense_moment_2` | 긴장 걸기 |
| `NIGHT` 밤 / 꿈 / 잠자리 | `night_dream_0` · `night_dream_2` | 마무리 |
| `ENDING` 행복한 결말 | `happy_ending_0` · `happy_ending_2` | 마무리 |

## 쪽 종류 → 분위기
| 쪽 | 분위기 |
|---|---|
| 표지(`COVER`) · 만남(`MEET`) | `DISCOVERY` |
| 출발(`DEPART`) · 여정(`JOURNEY`) | `ADVENTURE` |
| 대화(`TALK`) · 미션 쪽(`RUB` 문지르기 · `DRAG` 건네주기) | `PLAYFUL` |
| 흔들림(`SHAKE`) · 실패(`FAIL`) | `TENSE` |
| 함께(`TOGETHER`, 맺음) | `ENDING` |
| 다 읽은 뒤 끝 화면(`Scene.FRIENDS` · `Scene.END`) | `NIGHT` |

- 분위기마다 두 곡 중 하나를 **책마다 고정**한다 — 책 내용(제목 + 첫 본문 쪽 문장)의 해시로 고른다. 책 id 는 책장에 꽂을 때 새로 생겨서(`StoryBookStore` `UUID`) 첫 읽기 때는 없다. 제목 · 문장은 첫 읽기와 다시 읽기에 같으므로 다시 읽어도 같은 쪽에서 같은 곡이 나온다
- 앞뒤 쪽이 같은 분위기면 곡을 처음부터 다시 틀지 않고 이어 간다

## 구성 요소
### 1. `net/Bgm.kt` — 재생 장치 (새 파일 · 앱 전체 하나)
- 플레이어 두 개(A · B)를 번갈아 쓴다 — 크로스페이드와 반복 이음새 둘 다 이 둘로 한다
- 공개 함수: `play(mood, bookKey)`(`bookKey` = 위 책 내용 해시) · `stop()` · `duck(on)` · `hold()` · `resume()` · `enabled`(설정)
- 음량 = 기본 0.25 × 덕킹(목소리 중 0.32 → 실제 약 0.08) × 페이드 진행값. 값은 상수로 두고 실기기에서 조정
- 페이드: 시작 1.5초 인 · 멈춤 1.5초 아웃 · 분위기 바뀜 1.5초 크로스페이드 · **반복은 곡 끝 2초 전에 같은 곡을 다른 플레이어로 처음부터 틀며 2초 크로스페이드**(곡 안에 페이드를 굽지 않으므로 한 바퀴마다 꺼졌다 켜지지 않는다)
- 덕킹 · 다시 키우기는 0.3초 램프(뚝 끊기지 않게)
- 실제 재생기(`MediaPlayer`)는 인터페이스 뒤에 둬서 테스트가 가짜로 바꿔 끼운다
- 재생 실패(파일 없음 · 기기가 Opus 를 못 읽음)는 조용히 음악 없이 — 이야기는 멈추지 않는다. 로그만 남긴다

### 2. `demo/BookMood.kt` — 분위기 고르기 (새 파일)
- `moodOf(kind: PageKind): BgmMood` · `trackOf(mood, bookKey): String`(자산 경로) — 순수 함수, 단위 테스트 대상

### 3. 연결 지점 (기존 파일, 한두 줄씩)
| 어디 | 무엇 |
|---|---|
| `Scenes.kt` `sceneBook` `announce()` | 쪽이 열릴 때 `Bgm.play(moodOf(pageKind(i)), bookKey)` |
| `Scenes.kt` 끝 화면 · 방으로 나감 | 끝 화면 `NIGHT` · 책을 떠나면 `Bgm.stop()` |
| `ui/Shelf.kt` `SavedStoryView` | 쪽 넘김마다 `play`(저장된 `SavedStoryPage.kind`) · 닫으면 `stop` |
| `net/Voice.kt` `playAndWait` 시작 · 끝 | `Bgm.duck(true)` · `duck(false)` — 오또 목소리는 모두 여기를 지난다 |
| `net/Voice.kt` `record` · `sound/ChildSound.kt` `mic` · `ui/Blow.kt` | 녹음 시작 `Bgm.hold()` · 끝 `resume()` — 미션 녹음 둘은 `Voice` 를 거치지 않아 따로 |
| `demo/Director.kt` `holdSession` · `resumeSession` | ⏸ · 화면 꺼짐에 `hold` · `resume` |
| `MainActivity.kt` `ON_STOP` | 책장 다시 읽기처럼 세션 밖 화면도 `Bgm.hold()`(지금 `kidScreen` 만 잡는다) · 돌아오면 `resume` |
| `ui/Sfx.kt` `FeelPrefs` · `ui/Consent.kt` `SoundSettingsSection` | `"music"` 키(기본 켜짐) · 「소리와 진동」에 「배경음악」 줄 |

### 4. 음원 굽기 — `eval/bake_bgm.py` (새 파일)
- 입력: `D:\bgm_test\out\raw\ace\<mood>_<take>.wav`(48kHz 스테레오 · 페이드 없는 원본)
- 처리: 앞뒤 무음 제거 → 음량 -18 LUFS(`loudnorm`) → Opus 48kbps VBR · 48kHz 스테레오 `.webm` · **페이드 없음**
- 출력: `android/app/src/main/assets/bgm/<mood>_<n>.webm` + 목록 `assets/bgm/tracks.json`(분위기 · 원본 이름 · seed · 길이)
- 같은 입력이면 같은 출력 — 다시 돌려도 바뀌지 않게

### 5. 출처 · 라이선스
- `docs/배경음악_출처.md`: 곡마다 모델 · 체크포인트 · 코드 커밋 · 프롬프트 · BPM · seed · 길이 · 생성일 · 만든 사람, 라이선스 근거(코드 MIT · 모델 카드 MIT · 「생성 음악 상업 이용 가능」 인용 · 확인일 10-07), 미해결 HF 토론 #27(가중치 LICENSE 파일 없음 · Qwen2.5-Omni 기반 라이선스)과 「유료 출시 전 다시 확인」
- 앱 오픈소스 고지에 한 줄: 「배경음악: ACE-Step 1.5(MIT)로 생성」

## 오류 · 경계
- 설정이 꺼져 있으면 `play` 는 아무것도 안 한다. 켜면 지금 쪽 분위기부터
- 녹음 중에 `play` 가 오면 곡만 바꿔 두고 녹음이 끝나면 페이드 인
- 다시 읽기 화면은 따로 `Director`(`reader`)를 쓰지만 `Bgm` 은 앱에 하나라 겹치지 않는다
- 오또가 말하지 않는 대본 모드에서도 음악은 나온다(덕킹 신호만 없다)

## 테스트
- `BookMoodTest`: 쪽 종류 전부의 분위기 · 같은 책은 같은 곡 · 다른 책은 두 곡이 고루
- `BgmTest`(가짜 재생기 · 시간 직접 진행): 시작 페이드 인 · 바뀜 크로스페이드 · 같은 분위기는 재시작 안 함 · 반복 이음새 · 덕킹 램프 · 녹음 중 멈춤과 이어 틀기 · `hold`/`resume` · 설정 끄기 · 파일 실패 시 조용히
- 연결 테스트: `sceneBook` 쪽 넘김 · 다시 읽기 쪽 넘김 · 끝 화면 `NIGHT` · 목소리 재생 시 덕킹 호출
- `bake_bgm.py`: 12개 · 크기 합 · 페이드 없음 · 길이
- 실기기: Opus `.webm` 재생(최소 기기 S7 Android 8 · S9) · 목소리 위 크기 · 반복 이음새 · ⏸ · 화면 꺼짐 · 녹음 미션

## 범위 밖
그림일기 · 협업 책 · 방(대기 화면) 음악 · 서버가 장면 분위기 고르기 · 곡 새로 생성

## 리뷰 · 알림
공용 파일(`Voice` · `Director` · `MainActivity` · `Scenes.kt` · `Shelf.kt` · `Sfx.kt` · `Consent.kt`)을 고친다 → 조장 리뷰어 · `sceneBook` 은 동화 담당(최민우)도. 공용 흐름에 훅을 더하므로 PR 전에 #221 에 이 설계를 링크해 알린다(§9-0).

## 위험
- 일부 기기가 `.webm` Opus 를 못 읽을 수 있다 → 실기기 확인, 안 되면 AAC 96kbps `.m4a`(약 8.5MB)로 바꾼다
- APK 약 5MB 증가
- 생성 곡이 기존 곡과 닮았을 위험(제작사 경고) → 귀 확인 · 출처 기록
