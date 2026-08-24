-- LiveKit egress 를 걷어내고 HLS 를 직접 만들면서 컬럼 이름이 거짓이 됐다. (#123)
--
-- hls_egress_id 에는 더 이상 egress id 가 들어가지 않는다. 우리가 만든 방송 세션 식별자다.
-- 값은 그대로 두고 이름만 바꾼다. 이름이 거짓인 컬럼은 다음 사람이 반드시 잘못 읽는다.
--
-- egress 를 뺀 이유: RoomComposite 가 CPU 4를 요구하는 것은 여러 참가자 화면을
-- 헤드리스 Chrome 으로 렌더링해 "합성" 하기 때문인데, 방송은 발표자 한 명만 나가므로
-- 합성할 것이 없다. 합성이 필요 없는데 합성기를 쓰고 있었다.

ALTER TABLE meeting
    CHANGE COLUMN hls_egress_id broadcast_session_id VARCHAR(100) NULL
        COMMENT '진행 중인 자체 HLS 송출 식별자. NULL 이면 송출 중이 아니다';
