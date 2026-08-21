# 기여 가이드

전체 개발 컨벤션은 **[docs/team-convention.md](docs/team-convention.md)** 를 따릅니다.

## 빠른 참조

### 작업 흐름

```
이슈 생성 → 브랜치 생성 → 커밋 → PR → 리뷰 → 머지
```

`master` 에 직접 커밋하지 않습니다.

### 브랜치

```
<type>/<이슈번호>-<간단한-설명>      예: perf/4-n-plus-one
```

이슈 번호 앞에 `#` 를 붙이지 않습니다. (셸 주석 문자라 push 시 따옴표가 필요해집니다)

### 커밋

```
<type>: <한국어 subject>
```

`feat` · `fix` · `refactor` · `perf` · `test` · `docs` · `style` · `chore`

콜론 뒤에 공백 하나. `fix :` 처럼 콜론 앞에 공백을 넣지 않습니다.

### 성능 측정

**1차 지표는 쿼리 수와 실행계획입니다. 응답 시간(ms)이 아닙니다.**
자세한 근거와 규칙은 [team-convention.md 1.6](docs/team-convention.md#16-성능-측정-원칙-필수) 를 참조하세요.
