-- 채팅 메시지. (#33)
--
-- INTERACTIVE(화상강의) 세션의 메시지만 저장한다.
-- BROADCAST(라이브방송)는 저장하지 않으므로 이 테이블에 들어오지 않는다.
-- 시청자 수천 명 x 초당 수십 메시지면 쓰기가 폭증하고,
-- 무엇보다 그 쓰기가 브로드캐스트 성능 측정을 가린다.

CREATE TABLE chat_message (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    meeting_id   BIGINT       NOT NULL,
    sender_email VARCHAR(255) NOT NULL,
    content      VARCHAR(1000) NOT NULL,
    sent_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_message_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id)
) ENGINE=InnoDB;

-- 조회는 항상 "이 회의의 최근 메시지" 다. 복합 인덱스로 정렬까지 받는다.
CREATE INDEX idx_chat_message_meeting_sent ON chat_message (meeting_id, sent_at);
