# 다시 그리기 GPU (Modal) — #460 ⑥

그림일기 「오또가 그려 줘」(`/image` `kind: redraw`)를 PC2 대신 **우리 계정의 Modal L4**에서 그린다. **기본은 꺼져 있다.** `REDRAW_MODAL_URL`이 비어 있으면 지금처럼 PC2 ComfyUI로 간다. 배경과 인물은 이 설정과 상관없이 늘 PC2로 간다.

| 파일 | 하는 일 |
|---|---|
| `otto_redraw_modal.py` | Modal 앱. Server 방식(`@app.server`), L4, 메모리 스냅샷. ComfyUI 노드를 같은 프로세스에서 부르고, 모델 볼륨은 읽기 전용 |
| `web.py` | `/wake` · `/draw`. 본문을 로그에 남기지 않고, 실패해도 예외 이름만 돌려준다. `modal` 없이 돌아서 백엔드 테스트(`tests/test_redraw_modal.py`)가 바로 부른다 |

## 왜 Server 방식인가
Modal은 `@modal.fastapi_endpoint`(Web Function)의 입력과 출력을 **최대 7일 보관**한다. Server 방식은 요청을 **저장하지 않고 컨테이너로 바로 넘긴다**(10-13 확인, `docs/그림생성_API_조사_410.md` §0-5 ④). 아이 그림 원본이 Modal에 남지 않게 하려고 Server 방식을 쓴다.

## 켜는 법
1. `modal run backend/modal_gpu/otto_redraw_modal.py::download_models`: 모델을 볼륨에 받는다. 한 번만 하면 된다
2. `modal deploy backend/modal_gpu/otto_redraw_modal.py`: 출력의 Server 주소를 적어 둔다
3. Modal 대시보드에서 Proxy Auth Token을 만든다
4. 서버 `.env`에 `REDRAW_MODAL_URL` · `REDRAW_MODAL_KEY` · `REDRAW_MODAL_SECRET`를 넣고 백엔드를 다시 띄운다

## 동작
- 그림일기 `/turn`이 올 때마다 백엔드가 `/wake`를 부른다(10초에 한 번까지). 그래서 아이가 그리는 동안 GPU가 켜져 있다
- 그림일기가 끝나면 1분 뒤(`scaledown_window=60`) GPU가 꺼진다. 켜져 있던 시간만 과금된다
- 실측(10-13): 켜져 있을 때 한 장에 3.0~3.3초, 책 내내 켜 두면 4/4가 45초 안에 들었다. 권당 약 150~180원이다
- 컨테이너가 아직 없으면 프록시가 503을 돌려준다. 백엔드는 45초 기한 안에서 다시 시도하고, 기한을 넘기면 프리셋을 쓴다(규칙 8)
