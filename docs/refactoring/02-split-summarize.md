# 471줄과 테스트 0개 — 닭-달걀은 어디서 끊는가

> **#135.** `ai/main.py` 의 `summarize_text_auto`.

---

## 반년 전의 나에게 반박한다

#117 에서 `backend_client.py` 를 뗄 때, 이 함수는 일부러 두고 이렇게 적었다.

> 테스트 없이 쪼개면 '동작이 바뀌었는지 알 방법이 없는 변경' 이 된다.
> 그런데 471줄이라 테스트를 쓸 수가 없다 — **닭-달걀이다.**

앞 문장은 맞다. 뒷 문장이 틀렸다.

**닭-달걀은 함수 전체에만 성립했고 조각에는 성립하지 않았다.**
함수 안에는 이미 경계가 있었다. 세어 보면 이렇다.

| 조각 | 줄수 | 성질 | 먼저 시험할 수 있나 |
|---|---:|---|---|
| `chunk_text` | 9 | 순수 함수 | **가능** |
| `find_kr_font_paths` | 30 | 파일시스템만 | **가능** (`tmp_path`) |
| 마크다운 파싱 | ~90 | 순수 함수로 뗄 수 있다 | **가능** |
| FPDF 그리기 | ~160 | 부수효과 | 스모크만 |
| LLM clean/map/reduce | ~120 | 네트워크 | 클라이언트를 주입하면 가능 |

**쪼갤 수 없어서 시험을 못 쓴 게 아니라, 쪼갤 순서를 안 정했던 것이다.**

---

## 결과

```
ai/main.py                471줄 · 시험 0
        ↓
ai/transcript_chunker.py   28줄 · 시험  8      순수
ai/kr_font.py              55줄 · 시험  9      파일시스템만
ai/markdown_blocks.py     115줄 · 시험 19      순수
ai/summary_pdf.py         261줄 · 시험 12      스모크
ai/summary_llm.py         198줄 · 시험 17      클라이언트 주입
ai/main.py                 88줄 · 조립만
```

파이썬 시험 **30 → 98.** (분리한 조각에 새로 쓴 것이 63개, 나머지는 기존 시험이다)

---

## 파싱과 그리기를 가른 것이 제일 컸다

원본은 `markdown_to_pdf` 안에서 **"이 줄이 무슨 블록인가" 와 "그것을 어떻게 그리나" 가
한 루프**에 있었다.

```python
in_code = False
code_is_math = False
callout = False
while i < len(lines):
    if line.strip().startswith("```"):
        ...  open_code(code_is_math)      # 상태 판정과 FPDF 호출이 같은 자리
```

세 개의 상태를 **FPDF 를 부르는 코드가 직접 들고 있었다.**
그래서 *"`## 요약` 다음 불릿은 콜아웃 안인가"* 를 물어볼 방법이
**PDF 를 만들어 눈으로 여는 것밖에 없었다.**

지금은 `parse_markdown_blocks` 가 값을 돌려준다.

```python
assert parse_markdown_blocks("## 요약\n- 하나")[1].in_callout is True
```

---

## 시험을 쓰자마자 결함이 셋 나왔다

**이게 이 작업의 값어치다.** 리팩터링이 아니라 그 리팩터링이 가능하게 만든 시험이 찾았다.

### 1. 되돌림 PDF 가 존재 이유인 조건에서 죽는다

원본에는 이런 전제가 깔려 있었다 — *"폰트가 없으면 글자가 네모로 나온다."*

**아니었다.**

```
fpdf.errors.FPDFUnicodeEncodingException:
  Character "강" at index 2 in text is outside the range of characters
  supported by the font used: "helvetica"
```

fpdf2 2.7 이후로는 **예외가 난다.** 그래서 구조가 이랬다.

```
markdown_to_pdf  실패
    ↓
plain_pdf (Helvetica)  ← 한글이면 여기서도 똑같이 죽는다
    ↓
summarize_text_auto 의 바깥 except
    ↓
ok: False               ← summary.md 는 이미 만들어져 있는데도
```

**요약 본문은 만들어졌는데 PDF 때문에 전부 버려진다.**
다시 하려면 정제와 map/reduce 를 처음부터, 즉 **토큰을 처음부터 다시 쓴다.**

