"""평가 질의 세트. (#140)

★ 라벨을 만드는 규칙

  용어형 15 — 강의에서 쓴 말 그대로 찾는다. 지금 구현이 잘하는 영역이다.
              이걸 안 넣으면 "새 방식이 좋다" 는 결론이 공짜로 나온다.

  질문형 15 — 사람이 실제로 묻는 방식. 강의에서 쓴 말과 다른 말이 섞인다.

★ 질의를 인위로 비틀지 않는다.

  처음에는 "질문형에는 자막에 나오는 단어가 하나도 없어야 한다" 는 규칙을 넣었다.
  그건 결론을 미리 정해 놓는 것이다 - 그렇게 만들면 어떤 렉시컬 방식도 질 수밖에 없고,
  임베딩이 이기는 것이 측정이 아니라 설계의 결과가 된다.

  대신 **겹치는 정도를 같이 보고한다.** 질의가 자막과 얼마나 겹치는지 드러내 놓고,
  그 조건에서 세 방식을 비교한다.

정답(relevant)은 "그 질문을 한 사람이 이 조각을 보면 납득하는가" 로 정했다.
주변 조각까지 넓게 잡지 않았다 - 넓히면 재현율이 부풀려진다.
"""
from __future__ import annotations

from dataclasses import dataclass

from corpus import SEGMENTS


@dataclass(frozen=True)
class Query:
    text: str
    relevant: tuple[int, ...]   # 1부터 세는 조각 번호
    kind: str                   # "lexical" | "question"


LEXICAL: list[Query] = [
    Query("비관적 잠금", (12,), "lexical"),
    Query("낙관적 잠금", (16,), "lexical"),
    Query("팬아웃", (36,), "lexical"),
    Query("하트비트", (35,), "lexical"),
    Query("Redis", (23,), "lexical"),
    Query("WebSocket", (27,), "lexical"),
    Query("Docker", (48,), "lexical"),
    Query("파이썬", (42, 43), "lexical"),
    Query("python", (42, 43), "lexical"),          # 정규화가 걸리는 경로
    Query("경합", (5,), "lexical"),
    Query("정원", (7,), "lexical"),
    Query("프록시", (30, 31), "lexical"),
    Query("대시보드", (53,), "lexical"),
    Query("인덱스", (2,), "lexical"),
    Query("수강 신청", (6,), "lexical"),
]

QUESTION: list[Query] = [
    Query("동시성 문제가 왜 생기나요",                    (3, 5, 9), "question"),
    Query("정원 초과로 더 들어가는 경우",                  (10,), "question"),
    Query("락을 걸어서 순서를 보장하는 방법",               (12, 13), "question"),
    Query("충돌이 적을 때 쓰는 잠금",                      (16,), "question"),
    Query("한 문장으로 갱신해서 경합 피하기",               (17, 18), "question"),
    Query("측정 환경 바뀌면 결과가 달라지나요",             (20, 21), "question"),
    Query("TTL 있는 값은 어디에 저장하나",                 (24, 25), "question"),
    Query("커넥션을 유지하는 프로토콜",                     (27, 28), "question"),
    Query("서버 푸시가 필요한 경우",                        (29,), "question"),
    Query("트래픽 없을 때 커넥션 끊김",                     (31, 32), "question"),
    Query("에러 로그 없이 사라지는 버그",                   (33,), "question"),
    Query("슬로우 컨슈머 때문에 지연되는 현상",             (39,), "question"),
    Query("힙이 차서 프로세스가 종료되는 원인",             (41,), "question"),
    Query("블로킹 연산이 이벤트 루프를 막는 문제",          (44, 45, 46), "question"),
    Query("임계값 근거를 어떻게 정하나",                    (56, 57), "question"),
]

ALL: list[Query] = LEXICAL + QUESTION


def tokens(text: str) -> list[str]:
    """공백으로 자르고 두 글자 미만은 버린다. 형태소 분석은 하지 않는다.

    형태소 분석기를 넣으면 모델이 하나 더 늘고, 그 모델의 품질이 측정에 섞인다.
    지금 재려는 것은 "말이 겹치는가" 이므로 이 정도로 충분하다.
    """
    return [t for t in text.replace("?", " ").split() if len(t) >= 2]


def lexical_overlap(query: Query) -> float:
    """질의 토큰 중 정답 조각에 그대로 나오는 비율.

    이 값이 높은 질문은 렉시컬 방식이 잘 찾는 것이 당연하다.
    낮은데도 렉시컬이 찾으면 우연이고, 높은데 임베딩만 찾으면 그건 이상하다.
    **결론을 읽을 때 이 값을 같이 봐야 한다.**
    """
    body = " ".join(SEGMENTS[n - 1] for n in query.relevant)
    ts = tokens(query.text)
    if not ts:
        return 0.0
    return sum(1 for t in ts if t in body) / len(ts)


def validate() -> None:
    """라벨이 형식적으로 성립하는지만 본다. 내용 판단은 사람이 한다."""
    for q in ALL:
        assert q.relevant, f"정답이 비어 있다: {q.text}"
        for no in q.relevant:
            assert 1 <= no <= len(SEGMENTS), f"조각 번호 범위 밖: {q.text} -> {no}"
        assert tokens(q.text), f"토큰이 없다: {q.text}"
