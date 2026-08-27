"""질문 형태 질의. (#140)

★ 이 파일이 생긴 이유.

  검색이 **질의 문자열 전체**를 부분문자열로 찾고 있었다.

      "비관적 잠금"                 1건
      "비관적 잠금 어디서 설명했지"   0건

  질문 형태는 무조건 0건이었다 - 의미 때문이 아니라 구현 때문이다.
  30개 질의로 재 보니 질문형 재현율이 0% 였다.

  그래서 렉시컬이 아무것도 못 찾을 때 2-gram 순위 단계로 넘어가게 했고,
  질문형 재현율이 54% 가 됐다. 모델 없이.
"""
import pytest

from transcript_search import search

TRANSCRIPT = "\n".join([
    "자 오늘은 백엔드 수업 세 번째 시간입니다",
    "여러 작업이 같은 자원을 동시에 건드리는 상황을 경합이라고 합니다",
    "첫째는 자물쇠를 거는 겁니다 비관적 잠금이라고 부릅니다",
    "읽을 때부터 다른 요청이 못 건드리게 막아 둡니다",
    "느린 사람이 한 명 있으면 그 사람 때문에 전체가 밀립니다",
    "그래서 대기열에 상한을 두어야 합니다",
    "Redis 를 세션 저장소로 쓰는 경우가 많습니다",
])


def nos(matches):
    return [m.segment_no for m in matches]


# ── 정확 일치는 그대로여야 한다 ────────────────────────────────────────

def test_exact_term_still_wins():
    """용어형에서 순위를 바꾸지 않는다.

    사람은 'Redis' 를 넣으면 정확 일치를 기대한다.
    비슷한 조각이 위에 오면 도구가 이상해 보인다.
    """
    assert nos(search(TRANSCRIPT, "Redis")) == [7]


def test_exact_hits_keep_segment_order():
    # 7번은 "많습니다" 라 "합니다" 를 포함하지 않는다
    assert nos(search(TRANSCRIPT, "합니다")) == [2, 6]


def test_exact_stage_does_not_fall_through():
    """정확 일치가 하나라도 있으면 순위 단계로 안 넘어간다."""
    matches = search(TRANSCRIPT, "비관적 잠금")
    assert nos(matches) == [3]
    assert matches[0].matched_term == "비관적 잠금"


# ── 질문 형태 ─────────────────────────────────────────────────────────

def test_question_used_to_return_nothing():
    """★ 이 시험이 이 변경의 이유다.

    질의 문자열 전체를 찾던 때는 여기서 0건이 나왔다.
    """
    matches = search(TRANSCRIPT, "비관적 잠금 어디서 설명했지")
    assert matches, "질문 형태가 0건이면 예전 구현으로 돌아간 것이다"
    assert 3 in nos(matches)


def test_question_with_different_wording():
    """강의에서 쓴 말과 다른 말로 물어도 걸린다."""
    assert 5 in nos(search(TRANSCRIPT, "느린 사람 때문에 밀리는 현상"))


def test_ranked_stage_orders_by_similarity():
    """가장 비슷한 것이 위에 온다 - 조각 순서가 아니다."""
    matches = search(TRANSCRIPT, "자물쇠 걸어서 못 건드리게 하는 방법")
    assert nos(matches)[0] in (3, 4)


def test_ranked_results_carry_context():
    match = search(TRANSCRIPT, "대기열 상한을 왜 두나", context=1)[0]
    assert match.context_before or match.context_after


def test_max_results_still_capped_in_ranked_stage():
    assert len(search(TRANSCRIPT, "수업 자원 요청 사람 저장소", max_results=2)) <= 2


def test_nonsense_query_returns_nothing():
    """아무 관련 없는 질의에 5건을 채워 주지 않는다.

    도구 응답은 그대로 모델 컨텍스트로 간다. 관련 없는 조각을 채우면
    "찾았다" 로 읽히고 모델이 그것으로 답한다.
    """
    assert search(TRANSCRIPT, "zzzz qqqq") == []


# ── 선택적 의미 검색 ──────────────────────────────────────────────────

def test_semantic_is_off_by_default(monkeypatch):
    """★ 설치돼 있어도 자동으로 켜지지 않는다.

    임베딩은 질의당 15ms 를 물린다. torch 가 있다는 이유만으로
    모든 사용자에게 그 비용을 씌우지 않는다.
    """
    import importlib

    import semantic
    monkeypatch.delenv("EDUMEET_SEMANTIC_SEARCH", raising=False)
    importlib.reload(semantic)
    ok, why = semantic.available()
    assert ok is False
    assert "EDUMEET_SEMANTIC_SEARCH" in why


def test_search_works_without_the_optional_dependency():
    """의미 검색이 꺼져 있어도 렉시컬 경로는 그대로 돈다."""
    assert nos(search(TRANSCRIPT, "Redis")) == [7]
    assert search(TRANSCRIPT, "느린 사람 때문에 밀리는 현상")


# ── 하이브리드 병합 ───────────────────────────────────────────────────
#
# torch 를 CI 에 넣지 않으려고 semantic_hits 를 가짜로 바꾼다.
# 재려는 것은 "모델이 좋은가" 가 아니라 "두 순위를 합치는 코드가 맞는가" 다.

def test_semantic_result_is_merged_not_used_as_last_resort(monkeypatch):
    """★ 처음에 계단식 폴백으로 짰다가 한 번도 안 도는 것을 측정에서 발견했다.

    2-gram 은 겹치는 조각이 하나만 있어도 결과를 내므로
    "렉시컬이 0건일 때만 의미 검색" 이라는 조건은 성립하지 않는다.
    """
    import transcript_search as ts

    # 렉시컬이 절대 안 고를 조각을 의미 검색이 1등으로 준다
    monkeypatch.setattr(ts, "semantic_hits",
                        lambda segments, query, limit: [(0, "(의미) x")])
    got = nos(ts.search(TRANSCRIPT, "느린 사람 때문에 밀리는 현상"))
    assert 1 in got, "의미 결과가 병합되지 않았다 - 폴백으로 되돌아갔는지 본다"


def test_lexical_is_not_discarded_when_semantic_is_on(monkeypatch):
    """용어형에서 렉시컬이 재현율 100% 다. 임베딩만 쓰면 잘하던 것을 잃는다."""
    import transcript_search as ts

    monkeypatch.setattr(ts, "semantic_hits",
                        lambda segments, query, limit: [(0, "(의미) x")])
    got = nos(ts.search(TRANSCRIPT, "자물쇠 걸어서 못 건드리게 하는 방법"))
    assert set(got) & {3, 4}, "렉시컬 결과가 사라졌다"


def test_exact_stage_never_calls_the_model(monkeypatch):
    """정확 일치가 있으면 모델을 안 부른다 - 용어 검색에 15ms 를 물릴 이유가 없다."""
    import transcript_search as ts

    called = []
    monkeypatch.setattr(ts, "semantic_hits",
                        lambda *a, **k: called.append(1) or [])
    ts.search(TRANSCRIPT, "Redis")
    assert called == [], "정확 일치인데 임베딩을 불렀다"
