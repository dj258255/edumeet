-- 실시간 자막을 회의 후 요약·검색 입력으로 재사용하기 위한 저장 테이블. (#131)
--
-- 자막은 화면에 먼저 도달해야 하므로 요청 경로에서 동기 저장하지 않는다.
-- Java 는 브로드캐스트 후 유계 큐에 넣고, 배치 작업이 이 테이블에 저장한다.
--
-- partial 자막은 계속 바뀌므로 저장하지 않는다. 저장 대상은 final 자막뿐이다.
-- 그래야 회의 후 요약에 같은 말을 여러 번 넣어 토큰을 낭비하지 않는다.

CREATE TABLE caption_segment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    meeting_id BIGINT NOT NULL,
    sequence BIGINT NULL COMMENT '회의 안에서 증가하는 자막 순서. 재시도 멱등성에 쓴다',
    spoken_at BIGINT NULL COMMENT '원본 오디오 기준 발화 시각(epoch millis)',
    received_at BIGINT NOT NULL COMMENT 'Java 가 내부 API 요청을 받은 시각(epoch millis)',
    published_at BIGINT NOT NULL COMMENT 'Java 가 STOMP 로 발행한 시각(epoch millis)',
    text VARCHAR(500) NOT NULL COMMENT '요약과 다시보기에서 쓰는 final 자막 텍스트',
    final_segment TINYINT(1) NOT NULL DEFAULT 1 COMMENT '저장된 행은 final 자막이다. partial 은 저장하지 않는다',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_caption_segment_meeting
        FOREIGN KEY (meeting_id) REFERENCES meeting (id),
    UNIQUE KEY ux_caption_segment_meeting_sequence (meeting_id, sequence),
    KEY idx_caption_segment_transcript (meeting_id, final_segment, sequence, spoken_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
