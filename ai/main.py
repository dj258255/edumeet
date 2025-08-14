# main.py
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
import os, re, glob, wave, traceback, subprocess, requests, time, json, shutil
from dotenv import load_dotenv
from openai import OpenAI
from fpdf import FPDF
app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_credentials=True,
    allow_methods=["*"], allow_headers=["*"],
)

HERE = os.path.dirname(os.path.abspath(__file__))
BASE_AUDIO_DIR = os.environ.get(
    "AUDIO_BASE_DIR",
    os.path.normpath(os.path.join(HERE, "..", "backend", "audio"))
)
MERGE_OUT_DIR = os.environ.get(
    "MERGE_OUT_DIR",
    os.path.normpath(os.path.join(HERE, "..", "FastAPIProject"))
)
os.makedirs(MERGE_OUT_DIR, exist_ok=True)

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


    print("[merge] input_files:")
    for p in input_files:
        size = os.path.getsize(p) if os.path.exists(p) else -1
        hdr = _peek_header(p, 12)
        print(f"  - {p} (size={size} bytes, header={hdr})")

     # 1) 기준 파라미터 확보 (첫 파일 오픈에서 에러가 나면 WAV가 아닐 가능성 큼)
    try:
        with wave.open(input_files[0], "rb") as w0:
            nchannels = w0.getnchannels()
            sampwidth = w0.getsampwidth()
            framerate = w0.getframerate()
            comptype = w0.getcomptype()
            compname = w0.getcompname()
            print(f"[merge] base params: ch={nchannels}, width={sampwidth}, rate={framerate}, comp={comptype}")
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
    print(f"▶️ [STT] 시작: {out_path} (class_id={class_id})")

    # ../backend/.env 로드
    env_path = os.path.join(os.path.dirname(__file__), "../backend/.env")
    print(f"🧩 env 경로: {env_path} exists= {os.path.exists(env_path)}")
    load_dotenv(env_path)

    raw_url = os.getenv("CLOVA_INVOKE_URL", "")
    secret  = os.getenv("CLOVA_SECRET_KEY", "")

    # BASE URL 정규화 → /recognizer/upload 붙여 사용
    try:
        base_url = _normalize_base_url(raw_url)
    except HTTPException as he:
        return {"ok": False, "detail": he.detail}

    endpoint = base_url + "/recognizer/upload"
    print("🌐 BASE_URL:", base_url)
    print("🔚 ENDPOINT:", endpoint)
    print("🔑 SECRET_KEY head:", (secret[:6] + "…") if secret else "None")
    print("🌐 BASE_URL raw repr:", repr(base_url))
    try:
        part = base_url.split("/external/v1/")[1]
        app_id, domain_id = part.split("/")[0], part.split("/")[1]
        print(f"🔎 app_id={app_id}, domain_id={domain_id}")
    except Exception:
        pass

    if not secret:
        return {"ok": False, "detail": "CLOVA_SECRET_KEY 가 비어 있습니다."}
    if not os.path.isfile(out_path):
        return {"ok": False, "detail": f"파일 없음: {out_path}"}

    # 파일 헤더/크기 로그
    hdr12 = _peek_header(out_path, 12)
    size = os.path.getsize(out_path)
    print(f"📦 업로드 파일 크기: {size} bytes, 헤더: {hdr12!r}")

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
    print("🐚 curl 예시:")
    print(
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
        print("⏱️ 타임아웃:", e)
        return {"ok": False, "detail": f"요청 타임아웃: {e}"}
    except Exception as e:
        print("⚠️ 요청 예외:", e)
        return {"ok": False, "detail": f"요청 실패: {e}"}

    took = time.time() - started
    ctype = resp.headers.get("content-type", "")
    print(f"✅ 응답: status={resp.status_code}, content-type={ctype}, took={took:.2f}s")
    try:
        print("🔁 resp headers:", dict(resp.headers))
    except Exception:
        pass
    preview = (resp.text or "")[:300].replace("\n", " ")
    print("📝 응답 미리보기:", preview)

    # 응답 덤프
    transcript_dir = os.path.dirname(out_path)
    debug_path = os.path.join(transcript_dir, "stt_response_debug.txt")
    try:
        with open(debug_path, "w", encoding="utf-8") as fw:
            fw.write(f"HTTP {resp.status_code}\nContent-Type: {ctype}\nTook: {took:.2f}s\n\n")
            fw.write(resp.text or "")
        print("💾 응답 덤프:", debug_path)
    except Exception as e:
        print("⚠️ 응답 덤프 저장 실패:", e)

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
        print("✅ transcript 저장:", transcript_path)
    except Exception as e:
        print("⚠️ transcript 저장 실패:", e)
        return {"ok": True, "text": text, "detail": f"저장 실패: {e}"}

    return {"ok": True, "text": text, "transcript_path": transcript_path}



def _load_openai_clients():
    # ../backend/.env 로드
    env_path = os.path.join(os.path.dirname(__file__), "../backend/.env")
    if os.path.exists(env_path):
        load_dotenv(env_path)
    print("env_path in _load_openai_clients:", env_path)

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
   
    try:
        oai, clean_model, summary_model = _load_openai_clients()
        env_path = os.path.normpath(os.path.join(os.path.dirname(__file__), "../backend/.env"))
        if os.path.exists(env_path):
            load_dotenv(env_path)

        use_claude = os.getenv("USE_GMS_CLAUDE", "false").lower() == "true"
        gms_key    = os.getenv("GMS_KEY", "").strip()
        
        gms_base = os.getenv("GMS_ANTHROPIC_BASE", "https://gms.ssafy.io/gmsapi/api.anthropic.com").rstrip("/")
        
        if not os.path.isfile(transcript_path):
            return {"ok": False, "detail": f"transcript 없음: {transcript_path}"}

        with open(transcript_path, "r", encoding="utf-8") as f:
            raw = f.read()

        def chunk_text(text: str, max_chars: int):
            chunks, buf = [], []
            for line in text.splitlines(keepends=True):
                if sum(len(x) for x in buf) + len(line) > max_chars and buf:
                    chunks.append("".join(buf)); buf = []
                buf.append(line)
            if buf: chunks.append("".join(buf))
            return chunks

        def pick_local_font() -> str | None:
            # 0) ENV가 최우선
            font_env = os.getenv("PDF_FONT_PATH", "").strip()
            if font_env and os.path.exists(font_env):
                return font_env

            # 1) FastAPIProject/fonts (main.py와 같은 폴더)
            candidates = [
                os.path.join(HERE, "fonts"),
                os.path.normpath(os.path.join(HERE, "..", "backend", "fonts")),  # 백엔드 쪽도 fallback
            ]
            for d in candidates:
                if not os.path.isdir(d):
                    continue
                # 우선순위로 NotoSansKR-Regular 우선
                for name in ("NotoSansKR-Regular.ttf", "NotoSansKR-Regular.otf"):
                    p = os.path.join(d, name)
                    if os.path.exists(p):
                        return p
                # 아무 ttf/otf 하나라도
                for fn in os.listdir(d):
                    if fn.lower().endswith((".ttf", ".otf")):
                        return os.path.join(d, fn)
            return None

        def markdown_to_pdf(md_text: str, pdf_path: str):
            font_path = pick_local_font()
            pdf = FPDF(format="A4", unit="mm")
            pdf.set_auto_page_break(auto=True, margin=15)
            pdf.add_page()
            if font_path and os.path.exists(font_path):
                pdf.add_font("KR", "", font_path, uni=True)
                pdf.add_font("KR-B", "", font_path, uni=True)
                pdf.add_font("KR-Mono", "", font_path, uni=True)
                base, bold, mono = "KR", "KR-B", "KR-Mono"
                pdf.set_font(base, size=12)
            else:
                base, bold, mono = "Arial", "Arial", "Courier"  # 한글 깨질 수 있음
                pdf.set_font(base, size=12)
            in_code = False
            for raw_line in md_text.splitlines():
                line = raw_line.rstrip("\n")
                if line.strip().startswith("```"):
                    in_code = not in_code
                    pdf.set_font(mono if in_code else base, size=10 if in_code else 12)
                    continue
                if in_code:
                    pdf.multi_cell(0, 6, txt=line); continue
                if line.startswith("### "):
                    pdf.set_font(bold, size=12); pdf.multi_cell(0,7,line[4:].strip()); pdf.set_font(base,12); pdf.ln(1); continue
                if line.startswith("## "):
                    pdf.set_font(bold, size=14); pdf.multi_cell(0,8,line[3:].strip()); pdf.set_font(base,12); pdf.ln(1); continue
                if line.startswith("# "):
                    pdf.set_font(bold, size=16); pdf.multi_cell(0,9,line[2:].strip()); pdf.set_font(base,12); pdf.ln(1); continue
                if line.strip().startswith("- "):
                    pdf.multi_cell(0,6,"• "+line.strip()[2:]); continue
                if not line.strip():
                    pdf.ln(1); continue
                pdf.multi_cell(0,6,line)
            pdf.output(pdf_path)

        # 1) 전처리(clean) — OpenAI
        system_clean = (
            "너는 한국어 전사 텍스트를 정제하는 도우미다. "
            "원문 의미를 보존하고 환각을 피한다. "
            "해야 할 일: 문장 경계/문장부호 복원, 띄어쓰기·맞춤법 보정, 중복/잡음 최소화. "
            "불명확하면 [불명확]로 표기하고 임의로 보충하지 않는다."
        )
        o3_max = int(os.getenv("O3_CHUNK_CHARS", "9000"))
        clean_chunks = []
        for i, ch in enumerate(chunk_text(raw, o3_max), 1):
            prompt = (
                "아래 한국어 텍스트를 의미 왜곡 없이 정리하세요.\n"
                "- 문장부호/문장 경계 복원, 띄어쓰기/맞춤법 보정\n"
                "- 명백한 중복/잡음은 간단히 정리(사실 추가/삭제 금지)\n"
                "- 고유명사가 한국어 음역일 때, 맥락이 명확하면 원어(예: C++)로 복원\n"
                "- 불명확하면 [불명확] 표기\n\n"
                f"{ch}"

            )
            try:
                resp = oai.responses.create(
                    model=clean_model,
                    input=[
                        {"role":"system","content": system_clean},
                        {"role":"user","content": prompt}
                    ],
                    temperature=0.2,
                    max_output_tokens=2000,
                )
                clean_chunks.append(resp.output_text.strip())
            except Exception:
                comp = oai.chat.completions.create(
                    model=clean_model,
                    messages=[
                        {"role":"system","content": system_clean},
                        {"role":"user","content": prompt}
                    ],
                    temperature=0.2,
                )
                clean_chunks.append(comp.choices[0].message.content.strip())
        cleaned = "\n\n".join(clean_chunks)
        cleaned_path = os.path.join(out_dir, "cleaned.txt")
        with open(cleaned_path, "w", encoding="utf-8") as fw:
            fw.write(cleaned)

        # 2) 맵 요약 — OpenAI
        system_summarize = (
            
            
            "너는 정확한 한국어 필기자다. 환각 없이 핵심을 구조화하고, "
            "수식은 입력에 실제 언급된 경우에만 ```math 블록을 사용한다."
        )
        sum_max = int(os.getenv("OAI_SUMMARY_CHARS", "8000"))
        map_notes = []
        for i, ch in enumerate(chunk_text(cleaned, sum_max), 1):
            prompt = (
                 "아래 텍스트를 한국어 강의 노트로 요약하세요.\n"
                "- 핵심 포인트 3~6개 불릿\n"
                "- 수학/과학/공학 등에서 실제 언급된 공식이 있으면 ```math 블록으로 표기\n"
                "- 입력에 없는 사실 금지, 불명확하면 [불명확]\n\n"
                f"{ch}"
            )
            try:
                resp = oai.responses.create(
                    model=summary_model,
                    input=[
                        {"role":"system","content": system_summarize},
                        {"role":"user","content": prompt}
                    ],
                    temperature=0.3,
                    max_output_tokens=2200,
                )
                map_notes.append(resp.output_text.strip())
            except Exception:
                comp = oai.chat.completions.create(
                    model=summary_model,
                    messages=[
                        {"role":"system","content": system_summarize},
                        {"role":"user","content": prompt}
                    ],
                    temperature=0.3,
                )
                map_notes.append(comp.choices[0].message.content.strip())

        notes_joined = "\n\n---\n\n".join(map_notes)

        # 3) 최종 리듀스 — Claude via GMS (우선)
        final_md = None
        if use_claude and gms_key:
            url = f"{gms_base}/v1/messages"
            headers = {
                "Content-Type": "application/json",
                "x-api-key": gms_key,
                "anthropic-version": "2023-06-01",
            }
            payload = {
                "model": "claude-3-7-sonnet-latest",
                "max_tokens": 4500,
                "system": "너는 한국어 기술 문서 작성자다. 부분 요약들을 하나의 일관된 마크다운 문서로 통합하라. 중복 제거, 용어/표기 통일, 사실 보존, 환각 금지.",
                "messages": [
                    {"role": "user", "content":
                        "다음 '부분 요약 노트'를 통합해 하나의 강의 문서를 만들어라.\n"
                        "- 섹션: # 요약(5~8문장), ## 핵심 개념(불릿으로 리스트), ## 수식/정의(수학/과학 등 수식이 있는 경우만, ```math)\n"
                        "- 중복 제거, 용어 일관성 유지, 사실 추가/삭제 금지\n\n"
                        f"{notes_joined}"
                    }
                ],
            }
            r = requests.post(url, headers=headers, data=json.dumps(payload), timeout=120)
            if r.status_code == 200:
                j = r.json()
                content = j.get("content", [])
                if content and isinstance(content, list) and isinstance(content[0], dict) and "text" in content[0]:
                    final_md = content[0]["text"].strip()
            else:
                print("[GMS Claude] HTTP", r.status_code, r.text[:200])

        # 폴백 — OpenAI reduce
        if not final_md:
            prompt = (
                "다음 요약 노트 묶음을 하나의 문서로 통합하세요. "
                "중복 제거, 용어 일관성 유지, 사실추가 금지. "
                "출력은 Markdown으로 하고 아래 섹션을 포함:\n"
                "1) 요약(5~8문장)\n"
                "2) 핵심 개념 리스트\n"
                "3) 수학, 과학, 공학과 같이 공식이 필요, 언급 되거나 공식이 있으면 설명이 잘 된다면 수식을 표기해줘\n"
                f"{notes_joined}"
            )
            try:
                resp = oai.responses.create(
                    model=summary_model,
                    input=[
                        {"role":"system","content":
                         """
                        내가 한국어로 작성된 방대한 텍스트를 너에게 줄게. 
                        텍스트 내용은 선생님이 학생들에게 가르친 내용, 즉 수업 내용이야. 
                        따라서 텍스트는 수학, 언어, 과학, 역사, 경제, 공학 등 초,중,고, 대학교를 포함해 주식, 부동산 등 다양한 내용일 수 있어. 
                        텍스트는 정말 많은 단어를 포함하고 있기 때문에 나는 텍스트를 잘 요약해서 학생들에게 주고 싶어. 
                        따라서 텍스트에 있는 수업 내용만을 포함하고, hallucinations를 피하고, 한국어 깔끔한 한국어 Markdown을 작성해줘. 
                        """},
                        {"role":"user","content":prompt}
                    ],
                    temperature=0.3,
                    max_output_tokens=3000,
                )
                final_md = resp.output_text.strip()
            except Exception:
                comp = oai.chat.completions.create(
                    model=summary_model,
                    messages=[
                        {"role":"system","content":"You are a senior Korean technical writer. Merge partial notes into one coherent Markdown document."},
                        {"role":"user","content":prompt}
                    ],
                    temperature=0.3,
                )
                final_md = comp.choices[0].message.content.strip()

        # 4) 저장 & PDF
        summary_md_path  = os.path.join(out_dir, "summary.md")
        summary_pdf_path = os.path.join(out_dir, "summary.pdf")
        with open(summary_md_path, "w", encoding="utf-8") as fw:
            fw.write(final_md)

        try:
            markdown_to_pdf(final_md, summary_pdf_path)
        except Exception as pdf_err:
            # 폰트 등으로 실패해도 PDF 파일은 만든다(내용이 일부 깨질 수 있음)
            print(f"[PDF] markdown_to_pdf 실패, fallback 실행: {pdf_err}")
            pdf = FPDF(format="A4", unit="mm")
            pdf.set_auto_page_break(auto=True, margin=15)
            pdf.add_page()
            pdf.set_font("Arial", size=12)
            for line in final_md.splitlines():
                pdf.multi_cell(0, 6, line)
            pdf.output(summary_pdf_path)

        print("✅ summary 저장:", summary_md_path, " / ", summary_pdf_path)

        return {
            "ok": True,
            "summary_path": summary_md_path,
            "summary_pdf_path": summary_pdf_path,
            "clean_path": cleaned_path,
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

def send_summary_to_api(class_id: str, meetingId : str ,md_path: str | None, pdf_path: str | None) -> dict:

    try:
        env_path = os.path.join(os.path.dirname(__file__), "../backend/.env")
        if os.path.exists(env_path):
            load_dotenv(env_path)

        #url_tpl = os.getenv("SUMMARY_UPLOAD_URL", "").strip()
        url=os.getenv("SUMMARY_UPLOAD_URL").strip()

        headers = {"Accept": "application/json"}


        files = {}
        if pdf_path and os.path.isfile(pdf_path):
            files["summaryFile"] = ("summary.pdf", open(pdf_path, "rb"), "application/pdf")
        elif md_path and os.path.isfile(md_path):
            files["summary_md"] = ("summary.md", open(md_path, "rb"), "text/markdown; charset=utf-8")
        else:
            return {"ok": False, "detail": "전송할 파일이 없습니다.(md/pdf 없음)"}


        data = {
            "class_id": str(class_id),
            "meetingId":str(meetingId)
        }
        resp = requests.post(url, headers=headers, data=data, files=files, timeout=60)

        # 파일 핸들 닫기
        for f in files.values():
            try: f[1].close()
            except: pass

        if 200 <= resp.status_code < 300:
            return {"ok": True, "status": resp.status_code, "text": (resp.text or "")[:200]}
        return {"ok": False, "status": resp.status_code, "text": (resp.text or "")[:200]}

    except Exception as e:
        return {"ok": False, "detail": f"업로드 실패: {e}"}


@app.post("/STT/{class_id}")
async def merge_audio(class_id: str, request : Request):
    body = await request.json()
    meeting_id = body.get("meetingId")
    print("파이썬 merge 합병 처리 -> class_id : ", class_id)
    print("meetingId : " , meeting_id)


    in_dir = os.path.join(BASE_AUDIO_DIR, str(class_id))
    print("in_dir : ", in_dir)
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
    print("out_path : ", out_path)

    try:
        wav_ready = [ensure_wav(p) for p in files]
        
        for p in wav_ready:
            size = os.path.getsize(p)
            with open(p, "rb") as f:
                hdr = f.read(12)
            print(f"  - {p} (size={size}, header={hdr})")  # 여기서는 꼭 b'RIFF'가 찍혀야 함

        #1) 음성 파일 Merge
        merged = merge_wav_files(wav_ready, out_path)
        print("merged => ", merged)
        #2) STT
        stt_result = Start_STT(out_path,class_id)
        # STT 실패 시 즉시 반환
        if not stt_result.get("ok"):
            return {
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
            }

         # 3) STT 성공 → 요약 실행
        transcript_path = stt_result.get("transcript_path")
        if not transcript_path:
            return {
                "status": "stt_ok_no_transcript",
                "message": "STT는 성공했지만 transcript 경로가 없습니다.",
                "class_id": class_id,
                "output_path": merged,
                "stt_ok": True,
                "transcript_path": None,
                "summary_ok": False
            }

        summary_result = summarize_text_auto(transcript_path, os.path.dirname(out_path))
        
        upload_result = None
        cleanup_result = None
        if (summary_result or {}).get("ok"):
            upload_result = send_summary_to_api(
                class_id=class_id,
                meetingId=meeting_id,
                md_path=(summary_result or {}).get("summary_path"),
                pdf_path=(summary_result or {}).get("summary_pdf_path"),
            )
            # 업로드가 성공했을 때만 디렉토리 통째 삭제
            if upload_result and upload_result.get("ok"):
                class_out_dir = os.path.join(MERGE_OUT_DIR, str(class_id))
                cleanup_result = cleanup_class_dir(class_out_dir)
            else:
                cleanup_result = {"ok": False, "detail": "업로드 실패로 삭제 건너뜀"}


        
        return {
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
            "upload_result": upload_result,
            "cleanup_result": cleanup_result,
        }
        

    except ValueError as ve:
        # 포맷/파라미터 문제 등
        raise HTTPException(status_code=415, detail=str(ve))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Merge failed: {e}")
