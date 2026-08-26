"""PDF 를 실제로 만들어 본다. (#135)

여기는 순수 함수가 아니라 스모크다. 파싱은 `test_markdown_blocks.py` 가 이미
따로 잰다 - 그래서 여기서는 **그리다가 죽지 않는가**와 **되돌림이 어디까지 가는가**만 본다.

★ 이 시험을 쓰다가 결함을 하나 찾았다.

원본에는 "폰트가 없으면 글자가 네모로 나온다" 는 전제가 깔려 있었다. 아니었다 -
fpdf2 2.7 이후로는 FPDFUnicodeEncodingException 이 난다. 그래서 되돌림 경로가
성립하지 않았다. markdown_to_pdf 가 죽어 plain_pdf 로 내려가는데 한글이면
**거기서도 똑같이 죽고**, 그 예외가 summarize_text_auto 바깥까지 올라가
**summary.md 를 이미 만들어 놓고도 요약 전체가 실패**했다.

471줄 안에 있을 때는 이 조건을 만들 자리가 없었다.
"""
import pytest

import summary_pdf
from kr_font import find_kr_font_paths
from summary_pdf import has_unicode_font, markdown_to_pdf, plain_pdf, write_pdf

MD = "\n".join([
    "# 강의 요약",
    "",
    "## 요약",
    "- 첫째 항목",
    "- 둘째 항목",
    "",
    "## 핵심 개념",
    "본문 문단입니다",
    "```math",
    "a^2 + b^2 = c^2",
    "```",
    "### 작은 제목",
    "```",
    "print('hello')",
    "```",
])

NO_FONTS = {"regular": None, "bold": None, "all": {}}
ASCII_MD = "# Title\n\n- one\n- two\n\nplain paragraph"


@pytest.fixture
def kr_fonts():
    """저장소에 들어 있는 진짜 폰트. 없으면 이 시험들은 의미가 없다."""
    fonts = find_kr_font_paths()
    if not fonts.get("regular"):
        pytest.skip("ai/fonts 에 한글 폰트가 없다")
    return fonts


def _pdf_bytes(path):
    assert path.exists(), "PDF 가 안 만들어졌다"
    data = path.read_bytes()
    assert data.startswith(b"%PDF"), "PDF 헤더가 아니다"
    return data


def test_renders_a_full_document(kr_fonts, tmp_path):
    out = tmp_path / "s.pdf"
    markdown_to_pdf(MD, str(out), fonts=kr_fonts)
    assert len(_pdf_bytes(out)) > 500


def test_renders_an_empty_document(kr_fonts, tmp_path):
    out = tmp_path / "empty.pdf"
    markdown_to_pdf("", str(out), fonts=kr_fonts)
    _pdf_bytes(out)


def test_renders_a_document_that_leaves_a_callout_open(kr_fonts, tmp_path):
    """`## 요약` 으로 끝나는 문서. 닫는 처리를 안 하면 여기서 드러난다."""
    out = tmp_path / "open.pdf"
    markdown_to_pdf("# 제목\n## 요약\n- 하나", str(out), fonts=kr_fonts)
    _pdf_bytes(out)


def test_renders_an_unclosed_code_fence(kr_fonts, tmp_path):
    out = tmp_path / "fence.pdf"
    markdown_to_pdf("```\n닫히지 않은 코드", str(out), fonts=kr_fonts)
    _pdf_bytes(out)


def test_plain_pdf_wraps_very_long_lines(kr_fonts, tmp_path):
    out = tmp_path / "plain.pdf"
    plain_pdf("가" * 5000, str(out), fonts=kr_fonts)
    _pdf_bytes(out)


def test_even_ascii_fails_without_a_unicode_font_because_of_the_bullet(tmp_path):
    """★ 영문 문서도 안 된다 - 불릿 기호 자체가 latin-1 밖이다.

    본문이 전부 ASCII 여도 `- ` 를 그리는 순간 `•` 를 찍는다. 그래서
    "영문이면 폰트 없이도 된다" 는 위안이 성립하지 않는다.
    폰트가 없으면 이 서비스의 PDF 는 사실상 만들 수 없다.
    """
    with pytest.raises(Exception) as caught:
        markdown_to_pdf(ASCII_MD, str(tmp_path / "ascii.pdf"), fonts=NO_FONTS)
    assert "•" in str(caught.value)


def test_ascii_without_bullets_renders_without_a_unicode_font(tmp_path):
    """불릿이 없으면 영문 문서는 나온다 - 경계를 정확히 적어 둔다."""
    out = tmp_path / "ascii-plain.pdf"
    markdown_to_pdf("# Title\n\nplain paragraph", str(out), fonts=NO_FONTS)
    _pdf_bytes(out)


def test_korean_without_a_unicode_font_raises(tmp_path):
    """★ 원본이 틀렸던 지점. 네모로 나오는 게 아니라 예외가 난다."""
    with pytest.raises(Exception) as caught:
        markdown_to_pdf("# 강의 요약", str(tmp_path / "x.pdf"), fonts=NO_FONTS)
    assert "helvetica" in str(caught.value).lower()


def test_has_unicode_font():
    assert has_unicode_font(NO_FONTS) is False
    assert has_unicode_font({"regular": "/some/font.ttf"}) is True


def test_write_pdf_uses_the_pretty_path_when_it_works(kr_fonts, tmp_path):
    out = tmp_path / "w.pdf"
    assert write_pdf(MD, str(out), fonts=kr_fonts) == "pretty"
    _pdf_bytes(out)


def test_write_pdf_falls_back_to_plain_when_rendering_dies(kr_fonts, tmp_path, monkeypatch):
    """꾸미다 실패해도 글자는 넣는다."""
    def boom(*args, **kwargs):
        raise RuntimeError("multi_cell 폭 계산 실패")

    monkeypatch.setattr(summary_pdf, "markdown_to_pdf", boom)
    out = tmp_path / "fallback.pdf"
    assert write_pdf(MD, str(out), fonts=kr_fonts) == "plain"
    _pdf_bytes(out)


def test_write_pdf_gives_up_instead_of_raising(tmp_path):
    """★ 한글 + 폰트 없음 = 두 경로가 다 죽는다. 그래도 예외를 올리지 않는다.

    요약 본문은 이미 만들어졌다. 그릇 때문에 내용을 버리면
    정제와 요약을 처음부터 다시 해야 하고, 그건 토큰을 다시 쓰는 일이다.
    """
    assert write_pdf("# 강의 요약", str(tmp_path / "none.pdf"), fonts=NO_FONTS) == "failed"


def test_document_title_is_the_first_h1():
    assert summary_pdf._document_title("잡담\n# 진짜 제목\n# 두 번째") == "진짜 제목"
    assert summary_pdf._document_title("h1 이 없다") == ""
