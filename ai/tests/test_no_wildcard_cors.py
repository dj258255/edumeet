"""아무 사이트나 인증된 채로 부를 수 있는 CORS 설정을 막는다. (#179)

★ 무엇이 위험한가

  ``allow_origins=["*"]`` 와 ``allow_credentials=True`` 를 같이 쓰면
  Starlette 은 쿠키가 붙은 요청에 대해 **요청이 보낸 origin 을 그대로
  되돌려 준다**. 브라우저 입장에서는 "이 사이트는 허용됨" 이 되므로
  아무 사이트나 이 API 를 인증된 채로 부를 수 있다.

  ``*`` 를 쓰면 자격증명이 안 실린다고 알고 있는 사람이 많은데,
  그건 브라우저가 ``Access-Control-Allow-Origin: *`` 를 받았을 때의 이야기다.
  Starlette 은 이 조합에서 ``*`` 를 보내지 않고 origin 을 되돌려 준다.

★ 왜 "지금은 CORS 자체가 없다" 로 끝내지 않는가

  지금 이 서비스는 브라우저가 닿을 수 없다. 그래서 미들웨어를 지웠다.
  하지만 나중에 브라우저가 필요해지면 누군가 다시 넣을 것이고,
  가장 먼저 붙여 보는 값이 ``*`` 다 - 그게 제일 빨리 동작하기 때문이다.

  그 순간을 막는다. 이 시험은 CORS 를 못 쓰게 하는 것이 아니라
  **와일드카드와 자격증명을 같이 쓰는 것**만 막는다.
"""

from __future__ import annotations

import ast
import pathlib

AI_DIR = pathlib.Path(__file__).resolve().parent.parent


def _cors_calls(tree: ast.AST):
    """add_middleware(CORSMiddleware, ...) 호출을 찾는다."""
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        func = node.func
        if not (isinstance(func, ast.Attribute) and func.attr == "add_middleware"):
            continue
        if not node.args:
            continue
        first = node.args[0]
        name = first.id if isinstance(first, ast.Name) else getattr(first, "attr", None)
        if name == "CORSMiddleware":
            yield node


def _keyword(call: ast.Call, name: str):
    for kw in call.keywords:
        if kw.arg == name:
            return kw.value
    return None


def test_no_wildcard_origin_with_credentials():
    offenders = []

    for path in sorted(AI_DIR.glob("*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        for call in _cors_calls(tree):
            origins = _keyword(call, "allow_origins")
            creds = _keyword(call, "allow_credentials")

            wildcard = isinstance(origins, ast.List) and any(
                isinstance(e, ast.Constant) and e.value == "*" for e in origins.elts
            )
            credentialed = isinstance(creds, ast.Constant) and creds.value is True

            if wildcard and credentialed:
                offenders.append(f"{path.name}:{call.lineno}")

    assert not offenders, (
        "allow_origins=['*'] 와 allow_credentials=True 를 같이 쓰고 있다: "
        f"{offenders}\n"
        "Starlette 은 이 조합에서 요청이 보낸 origin 을 그대로 되돌려 준다.\n"
        "아무 사이트나 이 API 를 인증된 채로 부를 수 있다는 뜻이다.\n"
        "허용할 origin 을 명시하거나, 자격증명이 필요 없으면 "
        "allow_credentials 를 빼라."
    )


def test_the_rule_actually_matches():
    """안 잡히는 규칙은 없는 것과 같다."""
    bad = ast.parse(
        "app.add_middleware(CORSMiddleware, allow_origins=['*'], allow_credentials=True)"
    )
    calls = list(_cors_calls(bad))
    assert calls, "CORSMiddleware 호출 자체를 못 찾으면 이 시험은 늘 초록이다"

    good = ast.parse(
        "app.add_middleware(CORSMiddleware, "
        "allow_origins=['https://studywithtymee.com'], allow_credentials=True)"
    )
    origins = _keyword(list(_cors_calls(good))[0], "allow_origins")
    assert not any(
        isinstance(e, ast.Constant) and e.value == "*" for e in origins.elts
    ), "명시한 origin 까지 잡으면 고칠 방법이 없어진다"
