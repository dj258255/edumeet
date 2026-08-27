"""블록을 PDF 로 그린다 - 마크다운은 여기서 읽지 않는다. (#135)

파싱은 `markdown_blocks.py` 가 한다. 여기 있는 것은 FPDF 호출뿐이다.

★ 폰트가 없을 때 무슨 일이 나는가 - 원본 주석이 틀렸다. (#135)

원본 코드에는 "폰트가 없으면 글자가 네모로 나온다" 는 전제가 깔려 있었다.
**fpdf2 2.7 이후로는 그렇지 않다.**

    FPDFUnicodeEncodingException: Character "강" ... is outside the range of
    characters supported by the font used: "helvetica"

예외가 난다. 그래서 되돌림 경로가 성립하지 않았다 -
`markdown_to_pdf` 가 죽어서 `plain_pdf` 로 내려가는데, 한글이면 **거기서도 똑같이
죽는다.** 존재 이유인 바로 그 조건에서 못 도는 되돌림이었다.

그리고 그 예외는 `summarize_text_auto` 의 바깥 except 까지 올라가서
**요약 전체를 실패시켰다.** `summary.md` 는 이미 만들어져 있는데도 그렇다.

지금은 세 값을 돌려준다 - `pretty` / `plain` / `failed`.
PDF 를 못 만들어도 마크다운은 나간다. `send_summary_to_api` 는 이미
`pdf_path` 가 없어도 md 만 올린다.
"""
from __future__ import annotations

import os
import textwrap

from fpdf import FPDF

from kr_font import find_kr_font_paths
from markdown_blocks import callout_left_open, parse_markdown_blocks

ACCENT = (34, 197, 94)     # 브랜드 그린
CODE_BG = (245, 246, 248)
MATH_BG = (240, 245, 255)
CALL_BG = (248, 250, 246)

LINE_H = 6.0
PARA_GAP = 1.5
BULLET_INDENT = 5.5


def _document_title(md_text: str) -> str:
    """헤더에 반복해 찍을 제목. 첫 번째 H1 이다."""
    for line in md_text.splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return ""


def _pdf_class(regular_path: str | None):
    class PrettyPDF(FPDF):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, **kwargs)
            self.doc_title = ""
            self.family_base = "NotoKR" if regular_path else "Helvetica"
            self.family_mono = "NotoKR-Mono" if regular_path else "Courier"

        def header(self):
            if not self.doc_title:
                return
            self.set_y(12)
            self.set_font(self.family_base, "B", 15)
            self.set_text_color(25, 25, 25)
            self.cell(0, 8, self.doc_title, ln=1)
            self.set_draw_color(200, 200, 200)
            self.set_line_width(0.4)
            self.line(self.l_margin, self.get_y() + 1, self.w - self.r_margin, self.get_y() + 1)
            self.ln(5)

        def footer(self):
            self.set_y(-15)
            self.set_font(self.family_base, "", 10)
            self.set_text_color(120, 120, 120)
            self.cell(0, 8, f"{self.page_no()}", align="C")

    return PrettyPDF


def markdown_to_pdf(md_text: str, pdf_path: str, fonts: dict | None = None) -> None:
    fonts = fonts or find_kr_font_paths()
    regular_path = fonts.get("regular")
    bold_path = fonts.get("bold")

    pdf = _pdf_class(regular_path)(format="A4", unit="mm")
    pdf.set_left_margin(18)
    pdf.set_right_margin(18)
    pdf.set_auto_page_break(auto=True, margin=18)
    pdf.add_page()

    if regular_path:
        # uni=True 는 fpdf2 2.5.1 부터 무시되는 인자다. 지금은 무해하지만
        # "다음 릴리스에서 제거" 라고 예고돼 있고 requirements 가 <3 이라
        # 2.x 안에서 사라질 수 있다. 그때 TypeError 로 죽는다.
        pdf.add_font("NotoKR", "", regular_path)
        pdf.add_font("NotoKR", "B", bold_path or regular_path)
        # 코드 블록도 같은 폰트를 쓴다. 등폭 폰트로 바꾸면 한글이 못 그려진다.
        pdf.add_font("NotoKR-Mono", "", regular_path)

    base_family = pdf.family_base
    mono_family = pdf.family_mono
    usable_w = pdf.w - pdf.l_margin - pdf.r_margin
    pdf.doc_title = _document_title(md_text)

    pdf.set_font(base_family, "", 12)
    pdf.set_text_color(20, 20, 20)

    def hr(gap=2):
        pdf.set_draw_color(230, 230, 230)
        pdf.set_line_width(0.4)
        pdf.line(pdf.l_margin, pdf.get_y(), pdf.w - pdf.r_margin, pdf.get_y())
        pdf.ln(gap)

    def para(text, h=LINE_H, fill=False):
        pdf.set_x(pdf.l_margin)
        pdf.multi_cell(usable_w, h, text, fill=fill)
        pdf.ln(PARA_GAP)

    def bullet(text, fill=False):
        pdf.set_x(pdf.l_margin)
        pdf.cell(BULLET_INDENT, LINE_H, "•", fill=fill)
        pdf.set_x(pdf.l_margin + BULLET_INDENT)
        pdf.multi_cell(usable_w - BULLET_INDENT, LINE_H, text, fill=fill)

    blocks = parse_markdown_blocks(md_text)

    for block in blocks:
        if block.kind == "code":
            pdf.ln(0.5)
            pdf.set_fill_color(*(MATH_BG if block.is_math else CODE_BG))
            pdf.set_draw_color(220, 220, 220)
            pdf.set_line_width(0.2)
            pdf.set_font(mono_family, "", 10)
            pdf.set_text_color(40, 40, 0 if block.is_math else 40)
            for line in block.lines:
                pdf.set_x(pdf.l_margin + 2)
                pdf.multi_cell(usable_w - 4, 5, line, fill=True)
            pdf.set_text_color(20, 20, 20)
            pdf.set_font(base_family, "", 12)
            pdf.ln(1.0)

        elif block.kind == "h3":
            pdf.set_font(base_family, "B", 13)
            para(block.text)
            pdf.set_font(base_family, "", 12)

        elif block.kind == "h2":
            pdf.set_font(base_family, "B", 16)
            pdf.set_text_color(*ACCENT)
            para(block.text)
            pdf.set_text_color(20, 20, 20)
            hr(gap=2)
            if block.starts_callout:
                pdf.ln(0.5)
                pdf.set_fill_color(*CALL_BG)
            elif block.ends_callout:
                pdf.ln(1.0)
            pdf.set_font(base_family, "", 12)

        elif block.kind == "h1":
            pdf.set_font(base_family, "B", 20)
            para(block.text)
            hr(gap=3)
            pdf.set_font(base_family, "", 12)

        elif block.kind == "bullet":
            if block.in_callout:
                pdf.set_fill_color(*CALL_BG)
                bullet(block.text, fill=True)
            else:
                bullet(block.text)

        elif block.kind == "blank":
            pdf.ln(2 if block.in_callout else 1)

        else:  # para
            if block.in_callout:
                pdf.set_fill_color(*CALL_BG)
                para(block.text, fill=True)
            else:
                para(block.text)

    if callout_left_open(blocks):
        pdf.ln(1.0)

    pdf.output(pdf_path)


