"""산출물 경로가 저장소를 오염시키지 않는지 고정한다. (#134)

전에는 두 가지가 같이 잘못돼 있었다.

  1. MERGE_OUT_DIR 기본값이 저장소 루트의 `FastAPIProject/` 였다.
     PyCharm 이 지어 준 프로젝트 이름이 그대로 출력 경로가 됐다.
  2. `os.makedirs(MERGE_OUT_DIR, exist_ok=True)` 가 모듈 최상단에 있었다.
     그래서 **import 만 해도** 디렉터리가 생겼다. pytest 가 테스트를 수집하는
     것만으로도 저장소 루트에 빈 디렉터리가 만들어졌다.

두 번째가 더 나쁘다. import 에 부수효과가 있으면 "이 모듈을 읽기만 하는" 코드가
존재할 수 없다. 그래서 이 시험은 **별도 프로세스에서 import 만** 시켜 본다.
같은 프로세스에서 확인하면 다른 테스트가 이미 import 해 둔 뒤라 아무 의미가 없다.
"""
import os
import subprocess
import sys

AI_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def test_import_alone_creates_no_directory(tmp_path):
    """import 는 아무것도 만들지 않아야 한다."""
    out_dir = tmp_path / "output"

    env = dict(os.environ)
    env["MERGE_OUT_DIR"] = str(out_dir)
    env["AUDIO_BASE_DIR"] = str(tmp_path / "audio")
    env["PYTHONPATH"] = AI_DIR

    result = subprocess.run(
        [sys.executable, "-c", "import main"],
        cwd=AI_DIR, env=env, capture_output=True, text=True, timeout=120,
    )

    assert result.returncode == 0, result.stderr
    assert not out_dir.exists(), (
        f"import 만 했는데 {out_dir} 가 생겼다. "
        "모듈 최상단에 makedirs 가 돌아왔는지 확인한다"
    )


def test_default_output_dir_stays_inside_ai(monkeypatch):
    """기본 산출물 경로는 ai/ 안이어야 한다 - 저장소 루트로 나가면 안 된다.

    디스크에 `FastAPIProject/` 가 있는지로 확인하지 않는다. 그 디렉터리는
    이 저장소의 다른 브랜치를 체크아웃하고 뭔가를 돌리기만 해도 생긴다 -
    그러면 이 시험이 **이번 실행과 무관한 이유로** 빨개진다.

    확인할 것은 "지금 코드가 어디를 가리키는가" 하나다.
    import 부수효과는 위의 subprocess 시험이 따로 잡는다.
    """
    monkeypatch.delenv("MERGE_OUT_DIR", raising=False)
    import importlib

    import main
    importlib.reload(main)

    default = os.path.realpath(os.path.join(AI_DIR, "var", "output"))
    actual = os.path.realpath(main.MERGE_OUT_DIR)

    assert actual == default
    repo_root = os.path.realpath(os.path.dirname(AI_DIR))
    assert actual.startswith(os.path.realpath(AI_DIR)), (
        f"산출물 경로가 ai/ 밖이다: {actual} (저장소 루트={repo_root})"
    )
