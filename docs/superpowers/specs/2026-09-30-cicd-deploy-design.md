# CI/CD + 배포 브랜치 설계 (2026-09-30)

작성 근거: 사용자 요청 — "CI/CD 이용하고 싶은데. 배포형 브랜치까지 따로 만들고"
브레인스토밍 분류: **architectural** (새 서브시스템, 팀 전체 워크플로 변경)

## 배경

현재 이 레포에는 테스트/빌드를 자동으로 검증하는 CI가 없다. `.github/workflows/discord-github-events.yml`은
Discord 알림용이고 테스트·빌드와 무관하다. 브랜치는 `main` + `feature/<이름>`뿐이고, 배포는 전부 수동이다
(`docs/서버_세팅.md` — 백엔드는 PC1/PC2에서 사람이 직접 `uvicorn` 실행, 안드로이드는 조장이 Play Console에 수동 업로드).

백엔드는 GPU(STT + ComfyUI)가 필수라 클라우드 CI 러너에서 실제 추론 경로를 돌릴 수 없다. 다만
`backend/app/config.py`의 `mock` 설정과 `tests/test_api.py`가 보여주듯, 백엔드 pytest 스위트는
`settings.mock = True`로 GPU·키 없이 전부 돈다. `requirements.txt`의 CUDA 전용 패키지(`nvidia-cublas-cu12`,
`nvidia-cudnn-cu12`)는 `sys_platform == "win32"` 마커가 붙어 있어 Linux 클라우드 러너에서는 자동으로 설치가
스킵된다. 안드로이드 Robolectric 유닛테스트와 `assembleDebug`도 JVM + Android SDK만 있으면 GPU 없이
클라우드 러너에서 실행 가능하다.

## 결정된 범위

- CI: 테스트(백엔드 pytest, 안드로이드 Gradle 유닛테스트) + 빌드 산출물 확인(안드로이드 debug APK, 서명 없음)
- CD: **백엔드 자동 배포 포함** — `release` 브랜치 병합 시 실제 GPU PC(PC1, 이 문서 작성 PC)에 상시 설치된
  self-hosted GitHub Actions 러너가 코드를 pull하고 백엔드 프로세스를 재시작한다
- 범위 밖: 안드로이드 서명·Play Console 자동 업로드(조장 수동 원칙 유지), ComfyUI(PC2) 자동 배포,
  GPU 연동 실통합 테스트, 린트/포맷 검사, 자동 롤백

## 브랜치 정책

- `main` — 지금처럼 통합 브랜치. `feature/<이름>` → PR → 조장 병합. 직접 푸시 금지(기존 규칙 유지)
- `release` — 신설 배포 브랜치. **오직 조장만 `main` → `release` PR을 병합**할 수 있다. 이 브랜치에
  새 커밋이 올라가는 것이 곧 "지금 상태를 PC1에 배포한다"는 명시적 의사결정이다
- 머지 방향은 항상 `main → release` 한쪽으로만. `release`에서 직접 커밋하거나 `release → main` 역병합은
  하지 않는다(배포 브랜치가 오염되지 않게)

**GitHub 브랜치 보호 규칙** (사람이 GitHub 웹 Settings → Branches에서 설정 — 관리자 권한 필요):
- `main`: PR 필수, CI(테스트+빌드) 통과 필수, 직접 푸시 금지
- `release`: PR 필수, CI 통과 필수, "Restrict who can push to matching branches"에 조장 계정만 등록해
  조장 외에는 병합 자체가 불가능하게 한다

## CI 워크플로 — `.github/workflows/ci.yml`

트리거: `main`, `release` 대상 PR + `main` 푸시. 전부 GitHub-hosted `ubuntu-latest`(GPU 불필요).

**Job `backend`**
```yaml
- actions/setup-python@v5 (python-version: '3.12')
- cd backend && pip install -r requirements.txt
- pytest -q tests
```

**Job `android`** (병렬)
```yaml
- actions/setup-java@v4 (distribution: temurin, java-version: '17')
- android-actions/setup-android@v3
- cd android && ./gradlew -q :app:testDebugUnitTest
- ./gradlew -q :app:assembleDebug
- 실패 시 app/build/test-results/testDebugUnitTest/*.xml 을 아티팩트로 업로드
```

