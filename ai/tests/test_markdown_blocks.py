"""마크다운을 블록으로 읽는 규칙. (#135)

★ 이 시험이 이번 분리의 이유다.

전에는 파싱과 FPDF 그리기가 한 루프에 있었다. `in_code` · `code_is_math` ·
`callout` 세 상태를 그리는 코드가 직접 들고 있어서 **"이 줄이 무슨 블록인가" 를
물어볼 자리가 없었다.** PDF 를 만들어 열어 보는 것 말고는 확인할 방법이 없었다.
"""
from markdown_blocks import Block, callout_left_open, parse_markdown_blocks


def kinds(md):
    return [b.kind for b in parse_markdown_blocks(md)]


def test_empty_document_has_no_block():
    assert parse_markdown_blocks("") == []


def test_heading_levels():
    assert kinds("# 하나\n## 둘\n### 셋") == ["h1", "h2", "h3"]


def test_heading_text_drops_the_marker():
    blocks = parse_markdown_blocks("# 제목입니다")
    assert blocks[0].text == "제목입니다"


def test_four_hashes_is_a_paragraph_not_a_heading():
    """`#### ` 는 처리하지 않는다 - 원본 동작이다. 조용히 h3 로 만들지 않는다."""
    assert kinds("#### 넷") == ["para"]


def test_bullet_and_blank_and_paragraph():
    assert kinds("- 하나\n\n문단") == ["bullet", "blank", "para"]


def test_bullet_text_drops_the_marker():
    assert parse_markdown_blocks("  - 들여쓴 불릿")[0].text == "들여쓴 불릿"


def test_code_fence_collects_lines():
    blocks = parse_markdown_blocks("```\na = 1\nb = 2\n```")
    assert len(blocks) == 1
    assert blocks[0].kind == "code"
    assert blocks[0].lines == ("a = 1", "b = 2")
    assert blocks[0].is_math is False


def test_math_fence_is_marked():
    """```math 는 배경색이 다르다. 파싱 단계에서 구분해 둬야 그리는 쪽이 단순해진다."""
    assert parse_markdown_blocks("```math\nE = mc^2\n```")[0].is_math is True


def test_headings_inside_a_fence_are_code_not_headings():
    """★ 펜스 안에서는 어떤 마커도 해석하지 않는다."""
    blocks = parse_markdown_blocks("```\n# 이건 주석이다\n- 불릿 아님\n```")
    assert len(blocks) == 1
    assert blocks[0].lines == ("# 이건 주석이다", "- 불릿 아님")


def test_unclosed_fence_swallows_the_rest():
    """모델이 펜스를 안 닫는 일이 실제로 있다. 나머지를 문단으로 그리면 더 안 읽힌다."""
    blocks = parse_markdown_blocks("```\n코드\n계속 코드")
    assert len(blocks) == 1
    assert blocks[0].lines == ("코드", "계속 코드")


def test_summary_heading_opens_a_callout():
    blocks = parse_markdown_blocks("## 요약\n첫 줄")
    assert blocks[0].starts_callout is True
    assert blocks[1].in_callout is True


def test_summary_with_spaces_still_opens_a_callout():
    """`## 요 약` 도 연다 - 공백을 지우고 비교한다."""
    assert parse_markdown_blocks("## 요 약")[0].starts_callout is True


def test_english_summary_heading_also_opens():
    assert parse_markdown_blocks("## Summary")[0].starts_callout is True


def test_another_heading_closes_the_callout():
    blocks = parse_markdown_blocks("## 요약\n안\n## 핵심 개념\n밖")
    assert blocks[2].ends_callout is True
    assert blocks[1].in_callout is True
    assert blocks[3].in_callout is False


def test_heading_does_not_close_what_was_never_open():
    assert parse_markdown_blocks("## 핵심 개념")[0].ends_callout is False


def test_callout_left_open_at_the_end_is_reported():
    """★ 마지막 블록의 in_callout 을 보면 안 된다.

    그 값은 그 블록을 처리하기 **전** 상태라, 마지막 줄이 `## 요약` 이면
    열어 놓고도 False 로 보인다.
    """
    assert callout_left_open(parse_markdown_blocks("## 요약")) is True
    assert callout_left_open(parse_markdown_blocks("## 요약\n안\n## 다음")) is False
    assert callout_left_open(parse_markdown_blocks("문단만 있다")) is False


def test_paragraph_keeps_its_leading_spaces():
    """문단은 strip 하지 않는다 - 원본이 raw 줄을 그대로 그렸다."""
    assert parse_markdown_blocks("   들여쓴 문단")[0].text == "   들여쓴 문단"


def test_blocks_are_immutable():
    """블록은 값이다. 그리는 쪽이 상태를 되돌려 놓는 실수를 못 하게 한다."""
    import dataclasses
    import pytest
    block = parse_markdown_blocks("문단")[0]
    with pytest.raises(dataclasses.FrozenInstanceError):
        block.kind = "h1"


def test_full_document_shape():
    md = "\n".join([
        "# 강의 요약",
        "",
        "## 요약",
        "- 첫째",
        "- 둘째",
        "",
        "## 핵심 개념",
        "본문입니다",
        "```math",
        "a^2 + b^2 = c^2",
        "```",
    ])
    blocks = parse_markdown_blocks(md)
    assert kinds(md) == ["h1", "blank", "h2", "bullet", "bullet",
                         "blank", "h2", "para", "code"]
    assert [b.in_callout for b in blocks[3:6]] == [True, True, True]
    assert blocks[7].in_callout is False
    assert callout_left_open(blocks) is False
