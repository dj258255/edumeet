# main.py
from pydantic import BaseModel
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
import os, re, glob, wave, traceback, subprocess, requests, time, json, shutil

# 백엔드와 말하는 코드는 backend_client.py 로 옮겼다. (#117)
# 요약 파이프라인은 다섯 조각으로 나눴다. (#135)
#
# #117 에서는 summarize_text_auto 를 두고 이렇게 적었다 -
#   "471줄인데 테스트가 0개다. 테스트 없이 쪼개면 동작이 바뀌었는지 알 방법이
#    없고, 471줄이라 테스트를 쓸 수도 없다 - 닭-달걀이다."
#
# 닭-달걀은 함수 전체에만 성립했다. 함수 안에는 이미 경계가 있었고,
# 그중 셋은 순수 함수라 옮기기만 하면 바로 시험할 수 있었다.
# 쪼갤 수 없어서 못 쓴 게 아니라 쪼갤 순서를 안 정했던 것이다.
from backend_client import (
    send_summary_to_api,
    send_captions_to_api,
    choose_summary_transcript,
    _split_into_captions,
    _normalize_meeting_id,
)
# 로그를 JSON 으로 낸다. (#167)
#
#   print 는 레벨도 맥락도 없고 끌 수도 없다. 동시 요청이 섞이면
#   어느 줄이 어느 요청인지 모른다. 자바 쪽은 이미 JSON 이라(#166)
#   파이썬만 평문이면 같은 회의를 두 곳에서 따로 찾아야 한다.
import logging
import logging_setup as _log_ctx
from logging_setup import setup as _setup_logging

_setup_logging("ai")
log = logging.getLogger(__name__)

from dotenv import load_dotenv
from openai import OpenAI

# summarize_text_auto 에서 뗀 조각들. (#135)
#   #117 에서 "471줄이라 테스트를 쓸 수 없다" 고 적어 둔 그 함수다.
#   닭-달걀은 함수 전체에만 성립했고 조각에는 성립하지 않았다.
from summary_llm import (
    clean_transcript,
    join_notes,
    map_summarize,
    reduce_via_gms,
    reduce_via_openai,
)
from summary_pdf import write_pdf
app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_credentials=True,
    allow_methods=["*"], allow_headers=["*"],
)


@app.middleware("http")
async def bind_log_context(request: Request, call_next):
    """이 요청의 모든 로그에 요청 아이디와 회의 번호를 붙인다. (#167)

    자바가 ``X-Request-Id`` 를 실어 보내면 그 값을 쓴다. 그래야
    **서비스 경계를 넘어 한 줄로 묶인다** - 자바 쪽 로그와 파이썬 쪽 로그를
    같은 값으로 찾을 수 있다. 없으면 여기서 만들고 응답에 실어 돌려준다.

    회의 번호는 경로에서 뽑는다. 모아 놓기만 하고 거를 것이 없으면 못 쓴다.
    """
    request_id = request.headers.get("X-Request-Id") or _log_ctx.new_request_id()
    meeting = re.search(r"/(?:STT|summary|captions)/(\d+)", request.url.path)
    _log_ctx.bind(request_id, meeting.group(1) if meeting else "")

    response = await call_next(request)
    response.headers["X-Request-Id"] = request_id
    return response

