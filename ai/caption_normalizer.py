"""Caption text normalization for the hot path.

The realtime caption path cannot spend a model call per caption just to fix
common technical terms.  A small deterministic glossary is cheaper, faster, and
reviewable.  Heavier correction belongs to the post-meeting summary/search path.
"""
from __future__ import annotations

import re


_TERM_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\bpython\b", re.IGNORECASE), "파이썬"),
    (re.compile(r"\bjava\s*script\b", re.IGNORECASE), "자바스크립트"),
    (re.compile(r"\bjavascript\b", re.IGNORECASE), "자바스크립트"),
    (re.compile(r"\bspring\s*boot\b", re.IGNORECASE), "스프링 부트"),
    (re.compile(r"\bspring\b", re.IGNORECASE), "스프링"),
    (re.compile(r"\breact\b", re.IGNORECASE), "리액트"),
    (re.compile(r"\bvue\b", re.IGNORECASE), "Vue"),
    (re.compile(r"\bdocker\b", re.IGNORECASE), "Docker"),
    (re.compile(r"\bkubernetes\b", re.IGNORECASE), "쿠버네티스"),
    (re.compile(r"\bk8s\b", re.IGNORECASE), "쿠버네티스"),
    (re.compile(r"\bgithub\b", re.IGNORECASE), "GitHub"),
    (re.compile(r"\bquery\s*dsl\b", re.IGNORECASE), "QueryDSL"),
    (re.compile(r"\bmysql\b", re.IGNORECASE), "MySQL"),
    (re.compile(r"\bpostgresql\b", re.IGNORECASE), "PostgreSQL"),
    (re.compile(r"\bpostgres\b", re.IGNORECASE), "PostgreSQL"),
    (re.compile(r"\bredis\b", re.IGNORECASE), "Redis"),
    (re.compile(r"\bweb\s*socket\b", re.IGNORECASE), "WebSocket"),
    (re.compile(r"\bwebrtc\b", re.IGNORECASE), "WebRTC"),
    (re.compile(r"\bhls\b", re.IGNORECASE), "HLS"),
]


def normalize_caption_text(text: str) -> str:
    """Normalize common STT variants without calling an LLM.

    The rule is intentionally conservative.  It only rewrites isolated ASCII
    terms that Korean STT often emits inconsistently in technical lectures.
    We do not guess grammar, punctuation, or sentence meaning in the hot path.
    """
    if not text:
        return text

    normalized = text
    for pattern, replacement in _TERM_PATTERNS:
        normalized = pattern.sub(replacement, normalized)
    return normalized