def plain_pdf(md_text: str, pdf_path: str, fonts: dict | None = None) -> None:
    """서식 없이 글자만 넣는 되돌림 경로.

    꾸미다 실패해도 **요약 자체는 나가야 한다.** 요약 본문은 이미 만들어졌고,
    PDF 는 그것을 담는 그릇일 뿐이다. 그릇 때문에 내용을 버리지 않는다.
    """
    fonts = fonts or find_kr_font_paths()
    pdf = FPDF(format="A4", unit="mm")
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.add_page()

    regular_path = fonts.get("regular")
    if regular_path and os.path.exists(regular_path):
        pdf.add_font("NotoKR", "", regular_path)
        pdf.set_font("NotoKR", size=12)
    else:
        pdf.set_font("Helvetica", size=12)

    for line in md_text.splitlines():
        wrapped = textwrap.wrap(line, width=100, break_long_words=True,
                                break_on_hyphens=False) or [""]
        for segment in wrapped:
            # ★ x 를 매번 왼쪽으로 되돌린다. (#135)
            #
            #   `multi_cell(0, ...)` 의 폭은 "지금 x 부터 오른쪽 여백까지" 다.
            #   fpdf2 의 multi_cell 은 끝나고 x 를 셀 오른쪽에 두므로,
            #   되돌리지 않으면 두 번째 줄부터 폭이 0 에 가까워진다.
            #
            #       fpdf.errors.FPDFException:
            #           Not enough horizontal space to render a single character
            #
            #   즉 이 되돌림 경로는 **한 줄짜리 문서에서만** 돌고 있었다.
            #   되돌림은 잘 안 도는 길이라 그 사실을 아무도 못 봤다.
            pdf.set_x(pdf.l_margin)
            pdf.multi_cell(0, 6, segment)
    pdf.output(pdf_path)


def has_unicode_font(fonts: dict) -> bool:
    """한글을 그릴 수 있는 폰트가 있는가.

    없으면 한글 PDF 는 **만들 수 없다.** 네모로라도 나오는 게 아니라 예외가 난다.
    """
    return bool(fonts.get("regular"))


def write_pdf(md_text: str, pdf_path: str, fonts: dict | None = None) -> str:
    """꾸민 PDF -> 글자만 -> 포기. 어디까지 갔는지 돌려준다.

    포기해도 예외를 올리지 않는다. **요약 본문은 이미 만들어졌고**, PDF 는 그것을
    담는 그릇일 뿐이다. 그릇 때문에 내용을 버리면 요약을 처음부터 다시 해야 하고,
    그건 토큰을 다시 쓰는 일이다.

    :return: ``pretty`` · ``plain`` · ``failed``
    """
    fonts = fonts or find_kr_font_paths()
    if not has_unicode_font(fonts):
        print("[PDF] 한글 폰트를 못 찾았다. ai/fonts 를 확인한다 - "
              "한글이 있으면 PDF 생성은 실패한다")

    try:
        markdown_to_pdf(md_text, pdf_path, fonts=fonts)
        return "pretty"
    except Exception as err:
        print(f"[PDF] 서식 있는 PDF 실패, 글자만 넣어 본다: {err}")

    try:
        plain_pdf(md_text, pdf_path, fonts=fonts)
        return "plain"
    except Exception as err:
        print(f"[PDF] 글자만 넣기도 실패했다. 마크다운만 낸다: {err}")
        return "failed"