HERE = os.path.dirname(os.path.abspath(__file__))
BASE_AUDIO_DIR = os.environ.get(
    "AUDIO_BASE_DIR",
    os.path.normpath(os.path.join(HERE, "..", "backend", "audio"))
)
# 산출물(합친 wav · transcript · summary)이 쌓이는 곳. (#134)
#
# 기본값이 저장소 루트의 `FastAPIProject/` 였다 - PyCharm 이 지어 준 프로젝트 이름이
# 그대로 출력 경로가 된 것이다. 두 가지가 같이 잘못돼 있었다.
#
#   1. 이 모듈을 import 만 해도 저장소 루트에 디렉터리가 생겼다.
#      makedirs 가 모듈 최상단에 있어서 테스트 수집만으로도 실행됐다.
#   2. 산출물이 저장소 루트로 나가서 `ai/1/` 같은 실행 결과가 커밋됐다.
#      실제로 1.8MB 짜리 wav 가 이력에 들어가 있었다.
#
# 이제 `ai/var/output` 아래로 모으고 .gitignore 로 막는다.
# makedirs 는 실제로 쓰는 곳(merge_audio)이 이미 하고 있으므로 여기서는 하지 않는다.
MERGE_OUT_DIR = os.environ.get(
    "MERGE_OUT_DIR",
    os.path.join(HERE, "var", "output")
)

def _numeric_key(path: str) -> int:
    """audio_12.wav -> 12 정렬키"""
    name = os.path.basename(path)
    m = re.search(r"(\d+)", name)
    return int(m.group(1)) if m else 0

def _peek_header(path: str, n=16) -> bytes:
    try:
        with open(path, "rb") as f:
            return f.read(n)
    except Exception:
        return b""


def _is_riff(path: str) -> bool:
    try:
        with open(path, "rb") as f:
            return f.read(4) == b"RIFF"
    except Exception:
        return False


def ensure_wav(file_path: str) -> str:
    # 이미 WAV(RIFF)이면 그대로 사용
    try:
        with open(file_path, 'rb') as f:
            if f.read(4) == b'RIFF':
                return file_path
    except Exception:
        pass

    # 변환 경로
    base, _ = os.path.splitext(file_path)
    wav_path = base + ".conv.wav"

    # ffmpeg 변환 (16kHz, mono, PCM 16-bit)
    cmd = ["ffmpeg", "-y", "-i", file_path, "-ar", "16000", "-ac", "1", "-acodec", "pcm_s16le", wav_path]
    subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    # 변환 결과 검증
    with open(wav_path, 'rb') as f:
        if f.read(4) != b'RIFF':
            raise RuntimeError(f"ffmpeg 변환 후에도 WAV가 아닙니다: {wav_path}")

    return wav_path


