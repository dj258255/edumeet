# main.py
from pydantic import BaseModel
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
import os, re, glob, wave, traceback, subprocess, requests, time, json, shutil, textwrap
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

        def find_kr_font_paths() -> dict:
            
            candidates_dirs = [
                os.path.join(HERE, "fonts"),
                os.path.normpath(os.path.join(HERE, "..", "backend", "fonts")),
            ]
            names = [
                "NotoSansKR-Regular.ttf", "NotoSansKR-Regular.otf",
                "NotoSansKR-Medium.ttf", "NotoSansKR-SemiBold.ttf",
                "NotoSansKR-Bold.ttf", "NotoSansKR-Black.ttf",
                "NotoSansKR-Light.ttf", "NotoSansKR-ExtraLight.ttf",
                "NotoSansKR-ExtraBold.ttf", "NotoSansKR-Thin.ttf",
            ]
            found = {}
            for d in candidates_dirs:
                if not os.path.isdir(d):
                    continue
                lowerfiles = {fn.lower(): os.path.join(d, fn) for fn in os.listdir(d)}
                for n in names:
                    for k, p in lowerfiles.items():
                        if k.endswith(n.lower()):
                            key = n.split(".")[0]  # e.g. NotoSansKR-Bold
                            found[key] = p
            # 대표 regular/bold 결정
            regular = (found.get("NotoSansKR-Regular") or
                       next((p for k, p in found.items() if "Regular" in k), None) or
                       next(iter(found.values()), None))
            bold    = (found.get("NotoSansKR-Bold") or
                       found.get("NotoSansKR-SemiBold") or
                       found.get("NotoSansKR-ExtraBold") or
                       regular)
            return {"regular": regular, "bold": bold, "all": found}
        
        def markdown_to_pdf(md_text: str, pdf_path: str):
            fonts = find_kr_font_paths()
            regular_path = fonts.get("regular")
            bold_path    = fonts.get("bold")

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
                    # 얇은 구분선
                    self.set_draw_color(200, 200, 200)
                    self.set_line_width(0.4)
                    self.line(self.l_margin, self.get_y()+1, self.w - self.r_margin, self.get_y()+1)
                    self.ln(5)

                def footer(self):
                    self.set_y(-15)
                    self.set_font(self.family_base, "", 10)
                    self.set_text_color(120, 120, 120)
                    self.cell(0, 8, f"{self.page_no()}", align="C")

            ACCENT = (34, 197, 94)   # 브랜드 그린
            CODE_BG = (245, 246, 248)
            CALL_BG = (248, 250, 246)

            pdf = PrettyPDF(format="A4", unit="mm")
            pdf.set_left_margin(18)
            pdf.set_right_margin(18)
            pdf.set_auto_page_break(auto=True, margin=18)
            pdf.add_page()

            if regular_path:
                pdf.add_font("NotoKR", "", regular_path, uni=True)
                pdf.add_font("NotoKR", "B", bold_path or regular_path, uni=True)
                # 코드 블록에서도 한글 보이게 동일 폰트 사용
                pdf.add_font("NotoKR-Mono", "", regular_path, uni=True)
            # 기본 폰트
            base_family = pdf.family_base
            mono_family = pdf.family_mono

            usable_w = pdf.w - pdf.l_margin - pdf.r_margin
            line_h   = 6.0
            para_gap = 1.5
            bullet_indent = 5.5
            # 문서 제목(H1 첫 줄) 추출 → 헤더에 사용
            for ln in md_text.splitlines():
                if ln.startswith("# "):
                    pdf.doc_title = ln[2:].strip()
                    break

            pdf.set_font(base_family, "", 12)
            pdf.set_text_color(20, 20, 20)

            def hr(gap=2):
                pdf.set_draw_color(230, 230, 230)
                pdf.set_line_width(0.4)
                pdf.line(pdf.l_margin, pdf.get_y(), pdf.w - pdf.r_margin, pdf.get_y())
                pdf.ln(gap)

            def para(text, h=line_h, fill=False):
                pdf.set_x(pdf.l_margin)
                pdf.multi_cell(usable_w, h, text, fill=fill)
                pdf.ln(para_gap)

            def bullet(text):
                x = pdf.get_x()
                pdf.set_x(pdf.l_margin)
                pdf.cell(bullet_indent, line_h, "•")
                pdf.set_x(pdf.l_margin + bullet_indent)
                pdf.multi_cell(usable_w - bullet_indent, line_h, text)

            # 코드/수식 박스
            in_code = False
            code_is_math = False
            def open_code(is_math=False):
                # 배경 박스 느낌으로 줄마다 fill True
                pdf.ln(0.5)
                pdf.set_fill_color(*CODE_BG if not is_math else (240, 245, 255))
                pdf.set_draw_color(220, 220, 220)
                pdf.set_line_width(0.2)
                pdf.set_font(mono_family, "", 10)
                pdf.set_text_color(40, 40, 40 if not is_math else 0)

            def close_code():
                pdf.set_text_color(20, 20, 20)
                pdf.set_font(base_family, "", 12)
                pdf.ln(1.0)

            # 요약(Callout) 박스 모드
            callout = False
            def open_callout():
                pdf.ln(0.5)
                pdf.set_fill_color(*CALL_BG)
            def close_callout():
                pdf.ln(1.0)

            # 본문 렌더링
            lines = md_text.splitlines()
            i = 0
            while i < len(lines):
                raw = lines[i]
                line = raw.rstrip("\n")

                # 코드/수식 토글
                if line.strip().startswith("```"):
                    if not in_code:
                        tag = line.strip()[3:].strip().lower()
                        code_is_math = (tag == "math")
                        in_code = True
                        open_code(code_is_math)
                    else:
                        in_code = False
                        close_code()
                    i += 1
                    continue
                    
                if in_code:
                    # 줄 단위로 채워진 박스
                    pdf.set_x(pdf.l_margin + 2)
                    pdf.multi_cell(usable_w - 4, 5, line, fill=True)
                    i += 1
                    continue

                 # 섹션 헤딩
                if line.startswith("### "):
                    pdf.set_font(base_family, "B", 13)
                    para(line[4:].strip())
                    pdf.set_font(base_family, "", 12)
                    i += 1
                    continue

                if line.startswith("## "):
                    # 요약 헤딩은 Accent 라벨 + Callout 박스 시작
                    title = line[3:].strip()
                    pdf.set_font(base_family, "B", 16)
                    # 액센트 라벨
                    pdf.set_text_color(*ACCENT)
                    para(title)
                    pdf.set_text_color(20, 20, 20)
                    hr(gap=2)

                    # "요약" 섹션이면 배경 박스 모드
                    if title.replace(" ", "") in ("요약", "Summary"):
                        callout = True
                        open_callout()
                    else:
                        if callout:
                            callout = False
                            close_callout()
                    pdf.set_font(base_family, "", 12)
                    i += 1
                    continue

                if line.startswith("# "):
                    pdf.set_font(base_family, "B", 20)
                    # 큰 타이틀은 아래 여백 조금 더
                    para(line[2:].strip())
                    hr(gap=3)
                    pdf.set_font(base_family, "", 12)
                    i += 1
                    continue

                # 불릿
                if line.strip().startswith("- "):
                    body = line.strip()[2:]
                    if callout:
                        # 콜아웃 내부 불릿은 약간 더 촘촘히 + 배경
                        pdf.set_fill_color(*CALL_BG)
                        x = pdf.get_x()
                        pdf.set_x(pdf.l_margin)
                        pdf.cell(bullet_indent, line_h, "•", fill=True)
                        pdf.set_x(pdf.l_margin + bullet_indent)
                        pdf.multi_cell(usable_w - bullet_indent, line_h, body, fill=True)
                    else:
                        bullet(body)
                    i += 1
                    continue

                # 빈 줄
                if not line.strip():
                    pdf.ln(2 if callout else 1)
                    i += 1
                    continue

                # ✅ 일반 문단
                if callout:
                    pdf.set_fill_color(*CALL_BG)
                    para(line, fill=True)
                else:
                    para(line)
                i += 1

            # 마지막에 열어둔 callout 닫기
            if callout:
                close_callout()

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
            print(f"[PDF] markdown_to_pdf 실패, fallback 실행: {pdf_err}")
            pdf = FPDF(format="A4", unit="mm")
            pdf.set_auto_page_break(auto=True, margin=15)
            pdf.add_page()
            fonts = find_kr_font_paths()
            regular_path = fonts.get("regular")
            if regular_path and os.path.exists(regular_path):
                pdf.add_font("NotoKR", "", regular_path, uni=True)
                pdf.set_font("NotoKR", size=12)
            else:
                pdf.set_font("Helvetica", size=12)

            for line in final_md.splitlines():
                # 긴 라인도 끊어서 출력
                wrapped = textwrap.wrap(line, width=100, break_long_words=True, break_on_hyphens=False) or [""]
                for seg in wrapped:
                    pdf.multi_cell(0, 6, seg)
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

