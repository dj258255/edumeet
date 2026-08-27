"""PDF 에 쓸 한글 폰트를 찾는다. (#135)

폰트가 없으면 예외가 아니라 **글자가 네모로 나온다.** 그래서 발견이 늦다 -
`ai/Dockerfile` 이 `COPY fonts ./fonts` 를 왜 하는지도 여기에 달려 있다.

`summarize_text_auto` 안에 중첩 함수로 있던 것을 뗐다. 파일시스템만 만지므로
tmp_path 로 시험할 수 있다 - 471줄 안에 있을 때는 그럴 자리가 없었다.
"""
from __future__ import annotations

import os

HERE = os.path.dirname(os.path.abspath(__file__))

FONT_NAMES = [
    "NotoSansKR-Regular.ttf", "NotoSansKR-Regular.otf",
    "NotoSansKR-Medium.ttf", "NotoSansKR-SemiBold.ttf",
    "NotoSansKR-Bold.ttf", "NotoSansKR-Black.ttf",
    "NotoSansKR-Light.ttf", "NotoSansKR-ExtraLight.ttf",
    "NotoSansKR-ExtraBold.ttf", "NotoSansKR-Thin.ttf",
]


def default_search_dirs() -> list[str]:
    return [
        os.path.join(HERE, "fonts"),
        os.path.normpath(os.path.join(HERE, "..", "backend", "fonts")),
    ]


def find_kr_font_paths(search_dirs: list[str] | None = None) -> dict:
    """찾은 폰트와, 본문/굵게에 쓸 대표 두 개를 고른다.

    대표를 고르는 순서에 뜻이 있다 - **굵은 폰트가 없다고 실패하지 않는다.**
    Bold -> SemiBold -> ExtraBold -> (없으면) regular 로 내려간다.
    제목이 조금 덜 굵은 것보다 PDF 가 안 나오는 쪽이 나쁘다.
    """
    found: dict[str, str] = {}
    for directory in (search_dirs or default_search_dirs()):
        if not os.path.isdir(directory):
            continue
        lowered = {fn.lower(): os.path.join(directory, fn) for fn in os.listdir(directory)}
        for name in FONT_NAMES:
            for lower_name, path in lowered.items():
                if lower_name.endswith(name.lower()):
                    found[name.split(".")[0]] = path

    regular = (found.get("NotoSansKR-Regular")
               or next((p for k, p in found.items() if "Regular" in k), None)
               or next(iter(found.values()), None))
    bold = (found.get("NotoSansKR-Bold")
            or found.get("NotoSansKR-SemiBold")
            or found.get("NotoSansKR-ExtraBold")
            or regular)
    return {"regular": regular, "bold": bold, "all": found}