def merge_wav_files(input_files, out_path):
    """
    wave 모듈로 WAV 병합 (메모리 절약을 위해 블록 단위로 읽어서 씀)
    모든 입력 파일의 (channels, sampwidth, framerate, comptype) 동일해야 함
    """
    if not input_files:
        raise ValueError("병합할 WAV 파일이 없습니다.")


    log.info("[merge] input_files:")
    for p in input_files:
        size = os.path.getsize(p) if os.path.exists(p) else -1
        hdr = _peek_header(p, 12)
        log.info(f"  - {p} (size={size} bytes, header={hdr})")

     # 1) 기준 파라미터 확보 (첫 파일 오픈에서 에러가 나면 WAV가 아닐 가능성 큼)
    try:
        with wave.open(input_files[0], "rb") as w0:
            nchannels = w0.getnchannels()
            sampwidth = w0.getsampwidth()
            framerate = w0.getframerate()
            comptype = w0.getcomptype()
            compname = w0.getcompname()
            log.info(f"[merge] base params: ch={nchannels}, width={sampwidth}, rate={framerate}, comp={comptype}")
    except wave.Error as we:
        # RIFF가 아닌 경우 대부분 여기서 터짐
        raise HTTPException(status_code=415, detail=f"첫 파일이 WAV가 아닙니다: {input_files[0]} ({we})")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"첫 파일 오픈 중 예외: {input_files[0]} ({e})")


    # 2) 출력 파일 생성
    try:
        with wave.open(out_path, "wb") as out:
            out.setnchannels(nchannels)
            out.setsampwidth(sampwidth)
            out.setframerate(framerate)
            out.setcomptype(comptype, compname)

            # 3) 순서대로 이어붙이기
            for fpath in input_files:
                # 빠른 헤더 체크
                if not _is_riff(fpath):
                    raise HTTPException(status_code=415, detail=f"RIFF(WAV)가 아닌 파일: {fpath}")

                try:
                    with wave.open(fpath, "rb") as w:
                        # 파라미터 일치 검사
                        if (w.getnchannels() != nchannels or
                            w.getsampwidth() != sampwidth or
                            w.getframerate() != framerate or
                            w.getcomptype() != comptype):
                            raise HTTPException(
                                status_code=415,
                                detail=(f"오디오 파라미터 불일치: {os.path.basename(fpath)} "
                                        f"(ch={w.getnchannels()}, width={w.getsampwidth()}, "
                                        f"rate={w.getframerate()}, comp={w.getcomptype()}) "
                                        f"vs 기준(ch={nchannels}, width={sampwidth}, "
                                        f"rate={framerate}, comp={comptype})")
                            )

                        # 블록 단위 복사 (frame 단위)
                        block_frames = 64 * 1024
                        remaining = w.getnframes()
                        while remaining > 0:
                            chunk = min(remaining, block_frames)
                            data = w.readframes(chunk)
                            out.writeframes(data)
                            remaining -= chunk
                except wave.Error as we:
                    # 특정 파일에서만 WAV 파싱 오류
                    raise HTTPException(status_code=415, detail=f"WAV 파싱 실패: {fpath} ({we})")
                except HTTPException:
                    # 위에서 상태코드 정해 올린 경우 그대로 던짐
                    raise
                except Exception as e:
                    traceback.print_exc()
                    raise HTTPException(status_code=500, detail=f"파일 처리 중 예외: {fpath} ({e})")
    except HTTPException:
        raise
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"출력 파일 작성 중 예외: {out_path} ({e})")


    return out_path


def _normalize_base_url(url: str) -> str:
    """
    .env에는 base만 있어도 되고, 만약 /recognizer, /recognizer/url, /recognizer/upload가 붙어있으면 떼어낸다.
    """
    if not url:
        raise HTTPException(status_code=500, detail="CLOVA_INVOKE_URL 이 비었습니다.")
    url = url.strip().rstrip("/")
    for suf in ("/recognizer/upload", "/recognizer/url", "/recognizer"):
        if url.endswith(suf):
            url = url[: -len(suf)]
            break
    return url


