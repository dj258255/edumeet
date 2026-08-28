"""JSON 로그. (#167)

★ 왜 print 를 걷어내나

  print 는 세 가지를 못 한다.
    - 레벨이 없다. 진행 상황과 에러가 같은 무게로 나간다
    - 맥락이 없다. 동시 요청이 섞이면 어느 줄이 어느 요청인지 모른다
    - 끌 수 없다. 시끄러운 줄을 지우려면 코드를 고쳐야 한다

  자바 쪽은 이미 JSON 으로 찍고 Loki 가 필드로 거른다(#166).
  파이썬만 평문이면 같은 회의를 두 곳에서 따로 찾아야 한다.

★ 왜 stderr 인가

  MCP 서버는 stdio 로 말한다. stdout 이 프로토콜 채널이라
  거기 로그를 한 줄이라도 찍으면 클라이언트가 프레임을 못 읽는다.
  두 서비스가 같은 모듈을 쓰므로 기본값을 stderr 로 둔다.
  FastAPI 쪽은 stdout 이든 stderr 이든 도커가 둘 다 모은다.

★ 왜 표준 라이브러리만 쓰나

  ai/ 는 requirements.txt 가 이미 무겁고(torch 계열까지 선택 의존성으로 있다)
  MCP 는 저장소 체크아웃 위에서 도므로 의존성을 늘리면 설치가 늘어난다.
  JSON 한 줄을 만드는 데 라이브러리가 필요하지 않다.
"""

import json
import logging
import os
import sys
import time
import uuid
from contextvars import ContextVar

# 요청 하나가 여러 줄을 남긴다. 묶을 것이 없으면 동시 요청이 섞여
# 어느 줄이 어느 요청인지 모른다. 자바가 X-Request-Id 로 넘겨 주면
# 그 값을 쓰고, 없으면 만든다. 그래야 서비스 경계를 넘어 한 줄로 묶인다.
request_id_var: ContextVar[str] = ContextVar("request_id", default="")
meeting_id_var: ContextVar[str] = ContextVar("meeting_id", default="")

# 로그에 그대로 실으면 안 되는 것들. 값이 아니라 있음/없음만 남긴다.
_REDACT = ("token", "secret", "password", "api_key", "authorization")


class JsonFormatter(logging.Formatter):
    """자바 쪽(logstash-logback-encoder)과 필드 이름을 맞춘다.

    이름이 다르면 Loki 에서 서비스마다 다른 질의를 짜야 한다.
    ``| json | meetingId="3"`` 하나로 둘 다 걸려야 한다.
    """

    def __init__(self, service: str):
        super().__init__()
        self.service = service

    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "@timestamp": time.strftime(
                "%Y-%m-%dT%H:%M:%S", time.localtime(record.created)
            ) + f".{int(record.msecs):03d}",
            "level": record.levelname,
            "logger_name": record.name,
            "thread_name": record.threadName,
            "message": record.getMessage(),
            "service": self.service,
        }
        rid = request_id_var.get()
        if rid:
            payload["requestId"] = rid
        mid = meeting_id_var.get()
        if mid:
            payload["meetingId"] = mid

        # 호출부가 extra= 로 넘긴 것을 최상위 필드로 올린다.
        for key, value in getattr(record, "fields", {}).items():
            if any(bad in key.lower() for bad in _REDACT):
                payload[key] = "***" if value else None
            else:
                payload[key] = value

        if record.exc_info:
            # 스택은 자르지 않으면 예외 하나가 로그 수백 줄이 된다.
            trace = self.formatException(record.exc_info)
            payload["stack_trace"] = trace[:3000]
        return json.dumps(payload, ensure_ascii=False)


def setup(service: str, stream=None) -> logging.Logger:
    """루트 로거를 JSON 으로 바꾼다. 한 번만 부르면 된다.

    :param service: ``backend`` 와 구분되는 이름. Loki 에서 이것으로 거른다
    :param stream: 기본은 stderr. MCP 는 stdout 이 프로토콜이라 반드시 stderr 여야 한다
    """
    root = logging.getLogger()
    for handler in list(root.handlers):
        root.removeHandler(handler)

    handler = logging.StreamHandler(stream or sys.stderr)
    handler.setFormatter(JsonFormatter(service))
    root.addHandler(handler)
    root.setLevel(os.getenv("LOG_LEVEL", "INFO").upper())

    # uvicorn 은 자기 핸들러를 붙인다. 그대로 두면 같은 줄이 두 번,
    # 한 번은 JSON 으로 한 번은 평문으로 나간다.
    for name in ("uvicorn", "uvicorn.access", "uvicorn.error"):
        logger = logging.getLogger(name)
        logger.handlers = []
        logger.propagate = True

    return root


def new_request_id() -> str:
    return uuid.uuid4().hex[:8]


def bind(request_id: str = "", meeting_id: str = "") -> None:
    """이 요청 동안 모든 로그에 붙일 값을 건다.

    ContextVar 라 요청마다 격리된다. 자바 쪽 MDC 와 달리 지우지 않아도
    다른 요청으로 새지 않는다 - 다만 같은 컨텍스트를 재사용하면 남으므로
    요청 시작마다 다시 건다.
    """
    request_id_var.set(request_id or new_request_id())
    meeting_id_var.set(str(meeting_id) if meeting_id else "")
