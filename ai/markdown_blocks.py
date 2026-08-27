"""마크다운을 블록으로 읽는다 - 그리지는 않는다. (#135)

★ 왜 뗐나.

`markdown_to_pdf` 안에서 **"이 줄이 무슨 블록인가" 와 "그것을 어떻게 그리나" 가
한 루프에 섞여** 있었다. `in_code` · `code_is_math` · `callout` 세 상태를
FPDF 를 부르는 코드가 직접 들고 있어서, 파싱만 물어보는 시험을 쓸 자리가 없었다.

여기에는 FPDF 가 없다. 그래서 "이 줄은 h2 이고 콜아웃을 연다" 를 그냥 물어볼 수 있다.

★ 콜아웃은 왜 있나.

`## 요약` 섹션만 배경색 박스로 그린다. 그래서 h2 가 상태를 바꾼다 -
`요약`/`Summary` 면 열고, 다른 h2 면 닫는다. 문서 끝까지 열려 있으면 끝에서 닫는다.
"""
from __future__ import annotations

from dataclasses import dataclass, field

CALLOUT_TITLES = ("요약", "Summary")


@dataclass(frozen=True)
class Block:
    """한 덩어리.

    :param kind: h1 · h2 · h3 · bullet · code · blank · para
    :param lines: `code` 전용. 펜스 안의 줄들
    :param is_math: ```math 펜스인가. 배경색이 달라진다
    :param in_callout: 이 블록이 콜아웃 박스 안인가
    :param starts_callout: 이 h2 가 콜아웃을 여는가
    :param ends_callout: 이 h2 가 콜아웃을 닫는가
    """
    kind: str
    text: str = ""
    lines: tuple[str, ...] = field(default=())
    is_math: bool = False
    in_callout: bool = False
    starts_callout: bool = False
    ends_callout: bool = False


def _is_fence(line: str) -> bool:
    return line.strip().startswith("```")


def parse_markdown_blocks(md_text: str) -> list[Block]:
    """줄들을 블록으로 바꾼다.

    판정 순서를 원본 그대로 둔다 - 펜스 → 헤딩 → 불릿 → 빈 줄 → 문단.
    순서를 바꾸면 `- ` 로 시작하는 헤딩 같은 경계 사례에서 결과가 달라진다.
    """
    blocks: list[Block] = []
    lines = md_text.splitlines()
    callout = False
    i = 0

    while i < len(lines):
        line = lines[i].rstrip("\n")

        if _is_fence(line):
            tag = line.strip()[3:].strip().lower()
            body: list[str] = []
            i += 1
            # 닫는 펜스가 없으면 끝까지 코드로 본다. 원본도 그랬다 -
            # 모델이 펜스를 안 닫는 일이 실제로 있고, 그때 나머지를 문단으로
            # 그리면 코드가 본문 폭으로 흘러 더 읽기 어렵다.
            while i < len(lines) and not _is_fence(lines[i]):
                body.append(lines[i].rstrip("\n"))
                i += 1
            i += 1
            blocks.append(Block("code", lines=tuple(body),
                                is_math=(tag == "math"), in_callout=callout))
            continue

        if line.startswith("### "):
            blocks.append(Block("h3", text=line[4:].strip(), in_callout=callout))
        elif line.startswith("## "):
            title = line[3:].strip()
            starts = title.replace(" ", "") in CALLOUT_TITLES
            ends = (not starts) and callout
            blocks.append(Block("h2", text=title, in_callout=callout,
                                starts_callout=starts, ends_callout=ends))
            if starts:
                callout = True
            elif ends:
                callout = False
        elif line.startswith("# "):
            blocks.append(Block("h1", text=line[2:].strip(), in_callout=callout))
        elif line.strip().startswith("- "):
            blocks.append(Block("bullet", text=line.strip()[2:], in_callout=callout))
        elif not line.strip():
            blocks.append(Block("blank", in_callout=callout))
        else:
            blocks.append(Block("para", text=line, in_callout=callout))
        i += 1

    return blocks


def callout_left_open(blocks: list[Block]) -> bool:
    """문서가 끝날 때까지 콜아웃이 열려 있는가 - 그리는 쪽이 끝에서 닫아야 한다.

    마지막 블록의 `in_callout` 을 보면 안 된다. 그 값은 **그 블록을 처리하기 전**
    상태라, 마지막 h2 가 `## 요약` 이면 열어 놓고도 False 로 보인다.
    """
    state = False
    for block in blocks:
        if block.kind != "h2":
            continue
        if block.starts_callout:
            state = True
        elif block.ends_callout:
            state = False
    return state
