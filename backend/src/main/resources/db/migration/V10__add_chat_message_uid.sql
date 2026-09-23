-- 다시보기 채팅의 중복을 막는다. (#201)
--
-- ★ 왜 필요한가.
--   저장 대기열을 Redis Stream 으로 옮기면 **최소 한 번 전달**이 된다.
--   소비자가 DB 에 넣고 ACK 하기 전에 죽으면 다른 소비자가 같은 항목을 다시 가져간다.
--   그러면 같은 메시지가 두 번 저장될 수 있다.
--
-- ★ 왜 유니크 인덱스인가.
--   "가져가기 전에 있는지 조회한다" 는 방식은 조회와 삽입 사이에 창이 있다 -
--   두 소비자가 동시에 같은 uid 를 조회하면 둘 다 없다고 보고 둘 다 넣는다.
--   DB 제약이 그 창을 없앤다.
--
-- ★ 왜 NULL 을 허용하나.
--   기존 행에는 uid 가 없다. MySQL 유니크 인덱스는 NULL 을 여럿 허용하므로
--   기존 데이터를 건드리지 않고 붙일 수 있다. (업그레이드 경로에서 중요하다)
ALTER TABLE chat_message
    ADD COLUMN message_uid VARCHAR(36) NULL;

CREATE UNIQUE INDEX uk_chat_message_uid ON chat_message (message_uid);