def Start_STT(out_path: str, class_id: str) -> dict:
    log.info(f"▶️ [STT] 시작: {out_path} (class_id={class_id})")

    # ../backend/.env 로드
    env_path = os.path.join(os.path.dirname(__file__), "../backend/.env")
    log.info(f"🧩 env 경로: {env_path} exists= {os.path.exists(env_path)}")
    load_dotenv(env_path)

    raw_url = os.getenv("CLOVA_INVOKE_URL", "")
    secret  = os.getenv("CLOVA_SECRET_KEY", "")

    # BASE URL 정규화 → /recognizer/upload 붙여 사용
    try:
        base_url = _normalize_base_url(raw_url)
    except HTTPException as he:
        return {"ok": False, "detail": he.detail}

    endpoint = base_url + "/recognizer/upload"
    log.info(f"🌐 BASE_URL: {base_url}")
    log.info(f"🔚 ENDPOINT: {endpoint}")
    log.info(f'🔑 SECRET_KEY head: {(secret[:6] + "…") if secret else "None"}')
    log.info(f"🌐 BASE_URL raw repr: {repr(base_url)}")
    try:
        part = base_url.split("/external/v1/")[1]
        app_id, domain_id = part.split("/")[0], part.split("/")[1]
        log.info(f"🔎 app_id={app_id}, domain_id={domain_id}")
    except Exception:
        pass

    if not secret:
        return {"ok": False, "detail": "CLOVA_SECRET_KEY 가 비어 있습니다."}
    if not os.path.isfile(out_path):
        return {"ok": False, "detail": f"파일 없음: {out_path}"}

    # 파일 헤더/크기 로그
    hdr12 = _peek_header(out_path, 12)
    size = os.path.getsize(out_path)
    log.info(f"📦 업로드 파일 크기: {size} bytes, 헤더: {hdr12!r}")

    # A 방법: 화자 인식/워드 얼라인먼트 OFF
    request_body = {
        "language": "ko-KR",
        "completion": "sync",
        "callback": None,
        "userdata": None,
        "wordAlignment": False,           # OFF
        "fullText": True,
        "forbiddens": None,
        "boostings": None,
        "diarization": {"enable": False}, # OFF
        "sed": None,
    }

    headers = {
        "Accept": "application/json;UTF-8",
        "X-CLOVASPEECH-API-KEY": secret,
    }

    # 재현용 curl
    safe_path = out_path.replace("\\", "/")
    log.info("🐚 curl 예시:")
    log.info(
        'curl -X POST "{url}" '
        '-H "X-CLOVASPEECH-API-KEY: {key}" '
        '-H "Accept: application/json;UTF-8" '
        '-F "media=@{path}" '
        '-F "params={params};type=application/json"'
        .format(url=endpoint, key=(secret[:6] + "…"),
                path=safe_path, params=json.dumps(request_body, ensure_ascii=False))
    )

    started = time.time()
    try:
        with open(out_path, "rb") as f:
            files = {
                # 공식 예제와 동일: 파일 핸들을 그대로 전달
                "media": f,
                "params": (None, json.dumps(request_body, ensure_ascii=False).encode("UTF-8"), "application/json"),
            }
            resp = requests.post(endpoint, headers=headers, files=files, timeout=600)
    except requests.Timeout as e:
        log.warning(f"⏱️ 타임아웃: {e}")
        return {"ok": False, "detail": f"요청 타임아웃: {e}"}
    except Exception as e:
        log.warning(f"⚠️ 요청 예외: {e}")
        return {"ok": False, "detail": f"요청 실패: {e}"}

    took = time.time() - started
    ctype = resp.headers.get("content-type", "")
    log.info(f"✅ 응답: status={resp.status_code}, content-type={ctype}, took={took:.2f}s")
    try:
        log.info(f"🔁 resp headers: {dict(resp.headers)}")
    except Exception:
        pass
    preview = (resp.text or "")[:300].replace("\n", " ")
    log.info(f"📝 응답 미리보기: {preview}")

    # 응답 덤프
    transcript_dir = os.path.dirname(out_path)
    debug_path = os.path.join(transcript_dir, "stt_response_debug.txt")
    try:
        with open(debug_path, "w", encoding="utf-8") as fw:
            fw.write(f"HTTP {resp.status_code}\nContent-Type: {ctype}\nTook: {took:.2f}s\n\n")
            fw.write(resp.text or "")
        log.info(f"💾 응답 덤프: {debug_path}")
    except Exception as e:
        log.warning(f"⚠️ 응답 덤프 저장 실패: {e}")

    # 에러 처리 (메시지 보강)
    if resp.status_code == 404:
        hint = "404=경로 미매핑. 같은 도메인의 Invoke URL/Secret Key인지, URL 끝 경로(/recognizer/upload) 확인."
        return {"ok": False, "detail": f"HTTP 404: {resp.text} | HINT: {hint}"}
    if resp.status_code in (401, 403):
        return {"ok": False, "detail": "키/권한 오류. Secret Key/도메인 짝을 확인하세요."}
    if resp.status_code == 415:
        return {"ok": False, "detail": "전송 형식 오류. multipart(media/params) 구성 확인."}
    if resp.status_code == 400:
        return {"ok": False, "detail": f"요청 파라미터 오류: {resp.text}"}  # (이전 'speaker detect is off' 같은 케이스)
    if resp.status_code != 200:
        return {"ok": False, "detail": f"HTTP {resp.status_code}: {resp.text}"}

    # 결과 저장
    try:
        data = resp.json()
        text = data.get("text") or data.get("result") or json.dumps(data, ensure_ascii=False)
    except Exception:
        text = resp.text
    text = (text or "").strip()

    transcript_path = os.path.join(transcript_dir, "transcript.txt")
    try:
        with open(transcript_path, "w", encoding="utf-8") as fw:
            fw.write(text)
        log.info(f"✅ transcript 저장: {transcript_path}")
    except Exception as e:
        log.warning(f"⚠️ transcript 저장 실패: {e}")
        return {"ok": True, "text": text, "detail": f"저장 실패: {e}"}

    return {"ok": True, "text": text, "transcript_path": transcript_path}



