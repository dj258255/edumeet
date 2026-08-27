"""장문을 자르는 규칙. (#135)

`summarize_text_auto` 안에 있던 9줄짜리 중첩 함수다.
471줄 안에 있을 때는 이 시험 여덟 개를 쓸 자리가 없었다.
"""
from transcript_chunker import chunk_text


def test_short_text_stays_one_chunk():
    assert chunk_text("한 줄", 100) == ["한 줄"]


def test_empty_text_makes_no_chunk():
    assert chunk_text("", 100) == []


def test_splits_before_exceeding_the_limit():
    text = "aaaa\nbbbb\ncccc\n"      # 줄마다 5자(개행 포함)
    assert chunk_text(text, 10) == ["aaaa\nbbbb\n", "cccc\n"]


def test_keeps_line_endings():
    """★ keepends 다. 개행을 버리면 이어 붙일 때 문장이 붙어 버린다."""
    assert "".join(chunk_text("a\nb\nc\n", 3)) == "a\nb\nc\n"


def test_nothing_is_lost_or_duplicated():
    text = "\n".join(f"{i}번째 문장입니다" for i in range(200))
    for limit in (10, 50, 500, 10_000):
        assert "".join(chunk_text(text, limit)) == text


def test_a_single_long_line_is_not_cut():
    """★ 한 줄이 상한보다 길어도 자르지 않는다.

    문장 중간에서 자르면 정제 프롬프트가 잘린 문장을 완성하려 들면서
    없는 말을 만든다. 조각이 조금 큰 쪽이 낫다.
    """
    long_line = "가" * 100
    assert chunk_text(long_line, 10) == [long_line]


def test_long_line_still_starts_a_new_chunk():
    result = chunk_text("짧다\n" + "가" * 100, 10)
    assert len(result) == 2
    assert result[1] == "가" * 100


def test_limit_boundary_is_exclusive():
    """정확히 상한이면 아직 안 자른다 - 넘을 때 자른다."""
    assert chunk_text("abcde\n", 6) == ["abcde\n"]
    assert len(chunk_text("abcde\nx", 6)) == 2
