# AGENTS.md

이 레포에서 일하는 코딩 에이전트(Codex 등)를 위한 안내다. Claude Code 는 `CLAUDE.md` 를 읽는다.

## 가장 먼저 — 규칙의 정본은 `CLAUDE.md` 다

**작업을 시작하기 전에 `CLAUDE.md` 를 끝까지 읽는다.** 제품 설명 · 언어 규칙 · **남을 깨뜨리는 규칙 아홉** · 모델 표 · 이 레포에서 일하는 법이 거기 있다.
이 파일은 그 내용을 되풀이하지 않는다(같은 것을 두 곳에 적지 않는다 — 어느 쪽이 최신인지 아무도 모르게 된다). 아래는 **에이전트가 자주 틀리는 것**만 적는다.

그다음 **`guidelines/` 전체(0 ~ 10)를 번호 순서대로 읽는다.** 골라 읽지 않는다 — 소유 파일 · 병합 규칙(`9_역할과_작업.md`)처럼 「내 일과 상관없어 보이는」 문서에 남을 깨뜨리지 않는 규칙이 있다.
- 특히 놓치면 안 되는 것: `9_역할과_작업.md` 의 **누가 어느 파일을 맡는가 · 병합 규칙**, `3_API_명세.md` 의 서버 계약, `6_통합_체크포인트.md` 의 지금 일정
- 문서끼리 다르면 **`CLAUDE.md` 와 날짜가 더 최신인 쪽**을 따른다. 오래된 문장을 근거로 코드를 바꾸기 전에 사람에게 묻는다
- 조장의 현재 상태 · 다음 할 일은 `docs/조장_인수인계.md`

## 언어

- 사람에게 하는 답 · 문서(`guidelines/` · `docs/`) · 앱 문구: **한국어**
- 코드 · 주석 · 커밋 메시지 · PR: **영어**
- 긴 한국어를 셸 히어독으로 파일에 쓰지 않는다 — 두 번 깨졌다. 파일 쓰기 도구를 쓴다

## 브랜치 · 커밋

- **`main` 에 직접 푸시하지 않는다.** 자기 브랜치(`feature/<이름>`)에 올리고 조장이 병합한다
- **다른 팀원의 feature 브랜치를 직접 가져오지(merge · cherry-pick) 않는다.** 남의 작업이 필요하면 조장이 `main` 에 병합한 뒤 `main` 을 자기 브랜치로 받는다 — 두 브랜치가 서로를 품으면 병합 순서가 꼬이고 충돌이 두 배가 된다
- 남이 맡은 파일(`9_역할과_작업.md`)을 고쳐야 하면 먼저 그 사람과 조장에게 묻는다
- 작게, 자주 커밋한다. 테스트가 통과한 조각마다
- 병합 전에 **전체 테스트**가 초록이어야 한다. 테스트 하나만 돌려서 통과해도 전체에서 깨지면 병합되지 않는다(09-29 실제로 보류된 사례가 있다)

## 테스트

**JDK 17 이상**이면 된다. 경로는 PC 마다 다르다 — 아래 경로는 예시다. 가장 쉬운 건 Android Studio 에 들어 있는 JBR 이다.

PowerShell (Windows 기본)
```powershell
# 자기 PC 의 JDK 로 바꾼다. 모르면: Android Studio → Settings → Build Tools → Gradle → Gradle JDK 에 보이는 경로
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd android
.\gradlew.bat -q :app:testDebugUnitTest
# 결과는 app\build\test-results\testDebugUnitTest\*.xml 로 센다 (실패가 있으면 gradle 이 BUILD FAILED 를 낸다)

cd ..\backend
py -m pytest -q tests          # py 가 없으면 python -m pytest -q tests
```

Git Bash (조장 PC)
```
cd android && export JAVA_HOME="/c/Android/jdk-17.0.20.1+1" && ./gradlew -q :app:testDebugUnitTest
cd backend && py -m pytest -q tests
```

## 서버 연결 — 꼭 알아야 할 것

- **서버 호출은 `android/.../net/Server.kt` 한 곳에서만 한다.** 실패하면 `null` 이다 — 예외를 던지지 않는다. `null` 이면 지금의 대본으로 간다(앱은 멈추지 않는다). 이 파일은 조장 소유다 — 모양을 바꾸려면 먼저 묻는다
- **모드별 스위치**: 서버를 부르는 곳은 `if (Server.liveFor(s.mode))` 로 감싼다. 꺼진 모드는 대본 그대로다. 켜기: `-e server <주소> -e live story,diary,coop` 또는 시연 서랍 「서버 연결」
- **이름은 폰에서 가린다**(규칙 6): 서버로 보내기 전에 `val mask = s.nameMask()` → `mask.mask(text)` · `mask.maskSlots(slots)`, 받은 글자는 `mask.unmask(it)`. **보낼 때와 받을 때 같은 mask** 를 쓴다. 목소리로 읽을 글자는 `mask.speakable(text, ConsentStore.nameVoiceAgreed)`
- **진짜 마이크 답에는 대본 꼬리표가 없다.** 서버 모드에서 아이 말은 `Reply.Spoke(text)` 로 오고 **`value` · `answer` 는 비어 있다.** `r.value` 로 답을 고르는 코드는 서버 모드에서 무한 반복하거나(「다시 한번 말해 줄래?」) 앱을 죽인다(`toLong("")`) — 09-29 실기기에서 둘 다 났다. 글자(`r.text`)에서 찾거나 `Server.turn()` / `Server.judge()` 결과를 쓴다
- 서버 엔드포인트와 필드는 `guidelines/3_API_명세.md` 가 정본이다: `/judge` · `/turn`(판정 + 마스코트 대사) · `/story`(앱이 `pages` 로 쪽 목록을 보낸다) · `/image`(배경 · 실패하면 `preset`) · `/stt` · `/tts`

## 하지 않는 것

- 키 · 비밀번호를 읽거나 출력하지 않는다(`.env` · `~/otto-release/keystore.properties`)
- 슬롯 이름을 새로 만들지 않는다 — 12개 밖은 `extra`(규칙 1)
- 모델 ID 를 코드에 상수로 박지 않는다 — `.env` / `backend/app/config.py` 설정에서 읽는다
- 측정 없이 모델 · 프롬프트를 바꾸지 않는다 — 판정 프롬프트는 3회 측정 범위로 가른다(`eval/results.md`)