두 job 모두 통과해야 PR이 병합 가능(브랜치 보호 규칙과 연결).

## 배포 워크플로 — `.github/workflows/deploy.yml`

**트리거**: `release` 브랜치로의 push. `runs-on: [self-hosted, pc1]`

```yaml
steps:
  - checkout
  - .env 존재 확인 (Test-Path) — 없으면 즉시 실패. 키 없이 mock 서버가 조용히 뜨는 것을 막는다
  - pip install -r backend/requirements.txt
  - scripts/deploy/stop_backend.ps1
  - scripts/deploy/start_backend.ps1
  - 최대 30초, 2초 간격 재시도: curl 127.0.0.1:8010/health → {"status":"ok","mock":false}
  - 실패 시 워크플로를 실패 처리(백엔드는 이미 재시작이 시도된 상태이므로 조장에게 알림 필요)
```

### 재시작 스크립트

`scripts/deploy/stop_backend.ps1`:
- `Get-NetTCPConnection -LocalPort 8010 -ErrorAction SilentlyContinue`로 8010번을 쓰는 프로세스를 찾아 종료
- 프로세스가 없으면 그냥 통과(최초 배포 시나리오)

`scripts/deploy/start_backend.ps1`:
- 레포의 `backend` 디렉터리에서 `Start-Process py -ArgumentList "-m uvicorn main:app --host 0.0.0.0 --port 8010"` 으로
  분리 실행(콘솔에 붙지 않음), stdout/stderr는 `logs/backend-<timestamp>.log`로 리다이렉트
- 즉시 반환(워크플로가 헬스체크로 기동 완료를 확인)

`/health`는 프로세스 기동 여부만 즉시 알려준다(모델 로딩 완료 여부는 아님 — `backend/main.py`의 기존 동작과
동일). 이는 `docs/서버_세팅.md` §4의 기존 수동 확인 절차("확인: `http://127.0.0.1:8010/health` → `mock:false`")와
일치시킨 것으로, 모델 완전 로딩 후 "1~2분 기다린 다음 시연" 하는 관행은 기존과 동일하게 사람이 배포 후 유지한다.

## self-hosted 러너 설치 (일회성 인프라)

1. `[사람]` GitHub 리포 → Settings → Actions → Runners → New self-hosted runner (Windows) → 등록 토큰 확인
2. `[세션]` 일반 PowerShell(관리자 불필요)에서 GitHub가 안내하는 다운로드·압축 해제·`config.cmd` 명령 실행,
   `--labels pc1,backend-deploy` 지정, 토큰은 사람이 붙여넣는다
3. `[사람]` 관리자 PowerShell에서 GitHub가 안내하는 서비스 설치 스크립트로 Windows 서비스 등록(PC 재부팅 시
   자동 시작)
4. `[사람]` 러너의 작업 폴더(`C:\actions-runner\_work\<repo>\<repo>\.env`)에 USB로 `.env`를 1회 복사
   (현재 수동 절차와 동일 — 세션은 `.env` 내용을 읽지 않는다)

## 안전 규칙

- ComfyUI(PC2)는 이 배포 범위 밖 — 별도 관리, 현행 수동 절차 유지
- 배포는 `main → release` PR을 조장이 병합할 때만 발생 — 임의 트리거 불가
- 러너는 리포를 읽고 실행만 한다. 러너가 리포에 다시 푸시하는 일은 없다
- 롤백은 범위 밖(YAGNI) — 실패 시 조장이 `release`를 이전 커밋으로 되돌려 재배포

## 테스트 계획

- CI 워크플로 자체는 실제 PR을 열어 초록/빨강이 의도대로 나오는지로 검증(고의로 실패하는 테스트를 임시로
  넣었다 빼는 방식)
- 배포 워크플로는 `release`에 사소한 변경(예: 로그 문구 하나)을 병합해 PC1에서 실제로 재시작·헬스체크가
  도는지 확인
- `stop_backend.ps1` / `start_backend.ps1`은 PC1에서 수동으로 먼저 실행해 동작을 확인한 뒤 워크플로에
  연결한다
