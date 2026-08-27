# EduMeet 강의 자막 MCP 서버

저장된 강의 자막을 MCP 클라이언트(Claude Code · Claude Desktop)에서 찾고 읽는다.

```
MCP 클라이언트
    │ stdio
이 서버 ──── HTTP + X-Internal-Token ────► Java ────► caption_segment
             (contracts/internal-api.json)
```

## 도구

| | |
|---|---|
| `list_caption_meetings` | 자막이 있는 회의를 최근 발화 순으로 |
| `search_transcript` | 회의 안에서 찾기 — 조각 단위 + 앞뒤 문맥 |
| `get_transcript` | final 자막 전문 |

셋으로 나눈 이유 — `get_transcript` 하나만 두면 모델이 자막 전체를 컨텍스트에 넣는다.
한 시간짜리 강의면 그것만으로 예산이 끝난다.

## 왜 DB 를 직접 읽지 않는가

`CaptionSegmentRepository.findTranscriptSegments` 의 정렬이 단순하지 않다 —
`sequence` 가 없는 조각을 뒤로 미루고 `sequence → spokenAt → id` 순으로 세운다.
그 JavaDoc 이 직접 경고한다. **"여기서 순서가 흔들리면 요약 결과도 흔들린다."**

직접 읽으면 그 규칙을 여기서 한 번 더 구현해야 하고, 두 구현은 반드시 갈라진다.
그래서 이미 있는 내부 API 를 경유한다. 결과적으로 계약 파일 하나를
**Java · 파이썬 · MCP 셋이 함께 읽는다.**

## ★ 영어로 찾아도 걸린다

자막은 hot path 에서 용어 사전을 지나며 정규화된 뒤 저장된다.

```
STT  "python 을 배웠습니다"   →   저장  "파이썬 을 배웠습니다"
```

그래서 `python` 으로 찾으면 **오류가 아니라 0건**이 나오고,
0건은 *"그 회의에서 파이썬 얘기를 안 했나 보다"* 로 잘못 읽힌다.

이 서버는 질의에도 같은 사전을 적용한다. 사전을 베끼지 않고
`ai/caption_normalizer.py` 를 그대로 import 한다 —
**MCP stdio 서버는 컨테이너가 아니라 저장소 체크아웃 위에서 돌기 때문에** 가능하다.
(`ai/` 는 빌드 컨텍스트가 `./ai` 라 `contracts/` 를 런타임에 못 읽는다.)

## 설치

```bash
cd mcp-server
uv venv --python 3.12 .venv
VIRTUAL_ENV=.venv uv pip install -r requirements.txt
```

## MCP 클라이언트 등록

`claude mcp add` 또는 설정 파일에 직접:

```json
{
  "mcpServers": {
    "edumeet-transcript": {
      "command": "/절대경로/edumeet/mcp-server/.venv/bin/python",
      "args": ["/절대경로/edumeet/mcp-server/server.py"],
      "env": {
        "EDUMEET_API_BASE_URL": "https://api.studywithtymee.com",
        "EDUMEET_INTERNAL_TOKEN": "<X-Internal-Token 값>"
      }
    }
  }
}
```

| 환경변수 | |
|---|---|
| `EDUMEET_API_BASE_URL` | **필수.** 스킴 포함, 끝 슬래시 없이 |
| `EDUMEET_INTERNAL_TOKEN` | **필수.** Java 의 `edumeet.internal.api-token` 과 같은 값 |
| `EDUMEET_CONTRACT_PATH` | 저장소 밖에서 실행할 때만 |

> **`/actuator` 처럼 이 경로도 공개되어 있지 않다.** `/api/v1/internal/**` 는
> `hasRole("INTERNAL")` 아래이고 토큰은 Ansible Vault 에 있다.
> 토큰을 MCP 클라이언트 설정에 넣는다는 것은 **그 기계를 신뢰한다는 뜻**이다.

## 시험

```bash
./.venv/bin/python -m pytest -q
```

세 갈래를 잰다.

| | |
|---|---|
| `test_contract.py` | 부르는 경로·헤더가 공유 계약과 같은가 (**계약의 세 번째 소비자**) |
| `test_transcript_search.py` | 무엇을 돌려주는가 — 순수 함수만 |
| `test_server_tools.py` | 도구가 **실제로 등록**되었는가, 실패가 문장으로 가는가 |

마지막 것이 있는 이유는 이 저장소가 같은 함정을 일곱 번 만났기 때문이다
([`docs/ops/07-declared-but-unused.md`](../docs/ops/07-declared-but-unused.md)) —
함수가 옳아도 `@server.tool()` 이 안 붙어 있으면 클라이언트에는 아무것도 안 보인다.

## 알아둘 것

**MCP 파이썬 SDK 2.0 에서 `FastMCP` 가 `MCPServer` 로 바뀌었다.**
1.x 예제를 그대로 쓰면 `import` 부터 죽는다. 그래서 `mcp>=2,<3` 으로 메이저를 못 박았다.
