# 시작 연출 번들

2026-10-08 민우·조장 승인 HTML PoC (`poc-html/app-preview`)의 무대·커튼·밧줄 그림과 고정 소리다.

- `otto-intro.mp3`: 팀 TTS로 미리 구운 「오또!」. 앱 시작 시 네트워크 호출 없음.
- `hop-jelly.wav`: PoC의 말랑 젤리(A2), 0.43초 합성 효과음. 점프 시작에 음량 0.45로 재생.
- `stage-premium.png`, `curtain-premium.png`, `rope-premium.png`: 승인된 원본 이미지. 원본의 알파 유지.
- 마스코트와 로고는 앱의 기존 `otto_pose_wave`, `logo_otto_v2`를 재사용한다.

고양이 도착 소리·다른 후보 소리·HTML 가짜 메뉴는 포함하지 않는다. 앱 효과음 설정을 따르며 화면을 떠나면 재생을 해제한다.
