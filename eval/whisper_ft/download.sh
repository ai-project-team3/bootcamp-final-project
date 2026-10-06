#!/bin/bash
# AI-Hub 「자유대화 음성(소아남여, 유아 등 혼합)」(데이터셋 108)에서 미세조정에 쓰는 두 파일만 받는다.
#   48685  1.AI챗봇_라벨링_자유대화(소아남여)_TRAINING.zip  약 3GB  (라벨 json)
#   48616  1.AI챗봇_8_자유대화(소아남여)_TRAINING.zip       약 14GB (3~6세 wav)
# 파일 번호는 `aihubshell -mode l -datasetkey 108` 로 10-06 에 확인한 값이다.
#
# ⚠️ 재배포 금지 데이터다. 받은 폴더를 레포 · 클라우드 · 메신저에 올리지 않는다(레포는 공개다).
# ⚠️ API 키는 사람이 **레포 루트 `.env`** 에 `AIHUB_APIKEY=<키>` 한 줄로 넣는다(README 「키 넣기」).
#    `.env` 는 .gitignore 에 있어 커밋되지 않는다 — 이 스크립트가 직접 읽고 화면에 찍지 않는다.
#    Claude 세션은 `.env` 를 열지 않는다(CLAUDE.md). 키를 채팅 · 명령줄에 적지 않는다.
#    (환경 변수 AIHUB_APIKEY 가 이미 있으면 그것을 쓴다)
#
#   bash eval/whisper_ft/download.sh D:/aihub
set -euo pipefail
DEST="${1:?받을 폴더를 준다 — 예: D:/aihub}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [ -z "${AIHUB_APIKEY:-}" ] && [ -f "$ROOT/.env" ]; then
    # 레포가 공개라 .env 가 깃에서 빠지는지 먼저 본다 — 아니면 멈춘다
    git -C "$ROOT" check-ignore -q .env || { echo ".env 가 .gitignore 에 없다 — 키가 공개될 수 있어 멈춘다"; exit 1; }
    # 메모장으로 저장하면 줄 끝 CR · 따옴표 · BOM 이 붙는다 — 떼고 읽는다. 값은 찍지 않는다
    AIHUB_APIKEY="$(sed -n 's/^\xEF\xBB\xBF//; s/^[[:space:]]*AIHUB_APIKEY[[:space:]]*=[[:space:]]*//p' "$ROOT/.env" | tail -1 | tr -d '\r"'"'"'' | sed 's/[[:space:]]*$//')"
fi
[ -n "${AIHUB_APIKEY:-}" ] || { echo "AI-Hub 키가 없다 — 레포 루트 .env 에 AIHUB_APIKEY=<키> 한 줄을 사람이 넣는다(README 「키 넣기」)"; exit 1; }
export AIHUB_APIKEY
echo "AI-Hub 키: 있음(${#AIHUB_APIKEY}자)"

# --check: 받지 않고 키만 확인한다 — 라벨 파일 앞 1KB 만 요청해 응답 코드를 본다(10-06 확인: 키가 없거나
# 틀리면 HTTP 502 + 「인증실패, 권한이 거부되었습니다」). 키 값은 찍지 않는다
if [ "${2:-}" = "--check" ]; then
    body="$(mktemp)"
    code=$(curl -s -o "$body" -r 0-1023 --max-time 20 -H "apikey:$AIHUB_APIKEY" -w "%{http_code}" \
        "https://api.aihub.or.kr/down/0.6/108.do?fileSn=48685" || true)
    if [ "$code" = "200" ] || [ "$code" = "206" ]; then
        echo "키 정상 — 데이터셋 108 을 받을 수 있다 (HTTP $code)"
    else
        echo "키 확인 실패 — HTTP $code · 응답: $(head -c 200 "$body" | tr -d '\0')"
        echo "  「인증실패」면: 키가 틀렸거나(앞뒤 공백 · 줄바꿈 · 다른 계정의 키) · 재발급으로 옛 키가 무효가 됐거나 · 그 계정에 데이터셋 108 이용 승인이 없다"
    fi
    rm -f "$body"
    exit 0
fi
mkdir -p "$DEST"
cd "$DEST"
[ -f aihubshell ] || curl -fsSL -o aihubshell https://api.aihub.or.kr/api/aihubshell.do
chmod +x aihubshell
# 키는 환경 변수로만 넘긴다(aihubshell 이 AIHUB_APIKEY 를 읽는다) — 명령줄에 두면 프로세스 목록에 보인다
bash ./aihubshell -mode d -datasetkey 108 -filekey 48685,48616 2>&1 | tee download.log | grep -vE "^\s*[0-9]+ " || true
RAW=$(find . -name "1.AI챗봇_8_자유대화(소아남여)_TRAINING.zip" | head -1)
LAB=$(find . -name "1.AI챗봇_라벨링_자유대화(소아남여)_TRAINING.zip" | head -1)
[ -n "$RAW" ] && [ -n "$LAB" ] || { echo "받기 실패 — download.log 를 본다(키 · 데이터셋 이용 승인 확인)"; exit 1; }
echo "원천: $DEST/${RAW#./}"
echo "라벨: $DEST/${LAB#./}"