def send_summary_to_api(class_id: str, meetingId: str | None,
                        md_path: str | None, pdf_path: str | None) -> dict:
    """요약본을 Java 로 올린다.

    Java 계약 (InternalMeetingSummaryController)
        POST /api/v1/internal/meetings/{meetingId}/summary
        헤더  X-Internal-Token: <공유 시크릿>
        본문  multipart: summary_md | summary_pdf

    ★ 여기가 오래 끊겨 있었다. (#91)
      - {meetingId} 를 치환하지 않아 URL 에 문자 그대로 나갔다.
        Java 는 %7BmeetingId%7D 를 Long 으로 파싱하려다 400 을 낸다.
      - meetingId 를 폼 필드로 보냈다. Java 는 경로 변수로 받는다.
      - X-Internal-Token 이 없었다. #27 에서 Java 에 검사를 붙이며
        파이썬 쪽을 고치지 않았다. 그 상태로는 전부 403 이다.

      양쪽 다 자기 코드는 맞았다. 맞춰 본 적이 없었을 뿐이라
      어느 쪽 단위 테스트로도 안 잡혔다. 그래서 요청 자체를 테스트로 고정한다.
    """
    try:
        # 설정은 이 서비스 것을 읽는다.
        # 전에는 "../backend/.env" 를 읽었다 - 다른 프로젝트(Express)의 설정 파일이다.
        env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.env")
        if os.path.exists(env_path):
            load_dotenv(env_path)

        url_tpl = os.getenv("SUMMARY_UPLOAD_URL", "").strip()
        if not url_tpl:
            return {"ok": False, "detail": "SUMMARY_UPLOAD_URL 미설정"}

        token = os.getenv("INTERNAL_API_TOKEN", "").strip()
        if not token:
            # 보내 봐야 403 이다. 403 을 받고 원인을 찾는 것보다
            # 보내기 전에 설정 문제라고 말하는 편이 원인에 가깝다.
            return {"ok": False,
                    "detail": "INTERNAL_API_TOKEN 미설정. Java 의 /api/v1/internal/** 은 "
                              "X-Internal-Token 없이는 403 이다."}

        mid = _normalize_meeting_id(meetingId)
        if mid is None:
            return {"ok": False,
                    "detail": f"meetingId 가 없다(값={meetingId!r}). Java 는 경로 변수로 요구한다."}

        url = (url_tpl
               .replace("{meetingId}", str(mid))
               .replace("{meeting_id}", str(mid))
               .replace("{classId}", str(class_id))
               .replace("{class_id}", str(class_id)))

        headers = {
            "Accept": "application/json",
            "X-Internal-Token": token,
        }

        files = {}
        if pdf_path and os.path.isfile(pdf_path):
            files["summary_pdf"] = ("summary.pdf", open(pdf_path, "rb"), "application/pdf")
        elif md_path and os.path.isfile(md_path):
            files["summary_md"] = ("summary.md", open(md_path, "rb"),
                                   "text/markdown; charset=utf-8")
        else:
            return {"ok": False, "detail": "전송할 파일이 없습니다.(md/pdf 없음)"}

        # class_id 는 참고용으로만 남긴다. Java 가 쓰는 식별자는 경로의 meetingId 다.
        data = {"class_id": str(class_id)}

        print("[upload:url]", url)
        print("[upload:token]", token[:4] + "…")
        print("[upload:files]", list(files.keys()))

        resp = requests.post(url, headers=headers, data=data, files=files, timeout=60)

        for f in files.values():
            try:
                f[1].close()
            except Exception:
                pass

        if 200 <= resp.status_code < 300:
            # Java 는 최초 기록이면 201, 재시도로 아무것도 안 바뀌었으면 200 을 준다.
            return {"ok": True, "status": resp.status_code,
                    "already_existed": resp.status_code == 200,
                    "text": (resp.text or "")[:200]}
        if resp.status_code == 403:
            return {"ok": False, "status": 403,
                    "detail": "Java 가 403. X-Internal-Token 값이 양쪽에서 다른지 확인할 것.",
                    "text": (resp.text or "")[:200]}
        return {"ok": False, "status": resp.status_code, "text": (resp.text or "")[:200]}

    except requests.Timeout as e:
        return {"ok": False, "detail": f"업로드 타임아웃(60s): {e}"}
    except Exception as e:
        return {"ok": False, "detail": f"업로드 실패: {e}"}

def _normalize_meeting_id(mid):
    """None, 'null', 'None', 'undefined', '', 공백 등을 None으로.
       숫자/숫자문자열만 허용해서 문자열로 반환."""
    if mid is None:
        return None
    if isinstance(mid, (int, float)) and not isinstance(mid, bool):
        return str(int(mid))
    if isinstance(mid, str):
        s = mid.strip()
        if s == "" or s.lower() in {"null", "none", "undefined"}:
            return None
        return s if s.isdigit() else None
    return None

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

    print("파이썬 merge 합병 처리 -> class_id : ", class_id)
    print("meetingId : " , Meeting_id)
    #print(f"meetingId(raw)={raw_meeting_id!r} -> meeting_id(norm)={meeting_id!r}")

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

        summary_result = summarize_text_auto(transcript_path, os.path.dirname(out_path))
        
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
