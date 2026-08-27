"""Java 내부 API 를 부른다. (#133)

경로와 헤더 이름은 전부 `contracts/internal-api.json` 에서 온다.
여기에 문자열로 적힌 경로는 하나도 없다 - 있으면 계약이 두 벌이 된다.
"""
from __future__ import annotations

import os

import requests

from contract import auth_header_name, build_url, load_contract

DEFAULT_TIMEOUT = 10


class InternalApiError(RuntimeError):
    """도구가 사람에게 그대로 보여 줄 수 있는 실패."""


class EduMeetClient:
    def __init__(self, base_url: str | None = None, token: str | None = None,
                 timeout: int = DEFAULT_TIMEOUT, contract: dict | None = None):
        self.base_url = (base_url or os.environ.get("EDUMEET_API_BASE_URL", "")).rstrip("/")
        self.token = token if token is not None else os.environ.get("EDUMEET_INTERNAL_TOKEN", "")
        self.timeout = timeout
        self.contract = contract or load_contract()

    def _headers(self) -> dict:
        return {auth_header_name(self.contract): self.token}

    def _get(self, endpoint_name: str, params: dict | None = None, **path_vars) -> dict:
        if not self.base_url:
            raise InternalApiError(
                "EDUMEET_API_BASE_URL 이 비어 있다. MCP 클라이언트 설정의 env 를 확인한다."
            )
        if not self.token:
            # 토큰이 없으면 Java 가 401 을 준다. 그 401 을 받아서 "인증 실패" 라고
            # 말하면 원인을 서버에서 찾게 된다 - 여기서 먼저 잡는다.
            raise InternalApiError(
                f"{auth_header_name(self.contract)} 값이 비어 있다. "
                "EDUMEET_INTERNAL_TOKEN 을 설정한다."
            )

        url = build_url(self.base_url, endpoint_name, self.contract, **path_vars)
        try:
            response = requests.get(url, headers=self._headers(), params=params,
                                    timeout=self.timeout)
        except requests.RequestException as exc:
            raise InternalApiError(f"백엔드에 닿지 못했다: {url} ({exc})") from exc

        expected = self.contract["endpoints"][endpoint_name]["successStatus"]
        if response.status_code not in expected:
            raise InternalApiError(
                f"{url} 가 {response.status_code} 를 냈다 (기대: {expected}). "
                f"{response.text[:200]}"
            )
        return response.json()

    def list_caption_meetings(self, limit: int = 50) -> dict:
        return self._get("captionMeetings", params={"limit": limit})

    def get_transcript(self, meeting_id: int | str) -> dict:
        return self._get("captionTranscript", meetingId=meeting_id)
