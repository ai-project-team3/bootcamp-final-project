# GPU 백엔드 컨테이너. 빌드 컨텍스트는 레포 루트:
#   docker build -f backend/Dockerfile -t otto-backend .
#   docker run --gpus all --env-file C:\otto\.env -p 8000:8010 otto-backend
#   (team server: host 8000 -> container 8010 · 10-01 PR #49 · scripts/deploy/start_backend.ps1)
#
# backend/app/llm/judge_prompt.py 등이 eval/*.md · *.json · prompt_block.py를 REPO/"eval"
# 경로로 직접 읽는다("측정값과 서빙 프롬프트가 어긋나지 않게") — backend/만으론 안 되고 eval/도 같이 필요하다.
#
# backend/app/routers/stt.py의 _add_cuda_dlls()는 os.name == "nt"일 때만 DLL 경로를 잡는다.
# Linux에서는 pip로 받은 cuBLAS/cuDNN .so를 LD_LIBRARY_PATH로 직접 알려줘야 한다(requirements.txt의
# nvidia-cublas-cu12 · nvidia-cudnn-cu12는 sys_platform == "win32" 전용이라 여기선 따로 설치).
FROM python:3.12-slim

WORKDIR /app

COPY backend/requirements.txt backend/requirements.txt
RUN pip install --no-cache-dir -r backend/requirements.txt \
    && pip install --no-cache-dir nvidia-cublas-cu12 nvidia-cudnn-cu12==9.*

COPY backend backend
COPY eval eval

WORKDIR /app/backend
ENV LD_LIBRARY_PATH=/usr/local/lib/python3.12/site-packages/nvidia/cublas/lib:/usr/local/lib/python3.12/site-packages/nvidia/cudnn/lib

EXPOSE 8010
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8010"]
