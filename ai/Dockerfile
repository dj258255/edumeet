# AI 서비스 (STT · 요약 · PDF). (#110)
#
# ★ 3.12 를 고정한다. 코드가 `str | None`(PEP 604)을 쓰므로 3.10 미만에서는
#   import 조차 되지 않는다. 3.9 로 돌리다 그 사실을 알았고,
#   그때는 어느 버전이 필요한지 저장소 어디에도 적혀 있지 않았다.
FROM python:3.12-slim AS base

WORKDIR /app

# 의존성을 먼저 넣어 레이어를 캐시한다. main.py 가 바뀌어도 재설치하지 않는다.
COPY requirements.txt ./
RUN pip install --no-cache-dir -q -r requirements.txt

# 폰트는 PDF 생성에 쓴다. 없으면 한글이 깨진 채로 나온다 -
# 예외가 아니라 "글자가 네모로 나오는" 형태라 발견이 늦다.
COPY fonts ./fonts
# main.py imports the backend client and caption normalizer at startup.
# Keep this list explicit so a missing runtime module is visible in review.
COPY main.py backend_client.py caption_normalizer.py config.env ./

# 컨테이너 메모리 제한을 파이썬이 알 방법이 없으므로 워커 수로 관리한다.
# 이 서비스는 한 요청이 수 분 걸리는 대신 동시 요청이 적다.
ENV UVICORN_WORKERS=2 \
    PYTHONUNBUFFERED=1

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request;urllib.request.urlopen('http://localhost:8000/docs',timeout=3)" || exit 1

CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port 8000 --workers ${UVICORN_WORKERS}"]