def _load_openai_clients():
    # ../backend/.env 로드
    env_path = os.path.join(os.path.dirname(__file__), "../backend/.env")
    if os.path.exists(env_path):
        load_dotenv(env_path)
    log.info(f"env_path in _load_openai_clients: {env_path}")

    use_gms_openai = os.getenv("USE_GMS_OPENAI", "false").lower() == "true"
    openai_key = os.getenv("OPENAI_API_KEY", "").strip()

    if use_gms_openai:
        gms_key = os.getenv("GMS_KEY", "").strip()
        gms_openai_base = os.getenv("GMS_OPENAI_BASE", "").strip().rstrip("/")
        if not gms_key or not gms_openai_base:
            raise RuntimeError("USE_GMS_OPENAI=true 인데 GMS_KEY 또는 GMS_OPENAI_BASE 가 비어 있습니다.")
        # GMS 프록시 경유 (중요: /v1 붙이기)
        client = OpenAI(api_key=gms_key, base_url=gms_openai_base + "/v1")
    else:
        if not openai_key:
            raise RuntimeError("OPENAI_API_KEY 가 필요합니다.")
        client = OpenAI(api_key=openai_key)

    clean_model   = os.getenv("OPENAI_CLEAN_MODEL",   "gpt-4o-mini")
    summary_model = os.getenv("OPENAI_SUMMARY_MODEL", "gpt-4o-mini")
    return client, clean_model, summary_model


