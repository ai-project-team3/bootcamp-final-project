# design/ — pen.dev 디자인 파일

앱 화면 시안을 [pen.dev](https://www.pen.dev/)로 그려서 여기에 둡니다.
`.pen` 파일은 JSON이라 코드처럼 깃으로 이력이 남습니다.

## 설치

1. VS Code 확장 `pen.dev` 설치 — `code --install-extension highagency.pencildev`
2. `.pen` 파일을 열고 로그인
3. (Claude Code를 쓰면) 설정 ⚙️ → **MCP** → **Claude Code CLI** 켜기 → Claude Code를 다시 시작하고 `/mcp`에서 `pencil` 확인
4. `.pen` 파일이 글자로만 보이면 명령 팔레트에서 **pen.dev: Toggle Design Mode** 실행

## 파일

| 파일 | 내용 |
|---|---|
| `디자인시스템.md` | 디자인 시스템의 결정 사항과 근거(참고한 앱) |
| `UX_브리프.md` | 사용자 · UX 원칙. pen.dev AI 채팅에 붙여 넣는 용도 |
| `assets/` | 오또 자세(`poses/`) · 로고 · 배경 · 캐릭터 · 펠트 결 · 글꼴(`fonts/`, OFL) |
| `tools/build_design_system.py` | `.pen` 을 처음 만든 스크립트 (다시 돌리려면 `--force`) |
| `tools/make_logo2.py` | 로고 (Black Han Sans 펠트 패치) · 후보는 `assets/logo_candidates/` |
| `tools/make_poses.py` · `collect_poses.py` | ComfyUI(FLUX.1 Kontext)로 오또 자세 그림 만들기 · 고른 것 모으기 |

- 새 화면은 디자인 시스템의 화면 틀(800×360)을 복사해서 시작하고, 컴포넌트는 **인스턴스**로 가져다 씁니다.
- 색은 `$--felt-coral` 처럼 변수로 씁니다.
- 앱에 반영할 때 `Theme.kt` 를 이 시스템 값으로 바꿉니다(아직 안 바꿈).

## 파일 — 흐름도 구간별 (2026-09-28)

| 파일 | 흐름도 구간 |
|---|---|
| `otto_00_design_system.pen` | 색 · 글자 · 컴포넌트 · 흐름도 · 오또 자세 |
| `otto_01_onboarding.pen` | 처음 한 번 — CLAP ~ 기능 소개 (10) |
| `otto_02_home.pen` | 오또의 방 (3) |
| `otto_03_story.pen` | 이야기 짓기 — 주인공 고르기 + 세 모드 (10) |
| `otto_04_book.pen` | 책 — 만드는 중 ~ 책장 · 하루 한도 (6) |
| `otto_05_parent.pen` | 부모 영역 · 계정 · 탈퇴 (5) |
| `otto_06_exceptions.pen` | 예외 (3) |
| `otto_design_system.pen` | 위 전부를 한 파일에 (전체 보기용) |

그림은 모두 `assets/` 를 같이 쓴다. 화면 파일마다 공통 컴포넌트 판이 들어 있어 따로 열어도 깨지지 않는다.

### 같이 고치는 규칙

- **이제부터 `.pen` 은 pen.dev 에서 직접 고친다.** 스크립트(`tools/build_design_system.py`)는 처음 만들 때 쓴 것이고,
  다시 돌리면 직접 고친 내용이 덮어써지므로 `--force` 없이는 멈춘다.
- `.pen` 은 JSON 이라 두 사람이 같은 파일을 동시에 고치면 병합 충돌이 난다 → **파일마다 담당자 한 명.**
- 고친 사람이 ① 깃에 커밋 · 푸시 ② 그 파일의 공유 링크를 **Update snapshot** — 깃(원본)과 링크(보기용)가 어긋나지 않게.
- 깃과 pen.dev 는 자동으로 연결되지 않는다. 팀원 것을 보려면 `git pull` 뒤 편집기로 연다.

## 보여 주기

pen.dev **Share**로 링크를 만들면 설치하지 않은 사람도 브라우저로 볼 수 있습니다.
고친 뒤 **Update snapshot**을 누르면 같은 링크에 새 버전이 뜹니다.

## Compose로 옮기기

pen.dev가 공식으로 내보내는 코드는 웹 쪽(React · HTML 등)입니다.
Compose는 Claude Code에 부탁합니다. 예: *"`design/home.pen` 보고 첫 화면을 Compose로 구현해 줘"*
