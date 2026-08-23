-- 오디오 전용 방송 세션 타입 추가. (#65)
--
-- session_type 은 MySQL ENUM 이라 값을 늘리려면 컬럼 정의를 바꿔야 한다.
-- 애플리케이션 enum 에만 추가하면 저장할 때 Data truncated 로 실패한다.
--
-- 순서를 유지한다. ENUM 은 내부적으로 순서 번호로 저장되므로
-- 기존 값 사이에 끼워 넣으면 이미 저장된 행의 의미가 바뀐다.

ALTER TABLE meeting
    MODIFY COLUMN session_type ENUM('BROADCAST', 'INTERACTIVE', 'AUDIO_BROADCAST') NOT NULL;
