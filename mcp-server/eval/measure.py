"""검색 방식을 같은 질의 세트로 재서 비교한다. (#140)

사용법
    PYTHONPATH=.:eval .venv/bin/python eval/measure.py
    PYTHONPATH=.:eval .venv/bin/python eval/measure.py --with-embedding

★ 무엇을 재는가

  recall@k   정답 조각 중 상위 k 안에 들어온 비율. "찾아는 주는가"
  MRR        첫 정답의 순위 역수 평균. "위에 놓아 주는가"

  둘을 나눠 보는 이유 - 도구 응답은 모델 컨텍스트로 들어간다.
  10건을 주면서 정답이 9등이면 찾아 준 것이 아니다.
"""
from __future__ import annotations

import argparse
import statistics as st
import time

from baselines import rank_char_ngram, rank_shipped, rank_token_or
from queries import ALL, LEXICAL, QUESTION, lexical_overlap

K = 5


def recall_at_k(ranked: list[int], relevant: tuple[int, ...], k: int = K) -> float:
    top = set(ranked[:k])
    return len(top & set(relevant)) / len(relevant)


def reciprocal_rank(ranked: list[int], relevant: tuple[int, ...]) -> float:
    for pos, seg in enumerate(ranked, 1):
        if seg in relevant:
            return 1.0 / pos
    return 0.0


def evaluate(name: str, rank_fn, queries) -> dict:
    recalls, rrs = [], []
    started = time.perf_counter()
    for q in queries:
        ranked = rank_fn(q.text, K)
        recalls.append(recall_at_k(ranked, q.relevant))
        rrs.append(reciprocal_rank(ranked, q.relevant))
    elapsed = (time.perf_counter() - started) / max(1, len(queries))
    return {
        "name": name,
        "recall": st.mean(recalls),
        "mrr": st.mean(rrs),
        "zero": sum(1 for r in recalls if r == 0),
        "ms": elapsed * 1000,
        "n": len(queries),
    }


def table(rows: list[dict], title: str) -> None:
    print(f"\n{title}")
    print(f"  {'방식':<22} {'recall@5':>9} {'MRR':>7} {'0건':>5} {'질의당':>9}")
    print("  " + "─" * 56)
    for r in rows:
        print(f"  {r['name']:<22} {r['recall']:>8.0%} {r['mrr']:>7.2f} "
              f"{r['zero']:>3}/{r['n']:<2} {r['ms']:>7.2f}ms")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--with-embedding", action="store_true")
    a = ap.parse_args()

    methods = [
        ("B 토큰 매칭", rank_token_or),
        ("B' 문자 2-gram", rank_char_ngram),
        ("E 넣은 구현", rank_shipped),
    ]
    if a.with_embedding:
        from embedding_index import rank_embedding, rank_hybrid, model_info
        print(f"\n임베딩 모델: {model_info()}")
        methods += [("C 임베딩", rank_embedding), ("D 하이브리드", rank_hybrid)]

    print(f"\n조각 60개 · 질의 {len(ALL)}개 (용어형 {len(LEXICAL)} / 질문형 {len(QUESTION)}) · k={K}")
    print(f"어휘 겹침  용어형 {st.mean([lexical_overlap(q) for q in LEXICAL]):.0%}"
          f" · 질문형 {st.mean([lexical_overlap(q) for q in QUESTION]):.0%}")

    for label, qs in [("전체", ALL), ("용어형만", LEXICAL), ("질문형만", QUESTION)]:
        table([evaluate(n, f, qs) for n, f in methods], label)

    print("\n질문형에서 어느 방식도 못 찾은 질의")
    unfound = []
    for q in QUESTION:
        if all(recall_at_k(f(q.text, K), q.relevant) == 0 for _, f in methods):
            unfound.append(q)
    if unfound:
        for q in unfound:
            print(f"  - {q.text}  (겹침 {lexical_overlap(q):.0%}, 정답 {list(q.relevant)})")
    else:
        print("  없음")


if __name__ == "__main__":
    main()
