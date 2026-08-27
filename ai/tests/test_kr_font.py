"""한글 폰트를 찾는 규칙. (#135)

폰트가 없으면 예외가 아니라 **글자가 네모로 나온다.** 그래서 발견이 늦고,
늦게 발견되는 것은 시험으로 고정해 둘 값어치가 있다.
"""
from kr_font import default_search_dirs, find_kr_font_paths


def _make(dirpath, *names):
    dirpath.mkdir(parents=True, exist_ok=True)
    for name in names:
        (dirpath / name).write_bytes(b"not-a-real-font")
    return str(dirpath)


def test_no_directory_means_no_font(tmp_path):
    result = find_kr_font_paths([str(tmp_path / "없는곳")])
    assert result["regular"] is None
    assert result["bold"] is None
    assert result["all"] == {}


def test_finds_regular_and_bold(tmp_path):
    d = _make(tmp_path / "fonts", "NotoSansKR-Regular.ttf", "NotoSansKR-Bold.ttf")
    result = find_kr_font_paths([d])
    assert result["regular"].endswith("NotoSansKR-Regular.ttf")
    assert result["bold"].endswith("NotoSansKR-Bold.ttf")


def test_bold_falls_back_through_semibold_then_regular(tmp_path):
    """★ 굵은 폰트가 없다고 실패하지 않는다.

    제목이 덜 굵은 것보다 PDF 가 아예 안 나오는 쪽이 나쁘다.
    """
    semi = _make(tmp_path / "a", "NotoSansKR-Regular.ttf", "NotoSansKR-SemiBold.ttf")
    assert find_kr_font_paths([semi])["bold"].endswith("NotoSansKR-SemiBold.ttf")

    only_regular = _make(tmp_path / "b", "NotoSansKR-Regular.ttf")
    result = find_kr_font_paths([only_regular])
    assert result["bold"] == result["regular"]


def test_uses_any_font_when_regular_is_missing(tmp_path):
    d = _make(tmp_path / "fonts", "NotoSansKR-Light.ttf")
    result = find_kr_font_paths([d])
    assert result["regular"].endswith("NotoSansKR-Light.ttf")


def test_matching_is_case_insensitive(tmp_path):
    d = _make(tmp_path / "fonts", "notosanskr-regular.TTF")
    assert find_kr_font_paths([d])["regular"] is not None


def test_otf_is_accepted_too(tmp_path):
    d = _make(tmp_path / "fonts", "NotoSansKR-Regular.otf")
    assert find_kr_font_paths([d])["regular"].endswith(".otf")


def test_unrelated_files_are_ignored(tmp_path):
    d = _make(tmp_path / "fonts", "Arial.ttf", "readme.txt")
    assert find_kr_font_paths([d])["all"] == {}


def test_earlier_directory_can_be_overridden_by_later_one(tmp_path):
    """뒤에 오는 디렉터리가 이긴다 - 원본 구현의 동작이다.

    ai/fonts 가 먼저고 backend/fonts 가 뒤다. 배포 이미지는 ai/fonts 만 갖고,
    로컬에는 둘 다 있을 수 있다.
    """
    first = _make(tmp_path / "first", "NotoSansKR-Regular.ttf")
    second = _make(tmp_path / "second", "NotoSansKR-Regular.ttf")
    assert find_kr_font_paths([first, second])["regular"].startswith(second)


def test_default_dirs_point_at_ai_then_backend():
    dirs = default_search_dirs()
    assert dirs[0].endswith("ai/fonts")
    assert dirs[1].endswith("backend/fonts")
