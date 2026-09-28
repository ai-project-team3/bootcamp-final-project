# design/ — pen.dev 디자인 파일

앱 화면 시안을 [pen.dev](https://www.pen.dev/)로 그려서 여기에 둡니다.
`.pen` 파일은 JSON이라 코드처럼 깃으로 이력이 남습니다.

## 설치

1. VS Code 확장 `pen.dev` 설치 — `code --install-extension highagency.pencildev`
2. `.pen` 파일을 열고 로그인
3. (Claude Code를 쓰면) 설정 ⚙️ → **MCP** → **Claude Code CLI** 켜기 → Claude Code를 다시 시작하고 `/mcp`에서 `pencil` 확인
4. `.pen` 파일이 글자로만 보이면 명령 팔레트에서 **pen.dev: Toggle Design Mode** 실행

## 파일

- `tokens.pen` — 색·글꼴·모서리 값과 폰 화면 틀 (412×915)
- 새 화면은 `tokens.pen`의 **폰 화면 틀**을 복사해서 시작하고, 색은 `$--sun`처럼 변수로 씁니다.

## 색은 `Theme.kt`와 같은 값

`tokens.pen`의 변수는 `android/.../ui/Theme.kt` 값을 그대로 옮긴 것입니다.
**한쪽을 바꾸면 다른 쪽도 같이 바꿉니다.** 글꼴은 앱과 같은 Jua입니다.

## 보여 주기

pen.dev **Share**로 링크를 만들면 설치하지 않은 사람도 브라우저로 볼 수 있습니다.
고친 뒤 **Update snapshot**을 누르면 같은 링크에 새 버전이 뜹니다.

## Compose로 옮기기

pen.dev가 공식으로 내보내는 코드는 웹 쪽(React · HTML 등)입니다.
Compose는 Claude Code에 부탁합니다. 예: *"`design/home.pen` 보고 첫 화면을 Compose로 구현해 줘"*
