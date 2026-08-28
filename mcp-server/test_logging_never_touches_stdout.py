"""로그가 stdout 을 오염시키지 않는지 고정한다. (#167)

★ 왜 이 시험이 필요한가

  MCP stdio 서버는 stdout 이 곧 프로토콜 채널이다.
  로그가 거기 한 줄이라도 섞이면 클라이언트가 프레임을 못 읽는다.

  증상이 고약하다. "로그가 지저분하다" 가 아니라 **도구가 통째로 안 붙는다** 이고,
  원인이 로그라고는 아무도 생각하지 않는다. 그래서 사람이 눈으로 지키게 두면 안 된다.

  print 한 줄, logging.basicConfig() 한 줄이면 깨진다.
  둘 다 기본 출력이 stdout 이다.
"""

from __future__ import annotations

import io
import logging
import os
import subprocess
import sys

import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
AI_DIR = os.path.join(os.path.dirname(HERE), "ai")


def test_로거를_붙여도_stdout_은_비어_있다(capsys):
    """setup() 이 stderr 로만 쓰는지 본다."""
    sys.path.insert(0, AI_DIR)
    from logging_setup import setup

    setup("mcp", stream=sys.stderr)
    logging.getLogger("t").info("이 줄은 stderr 로만 가야 한다")

    captured = capsys.readouterr()
    assert captured.out == "", (
        "stdout 에 로그가 섞였다. MCP 는 stdout 이 프로토콜 채널이라 "
        f"클라이언트가 프레임을 못 읽는다. 새어 나온 것: {captured.out!r}"
    )
    assert "이 줄은 stderr" in captured.err


def test_서버_모듈을_불러오는_것만으로도_stdout_이_안_더러워진다():
    """import 부작용까지 본다.

    모듈 최상위에 print 나 basicConfig 가 있으면 여기서 걸린다.
    서버를 띄우기 전에 이미 깨져 있는 경우다.
    """
    code = (
        "import sys, io\n"
        "buf = io.StringIO(); sys.stdout = buf\n"
        "import server\n"
        "server._setup_logging()\n"
        "import logging; logging.getLogger('x').warning('경고 한 줄')\n"
        "sys.stdout = sys.__stdout__\n"
        "print('LEAKED:' + buf.getvalue())\n"
    )
    result = subprocess.run(
        [sys.executable, "-c", code], cwd=HERE,
        capture_output=True, text=True, timeout=60,
    )
    if result.returncode != 0:
        pytest.skip(f"server 모듈을 못 불러왔다: {result.stderr[-300:]}")

    leaked = result.stdout.split("LEAKED:", 1)[-1].strip()
    assert leaked == "", (
        "server 를 불러오거나 로그를 찍는 것만으로 stdout 이 더러워졌다. "
        f"새어 나온 것: {leaked!r}"
    )
