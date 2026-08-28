"""로그가 JSON 으로 나가고, 포맷 인자를 잘못 쓰지 않았는지 고정한다. (#167)

★ 왜 두 번째 시험이 필요한가

  print 를 logging 으로 바꾸면서 실제로 밟은 함정이다.

      print("[upload:url]", url)      # 둘을 나란히 찍는다
      log.info("[upload:url]", url)   # url 이 포맷 인자가 된다

  logging 에서 두 번째 인자는 ``%s`` 에 끼울 값이다. 첫 인자에 자리표시자가
  없으면 ``TypeError: not all arguments converted`` 가 난다.

  그런데 **이 예외는 로그를 삼키고 프로그램은 계속 돈다.** logging 이
  자기 안에서 잡아 stderr 에 "--- Logging error ---" 만 찍는다.
  즉 **그 줄만 조용히 사라진다.** 정작 그 줄이 필요한 순간에 없다.
"""

from __future__ import annotations

import ast
import io
import json
import logging
import os
import pathlib

import pytest

AI_DIR = pathlib.Path(__file__).resolve().parent.parent


def test_JSON_한_줄로_나가고_맥락이_필드가_된다():
    import sys
    sys.path.insert(0, str(AI_DIR))
    import logging_setup as L

    buf = io.StringIO()
    L.setup("ai", buf)
    L.bind("req0001", "42")
    logging.getLogger("t").info("자막을 보냈다", extra={"fields": {"count": 3}})

    line = buf.getvalue().strip()
    payload = json.loads(line)

    assert payload["message"] == "자막을 보냈다"
    assert payload["service"] == "ai", "자바·MCP 로그와 섞일 때 구분이 안 된다"
    assert payload["requestId"] == "req0001", (
        "자바가 넘긴 요청 아이디가 안 실리면 서비스 경계를 넘어 못 묶는다"
    )
    assert payload["meetingId"] == "42", (
        "회의 번호가 없으면 Loki 에 모아 놔도 회의별로 못 거른다"
    )
    assert payload["count"] == 3


def test_비밀값은_값이_아니라_있음만_남는다():
    import sys
    sys.path.insert(0, str(AI_DIR))
    import logging_setup as L

    buf = io.StringIO()
    L.setup("ai", buf)
    logging.getLogger("t").info("호출", extra={"fields": {"api_token": "s3cr3t"}})

    payload = json.loads(buf.getvalue().strip())
    assert payload["api_token"] == "***", "로그에 토큰이 그대로 남으면 로그가 비밀을 흘린다"


@pytest.mark.parametrize("path", ["main.py", "backend_client.py", "summary_llm.py",
                                  "summary_pdf.py", "backend_client.py"])
def test_로그_호출에_포맷_인자를_나열하지_않는다(path):
    """``log.info("...", x)`` 를 잡는다.

    이 형태는 예외가 밖으로 안 나오고 **그 줄만 조용히 사라진다.**
    사람이 눈으로 지키면 print 를 옮길 때마다 다시 생긴다.
    """
    source_path = AI_DIR / path
    if not source_path.exists():
        pytest.skip(f"{path} 없음")
    tree = ast.parse(source_path.read_text(encoding="utf-8"))

    bad = []
    for node in ast.walk(tree):
        if (isinstance(node, ast.Call)
                and isinstance(node.func, ast.Attribute)
                and node.func.attr in ("info", "warning", "error", "debug", "exception")
                and isinstance(node.func.value, ast.Name)
                and node.func.value.id in ("log", "logger", "logging")
                and len(node.args) > 1):
            first = node.args[0]
            has_placeholder = (
                isinstance(first, ast.Constant)
                and isinstance(first.value, str)
                and "%" in first.value
            )
            if not has_placeholder:
                bad.append(node.lineno)

    assert not bad, (
        f"{path} 줄 {bad}: 로그에 인자를 나열했다. "
        "logging 의 두 번째 인자는 %s 에 끼울 값이라 자리표시자가 없으면 "
        "TypeError 가 나고 그 줄이 조용히 사라진다. f-string 으로 합쳐야 한다"
    )