def summarize_text_auto(transcript_path: str, out_dir: str) -> dict:
    """transcript 를 정제 -> 부분 요약 -> 통합 -> md/pdf 로 만든다. (#135)

    ★ 전에는 이 함수가 471줄이었고 테스트가 0개였다.

      #117 에서 backend_client 를 뗄 때 여기는 일부러 두고 이렇게 적었다 -
      "테스트 없이 쪼개면 동작이 바뀌었는지 알 방법이 없고, 471줄이라
       테스트를 쓸 수도 없다. 닭-달걀이다."

      닭-달걀은 **함수 전체에만 성립하고 조각에는 성립하지 않았다.**
      쪼갤 수 없어서 못 쓴 게 아니라 쪼갤 순서를 안 정했던 것이다.
      테스트를 먼저 씌울 수 있는 것부터 뗐다.

          transcript_chunker   순수 함수
          kr_font              파일시스템만
          markdown_blocks      순수 함수 - 파싱과 그리기를 갈랐다
          summary_pdf          블록을 받아 그리기만
          summary_llm          클라이언트를 인자로 받는다

      여기 남은 것은 조립과 파일 입출력이다.
    """
    try:
        client, clean_model, summary_model = _load_openai_clients()

        env_path = os.path.normpath(os.path.join(os.path.dirname(__file__), "../backend/.env"))
        if os.path.exists(env_path):
            load_dotenv(env_path)

        if not os.path.isfile(transcript_path):
            return {"ok": False, "detail": f"transcript 없음: {transcript_path}"}

        with open(transcript_path, "r", encoding="utf-8") as f:
            raw = f.read()

        # 1) 정제
        cleaned = clean_transcript(client, clean_model, raw)
        cleaned_path = os.path.join(out_dir, "cleaned.txt")
        with open(cleaned_path, "w", encoding="utf-8") as fw:
            fw.write(cleaned)

        # 2) 부분 요약 (map)
        notes_joined = join_notes(map_summarize(client, summary_model, cleaned))

        # 3) 통합 (reduce). GMS 를 먼저 보고 안 되면 OpenAI 로 간다 -
        #    GMS 는 사내 프록시라 없을 수 있고, 없다고 요약을 버릴 이유는 없다.
        final_md = None
        if os.getenv("USE_GMS_CLAUDE", "false").lower() == "true":
            gms_key = os.getenv("GMS_KEY", "").strip()
            if gms_key:
                final_md = reduce_via_gms(
                    notes_joined,
                    os.getenv("GMS_ANTHROPIC_BASE",
                              "https://gms.ssafy.io/gmsapi/api.anthropic.com"),
                    gms_key,
                )
        if not final_md:
            final_md = reduce_via_openai(client, summary_model, notes_joined)

        # 4) 저장
        summary_md_path = os.path.join(out_dir, "summary.md")
        summary_pdf_path = os.path.join(out_dir, "summary.pdf")
        with open(summary_md_path, "w", encoding="utf-8") as fw:
            fw.write(final_md)

        # ★ PDF 실패가 요약 전체를 실패시키지 않는다. (#135)
        #
        #   전에는 여기서 난 예외가 바깥 except 까지 올라가 ok:False 가 됐다.
        #   summary.md 는 이미 만들어져 있는데도 그랬다 - 그러면 다시 하려면
        #   정제와 요약을 처음부터, 즉 토큰을 처음부터 다시 쓴다.
        #
        #   send_summary_to_api 는 pdf_path 가 없어도 md 만 올린다.
        pdf_mode = write_pdf(final_md, summary_pdf_path)
        log.info(f'summary 저장: {summary_md_path} / {summary_pdf_path if pdf_mode != "failed" else "(PDF 없음)"} {f"[{pdf_mode}]"}')

        return {
            "ok": True,
            "summary_path": summary_md_path,
            "summary_pdf_path": summary_pdf_path if pdf_mode != "failed" else None,
            "clean_path": cleaned_path,
            "pdf_mode": pdf_mode,
        }

    except Exception as e:
        traceback.print_exc()
        return {"ok": False, "detail": f"summarize_text_auto 실패: {e}"}


def cleanup_class_dir(class_dir: str) -> dict:
    try:
        if not os.path.isdir(class_dir):
            return {"ok": False, "detail": f"디렉토리 없음: {class_dir}"}

        base = os.path.realpath(MERGE_OUT_DIR)
        target = os.path.realpath(class_dir)

        if os.path.dirname(target) != base:
            return {"ok": False, "detail": f"허용 경로 아님: {target} (base={base})"}

        shutil.rmtree(target)
        return {"ok": True, "deleted_dir": target}
    except Exception as e:
        return {"ok": False, "detail": f"디렉토리 삭제 실패: {e}"}

class SttRequest(BaseModel):
    """요청 본문. 이전에는 Request 를 받아 await request.json() 을 했다.

    핸들러를 def 로 바꾸려면 await 를 쓸 수 없으므로 pydantic 모델로 받는다.
    부수효과로 meetingId 누락이 500 이 아니라 422 로 나간다 -
    잘못 보낸 쪽이 무엇을 잘못했는지 알 수 있다.
    """
    meetingId: str | None = None


