-- 다시보기용 상대 시각. (#61)
--
-- 다시보기는 재생 위치(0:12:34)에 맞춰 그때의 채팅을 보여준다.
-- 절대 시각(sent_at)에서 Meeting.startTime 을 빼서 계산할 수도 있지만
-- 저장 시점에 넣어 둔다 - 방송 시작 시각이 나중에 보정되면
-- 이미 저장된 채팅의 재생 위치가 전부 어긋난다.
--
-- NULL 을 허용하는 이유: 이 컬럼이 생기기 전에 저장된 행이 있다.
-- 그것들은 계산으로 메울 수 있고, 못 메워도 실시간 채팅에는 지장이 없다.

ALTER TABLE chat_message
    ADD COLUMN offset_millis BIGINT NULL COMMENT '회의 시작 시각 기준 경과 밀리초';

-- 다시보기는 "이 회의의 채팅을 재생 순서대로" 읽는다.
CREATE INDEX idx_chat_message_replay ON chat_message (meeting_id, offset_millis);
