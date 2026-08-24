import os
import sys

AI_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, AI_DIR)

from caption_normalizer import normalize_caption_text


def test_normalizes_common_technical_terms_without_model_call():
    text = "오늘은 python 과 spring boot 로 websocket 서버를 만들겠습니다."

    assert normalize_caption_text(text) == "오늘은 파이썬 과 스프링 부트 로 WebSocket 서버를 만들겠습니다."


def test_keeps_database_product_names_readable():
    text = "mysql 과 postgres 그리고 redis 비용을 비교합니다."

    assert normalize_caption_text(text) == "MySQL 과 PostgreSQL 그리고 Redis 비용을 비교합니다."


def test_does_not_rewrite_inside_larger_korean_words():
    text = "파이썬과 자바스크립트는 이미 한글 표기입니다."

    assert normalize_caption_text(text) == text