@app.post("/STT/{class_id}")
def merge_audio(class_id: str, body: SttRequest):
    """음성 병합 → STT → 요약 → 업로드.

    ★ async def 가 아니라 def 다. (#91)

      이 함수는 처음부터 끝까지 동기 블로킹이다.
        merge_wav_files       CPU + 파일 I/O
        Start_STT             requests.post(timeout=600)   최대 10분
        summarize_text_auto   OpenAI 동기 호출
        send_summary_to_api   requests.post(timeout=60)

      FastAPI 는 async def 핸들러를 이벤트 루프에서 직접 돌린다.
      그 안에서 블로킹하면 그동안 이 워커는 다른 요청을 하나도 못 받는다.
      실측: 0.5초 블로킹 두 요청이 1.02초 걸렸다 - 완전히 직렬이다.
      운영에서 그 자리는 10분이다.

      반대로 def 로 두면 FastAPI 가 스레드풀에서 돌린다.
      async 를 붙인 것이 상황을 악화시키고 있었다.

      진짜 비동기화(requests -> httpx.AsyncClient, 파일 I/O 까지)가 더 낫지만
      1,022줄 전체를 손대야 하고, 이 서비스는 회의당 1회 호출이라
      스레드풀로 충분하다. 처리량이 문제가 되면 그때 다시 본다.
      한계는 tests/test_event_loop_blocking.py 에 적어 뒀다.
    """
    Meeting_id = body.meetingId
    #meeting_id = _normalize_base_url(raw_meeting_id)

    log.info(f"파이썬 merge 합병 처리 -> class_id :  {class_id}")
    log.info(f"meetingId :  {Meeting_id}")
    #print(f"meetingId(raw)={raw_meeting_id!r} -> meeting_id(norm)={meeting_id!r}")

    in_dir = os.path.join(BASE_AUDIO_DIR, str(class_id))
    log.info(f"in_dir :  {in_dir}")
    if not os.path.isdir(in_dir):
        raise HTTPException(status_code=400, detail=f"Directory not found: {in_dir}")



    # 대상 파일 수집
    patterns = [os.path.join(in_dir, "audio_*.wav"), os.path.join(in_dir, "*.wav")]
    candidates = []
    for pat in patterns:
        candidates.extend(glob.glob(pat))
    files = sorted(set(candidates), key=_numeric_key)

    if not files:
        raise HTTPException(status_code=404, detail=f"No WAV files found in {in_dir}")

    
    class_out_dir = os.path.join(MERGE_OUT_DIR, str(class_id))
    os.makedirs(class_out_dir, exist_ok=True)
    out_path = os.path.join(class_out_dir, f"Merge__{class_id}.wav")
    log.info(f"out_path :  {out_path}")

    try:
        wav_ready = [ensure_wav(p) for p in files]
        
        for p in wav_ready:
            size = os.path.getsize(p)
            with open(p, "rb") as f:
                hdr = f.read(12)
            log.info(f"  - {p} (size={size}, header={hdr})")  # 여기서는 꼭 b'RIFF'가 찍혀야 함

        #1) 음성 파일 Merge
        merged = merge_wav_files(wav_ready, out_path)
        log.info(f"merged =>  {merged}")
        #2) STT
        stt_result = Start_STT(out_path,class_id)
        # STT 실패 시 즉시 반환
        if not stt_result.get("ok"):
            # ★ 200 이 아니라 502 다. (#91)
            #   외부 의존(STT 서버) 실패는 우리 잘못이 아니고 재시도 여지가 있다.
            #   200 에 담아 보내면 프록시·모니터링·재시도 정책이 전부 성공으로 센다 -
            #   실패율 지표가 0 으로 보이고 알림이 안 울린다.
            raise HTTPException(status_code=502, detail={
                "status": "stt_failed",
                "message": "STT 실패",
                "class_id": class_id,
                "input_dir": in_dir,
                "files_merged": [os.path.basename(f) for f in files],
                "output_path": merged,
                "stt_ok": False,
                "stt_detail": stt_result.get("detail"),
                "summary_ok": False,
                "summary_path": None,
                "summary_pdf_path": None,
                "clean_path": None,
                "summary_detail": "STT 실패로 요약 미수행",
            })

         # 3) STT 성공 → 요약 실행
        transcript_path = stt_result.get("transcript_path")
        if not transcript_path:
            # STT 가 성공이라 했는데 결과 경로가 없다. 그것도 실패다.
            raise HTTPException(status_code=502, detail={
                "status": "stt_ok_no_transcript",
                "message": "STT는 성공했지만 transcript 경로가 없습니다.",
                "class_id": class_id,
                "output_path": merged,
                "stt_ok": True,
                "transcript_path": None,
                "summary_ok": False
            })

        # ★ STT 결과를 자막으로도 보낸다. (#113)
        #   실시간이 아니다 - 녹음이 끝난 뒤 나온 텍스트를 문장 단위로 나눠 보낸다.
        #   다시보기 자막으로 쓰인다. 실패해도 요약은 계속한다 -
        #   자막이 없다고 요약까지 버릴 이유가 없다.
        caption_result = send_captions_to_api(Meeting_id, stt_result.get("text", ""))
        if not (caption_result or {}).get("ok"):
            log.warning(f"[caption] 전송 실패(요약은 계속): {caption_result}")

        summary_input = choose_summary_transcript(Meeting_id, transcript_path, os.path.dirname(out_path))
        if summary_input.get("fallback"):
            log.info(f'[summary:transcript] caption archive 대신 local transcript 사용: {summary_input.get("detail")}')
        else:
            log.info(f'[summary:transcript] caption archive 사용: {summary_input.get("segmentCount")} segments')

        summary_result = summarize_text_auto(summary_input["path"], os.path.dirname(out_path))
        
        upload_result = None
        cleanup_result = None
        if (summary_result or {}).get("ok"):
            upload_result = send_summary_to_api(
                class_id=class_id,
                meetingId=Meeting_id,
                md_path=(summary_result or {}).get("summary_path"),
                pdf_path=(summary_result or {}).get("summary_pdf_path"),
            )
        #업로드가 성공했을 때만 디렉토리 통째 삭제
        if upload_result and upload_result.get("ok"):
            class_out_dir = os.path.join(MERGE_OUT_DIR, str(class_id))
            cleanup_result = cleanup_class_dir(class_out_dir)
        else:
            cleanup_result = {"ok": False, "detail": "업로드 실패로 삭제 건너뜀"}


        
        payload = {
            "status": "summary_done" if (summary_result or {}).get("ok") else "summary_failed",
            "message": "STT 성공 및 요약 처리 완료" if (summary_result or {}).get("ok") else "STT 성공, 요약 실패",
            "class_id": class_id,
            "input_dir": in_dir,
            "files_merged": [os.path.basename(f) for f in files],
            "output_path": merged,
            "stt_ok": True,
            "transcript_path": transcript_path,
            "stt_detail": stt_result.get("detail"),
            "summary_ok": (summary_result or {}).get("ok", False),
            "summary_path": (summary_result or {}).get("summary_path"),
            "summary_pdf_path": (summary_result or {}).get("summary_pdf_path"),
            "clean_path": (summary_result or {}).get("clean_path"),
            "summary_detail": (summary_result or {}).get("detail"),
            "caption_result": caption_result,
            "summary_transcript": summary_input,
            "upload_result": upload_result,
            "cleanup_result": cleanup_result,
        }

        # ★ 요약 실패도 502 다. 성공 경로만 200 이다. (#91)
        if not (summary_result or {}).get("ok"):
            raise HTTPException(status_code=502, detail=payload)
        return payload
        

    except HTTPException:
        # 위에서 의도적으로 던진 502/4xx 를 아래 except Exception 이
        # 500 으로 덮어쓰면 안 된다. 그러면 상태 코드를 나눈 의미가 없다.
        raise
    except ValueError as ve:
        # 포맷/파라미터 문제 등
        raise HTTPException(status_code=415, detail=str(ve))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Merge failed: {e}")