`send_summary_to_api` 는 이미 `pdf_path` 가 없어도 md 만 올리도록 돼 있었다.
받을 준비는 되어 있는데 보내는 쪽이 포기하고 있었다.

### 2. 되돌림 PDF 는 한 줄짜리 문서에서만 돌았다

```
fpdf.errors.FPDFException: Not enough horizontal space to render a single character
```

```python
pdf.multi_cell(0, 6, segment)
```

`multi_cell(0, ...)` 의 폭은 **"지금 x 부터 오른쪽 여백까지"** 다.
fpdf2 의 `multi_cell` 은 끝나고 x 를 셀 오른쪽에 둔다.
되돌리지 않으면 **두 번째 줄부터 폭이 0 에 가까워진다.**

되돌림 경로는 잘 안 도는 길이라 아무도 못 봤다.
**잘 안 도는 길일수록 시험이 유일한 관측 수단이다.**

### 3. "영문이면 폰트 없이도 된다" 도 아니었다

본문이 전부 ASCII 여도 `- ` 를 만나면 `•` 를 찍는다. `•` 는 latin-1 밖이다.

```python
def test_even_ascii_fails_without_a_unicode_font_because_of_the_bullet():
    assert "•" in str(caught.value)
```

**폰트가 없으면 이 서비스의 PDF 는 사실상 만들 수 없다.** 위안이 없다.
그래서 `write_pdf` 가 먼저 확인하고 로그로 말한다.

---

## 한 번도 시험된 적 없던 폴백

`responses` → `chat.completions` 폴백이 **세 번 똑같이** 반복되고 있었다.

```python
try:
    resp = oai.responses.create(...)
    ...output_text
except Exception:
    comp = oai.chat.completions.create(...)
    ...choices[0].message.content
```

세 벌이라 고칠 때 하나를 빠뜨리기 쉽고, **한 번도 실행된 적이 없었다** —
`responses` 가 실패해야 도는 길인데 471줄 안에서는 그 실패를 만들 자리가 없었다.

한곳(`ask`)으로 모으고 가짜 클라이언트로 두 갈래를 다 지나가게 했다.
그러자 폴백에서만 나는 문제도 물어볼 수 있게 됐다.

```python
def test_fallback_drops_max_output_tokens():
    """구 API 는 그 인자를 모른다. 넘기면 TypeError 로 폴백까지 실패한다."""
```

### 클라이언트를 인자로 받는다

전에는 함수 안에서 `_load_openai_clients()` 를 불렀다.
그러면 시험이 돌 때마다 `OPENAI_API_KEY` 를 요구하고, 없으면 예외가 나서
**요약 로직을 한 줄도 못 재본다.**

> 이 저장소가 AI 서비스를 못 띄우는 이유 셋 중 하나가 "유료 키" 다.
> 키가 없어서 못 재던 것을 **키 없이 재게 만든 것**이 이 변경의 절반이다.

---

## 동작을 바꾸지 않은 부분

옮기기만 한 것과 고친 것을 나눠 적는다. 섞으면 나중에 어느 쪽인지 알 수 없다.

| | |
|---|---|
| **그대로 옮김** | `chunk_text` · `find_kr_font_paths` · 프롬프트 문자열 전부 · 블록 판정 순서 · 색상값 |
| **고침** | PDF 실패가 요약을 죽이지 않는다 · `plain_pdf` 의 x 되돌림 · `uni=True` 제거 |

프롬프트는 **공백 하나까지 그대로** 뒀다. 프롬프트가 바뀌면 결과가 바뀌는데
그것은 시험으로 못 잡는다.

`uni=True` 는 fpdf2 2.5.1 부터 무시되는 인자이고 "다음 릴리스에서 제거" 가 예고돼 있다.
`requirements` 가 `fpdf2>=2.7,<3` 이라 **2.x 안에서 사라질 수 있고**, 그때 `TypeError` 로 죽는다.

---

## 남은 것

| | |
|---|---|
| `merge_audio` 174줄 | 오디오 병합. 파일 입출력이라 tmp_path 로 뗄 수 있다. 다음 차례 |
| `Start_STT` 142줄 | CLOVA 호출. 클라이언트 주입으로 같은 방법이 쓰인다 |
| PDF 시각 검증 | 지금은 "죽지 않는가" 까지다. 픽셀 비교는 안 한다 — 폰트 버전에 따라 흔들려 유지비가 값어치를 넘는다 |
