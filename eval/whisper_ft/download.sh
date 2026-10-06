#!/bin/bash
# AI-Hub 「자유대화 음성(소아남여, 유아 등 혼합)」(데이터셋 108)에서 미세조정에 쓰는 두 파일만 받는다.
#   48685  1.AI챗봇_라벨링_자유대화(소아남여)_TRAINING.zip  약 3GB  (라벨 json)
#   48616  1.AI챗봇_8_자유대화(소아남여)_TRAINING.zip       약 14GB (3~6세 wav)
# 파일 번호는 `aihubshell -mode l -datasetkey 108` 로 10-06 에 확인한 값이다.
#
# ⚠️ 재배포 금지 데이터다. 받은 폴더를 레포 · 클라우드 · 메신저에 올리지 않는다(레포는 공개다).
# ⚠️ API 키는 사람이 사용자 환경 변수 AIHUB_APIKEY 로 미리 넣는다 — 채팅 · 파일 · 명령줄에 적지 않는다.
#    넣는 법은 README 「키 넣기」(입력칸에 붙여 넣는 방식 · 명령 기록에 안 남는다). Git Bash 도 그 값을 읽는다
#
#   bash eval/whisper_ft/download.sh D:/aihub
set -euo pipefail
DEST="${1:?받을 폴더를 준다 — 예: D:/aihub}"
: "${AIHUB_APIKEY:?AIHUB_APIKEY 가 없다 — 사람이 환경 변수로 넣는다(위 주석)}"
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
