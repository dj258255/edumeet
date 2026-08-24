"""동시 요청이 직렬화되는지 잰다. (#91)

무엇을 재나
    /STT/{class_id} 는 async def 인데 그 안에서 동기 I/O 를 부른다.
    FastAPI 는 async def 핸들러를 이벤트 루프에서 직접 돌리므로,
    안에서 블로킹하면 그동안 이 워커는 다른 요청을 하나도 못 받는다.

    실제로는 requests.post(timeout=600) 이라 최대 10분이다.
    여기서는 그 자리에 0.5초 sleep 을 놓고 두 요청을 동시에 보낸다.

읽는 법
    직렬화되면  총 시간 ~= 2 x 0.5s = 1.0s
    병렬이면    총 시간 ~= 0.5s

    이건 "느리다" 를 재는 게 아니다. **동시성이 있느냐 없느냐**를 잰다.
"""
import asyncio
import os
import sys
import time

import httpx
import pytest

AI_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, AI_DIR)

BLOCK_SECONDS = 0.5


@pytest.fixture
def app_with_blocking_stt(monkeypatch, tmp_path):
    """STT 자리에 동기 sleep 을 놓는다. 실제 코드에서는 requests.post 다."""
    import main

    audio_dir = tmp_path / "audio" / "1"
    audio_dir.mkdir(parents=True)
    # 최소한의 유효한 WAV 하나
    import wave
    with wave.open(str(audio_dir / "audio_1.wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(8000)
        w.writeframes(b"\x00\x00" * 800)

    monkeypatch.setattr(main, "BASE_AUDIO_DIR", str(tmp_path / "audio"))
    monkeypatch.setattr(main, "MERGE_OUT_DIR", str(tmp_path / "merged"))

    def blocking_stt(out_path, class_id):
        time.sleep(BLOCK_SECONDS)          # 동기 블로킹. requests.post 와 같은 성질
        return {"ok": False, "detail": "측정용 스텁"}

    monkeypatch.setattr(main, "Start_STT", blocking_stt)
    return main.app


async def _two_concurrent(app):
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://t") as c:
        started = time.perf_counter()
        r1, r2 = await asyncio.gather(
            c.post("/STT/1", json={"meetingId": "77"}),
            c.post("/STT/1", json={"meetingId": "78"}),
        )
        return time.perf_counter() - started, r1, r2


@pytest.mark.asyncio
async def test_two_requests_do_not_serialize(app_with_blocking_stt):
    """★ 동시 요청 2개가 직렬화되면 안 된다.

    직렬화 = 앞 요청이 끝날 때까지 뒤 요청이 시작조차 못 한다.
    운영에서는 이 블로킹이 최대 10분이다.
    """
    elapsed, r1, r2 = await _two_concurrent(app_with_blocking_stt)

    print(f"\n[측정] 동시 2요청 총 {elapsed:.2f}초 "
          f"(직렬 {BLOCK_SECONDS*2:.1f}초 / 병렬 {BLOCK_SECONDS:.1f}초 기대)")

    # 이 시험의 대상은 상태 코드가 아니라 동시성이다.
    # 스텁이 STT 실패를 돌려주므로 502 가 정상이다 (#91 의 상태 코드 정리).
    # 중요한 것은 두 요청이 모두 응답했다는 사실이다.
    assert r1.status_code == r2.status_code == 502
    assert elapsed < BLOCK_SECONDS * 1.6, (
        f"두 요청이 직렬화됐다. {elapsed:.2f}초 걸렸고 "
        f"직렬이면 {BLOCK_SECONDS*2:.1f}초, 병렬이면 {BLOCK_SECONDS:.1f}초다.\n"
        f"async def 안에서 동기 I/O 를 부르면 이벤트 루프가 막힌다.")


@pytest.mark.asyncio
async def test_more_concurrency_still_holds(app_with_blocking_stt):
    """4개를 동시에 보내도 직렬화되지 않는다.

    FastAPI 의 기본 스레드풀은 40 슬롯이다. 그보다 많이 몰리면 대기가 생긴다 -
    이 시험은 "동시성이 있다" 까지만 말하고 "무제한이다" 를 말하지 않는다.
    """
    app = app_with_blocking_stt
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://t") as c:
        started = time.perf_counter()
        await asyncio.gather(*[
            c.post("/STT/1", json={"meetingId": str(70 + i)}) for i in range(4)
        ])
        elapsed = time.perf_counter() - started
    print(f"\n[측정] 동시 4요청 총 {elapsed:.2f}초 (직렬이면 {BLOCK_SECONDS*4:.1f}초)")
    assert elapsed < BLOCK_SECONDS * 2, (
        f"4요청이 {elapsed:.2f}초. 직렬이면 {BLOCK_SECONDS*4:.1f}초다.")
