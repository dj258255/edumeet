# 23. 존재하지 않는 사용자를 위한 위험한 기본값 (#179)

파이썬 AI 서비스에 이 설정이 있었다.

```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_credentials=True,
    allow_methods=["*"], allow_headers=["*"],
)
```

---

## 이 조합이 왜 위험한가

`*` 를 쓰면 자격증명이 안 실린다고 알고 있는 사람이 많다.
그건 브라우저가 `Access-Control-Allow-Origin: *` 를 **받았을 때**의 이야기다.

Starlette 은 이 조합에서 `*` 를 보내지 않는다.

```python
# starlette/middleware/cors.py
if self.allow_all_origins and has_cookie:
    self.allow_explicit_origin(headers, origin)
```

쿠키가 붙은 요청에 대해 **요청이 보낸 origin 을 그대로 되돌려 준다.**
브라우저 입장에서는 "이 사이트는 허용됨" 이 된다 —
즉 **아무 사이트나 이 API 를 인증된 채로 부를 수 있다.**

---

## 고치지 않고 지웠다

고치려면 허용할 origin 을 적으면 된다. 그런데 적을 origin 이 없었다.

| | |
|---|---|
| 프론트 코드 | 이 서비스를 부르는 곳이 **없다** |
| nginx | 이 서비스로 보내는 `location` 이 **없다** |
| compose | `expose` 만 한다. 호스트 포트를 **안 연다** |

부르는 것은 백엔드뿐이고, **서버 간 호출에는 CORS 가 관여하지 않는다.**
브라우저가 보내는 `Origin` 헤더를 보고 브라우저에게 답하는 규칙이기 때문이다.

이 설정은 **존재하지 않는 사용자를 위한 것**이었다.
존재하지 않는 사용자를 위해 위험한 기본값을 들고 있을 이유가 없다.

[`07-declared-but-unused.md`](07-declared-but-unused.md) 의 뒤집힌 모양이다 —
지금까지는 "선언은 있는데 안 쓴다" 였는데, 이건 **"쓰는 사람이 없는데 문은 열려 있다"** 다.

---

## 지우고 나서 문을 잠갔다

지우는 것만으로는 끝이 아니다. 나중에 브라우저가 정말 필요해지면 누군가 다시 넣을 것이고,
**가장 먼저 붙여 보는 값이 `*` 다** — 그게 제일 빨리 동작하기 때문이다.

`tests/test_no_wildcard_cors.py` 가 그 순간을 막는다.
CORS 를 못 쓰게 하는 것이 아니라 **와일드카드와 자격증명을 같이 쓰는 것**만 막는다.

```
allow_origins=['*'] + allow_credentials=True     -> 빨강
allow_origins=['https://...'] + credentials      -> 초록
```

규칙을 넣고 **위반을 심어 실제로 빨개지는지 먼저 확인했다.**
안 잡히는 규칙은 없는 것보다 나쁘다 — 있다고 믿게 만들기 때문이다.
