"""검색이 무엇을 돌려주는가. (#133)

여기 있는 것은 전부 순수 함수 시험이다 - 네트워크도 MCP 도 없다.
도구의 값어치는 "무엇을 돌려주는가" 에서 갈리므로, 그 판단만 따로 떼어 잰다.
"""
import pytest

from glossary import GLOSSARY_AVAILABLE, normalize_query
from transcript_search import MAX_RESULTS_CEILING, render, search, search_terms

TRANSCRIPT = "\n".join([
    "오늘은 백엔드 이야기를 합니다",
    "파이썬 으로 STT 를 붙였습니다",
    "그리고 스프링 부트 로 자막을 뿌립니다",
    "Redis 는 refresh token 저장소로만 씁니다",
    "다음 시간에는 파이썬 비동기를 봅니다",
])


def test_empty_query_finds_nothing():
    assert search(TRANSCRIPT, "") == []
    assert search(TRANSCRIPT, "   ") == []


def test_empty_transcript_finds_nothing():
    assert search("", "파이썬") == []


def test_finds_every_segment_that_contains_the_term():
    matches = search(TRANSCRIPT, "파이썬")
    assert [m.segment_no for m in matches] == [2, 5]
    assert matches[0].text == "파이썬 으로 STT 를 붙였습니다"


def test_segment_number_is_one_based():
    """사람은 '두 번째 발화' 라고 말하지 '인덱스 1' 이라고 말하지 않는다."""
    assert search(TRANSCRIPT, "오늘은")[0].segment_no == 1


def test_context_is_neighbouring_segments_not_characters():
    """transcript 의 한 줄이 자막 조각 하나다. 문맥을 글자 수로 자를 이유가 없다."""
    match = search(TRANSCRIPT, "스프링", context=1)[0]
    assert match.context_before == ["파이썬 으로 STT 를 붙였습니다"]
    assert match.context_after == ["Redis 는 refresh token 저장소로만 씁니다"]


def test_context_zero_gives_only_the_segment():
    match = search(TRANSCRIPT, "스프링", context=0)[0]
    assert match.context_before == []
    assert match.context_after == []


def test_context_does_not_run_past_the_edges():
    first = search(TRANSCRIPT, "오늘은", context=3)[0]
    last = search(TRANSCRIPT, "다음 시간", context=3)[0]
    assert first.context_before == []
    assert last.context_after == []


def test_search_is_case_insensitive():
    assert search(TRANSCRIPT, "redis")
    assert search(TRANSCRIPT, "REDIS")


def test_max_results_is_capped():
    """결과가 그대로 모델 컨텍스트에 들어간다. 자막 전체를 돌려줄 것이면 검색일 이유가 없다."""
    long_transcript = "\n".join(["같은 말"] * 500)
    assert len(search(long_transcript, "같은 말", max_results=3)) == 3
    assert len(search(long_transcript, "같은 말", max_results=10_000)) == MAX_RESULTS_CEILING


@pytest.mark.skipif(not GLOSSARY_AVAILABLE, reason="ai/caption_normalizer.py 를 못 찾음")
def test_english_query_finds_the_normalized_korean_text():
    """★ 이 시험이 이 파일의 이유다.

    자막은 hot path 에서 정규화된 뒤 저장된다 - `python` 은 `파이썬` 으로 바뀐다.
    그래서 `python` 으로 찾으면 **오류가 아니라 0건**이 나온다.
    0건은 "그 얘기를 안 했다" 로 잘못 읽힌다. 조용히 틀리는 쪽이 더 나쁘다.
    """
    assert normalize_query("python") == "파이썬"
    matches = search(TRANSCRIPT, "python")
    assert [m.segment_no for m in matches] == [2, 5]
    assert matches[0].matched_term == "파이썬"


@pytest.mark.skipif(not GLOSSARY_AVAILABLE, reason="ai/caption_normalizer.py 를 못 찾음")
def test_query_terms_keep_both_forms():
    """원문만 쓰면 한국어를 놓치고, 정규화형만 쓰면 사전이 못 바꾼 영어를 놓친다."""
    assert search_terms("python") == ["python", "파이썬"]
    assert search_terms("파이썬") == ["파이썬"]


def test_render_says_what_it_searched_for_even_when_empty():
    """★ 빈 결과에 '없음' 만 주지 않는다.

    용어 사전이 질의를 바꿨는데 그 사실을 안 알려 주면
    0건이 "그런 말을 안 했다" 로 읽힌다.
    """
    text = render([], "쿠버네티스", total_segments=5)
    assert "쿠버네티스" in text
    assert "찾은 말" in text
    assert "걸린 조각이 없다" in text


def test_render_marks_the_matched_segment_apart_from_context():
    text = render(search(TRANSCRIPT, "스프링", context=1), "스프링", total_segments=5)
    assert "#3  그리고 스프링 부트 로 자막을 뿌립니다" in text
    assert "      파이썬 으로 STT 를 붙였습니다" in text
